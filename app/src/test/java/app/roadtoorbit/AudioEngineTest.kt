package app.roadtoorbit

import app.roadtoorbit.audio.AudioEngine
import app.roadtoorbit.audio.MusicControl
import app.roadtoorbit.audio.MusicScheduler
import app.roadtoorbit.audio.MusicSynth
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.Phase
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression tests for the out-of-memory crash on real devices: in the menu (and after a crash) the audio
 * engine asked for the music loop and cancelled it again on every frame, and every ask started a thread that
 * synthesised a whole loop. Frames must be cheap and idempotent whatever phase the game is in.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AudioEngineTest {
    /** Wraps a real scheduler and records what the engine asked for, call by call. */
    private class Spy(private val real: MusicControl) : MusicControl {
        val requests = ArrayList<MusicSynth.Track?>()
        override fun request(track: MusicSynth.Track?) { requests.add(track); real.request(track) }
        override fun release() = real.release()

        /** How often the request differed from the one before. */
        fun changes(): Int = requests.indices.count { it == 0 || requests[it] != requests[it - 1] }
    }

    private class Output : MusicScheduler.Output {
        val starts = AtomicInteger()
        val stops = AtomicInteger()
        override fun start(track: MusicSynth.Track, pcm: ShortArray) { starts.incrementAndGet() }
        override fun stop() { stops.incrementAndGet() }
    }

    private fun musicThreads() = Thread.getAllStackTraces().keys.count { it.name == "music" && it.isAlive }

    private class Rig(val renders: AtomicInteger, val output: Output, val spy: Spy, val engine: AudioEngine)

    private fun rig(): Rig {
        val renders = AtomicInteger()
        val output = Output()
        val scheduler = MusicScheduler(output, { _ -> renders.incrementAndGet(); Thread.sleep(80); ShortArray(64) })
        val spy = Spy(scheduler)
        return Rig(renders, output, spy, AudioEngine(RuntimeEnvironment.getApplication(), soundOn = true, musicOn = true, music = spy))
    }

    @Test fun menuFramesAskForTheLoopOnceAndRenderItOnce() {
        val r = rig()
        val game = Game(1)
        assertEquals(Phase.MENU, game.phase)
        repeat(6_000) { r.engine.update(game, paused = false) } // 100 s of frames
        Thread.sleep(400)
        assertEquals("the request changes once (silence -> menu loop), not every frame", 1, r.spy.changes())
        assertEquals("one render for one loop", 1, r.renders.get())
        assertEquals("one start, no stops", 1, r.output.starts.get())
        assertEquals(0, r.output.stops.get())
        assertTrue("one worker at most (found ${musicThreads()})", musicThreads() <= 1)
        r.engine.release()
    }

    @Test fun gameOverFramesAreCheapToo() {
        val r = rig()
        val game = Game(2)
        val input = GameInput()
        game.startRun(31)
        var t = 0f
        while (t < 120f && game.phase != Phase.GAME_OVER) {
            game.update(1f / 60f, input) // nobody steers, so the car hits traffic
            r.engine.update(game, paused = false)
            t += 1f / 60f
        }
        assertEquals(Phase.GAME_OVER, game.phase)
        Thread.sleep(400) // let a car-loop render that was still in flight finish
        val requestsBefore = r.spy.requests.size
        val changesBefore = r.spy.changes()
        val renders = r.renders.get()
        repeat(6_000) { r.engine.update(game, paused = false) }
        Thread.sleep(400)
        assertEquals("sitting on game over only ever asks for silence", changesBefore, r.spy.changes())
        assertTrue(r.spy.requests.size - requestsBefore == 6_000)
        assertEquals("nothing is rendered on game over", renders, r.renders.get())
        assertTrue("one worker at most (found ${musicThreads()})", musicThreads() <= 1)
        r.engine.release()
    }

    @Test fun pausedFramesAndSwitchStormsAreHarmless() {
        val r = rig()
        val game = Game(3)
        repeat(3_000) { r.engine.update(game, paused = true) }
        r.engine.setEnabled(true); r.engine.setMusicEnabled(true)
        r.engine.update(game, paused = false)
        Thread.sleep(400)
        assertEquals("the menu loop is synthesised once however often the frames come", 1, r.renders.get())
        assertEquals("pausing does not interrupt the menu music", 1, r.spy.changes())
        repeat(2_000) {
            r.engine.setMusicEnabled(it % 2 == 0)
            r.engine.setEnabled(it % 3 != 0)
            r.engine.update(game, paused = it % 5 == 0)
        }
        r.engine.setEnabled(true); r.engine.setMusicEnabled(true)
        r.engine.update(game, paused = false)
        Thread.sleep(400)
        assertEquals("flipping the switches never renders the loop again", 1, r.renders.get())
        assertTrue("one worker at most (found ${musicThreads()})", musicThreads() <= 1)
        r.engine.release()
    }
}
