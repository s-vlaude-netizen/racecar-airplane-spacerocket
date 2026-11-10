package app.roadtoorbit.audio

import app.roadtoorbit.audio.MusicSynth.Track
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A loop is rendered on a background thread at the start of each leg, so the ocean loops must not cost more than
 * the most expensive loops that already shipped (the rocket loop and the menu loop). Wall-clock timing is noisy,
 * so each track is timed several times, the best time counts, and the limit is relative to the older loops
 * measured in the same JVM under the same load.
 */
class MusicRenderCostTest {
    private val threads = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private class Cost(val millis: Double, val bytes: Long)

    @Suppress("DEPRECATION")
    private fun measure(track: Track, runs: Int): Cost {
        var best = Double.MAX_VALUE
        var bytes = 0L
        repeat(runs) {
            val a0 = threads.getThreadAllocatedBytes(Thread.currentThread().id)
            val t0 = System.nanoTime()
            MusicSynth.render(track)
            best = minOf(best, (System.nanoTime() - t0) / 1e6)
            bytes = threads.getThreadAllocatedBytes(Thread.currentThread().id) - a0
        }
        return Cost(best, bytes)
    }

    @Test
    fun theOceanLoopsCostLessThanTheBiggestOlderLoops() {
        val older = listOf(Track.ROCKET, Track.MENU)
        val ocean = listOf(Track.OCEAN_PLANE, Track.OCEAN_SUB, Track.OCEAN_CAR, Track.OCEAN_FINALE)
        for (t in older + ocean) measure(t, 1) // warm the JIT up
        val costs = (older + ocean).associateWith { measure(it, 3) }
        for ((t, c) in costs) println("music cost: %-14s %7.1f ms  %6d KB allocated".format(t, c.millis, c.bytes / 1024))
        val slowest = older.maxOf { costs.getValue(it).millis }
        val biggest = older.maxOf { costs.getValue(it).bytes }
        for (t in ocean) {
            val c = costs.getValue(t)
            assertTrue(c.millis < slowest * 1.5 + 50.0, "$t renders in %.0f ms; the slowest older loop takes %.0f ms".format(c.millis, slowest))
            assertTrue(c.bytes <= biggest, "$t allocates ${c.bytes / 1024} KB; the biggest older loop ${biggest / 1024} KB")
        }
    }
}
