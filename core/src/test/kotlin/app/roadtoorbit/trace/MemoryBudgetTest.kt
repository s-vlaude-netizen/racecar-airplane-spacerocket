package app.roadtoorbit.trace

import app.roadtoorbit.audio.MusicSynth
import app.roadtoorbit.audio.SoundId
import app.roadtoorbit.audio.SoundSynth
import app.roadtoorbit.game.Bot
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.HudBuffer
import app.roadtoorbit.game.Phase
import app.roadtoorbit.gfx.MeshId
import app.roadtoorbit.gfx.MeshLibrary
import app.roadtoorbit.gfx.SceneRenderer
import app.roadtoorbit.gl.NullGles
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Memory budgets. Phones give an app a Java heap of 128-256 MB and kill it beyond that, so nothing here may
 * leak or allocate wildly. The limits are generous (several times what is measured today); the point is to
 * fail loudly if something starts to grow.
 */
class MemoryBudgetTest {
    private val threads = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private fun allocated(): Long = threads.getThreadAllocatedBytes(Thread.currentThread().id)

    private fun used(): Long {
        repeat(4) { System.gc(); Thread.sleep(20) }
        val rt = Runtime.getRuntime()
        return rt.totalMemory() - rt.freeMemory()
    }

    private fun mb(bytes: Long) = bytes / (1024 * 1024)

    @Test
    fun startupWorkStaysSmall() {
        val base = used()
        val lib = MeshLibrary()
        lib.prewarm()
        val meshes = used() - base
        println("memory: mesh library retains ${meshes / 1024} KB")
        assertTrue(mb(meshes) < 16, "all meshes should stay well under 16 MB, but retain ${mb(meshes)} MB")

        var wav = 0L
        val sfxStart = allocated()
        for (id in SoundId.values()) wav += SoundSynth.wav(SoundSynth.render(id)).size
        val sfx = allocated() - sfxStart
        println("memory: SFX allocate ${sfx / 1024} KB for ${wav / 1024} KB of wav")
        assertTrue(mb(sfx) < 24, "synthesising every sound effect allocated ${mb(sfx)} MB")

        // one loop is rendered at a time (MusicScheduler), so this is the peak the music can ever add
        for (track in MusicSynth.Track.values()) {
            val start = allocated()
            val pcm = MusicSynth.render(track)
            val bytes = allocated() - start
            println("memory: music $track allocates ${bytes / 1024} KB for ${pcm.size * 2 / 1024} KB of pcm")
            assertTrue(mb(bytes) < 40, "rendering the $track loop allocated ${mb(bytes)} MB")
        }
    }

    @Test
    fun playingForeverDoesNotLeakOrChurn() {
        val lib = MeshLibrary().also { it.prewarm() }
        val scene = SceneRenderer(NullGles(), lib)
        scene.resize(1600, 740)
        val hud = HudBuffer()
        val game = Game(11)
        val input = GameInput()
        val dt = 1f / 60f

        // warm everything up (lazy uploads, JIT) with one whole journey, then measure two more
        fun journey(seed: Long, measure: Boolean): Pair<Long, Long> {
            game.startRun(seed)
            val bot = Bot(game)
            var frames = 0L
            var bytes = 0L
            var t = 0f
            while (t < 400f && !(game.phase == Phase.VICTORY && game.phaseTime > 2f) && game.phase != Phase.GAME_OVER) {
                bot.control(input, dt)
                val start = if (measure) allocated() else 0L
                game.update(dt, input)
                scene.rig.update(game, dt)
                while (game.pollSfx() != null) { /* drain */ }
                scene.render(game)
                hud.publish(game)
                if (measure) bytes += allocated() - start
                frames++
                t += dt
            }
            return frames to bytes
        }

        journey(101, measure = false)
        val afterWarmup = used()
        var frames = 0L
        var bytes = 0L
        for (seed in 102L..103L) {
            val (f, b) = journey(seed, measure = true)
            frames += f; bytes += b
        }
        val growth = used() - afterWarmup
        val perFrame = bytes / frames
        println("memory: $frames frames, heap grew ${growth / 1024} KB, ${perFrame} bytes of garbage per frame")
        assertTrue(mb(growth) < 6, "the heap grew by ${mb(growth)} MB over $frames frames - a leak?")
        assertTrue(perFrame < 4096, "a frame (simulation, rendering, HUD) allocates $perFrame bytes - per-frame garbage should stay tiny")
    }
}
