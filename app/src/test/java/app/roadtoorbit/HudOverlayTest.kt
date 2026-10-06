package app.roadtoorbit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import app.roadtoorbit.game.Difficulty
import app.roadtoorbit.game.HudState
import app.roadtoorbit.game.Phase
import app.roadtoorbit.gfx.VehicleMode
import java.io.File
import java.util.Properties
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real HUD on a transparent bitmap for every HUD snapshot dumped by the core journey trace
 * (core/build/traces/hud), so tools/render-check/compose.py can overlay it on the rendered 3D frames.
 * Does nothing when no snapshots exist.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HudOverlayTest {
    @Test fun renderOverlays() {
        val inDir = File(System.getProperty("hudStatesDir") ?: "../core/build/traces/hud")
        val files = inDir.listFiles { f -> f.name.endsWith(".properties") }?.sortedBy { it.name } ?: return
        val w = (System.getProperty("traceW") ?: "1600").toInt()
        val h = (System.getProperty("traceH") ?: "740").toInt()
        val outDir = File(System.getProperty("hudDir") ?: "build/hud").also { it.mkdirs() }
        for ((i, f) in files.withIndex()) {
            val p = Properties().also { pr -> f.inputStream().use { pr.load(it) } }
            val bridge = UiBridge()
            val hud = HudView(RuntimeEnvironment.getApplication(), bridge)
            hud.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            hud.layout(0, 0, w, h)
            fill(bridge.hud.latest, p)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            hud.draw(Canvas(bmp))
            File(outDir, "overlay_%04d.png".format(i)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun fill(s: HudState, p: Properties) {
        fun str(k: String) = p.getProperty(k) ?: ""
        fun int(k: String) = str(k).toIntOrNull() ?: 0
        fun flt(k: String) = str(k).toFloatOrNull() ?: 0f
        fun bool(k: String) = str(k).toBoolean()
        s.phase = Phase.valueOf(str("phase")); s.legIndex = int("legIndex"); s.legName = str("legName"); s.legSubtitle = str("legSubtitle")
        s.mode = VehicleMode.valueOf(str("mode")); s.journey = flt("journey"); s.legProgress = flt("legProgress"); s.zoneProgress = flt("zoneProgress")
        s.distanceToGate = flt("distanceToGate"); s.score = int("score"); s.bestScore = int("bestScore"); s.coins = int("coins")
        s.rings = int("rings"); s.ringStreak = int("ringStreak"); s.health = int("health"); s.maxHealth = int("maxHealth")
        s.difficulty = Difficulty.valueOf(str("difficulty")); s.boost = flt("boost"); s.boosting = bool("boosting"); s.invulnerable = bool("invulnerable")
        s.speedKmh = int("speedKmh"); s.altitudeKm = flt("altitudeKm"); s.runTime = flt("runTime")
        s.transformReady = bool("transformReady"); s.transforming = bool("transforming"); s.countdown = int("countdown")
        s.flash = flt("flash"); s.damageFlash = flt("damageFlash"); s.stars = int("stars"); s.newBest = bool("newBest")
        s.breakdownBase = int("breakdownBase"); s.breakdownHealth = int("breakdownHealth"); s.breakdownTime = int("breakdownTime")
        s.popupCount = int("popupCount").coerceAtMost(HudState.MAX_POPUPS)
        for (i in 0 until s.popupCount) {
            s.popupText[i] = str("popup.$i.text"); s.popupColor[i] = int("popup.$i.color"); s.popupAge[i] = flt("popup.$i.age")
            s.popupTtl[i] = flt("popup.$i.ttl"); s.popupBig[i] = bool("popup.$i.big")
        }
    }
}
