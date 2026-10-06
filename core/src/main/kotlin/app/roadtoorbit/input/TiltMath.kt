package app.roadtoorbit.input

import kotlin.math.atan2

/**
 * The geometry behind tilt steering, kept free of Android so it can be tested. Readings are gravity (or
 * acceleration at rest) in the phone's own axes; in the *screen* frame x points right, y up and z out of the screen.
 */
object TiltMath {
    // the same values as android.view.Surface.ROTATION_*
    const val ROTATION_0 = 0
    const val ROTATION_90 = 1
    const val ROTATION_180 = 2
    const val ROTATION_270 = 3

    /** Writes the screen-frame components of a device-frame reading into [out] (x right, y up, z out of the screen). */
    fun toScreen(rotation: Int, ax: Float, ay: Float, az: Float, out: FloatArray) {
        when (rotation) {
            ROTATION_90 -> { out[0] = -ay; out[1] = ax }
            ROTATION_180 -> { out[0] = -ax; out[1] = -ay }
            ROTATION_270 -> { out[0] = ay; out[1] = -ax }
            else -> { out[0] = ax; out[1] = ay }
        }
        out[2] = az
    }

    /**
     * How far the screen leans back, in degrees: 0 when it is vertical, positive when the top edge tips away from
     * the player (the screen faces up), negative when the top edge tips toward them, 90 when it lies flat.
     *
     * An angle rather than the raw gravity component along the screen's vertical axis, because that component
     * is *not* proportional to the tilt: it changes by almost nothing near vertical and its sensitivity varies
     * with how the phone is held (full deflection would take about 80 degrees of tilt at 15 degrees lean-back and
     * about 24 degrees at 60), and it cannot tell leaning forward from leaning back.
     */
    fun leanBackDeg(sy: Float, sz: Float): Float = Math.toDegrees(atan2(sz, sy).toDouble()).toFloat()

    /** [a] minus [b] as an angle in (-180, 180]. */
    fun angleDiff(a: Float, b: Float): Float {
        var d = (a - b) % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }
}
