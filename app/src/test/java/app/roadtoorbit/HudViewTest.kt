package app.roadtoorbit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import app.roadtoorbit.game.Phase
import app.roadtoorbit.gfx.VehicleMode
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Draws every HUD / menu screen into a bitmap with Robolectric's native graphics and saves PNGs for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HudViewTest {
    private val w = 1600
    private val h = 740

    private fun render(name: String, bridge: UiBridge, setup: (app.roadtoorbit.game.HudState) -> Unit) {
        val hud = HudView(RuntimeEnvironment.getApplication(), bridge)
        hud.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        hud.layout(0, 0, w, h)
        setup(bridge.hud.latest)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // a flat sky-ish backdrop so translucent HUD parts read like they do over the 3D scene
        val c = Canvas(bmp)
        c.drawColor(0xFF4F7FC8.toInt())
        hud.draw(c)
        val dir = File(System.getProperty("hudDir") ?: "build/hud")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(dir, "$name.png").length() > 1000)
    }

    private fun base(s: app.roadtoorbit.game.HudState, phase: Phase, leg: Int) {
        s.phase = phase
        s.legIndex = leg
        s.legName = arrayOf("GRAND PRIX", "SKY RALLY", "ORBIT RUN")[leg]
        s.legSubtitle = arrayOf("Race to the launch gate", "Climb to the edge of space", "Reach the Moon")[leg]
        s.mode = VehicleMode.values()[leg]
        s.journey = (leg + 0.55f) / 3f
        s.legProgress = 0.55f
        s.score = 12840
        s.bestScore = 22310
        s.health = 2
        s.maxHealth = 3
        s.difficulty = app.roadtoorbit.game.Difficulty.NORMAL
        s.boost = 0.62f
        s.speedKmh = if (leg == 0) 187 else if (leg == 1) 640 else 18200
        s.altitudeKm = if (leg == 1) 38.4f else if (leg == 2) 4210f else 0f
        s.popupCount = 0
    }

    @Test fun menu() = render("menu", UiBridge()) { s -> base(s, Phase.MENU, 0) }

    @Test fun carRun() = render("car_run", UiBridge()) { s ->
        base(s, Phase.RUN, 0)
        s.popupCount = 2
        s.popupText[0] = "CLOSE CALL +40"; s.popupColor[0] = 0xFF6FE8FF.toInt(); s.popupAge[0] = 0.4f; s.popupTtl[0] = 0.9f; s.popupBig[0] = false
        s.popupText[1] = "NITRO!"; s.popupColor[1] = 0xFF6FE8FF.toInt(); s.popupAge[1] = 0.2f; s.popupTtl[1] = 0.7f; s.popupBig[1] = false
    }

    @Test fun transformZone() = render("zone", UiBridge()) { s ->
        base(s, Phase.RUN, 0)
        s.transformReady = true
        s.zoneProgress = 0.45f
        s.popupCount = 1
        s.popupText[0] = "TRANSFORM ZONE!"; s.popupColor[0] = 0xFF6FE8FF.toInt(); s.popupAge[0] = 0.5f; s.popupTtl[0] = 1.6f; s.popupBig[0] = true
    }

    @Test fun planeRun() = render("plane_run", UiBridge()) { s ->
        base(s, Phase.RUN, 1)
        s.invulnerable = true
    }

    @Test fun rocketRun() = render("rocket_run", UiBridge()) { s ->
        base(s, Phase.RUN, 2)
        s.health = 1
        s.damageFlash = 0.5f
    }

    @Test fun countdown() = render("countdown", UiBridge()) { s ->
        base(s, Phase.COUNTDOWN, 0)
        s.countdown = 2
    }

    @Test fun paused() = render("paused", UiBridge().also { it.paused = true }) { s -> base(s, Phase.RUN, 1) }

    @Test fun gameOver() = render("game_over", UiBridge()) { s ->
        base(s, Phase.GAME_OVER, 1)
        s.newBest = true
    }

    @Test fun victory() = render("victory", UiBridge()) { s ->
        base(s, Phase.VICTORY, 2)
        s.stars = 2
        s.newBest = true
        s.score = 23455
        s.breakdownBase = 19120; s.breakdownHealth = 1200; s.breakdownTime = 3135
    }

    @Test fun errorScreen() = render("error", UiBridge().also { it.fatalError = "Graphics setup failed: demo" }) { s -> base(s, Phase.MENU, 0) }
}
