package app.roadtoorbit

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import app.roadtoorbit.game.Phase
import app.roadtoorbit.input.TiltMath

/**
 * Optional accelerometer steering. The attitude when a run starts is taken as neutral, so players can
 * hold the phone at any comfortable angle. Tilt the screen's right edge down to steer right; tilt the
 * top edge toward you to climb (or dive: the other way). A finger on the on-screen stick always takes priority.
 *
 * Sideways steering follows the gravity component along the screen's horizontal axis (right edge lower = steer
 * right, at any way of holding the phone); climbing follows the lean-back *angle* (see [TiltMath.leanBackDeg]).
 */
class TiltController(private val activity: Activity, private val bridge: UiBridge) : SensorEventListener {
    private val manager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var neutralX = 0f
    private var neutralLean = 0f
    private var haveNeutral = false
    private var fx = 0f
    private var fLean = 0f
    private val screen = FloatArray(3)
    private var rotation = Surface.ROTATION_90
    private var rotationCheckedAtMs = Long.MIN_VALUE / 2

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
        // the display rotation hardly ever changes; asking for it 50 times a second is wasted binder traffic
        val nowMs = e.timestamp / 1_000_000L
        if (nowMs - rotationCheckedAtMs > 100L) {
            rotationCheckedAtMs = nowMs
            @Suppress("DEPRECATION")
            val now = activity.windowManager.defaultDisplay.rotation
            if (now != rotation) {
                // the phone was flipped between the two landscapes: the old attitude means nothing any more
                rotation = now
                haveNeutral = false
            }
        }
        // express the reading in screen axes (x right, y up, z out of the screen) for the current display rotation
        TiltMath.toScreen(rotation, e.values[0], e.values[1], e.values[2], screen)
        val sx = screen[0]
        val lean = TiltMath.leanBackDeg(screen[1], screen[2])
        if (!haveNeutral) {
            // start the smoothing at the real attitude, not at zero, so the first calibration is not off by the ramp-up
            fx = sx
            fLean = lean
        }
        fx += (sx - fx) * 0.3f
        fLean += TiltMath.angleDiff(lean, fLean) * 0.3f
        // The attitude at the start of a run is neutral: while the countdown runs the neutral keeps following the
        // phone, so there is time to settle into a comfortable grip before GO.
        if (!haveNeutral || bridge.tiltRecalibrate || bridge.hud.latest.phase == Phase.COUNTDOWN) {
            neutralX = fx; neutralLean = fLean
            haveNeutral = true
            bridge.tiltRecalibrate = false
        }
        if (bridge.stickActive) return
        bridge.input.steerX = shape(-(fx - neutralX) / RANGE)
        // the top edge tipping toward the player (leaning back less) climbs
        bridge.input.steerY = shape(-TiltMath.angleDiff(fLean, neutralLean) / RANGE_DEG)
    }

    private fun shape(v: Float): Float {
        val a = kotlin.math.abs(v)
        if (a < DEAD) return 0f
        val s = ((a - DEAD) / (1f - DEAD)).coerceAtMost(1f)
        return if (v < 0f) -s else s
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val RANGE = 3.6f // m/s^2 along the screen's x axis for full sideways deflection (~21 degrees)
        const val RANGE_DEG = 21f // degrees of lean-back change for full climb or dive
        const val DEAD = 0.07f
    }
}
