package app.roadtoorbit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import app.roadtoorbit.game.Difficulty
import app.roadtoorbit.game.HudState
import app.roadtoorbit.game.Phase
import app.roadtoorbit.gfx.VehicleMode
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The HUD is redrawn every frame on the UI thread, so a frame's worth of garbage there has to stay small. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HudAllocationTest {
    // the Android compile classpath hides java.lang.management, the JVM running the test has it
    private val mx = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)!!
    private val getAllocated = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE)
    private fun allocated(): Long = getAllocated.invoke(mx, Thread.currentThread().id) as Long

    private fun bytesPerDraw(phase: Phase, leg: Int, paused: Boolean = false): Long {
        val bridge = UiBridge().also { it.paused = paused }
        val s: HudState = bridge.hud.latest
        s.phase = phase; s.legIndex = leg; s.legName = "GRAND PRIX"; s.legSubtitle = "Race to the launch gate"
        s.mode = VehicleMode.values()[leg]; s.journey = 0.4f; s.legProgress = 0.5f; s.zoneProgress = 0.3f; s.transformReady = true
        s.score = 12840; s.bestScore = 22310; s.health = 2; s.maxHealth = 3; s.difficulty = Difficulty.NORMAL; s.boost = 0.6f
        s.speedKmh = 187; s.altitudeKm = 38.4f; s.popupCount = 2
        s.popupText[0] = "CLOSE CALL +40"; s.popupTtl[0] = 1f; s.popupAge[0] = 0.3f
        s.popupText[1] = "NITRO!"; s.popupTtl[1] = 1f; s.popupAge[1] = 0.5f
        val hud = HudView(RuntimeEnvironment.getApplication(), bridge)
        hud.measure(View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(740, View.MeasureSpec.EXACTLY))
        hud.layout(0, 0, 1600, 740)
        val canvas = Canvas(Bitmap.createBitmap(1600, 740, Bitmap.Config.ARGB_8888))
        repeat(30) { hud.draw(canvas) } // warm up
        val frames = 200
        val start = allocated()
        repeat(frames) { hud.draw(canvas) }
        return (allocated() - start) / frames
    }

    @Test fun aHudFrameDoesNotCreateMuchGarbage() {
        val results = linkedMapOf(
            "menu" to bytesPerDraw(Phase.MENU, 0),
            "car run" to bytesPerDraw(Phase.RUN, 0),
            "plane run" to bytesPerDraw(Phase.RUN, 1),
            "rocket run" to bytesPerDraw(Phase.RUN, 2),
            "paused" to bytesPerDraw(Phase.RUN, 1, paused = true),
            "game over" to bytesPerDraw(Phase.GAME_OVER, 0),
            "victory" to bytesPerDraw(Phase.VICTORY, 2),
        )
        for ((name, bytes) in results) println("hud garbage per frame: $name ${bytes / 1024} KB ($bytes bytes)")
        for ((name, bytes) in results) assertTrue("$name allocates ${bytes / 1024} KB per frame", bytes < 64 * 1024)
    }
}
