package app.roadtoorbit.input

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TiltMathTest {
    private val out = FloatArray(3)
    private fun rad(deg: Float) = Math.toRadians(deg.toDouble()).toFloat()

    /** Gravity as the sensor reports it (pointing up) for a screen leaning back by [leanDeg], in the screen frame. */
    private fun leaned(leanDeg: Float): FloatArray {
        val a = rad(leanDeg)
        return floatArrayOf(0f, 9.81f * cos(a), 9.81f * sin(a))
    }

    /** What the sensor would report in the phone's own axes for a given screen-frame reading and display rotation. */
    private fun toDevice(rotation: Int, s: FloatArray): FloatArray = when (rotation) {
        TiltMath.ROTATION_90 -> floatArrayOf(s[1], -s[0], s[2])
        TiltMath.ROTATION_180 -> floatArrayOf(-s[0], -s[1], s[2])
        TiltMath.ROTATION_270 -> floatArrayOf(-s[1], s[0], s[2])
        else -> floatArrayOf(s[0], s[1], s[2])
    }

    @Test fun everyDisplayRotationMapsBackToTheScreenFrame() {
        val reading = floatArrayOf(2.5f, 7.0f, 6.1f)
        for (rotation in 0..3) {
            val d = toDevice(rotation, reading)
            TiltMath.toScreen(rotation, d[0], d[1], d[2], out)
            for (i in 0..2) assertEquals(reading[i], out[i], 1e-5f, "rotation $rotation axis $i")
        }
    }

    @Test fun leaningBackIsPositiveLeaningForwardIsNegativeAndTheScaleIsTrue() {
        for (lean in listOf(-60f, -10f, 0f, 10f, 35f, 60f, 85f)) {
            val g = leaned(lean)
            assertEquals(lean, TiltMath.leanBackDeg(g[1], g[2]), 0.01f, "lean $lean")
        }
    }

    /** The reason for the angle: a tilt of 10 degrees must be worth 10 degrees wherever the phone is held. */
    @Test fun sensitivityDoesNotDependOnHowThePhoneIsHeld() {
        for (neutral in listOf(-20f, 0f, 5f, 20f, 45f, 70f)) {
            val a = leaned(neutral)
            val b = leaned(neutral - 10f) // the top edge tips 10 degrees toward the player
            val change = TiltMath.angleDiff(TiltMath.leanBackDeg(b[1], b[2]), TiltMath.leanBackDeg(a[1], a[2]))
            assertEquals(-10f, change, 0.01f, "neutral lean $neutral")
        }
    }

    /** The raw component the controller used to read: shown here to document why it was replaced. */
    @Test fun theOldRawComponentWasUselessNearVerticalAndCouldNotTellTheDirection() {
        fun rawChange(neutral: Float, delta: Float) = leaned(neutral + delta)[1] - leaned(neutral)[1]
        // held nearly upright, a 10 degree tip toward the player barely registers (below the old 0.25 m/s^2 dead zone) ...
        assertTrue(abs(rawChange(5f, -10f)) < 0.25f)
        // ... and tipping the other way from vertical gives the *same sign* as tipping forward
        assertTrue(rawChange(0f, 10f) < 0f && rawChange(0f, -10f) < 0f)
    }

    @Test fun rollingTheScreenBarelyDisturbsThePitchEstimate() {
        // rotating the phone like a steering wheel (right edge down) by up to 30 degrees at a 30 degree lean-back
        val lean = rad(30f)
        for (rollDeg in listOf(0f, 10f, 20f, 30f)) {
            val roll = rad(rollDeg)
            val sx = -9.81f * cos(lean) * sin(roll)
            val sy = 9.81f * cos(lean) * cos(roll)
            val sz = 9.81f * sin(lean)
            val pitch = TiltMath.leanBackDeg(sy, sz)
            assertTrue(abs(pitch - 30f) < 4f, "roll $rollDeg changed the pitch estimate to $pitch")
            assertTrue(sx <= 0f)
        }
    }

    @Test fun angleDifferencesWrapAround() {
        assertEquals(-2f, TiltMath.angleDiff(179f, -179f), 1e-4f)
        assertEquals(2f, TiltMath.angleDiff(-179f, 179f), 1e-4f)
        assertEquals(10f, TiltMath.angleDiff(25f, 15f), 1e-4f)
        assertEquals(0f, TiltMath.angleDiff(360f, 0f), 1e-4f)
        assertEquals(180f, TiltMath.angleDiff(180f, 0f), 1e-4f)
    }
}
