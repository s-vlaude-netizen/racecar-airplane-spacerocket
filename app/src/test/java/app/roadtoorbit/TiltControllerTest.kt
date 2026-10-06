package app.roadtoorbit

import android.app.Activity
import android.hardware.SensorEvent
import android.view.Surface
import app.roadtoorbit.game.Phase
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensorManager

/** Tilt steering with simulated sensor readings: the phone held at a lean, then tilted each way. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TiltControllerTest {
    private lateinit var activity: Activity
    private lateinit var bridge: UiBridge
    private lateinit var tilt: TiltController
    private var clockNs = 1_000_000_000L

    @Before fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        bridge = UiBridge().also { it.tiltOn = true }
        tilt = TiltController(activity, bridge)
    }

    private fun rad(deg: Float) = Math.toRadians(deg.toDouble()).toFloat()

    /** Feeds the controller a phone held with the screen leaning back [lean] degrees and the right edge [rollDown] degrees low. */
    private fun hold(rotation: Int, lean: Float, rollDown: Float = 0f, events: Int = 40) {
        shadowOf(activity.windowManager.defaultDisplay).setRotation(rotation)
        val g = 9.81f
        // gravity as the sensor reports it (pointing up), in the screen frame: x right, y up, z out of the screen
        val sx = -g * sin(rad(rollDown))
        val sy = g * cos(rad(lean))
        val sz = g * sin(rad(lean))
        // ... and in the device frame for this display rotation
        val device = when (rotation) {
            Surface.ROTATION_90 -> floatArrayOf(sy, -sx, sz)
            Surface.ROTATION_180 -> floatArrayOf(-sx, -sy, sz)
            Surface.ROTATION_270 -> floatArrayOf(-sy, sx, sz)
            else -> floatArrayOf(sx, sy, sz)
        }
        repeat(events) {
            val e: SensorEvent = ShadowSensorManager.createSensorEvent(3)
            for (i in 0..2) e.values[i] = device[i]
            clockNs += 20_000_000L
            e.timestamp = clockNs
            tilt.onSensorChanged(e)
        }
    }

    /** What happens when a player holds the phone still and taps PLAY: settle, then take that attitude as neutral. */
    private fun calibrate(rotation: Int, lean: Float, rollDown: Float = 0f) {
        hold(rotation, lean, rollDown)
        bridge.tiltRecalibrate = true
        hold(rotation, lean, rollDown, events = 3)
    }

    @Test fun holdingThePhoneStillSteersNothingWhateverTheLeanOrRotation() {
        for (rotation in listOf(Surface.ROTATION_90, Surface.ROTATION_270, Surface.ROTATION_0, Surface.ROTATION_180)) {
            for (lean in listOf(-15f, 5f, 30f, 60f)) {
                calibrate(rotation, lean)
                assertEquals("steerX rotation $rotation lean $lean", 0f, bridge.input.steerX, 1e-4f)
                assertEquals("steerY rotation $rotation lean $lean", 0f, bridge.input.steerY, 1e-4f)
            }
        }
    }

    @Test fun rightEdgeDownSteersRightAndLeftEdgeDownSteersLeftInBothLandscapes() {
        for (rotation in listOf(Surface.ROTATION_90, Surface.ROTATION_270)) {
            calibrate(rotation, lean = 30f)
            hold(rotation, lean = 30f, rollDown = 15f)
            assertTrue("rotation $rotation: right edge down should steer right, got ${bridge.input.steerX}", bridge.input.steerX > 0.5f)
            assertEquals(0f, bridge.input.steerY, 0.05f)
            hold(rotation, lean = 30f, rollDown = -15f)
            assertTrue("rotation $rotation: left edge down should steer left, got ${bridge.input.steerX}", bridge.input.steerX < -0.5f)
        }
    }

    @Test fun topEdgeTowardThePlayerClimbsAndAwayDives() {
        for (rotation in listOf(Surface.ROTATION_90, Surface.ROTATION_270)) {
            for (neutral in listOf(5f, 30f, 60f)) {
                calibrate(rotation, lean = neutral)
                hold(rotation, lean = neutral - 10f) // the top edge tips toward the player
                assertTrue("rotation $rotation lean $neutral: should climb, got ${bridge.input.steerY}", bridge.input.steerY > 0.3f)
                hold(rotation, lean = neutral + 10f)
                assertTrue("rotation $rotation lean $neutral: should dive, got ${bridge.input.steerY}", bridge.input.steerY < -0.3f)
            }
        }
    }

    @Test fun theSameTiltFeelsTheSameWhereverThePhoneIsHeld() {
        val readings = ArrayList<Float>()
        for (neutral in listOf(8f, 25f, 45f, 65f)) {
            calibrate(Surface.ROTATION_90, lean = neutral)
            hold(Surface.ROTATION_90, lean = neutral - 8f)
            readings.add(bridge.input.steerY)
        }
        assertTrue("climb for 8 degrees should be the same at any lean: $readings", readings.max() - readings.min() < 0.05f)
    }

    @Test fun aFingerOnTheStickAlwaysWins() {
        calibrate(Surface.ROTATION_90, lean = 30f)
        bridge.stickActive = true
        bridge.input.steerX = 0.25f
        hold(Surface.ROTATION_90, lean = 30f, rollDown = 20f)
        assertEquals("tilt must not overwrite the stick's value", 0.25f, bridge.input.steerX, 1e-6f)
    }

    @Test fun recalibratingMakesTheCurrentAttitudeTheNewNeutral() {
        calibrate(Surface.ROTATION_90, lean = 30f)
        hold(Surface.ROTATION_90, lean = 30f, rollDown = 15f)
        assertTrue(bridge.input.steerX > 0.5f)
        calibrate(Surface.ROTATION_90, lean = 30f, rollDown = 15f)
        assertEquals(0f, bridge.input.steerX, 1e-3f)
    }

    @Test fun theNeutralFollowsThePhoneDuringTheCountdownAndFreezesAtGo() {
        bridge.hud.latest.phase = Phase.COUNTDOWN
        hold(Surface.ROTATION_90, lean = 20f)
        hold(Surface.ROTATION_90, lean = 50f, rollDown = 8f) // fiddling with the grip while "3, 2, 1" counts down
        assertEquals("still neutral at the end of the countdown", 0f, bridge.input.steerX, 1e-3f)
        assertEquals(0f, bridge.input.steerY, 1e-3f)
        bridge.hud.latest.phase = Phase.RUN // GO
        hold(Surface.ROTATION_90, lean = 50f, rollDown = 23f)
        assertTrue("right edge down after GO steers right: ${bridge.input.steerX}", bridge.input.steerX > 0.5f)
    }

    @Test fun flippingBetweenTheTwoLandscapesRecalibratesAtOnce() {
        calibrate(Surface.ROTATION_90, lean = 30f)
        hold(Surface.ROTATION_270, lean = 30f, events = 12) // the player turned the phone around
        assertEquals(0f, bridge.input.steerX, 1e-3f)
        assertEquals(0f, bridge.input.steerY, 1e-3f)
    }

    @Test fun switchedOffItDoesNothing() {
        bridge.tiltOn = false
        bridge.input.steerX = 0.4f
        hold(Surface.ROTATION_90, lean = 30f, rollDown = 25f)
        assertEquals(0.4f, bridge.input.steerX, 1e-6f)
    }

    @Test fun theFirstCalibrationStartsFromTheRealAttitude() {
        // tilting the phone before the first event must not be mistaken for steering: a handful of events is enough
        hold(Surface.ROTATION_90, lean = 40f, rollDown = 20f, events = 3)
        assertEquals(0f, bridge.input.steerX, 1e-3f)
        assertEquals(0f, bridge.input.steerY, 1e-3f)
    }
}
