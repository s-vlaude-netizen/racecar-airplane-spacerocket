package app.roadtoorbit

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface

/**
 * Optional accelerometer steering. The attitude when a run starts is taken as neutral, so players can
 * hold the phone at any comfortable angle. Tilt the screen's right edge down to steer right; tilt the
 * top edge toward you to climb. A finger on the on-screen stick always takes priority.
 */
class TiltController(private val activity: Activity, private val bridge: UiBridge) : SensorEventListener {
    private val manager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var neutralX = 0f
    private var neutralY = 0f
    private var haveNeutral = false
    private var fx = 0f
    private var fy = 0f

    val available: Boolean get() = sensor != null

    fun start() {
        sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (!bridge.tiltOn) {
            haveNeutral = false
            return
        }
        @Suppress("DEPRECATION")
        val rotation = activity.windowManager.defaultDisplay.rotation
        val ax = e.values[0]
        val ay = e.values[1]
        // express the reading in screen axes (x right, y up) for the current display rotation
        val sx: Float
        val sy: Float
        when (rotation) {
            Surface.ROTATION_90 -> { sx = -ay; sy = ax }
            Surface.ROTATION_180 -> { sx = -ax; sy = -ay }
            Surface.ROTATION_270 -> { sx = ay; sy = -ax }
            else -> { sx = ax; sy = ay }
        }
        fx += (sx - fx) * 0.3f
        fy += (sy - fy) * 0.3f
        if (!haveNeutral || bridge.tiltRecalibrate) {
            neutralX = fx; neutralY = fy
            haveNeutral = true
            bridge.tiltRecalibrate = false
        }
        if (bridge.stickActive) return
        bridge.input.steerX = shape(-(fx - neutralX) / RANGE)
        bridge.input.steerY = shape((fy - neutralY) / RANGE)
    }

    private fun shape(v: Float): Float {
        val a = kotlin.math.abs(v)
        if (a < DEAD) return 0f
        val s = ((a - DEAD) / (1f - DEAD)).coerceAtMost(1f)
        return if (v < 0f) -s else s
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val RANGE = 3.6f // m/s^2 of tilt for full deflection (~21 degrees)
        const val DEAD = 0.07f
    }
}
