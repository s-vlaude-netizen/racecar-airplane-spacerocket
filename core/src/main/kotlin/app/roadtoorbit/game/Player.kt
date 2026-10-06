package app.roadtoorbit.game

import app.roadtoorbit.gfx.VehicleMode
import app.roadtoorbit.gfx.VehicleVisual

/** Mutable state of the player's transforming vehicle. */
class Player {
    var mode = VehicleMode.CAR
    val visual = VehicleVisual()

    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f

    /** Visual orientation in degrees (roll banks into turns, pitch follows climbs). */
    var yaw = 0f
    var pitch = 0f
    var roll = 0f

    var speed = 0f
    var health = Tuning.MAX_HEALTH
    var boostMeter = 0.35f
    var boostFactor = 0f
    var boosting = false
    var invuln = 0f
    var speedPenalty = 1f

    /** Suspension bounce (car) / hover wobble (rocket), purely visual. */
    var bounce = 0f
    var bounceV = 0f

    var alive = true

    fun reset(mode: VehicleMode, y: Float, speed: Float) {
        this.mode = mode
        visual.settle(mode)
        x = 0f; this.y = y
        vx = 0f; vy = 0f
        yaw = 0f; pitch = 0f; roll = 0f
        this.speed = speed
        health = Tuning.MAX_HEALTH
        boostMeter = 0.35f
        boostFactor = 0f
        boosting = false
        invuln = 0f
        speedPenalty = 1f
        bounce = 0f; bounceV = 0f
        alive = true
    }
}
