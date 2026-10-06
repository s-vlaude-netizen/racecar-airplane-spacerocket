package app.roadtoorbit

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashReporterTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun fresh() {
        CrashReporter.install(context)
        CrashReporter.clear(context)
    }

    @Test fun theReportHoldsWhatExplainsACrash() {
        CrashReporter.note("phase MENU leg 0")
        CrashReporter.note("action PLAY")
        val text = CrashReporter.format("The app was closed", "GLThread 12", IllegalStateException("boom", IOException("disk gone")))
        for (part in listOf(
            "Road to Orbit report", "The app was closed on thread \"GLThread 12\"", "java.lang.IllegalStateException: boom",
            "Caused by: java.io.IOException: disk gone", "Device:", "Memory: Java heap", "Threads:", "Recent: ", "phase MENU leg 0 > action PLAY",
        )) assertTrue("missing '$part' in:\n$text", text.contains(part))
        assertTrue("a report stays small enough to read and paste (${text.length})", text.length <= 5_000)
    }

    @Test fun theThreadCensusMakesAThreadStormObvious() {
        val threads = (1..30).map { i -> Thread({ Thread.sleep(2_000) }, "music-$i").apply { isDaemon = true; start() } }
        try {
            val text = CrashReporter.format("x", "main", RuntimeException("storm"))
            assertTrue("thread census should name the 30 'music' threads:\n$text", Regex("music x(\\d+)").find(text)?.groupValues?.get(1)?.toInt()?.let { it >= 30 } == true)
        } finally {
            threads.forEach { it.interrupt() }
        }
    }

    @Test fun savingAndReadingBack() {
        assertNull(CrashReporter.pending(context))
        val text = CrashReporter.save("The game hit an error", "GLThread 3", OutOfMemoryError("Failed to allocate a 32 byte allocation"))
        assertNotNull(text)
        val back = CrashReporter.pending(context)
        assertEquals(text, back)
        assertTrue(back!!.contains("OutOfMemoryError: Failed to allocate"))
        CrashReporter.clear(context)
        assertNull(CrashReporter.pending(context))
    }

    @Test fun anUncaughtExceptionLeavesAReportAndStillReachesTheOldHandler() {
        val seen = AtomicReference<Throwable>()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> seen.set(e) }
        try {
            CrashReporter.install(context) // chains in front of the handler set above
            val t = Thread { throw IllegalArgumentException("kaput") }.apply { name = "worker-under-test" }
            t.start()
            t.join()
            val report = CrashReporter.pending(context)
            assertNotNull("the crash should have been written to disk", report)
            assertTrue(report!!.contains("kaput"))
            assertTrue(report.contains("worker-under-test"))
            assertTrue("the previous handler must still run (the system's crash dialog)", seen.get() is IllegalArgumentException)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(old)
        }
    }

    @Test fun savingNeverThrowsEvenForAHostileException() {
        val hostile = object : RuntimeException() {
            override val message: String get() = throw IllegalStateException("getMessage explodes")
            override fun getStackTrace(): Array<StackTraceElement> = throw IllegalStateException("getStackTrace explodes")
        }
        val text = CrashReporter.save("kind", "t", hostile)
        assertNotNull(text)
        assertTrue(text!!.contains("kind on thread \"t\""))
    }
}
