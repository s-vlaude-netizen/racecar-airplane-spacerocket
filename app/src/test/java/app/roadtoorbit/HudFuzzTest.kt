package app.roadtoorbit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import app.roadtoorbit.game.Difficulty
import app.roadtoorbit.game.HudState
import app.roadtoorbit.game.Phase
import app.roadtoorbit.gfx.VehicleMode
import java.util.Random
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Throws everything a device can throw at the HUD view: every phase with random (and out-of-range) numbers and
 * strings, odd screen sizes, and thousands of random multi-touch events including malformed sequences.
 * Nothing may throw, and the steering the touches produce must stay finite and within [-1, 1].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HudFuzzTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val sizes = listOf(
        800 to 480, 1600 to 740, 2400 to 1080, 3200 to 720, 1080 to 2400, 320 to 200, 640 to 360, 1920 to 1080, 1 to 1, 7 to 3000, 3000 to 7,
    )
    private val strings = listOf(
        "", "x", "GRAND PRIX", "Race to the launch gate", "ÄÖÜ – 🚀 emoji", "w".repeat(300), "\n\n", "%s %d %,d", "CLOSE CALL +40",
    )

    private fun makeView(bridge: UiBridge, w: Int, h: Int): HudView {
        val hud = HudView(context, bridge)
        hud.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        hud.layout(0, 0, w, h)
        return hud
    }

    private fun Random.unit(): Float = nextFloat()
    private fun Random.wideFloat(max: Float): Float = when (nextInt(10)) {
        0 -> 0f
        1 -> -nextFloat() * max
        2 -> nextFloat() * max * 50f
        else -> nextFloat() * max
    }
    private fun Random.wideInt(max: Int): Int = when (nextInt(10)) {
        0 -> 0
        1 -> -nextInt(max + 1)
        2 -> Int.MAX_VALUE
        3 -> Int.MIN_VALUE
        else -> nextInt(max + 1)
    }

    /** A state the simulation could plausibly produce, plus out-of-range values it should never produce but must not crash on. */
    private fun fill(s: HudState, rnd: Random) {
        s.phase = Phase.values()[rnd.nextInt(Phase.values().size)]
        s.legIndex = rnd.nextInt(3)
        s.legName = strings[rnd.nextInt(strings.size)]
        s.legSubtitle = strings[rnd.nextInt(strings.size)]
        s.mode = VehicleMode.values()[rnd.nextInt(3)]
        s.journey = rnd.wideFloat(1f); s.legProgress = rnd.wideFloat(1f); s.zoneProgress = rnd.wideFloat(1f)
        s.distanceToGate = rnd.wideFloat(7000f)
        s.score = rnd.wideInt(2_000_000); s.bestScore = rnd.wideInt(2_000_000)
        s.coins = rnd.wideInt(500); s.rings = rnd.wideInt(50); s.ringStreak = rnd.wideInt(30); s.nearMisses = rnd.wideInt(100)
        s.health = rnd.nextInt(9) - 3; s.maxHealth = 2 + rnd.nextInt(3)
        s.difficulty = Difficulty.values()[rnd.nextInt(3)]
        s.boost = rnd.wideFloat(1f); s.boosting = rnd.nextBoolean(); s.invulnerable = rnd.nextBoolean()
        s.speedKmh = rnd.wideInt(30_000); s.altitudeKm = rnd.wideFloat(400_000f); s.runTime = rnd.wideFloat(1000f)
        s.transformReady = rnd.nextBoolean(); s.transforming = rnd.nextBoolean(); s.countdown = rnd.nextInt(5)
        s.flash = rnd.wideFloat(1f); s.damageFlash = rnd.wideFloat(1f)
        s.stars = rnd.nextInt(4); s.newBest = rnd.nextBoolean()
        s.breakdownBase = rnd.wideInt(1_000_000); s.breakdownHealth = rnd.wideInt(10_000); s.breakdownTime = rnd.wideInt(100_000)
        s.popupCount = rnd.nextInt(HudState.MAX_POPUPS + 1)
        for (i in 0 until HudState.MAX_POPUPS) {
            s.popupText[i] = strings[rnd.nextInt(strings.size)]
            s.popupColor[i] = rnd.nextInt()
            s.popupTtl[i] = rnd.nextFloat() * 3f
            s.popupAge[i] = rnd.nextFloat() * 4f
            s.popupBig[i] = rnd.nextBoolean()
        }
    }

    private fun randomBridge(rnd: Random): UiBridge {
        val bridge = UiBridge()
        fill(bridge.hud.latest, rnd)
        bridge.paused = rnd.nextInt(4) == 0
        bridge.tiltOn = rnd.nextBoolean(); bridge.soundOn = rnd.nextBoolean(); bridge.musicOn = rnd.nextBoolean()
        when (rnd.nextInt(10)) {
            0 -> { bridge.fatalError = strings[rnd.nextInt(strings.size)]; if (rnd.nextBoolean()) bridge.fatalDetails = "details\n" + strings[rnd.nextInt(strings.size)] }
            1 -> bridge.crashReport = strings[rnd.nextInt(strings.size)] + "\n" + strings[rnd.nextInt(strings.size)]
        }
        return bridge
    }

    @Test fun everyScreenSurvivesRandomStatesAndSizes() {
        val rnd = Random(2026)
        repeat(900) { n ->
            val (w, h) = sizes[rnd.nextInt(sizes.size)]
            val bridge = randomBridge(rnd)
            val hud = makeView(bridge, w, h)
            val bmp = Bitmap.createBitmap(maxOf(1, minOf(w, 1200)), maxOf(1, minOf(h, 1200)), Bitmap.Config.ARGB_8888)
            try {
                hud.draw(Canvas(bmp))
                // a second frame with a different state on the same view (cached strings, button layout)
                fill(bridge.hud.latest, rnd)
                hud.draw(Canvas(bmp))
            } catch (t: Throwable) {
                throw AssertionError("draw #$n failed at ${w}x$h in phase ${bridge.hud.latest.phase} (paused ${bridge.paused}, " +
                    "fatal ${bridge.fatalError != null}, crash ${bridge.crashReport != null}): $t", t)
            }
        }
    }

    private class Pointer(val id: Int, var x: Float, var y: Float)

    private fun event(down: Long, action: Int, ptrs: List<Pointer>): MotionEvent {
        val props = Array(ptrs.size) { MotionEvent.PointerProperties().apply { id = ptrs[it].id; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(ptrs.size) { MotionEvent.PointerCoords().apply { x = ptrs[it].x; y = ptrs[it].y; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, ptrs.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0x1002, 0)
    }

    @Test fun randomMultiTouchNeverCrashesAndSteeringStaysSane() {
        val rnd = Random(77)
        repeat(160) { round ->
            val (w, h) = sizes[rnd.nextInt(sizes.size - 3)] // sensible sizes; degenerate ones are covered by the draw fuzz
            val bridge = randomBridge(rnd)
            val hud = makeView(bridge, w, h)
            val bmp = Bitmap.createBitmap(w.coerceAtMost(1200), h.coerceAtMost(1200), Bitmap.Config.ARGB_8888)
            hud.draw(Canvas(bmp)) // lays out the buttons and pills
            val active = ArrayList<Pointer>()
            val down = SystemClock.uptimeMillis()
            fun px() = (rnd.nextFloat() * 1.3f - 0.15f) * w   // inside and a little outside the view
            fun py() = (rnd.nextFloat() * 1.3f - 0.15f) * h
            try {
                repeat(80) { step ->
                    when (rnd.nextInt(8)) {
                        0 -> if (active.isEmpty()) { active.add(Pointer(rnd.nextInt(3), px(), py())); hud.dispatchTouchEvent(event(down, MotionEvent.ACTION_DOWN, active)) }
                        1 -> if (active.isNotEmpty() && active.size < 5) {
                            val id = (0..4).first { c -> active.none { it.id == c } }
                            active.add(Pointer(id, px(), py()))
                            hud.dispatchTouchEvent(event(down, MotionEvent.ACTION_POINTER_DOWN or ((active.size - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), active))
                        }
                        2, 3, 4 -> if (active.isNotEmpty()) {
                            for (p in active) if (rnd.nextBoolean()) { p.x = px(); p.y = py() }
                            hud.dispatchTouchEvent(event(down, MotionEvent.ACTION_MOVE, active))
                        }
                        5 -> if (active.size >= 2) {
                            val idx = rnd.nextInt(active.size)
                            hud.dispatchTouchEvent(event(down, MotionEvent.ACTION_POINTER_UP or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), active))
                            active.removeAt(idx)
                        } else if (active.size == 1) {
                            hud.dispatchTouchEvent(event(down, MotionEvent.ACTION_UP, active))
                            active.clear()
                        }
                        6 -> if (active.isNotEmpty() && rnd.nextInt(6) == 0) {
                            hud.dispatchTouchEvent(event(down, MotionEvent.ACTION_CANCEL, active))
                            active.clear()
                        }
                        else -> {
                            // malformed: events no well-behaved touch screen would send (an UP for a finger that never went down)
                            val ghost = listOf(Pointer(rnd.nextInt(5), px(), py()))
                            val action = intArrayOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_DOWN)[rnd.nextInt(4)]
                            hud.dispatchTouchEvent(event(down, action, ghost))
                            if (action == MotionEvent.ACTION_DOWN) hud.dispatchTouchEvent(event(down, MotionEvent.ACTION_CANCEL, ghost))
                            active.clear()
                        }
                    }
                    val sx = bridge.input.steerX
                    val sy = bridge.input.steerY
                    assertTrue("steering must be finite and within [-1,1]: $sx $sy", sx.isFinite() && sy.isFinite() && sx in -1f..1f && sy in -1f..1f)
                    if (step % 16 == 0) {
                        // the game thread publishes new states in between
                        fill(bridge.hud.latest, rnd)
                        hud.draw(Canvas(bmp))
                    }
                }
            } catch (t: Throwable) {
                throw AssertionError("touch round $round failed at ${w}x$h in phase ${bridge.hud.latest.phase}: $t", t)
            }
            // a finger sequence that ends with CANCEL must leave nothing pressed
            hud.dispatchTouchEvent(event(down, MotionEvent.ACTION_CANCEL, listOf(Pointer(0, 0f, 0f))))
            assertTrue("no stuck boost after a cancel", !bridge.input.boost)
            assertTrue("no stuck stick after a cancel", !bridge.stickActive)
        }
    }
}
