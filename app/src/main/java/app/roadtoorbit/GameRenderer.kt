package app.roadtoorbit

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import app.roadtoorbit.audio.AudioEngine
import app.roadtoorbit.game.Difficulty
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.Phase
import app.roadtoorbit.game.Sfx
import app.roadtoorbit.gfx.MeshLibrary
import app.roadtoorbit.gfx.SceneRenderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Runs on the GL thread: advances the simulation, renders, publishes the HUD snapshot and drives
 * audio/haptics. GPU resources are rebuilt whenever the surface (GL context) is recreated; the
 * game itself survives that.
 */
class GameRenderer(
    private val bridge: UiBridge,
    private val audio: AudioEngine,
    private val prefs: Prefs,
    private val haptic: (Boolean) -> Unit,
    private val applyRenderScale: (Float) -> Unit,
) : GLSurfaceView.Renderer {
    val game = Game(System.nanoTime()).also {
        it.difficulty = Difficulty.values()[prefs.difficulty]
        it.bestScore = prefs.best(it.difficulty)
    }

    private val library = MeshLibrary().also { lib ->
        // generate all procedural geometry off the GL thread so nothing hitches mid-game
        Thread({
            try {
                lib.prewarm()
            } catch (t: Throwable) {
                Log.w(TAG, "mesh prewarm failed; the remaining meshes are built on demand", t)
            }
        }, "mesh-prewarm").apply { isDaemon = true; start() }
    }
    private var scene: SceneRenderer? = null
    private var width = 1
    private var height = 1
    private var lastNanos = 0L
    private var failed = false
    private var recentFailures = 0
    private var lastFailureNanos = 0L
    private var lastPhase: Phase? = null

    // adaptive resolution: if frames stay slow, render fewer pixels (the HUD stays sharp)
    private var scale = prefs.renderScale
    private var avgDt = 1f / 60f
    private var slowFrames = 0
    private var settleFrames = 120

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            scene?.release()
            scene = SceneRenderer(AndroidGles(), library).also { it.resize(width, height) }
            failed = false
            lastNanos = 0L
            bridge.fatalError = null
        } catch (t: Throwable) {
            Log.e(TAG, "graphics setup failed", t)
            failed = true
            bridge.fatalDetails = CrashReporter.save("Graphics setup failed", Thread.currentThread().name, t)
            bridge.fatalError = describe("Graphics setup failed", t)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        CrashReporter.note("surface ${w}x$h")
        if (w <= 0 || h <= 0) return // transient while windows resize; keep the last usable size
        width = w
        height = h
        GLES30.glViewport(0, 0, w, h)
        scene?.resize(w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        val s = scene
        if (failed || s == null) {
            GLES30.glClearColor(0.25f, 0.02f, 0.05f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            return
        }
        try {
            frame(s)
        } catch (t: Throwable) {
            onFrameFailure(t)
        }
    }

    /**
     * A frame threw. A one-off (say, a memory spike) is survivable: save a report, go back to the menu with a
     * fresh game and carry on. Three failures within ten seconds mean it is not going away: stop and say what it is.
     */
    private fun onFrameFailure(t: Throwable) {
        Log.e(TAG, "frame failed", t)
        val now = System.nanoTime()
        if (now - lastFailureNanos > 10_000_000_000L) recentFailures = 0
        lastFailureNanos = now
        recentFailures++
        val thread = Thread.currentThread().name
        if (recentFailures >= 3) {
            failed = true
            audio.silenceAll()
            bridge.fatalDetails = CrashReporter.save("The game hit repeated errors and stopped", thread, t)
            bridge.fatalError = describe("Unexpected error", t)
        } else {
            CrashReporter.save("The game hit an error and went back to the menu", thread, t)
            try {
                game.toMenu()
                bridge.paused = false
                bridge.input.neutral()
            } catch (inner: Throwable) {
                Log.e(TAG, "recovery failed", inner)
            }
        }
    }

    private fun frame(s: SceneRenderer) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1e9f).coerceIn(0.0005f, 0.05f)
        lastNanos = now
        adaptResolution(dt)

        while (true) {
            val a = bridge.actions.poll() ?: break
            handle(a)
        }

        if (!bridge.paused) {
            game.update(dt, bridge.input)
            s.rig.update(game, dt)
            var sfx = game.pollSfx()
            while (sfx != null) {
                audio.play(sfx)
                if (sfx == Sfx.HIT || sfx == Sfx.EXPLOSION) haptic(true)
                else if (sfx == Sfx.TRANSFORM || sfx == Sfx.TOUCHDOWN) haptic(false)
                sfx = game.pollSfx()
            }
            if (game.phase == Phase.VICTORY || game.phase == Phase.GAME_OVER) {
                if (game.bestScore > prefs.best(game.difficulty)) prefs.setBest(game.difficulty, game.bestScore)
            }
        }
        if (game.phase != lastPhase) {
            lastPhase = game.phase
            CrashReporter.note("phase ${game.phase} leg ${game.legIndex}")
        }
        // states what should be audible right now; idempotent, so calling it every frame (paused or not) is fine
        audio.update(game, bridge.paused)
        s.render(game)
        bridge.hud.publish(game)
        if (!bridge.ready) bridge.ready = true
    }

    private fun adaptResolution(dt: Float) {
        if (bridge.paused || game.phase != Phase.RUN) return
        if (settleFrames > 0) { settleFrames--; return }
        avgDt += (dt - avgDt) * 0.05f
        // sustained ~35 fps or worse → step the resolution down (never back up, to avoid flip-flopping)
        if (avgDt > 0.028f && scale > MIN_SCALE) slowFrames++ else slowFrames = 0
        if (slowFrames > 150) {
            slowFrames = 0
            scale = (scale - 0.15f).coerceAtLeast(MIN_SCALE)
            settleFrames = 180
            avgDt = 1f / 60f
            prefs.renderScale = scale
            applyRenderScale(scale)
        }
    }

    /** The scale to apply as soon as the surface exists (restored from a previous session). */
    val initialScale: Float get() = scale

    private fun handle(a: UiAction) {
        CrashReporter.note("action $a")
        when (a) {
            UiAction.PLAY -> {
                game.startRun()
                bridge.paused = false
                audio.play(Sfx.UI)
            }
            UiAction.RETRY -> {
                game.retryLeg()
                bridge.paused = false
                audio.play(Sfx.UI)
            }
            UiAction.MENU -> {
                game.toMenu()
                bridge.paused = false
                audio.silenceEngine()
            }
            UiAction.TOGGLE_SOUND -> {
                bridge.soundOn = !bridge.soundOn
                prefs.soundOn = bridge.soundOn
                audio.setEnabled(bridge.soundOn)
                if (bridge.soundOn) audio.play(Sfx.UI)
            }
            UiAction.TOGGLE_TILT -> {
                bridge.tiltOn = !bridge.tiltOn
                prefs.tiltOn = bridge.tiltOn
                audio.play(Sfx.UI)
            }
            UiAction.CYCLE_DIFFICULTY -> {
                game.difficulty = game.difficulty.next()
                prefs.difficulty = game.difficulty.ordinal
                game.bestScore = prefs.best(game.difficulty)
                audio.play(Sfx.UI)
            }
            UiAction.TOGGLE_MUSIC -> {
                bridge.musicOn = !bridge.musicOn
                prefs.musicOn = bridge.musicOn
                audio.setMusicEnabled(bridge.musicOn)
                audio.play(Sfx.UI)
            }
        }
    }

    /** Called from the UI thread's onPause so the GL thread stops making noise. */
    fun onAppPaused() {
        audio.silenceAll()
    }

    fun release() {
        scene?.release()
        scene = null
    }

    /** One short line for the error screen: what failed and where; the full trace goes to Logcat. */
    private fun describe(what: String, t: Throwable): String {
        val at = t.stackTrace.firstOrNull()?.let { " (${it.fileName}:${it.lineNumber})" } ?: ""
        val reason = (t.message ?: t.javaClass.simpleName).replace(Regex("\\s+"), " ")
        return "$what: $reason$at".take(120)
    }

    private companion object {
        const val MIN_SCALE = 0.55f
        const val TAG = "RoadToOrbit"
    }
}
