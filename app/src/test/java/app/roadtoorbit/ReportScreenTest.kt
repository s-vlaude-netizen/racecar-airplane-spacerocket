package app.roadtoorbit

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The crash / error report screens: what they show and that their buttons work. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReportScreenTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val w = 1600
    private val h = 740
    private val u = h / 100f

    private val report = """
        Road to Orbit report
        The app was closed by an uncaught exception on thread "music" at 2026-10-06 15:20:11
        java.lang.OutOfMemoryError: Failed to allocate a 32 byte allocation with 294720 free bytes and 287KB until OOM
          at app.roadtoorbit.audio.MusicSynth.render(MusicSynth.kt:31)
          at app.roadtoorbit.audio.AudioEngine${'$'}MusicPlayer.request${'$'}lambda(AudioEngine.kt:211)

        Device: Google Pixel 7, Android 14 (API 34), arm64-v8a, app 1.0 (1)
        Memory: Java heap 254 of 256 MB used, native heap 38 MB
        Threads: 143 (music x120, GLThread x1, main x1)
        Recent: phase MENU leg 0 > action PLAY
    """.trimIndent()

    private fun layout(bridge: UiBridge): HudView {
        val hud = HudView(context, bridge)
        hud.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        hud.layout(0, 0, w, h)
        return hud
    }

    private fun draw(hud: HudView, name: String? = null) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        hud.draw(Canvas(bmp))
        if (name != null) {
            val dir = File(System.getProperty("hudDir") ?: "build/hud").also { it.mkdirs() }
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun tap(hud: HudView, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        hud.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        hud.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun clipboardText(): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.text?.toString()
    }

    @Test fun crashReportFromTheLastSessionCanBeCopiedAndDismissed() {
        CrashReporter.install(context)
        CrashReporter.save("The app was closed", "music", OutOfMemoryError("x")) // leaves a file behind
        val bridge = UiBridge().also { it.crashReport = report }
        val hud = layout(bridge)
        draw(hud, "report_crash") // also lays out the buttons

        tap(hud, w / 2f - 20f * u, h * 0.89f) // COPY REPORT
        assertEquals("the whole report is copied", report, clipboardText())
        assertNotNull(bridge.crashReport)

        tap(hud, w / 2f + 17f * u, h * 0.89f) // CONTINUE
        assertNull("dismissed", bridge.crashReport)
        assertNull("and removed from disk", CrashReporter.pending(context))
        draw(hud) // the game is back
    }

    @Test fun aFatalErrorShowsDetailsAndOnlyOffersCopy() {
        val bridge = UiBridge().also {
            it.fatalError = "Unexpected error: Failed to allocate a 32 byte allocation (SceneRenderer.kt:312)"
            it.fatalDetails = report
        }
        val hud = layout(bridge)
        draw(hud, "report_fatal")
        tap(hud, w / 2f, h * 0.89f) // the single, centred COPY button
        assertEquals(report, clipboardText())
        assertTrue("a fatal error cannot be dismissed", bridge.fatalError != null)
        // tapping elsewhere does nothing (and must not start the game underneath)
        tap(hud, w / 2f, h * 0.4f)
        assertNull(bridge.actions.peek())
    }

    @Test fun theGameUnderneathDoesNotSeeTouchesWhileAReportIsShown() {
        val bridge = UiBridge().also { it.crashReport = report }
        val hud = layout(bridge)
        draw(hud)
        tap(hud, w * 0.5f, h * 0.5f) // would be PLAY on the menu
        tap(hud, w * 0.9f, h * 0.9f)
        assertNull("no UI action reached the game", bridge.actions.peek())
        assertTrue(!bridge.input.boost)
    }
}
