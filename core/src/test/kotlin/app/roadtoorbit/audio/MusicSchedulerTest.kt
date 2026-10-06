package app.roadtoorbit.audio

import app.roadtoorbit.audio.MusicSynth.Track
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class MusicSchedulerTest {
    private class Recorder : MusicScheduler.Output {
        val events = CopyOnWriteArrayList<String>()
        override fun start(track: Track, pcm: ShortArray) { events.add("start:$track") }
        override fun stop() { events.add("stop") }
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 10_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) fail("timed out waiting for: $what")
            Thread.sleep(2)
        }
    }

    private fun liveThreads(name: String) = Thread.getAllStackTraces().keys.count { it.name == name && it.isAlive }

    /**
     * The crash the app shipped with: every frame in the menu asked for the menu loop and then cancelled it
     * again, and every ask started a new thread that synthesised a whole loop (13 MB each) - dozens of them at
     * once until the heap was gone. However often and however fast requests flip, there must be one worker and
     * one render.
     */
    @Test
    fun aStormOfFlipFloppingRequestsRendersOnceOnOneThread() {
        val out = Recorder()
        val renders = AtomicInteger()
        val renderThreads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val s = MusicScheduler(out, { _ ->
            renders.incrementAndGet()
            renderThreads.add(Thread.currentThread().name)
            Thread.sleep(150) // a slow phone
            ShortArray(16)
        }, "music-storm")

        repeat(5_000) {
            s.request(Track.MENU)
            s.request(null)
        }
        s.request(Track.MENU)
        awaitUntil("the menu loop to start") { out.events.contains("start:MENU") }
        s.release()

        assertEquals(1, renders.get(), "each loop is rendered once, however often it is requested")
        assertEquals(setOf("music-storm"), renderThreads)
        awaitUntil("the worker to end") { liveThreads("music-storm") == 0 }
    }

    @Test
    fun repeatedIdenticalRequestsDoNothing() {
        val out = Recorder()
        val renders = AtomicInteger()
        val s = MusicScheduler(out, { _ -> renders.incrementAndGet(); ShortArray(16) }, "music-same")
        repeat(100_000) { s.request(Track.CAR) }
        awaitUntil("start") { out.events.contains("start:CAR") }
        repeat(100_000) { s.request(Track.CAR) }
        Thread.sleep(50)
        s.release()
        awaitUntil("stop at release") { out.events.contains("stop") }
        assertEquals(listOf("start:CAR", "stop"), out.events.toList())
        assertEquals(1, renders.get())
    }

    @Test
    fun switchingLoopsReplacesTheOldOneOnlyWhenTheNewOneIsReady() {
        val out = Recorder()
        val s = MusicScheduler(out, { _ -> Thread.sleep(60); ShortArray(16) }, "music-switch")
        s.request(Track.MENU)
        awaitUntil("menu") { out.events.contains("start:MENU") }
        s.request(Track.CAR)
        awaitUntil("car") { out.events.contains("start:CAR") }
        s.release()
        awaitUntil("stop at release") { out.events.last() == "stop" }
        // no silence in between: the old loop keeps playing until the new one replaces it
        assertEquals(listOf("start:MENU", "start:CAR", "stop"), out.events.toList())
    }

    @Test
    fun silenceStopsThePlaybackAndACachedLoopComesBackInstantly() {
        val out = Recorder()
        val renders = AtomicInteger()
        val s = MusicScheduler(out, { _ -> renders.incrementAndGet(); ShortArray(16) }, "music-silence")
        s.request(Track.PLANE)
        awaitUntil("start") { out.events.size == 1 }
        s.request(null)
        awaitUntil("stop") { out.events.size == 2 }
        s.request(Track.PLANE)
        awaitUntil("restart") { out.events.size == 3 }
        s.release()
        assertEquals(listOf("start:PLANE", "stop", "start:PLANE"), out.events.take(3).toList())
        assertEquals(1, renders.get(), "the second start comes from the cache")
    }

    @Test
    fun aLoopThatIsNoLongerWantedWhenItFinishesRenderingIsNotStarted() {
        val out = Recorder()
        val gate = java.util.concurrent.CountDownLatch(1)
        val s = MusicScheduler(out, { t -> if (t == Track.CAR) gate.await(); ShortArray(16) }, "music-moved-on")
        s.request(Track.CAR)           // blocks inside the render
        Thread.sleep(50)
        s.request(Track.MENU)          // the game moved on meanwhile
        gate.countDown()
        awaitUntil("menu") { out.events.contains("start:MENU") }
        s.release()
        assertTrue("start:CAR" !in out.events, "the stale loop must not start: ${out.events}")
    }

    @Test
    fun aFailingRenderIsRememberedAndDoesNotKillTheWorker() {
        val out = Recorder()
        val attempts = AtomicInteger()
        val s = MusicScheduler(out, { t ->
            if (t == Track.ROCKET) { attempts.incrementAndGet(); throw OutOfMemoryError("simulated") }
            ShortArray(16)
        }, "music-failing")
        repeat(1_000) { s.request(Track.ROCKET); Thread.yield(); s.request(null) }
        s.request(Track.ROCKET)
        Thread.sleep(100)
        s.request(Track.MENU)
        awaitUntil("menu after the failure") { out.events.contains("start:MENU") }
        s.release()
        assertEquals(1, attempts.get(), "a loop that failed to render is not retried every frame")
    }

    @Test
    fun releaseStopsThePlaybackAndEndsTheThread() {
        val out = Recorder()
        val s = MusicScheduler(out, { _ -> ShortArray(16) }, "music-release")
        s.request(Track.FINALE)
        awaitUntil("start") { out.events.contains("start:FINALE") }
        s.release()
        awaitUntil("stop") { out.events.contains("stop") }
        awaitUntil("thread end") { liveThreads("music-release") == 0 }
        s.request(Track.MENU) // ignored after release
        Thread.sleep(30)
        assertEquals(0, liveThreads("music-release"))
    }

    @Test
    fun aBrokenOutputNeverEscapesTheWorker() {
        val s = MusicScheduler(object : MusicScheduler.Output {
            override fun start(track: Track, pcm: ShortArray) = throw IllegalStateException("no audio device")
            override fun stop() = throw IllegalStateException("no audio device")
        }, { _ -> ShortArray(16) }, "music-broken")
        s.request(Track.MENU)
        s.request(null)
        s.request(Track.CAR)
        Thread.sleep(100)
        assertEquals(1, liveThreads("music-broken"), "the worker survives output failures")
        s.release()
        awaitUntil("thread end") { liveThreads("music-broken") == 0 }
    }
}
