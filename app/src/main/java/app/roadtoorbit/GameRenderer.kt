package app.roadtoorbit

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import app.roadtoorbit.audio.AudioEngine
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
) : GLSurfaceView.Renderer {
    val game = Game(System.nanoTime()).also { it.bestScore = prefs.bestScore }

    private val library = MeshLibrary()
    private var scene: SceneRenderer? = null
    private var width = 1
    private var height = 1
    private var lastNanos = 0L
    private var failed = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            scene?.release()
            scene = SceneRenderer(AndroidGles(), library).also { it.resize(width, height) }
            failed = false
            lastNanos = 0L
            bridge.fatalError = null
        } catch (t: Throwable) {
            failed = true
            bridge.fatalError = "Graphics setup failed: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
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
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1e9f).coerceIn(0.0005f, 0.05f)
        lastNanos = now

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
            audio.updateEngine(game)
            if (game.phase == Phase.VICTORY || game.phase == Phase.GAME_OVER) {
                if (game.bestScore > prefs.bestScore) prefs.bestScore = game.bestScore
            }
        } else {
            audio.silenceEngine()
        }
        s.render(game)
        bridge.hud.publish(game)
        if (!bridge.ready) bridge.ready = true
    }

    private fun handle(a: UiAction) {
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
            UiAction.CALIBRATE_TILT -> Unit
        }
    }

    /** Called from the UI thread's onPause so the GL thread stops making noise. */
    fun onAppPaused() {
        audio.silenceEngine()
    }

    fun release() {
        scene?.release()
        scene = null
    }
}
