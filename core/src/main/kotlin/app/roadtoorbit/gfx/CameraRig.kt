package app.roadtoorbit.gfx

import app.roadtoorbit.game.Game
import app.roadtoorbit.game.Phase
import app.roadtoorbit.math.Mathx
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Chase camera with a per-form rig, boost FOV kick, screen shake and scripted cinematic shots. */
class CameraRig {
    val camera = Camera()
    var aspect = 16f / 9f

    private var camX = 0f
    private var camY = 3f
    private var roll = 0f
    private var fov = 60f
    private var lastEye = FloatArray(3)
    private var lastTarget = FloatArray(3)
    private var crashPull = 0f

    fun update(g: Game, dtReal: Float) {
        val dt = dtReal.coerceIn(0.0001f, 0.05f)
        val c = camera
        c.aspect = aspect
        val p = g.player
        val t = g.clock

        when (g.phase) {
            Phase.MENU -> menuShot(g)
            Phase.COUNTDOWN -> {
                chase(g, dt, 0f)
                val u = Mathx.smoother(g.phaseTime / 2.6f)
                if (u < 1f) {
                    // swoop in from a low front-right view of the car
                    val ex = Mathx.lerp(5.2f, c.ex, u)
                    val ey = Mathx.lerp(1.5f, c.ey, u)
                    val ez = Mathx.lerp(-9.5f, c.ez, u)
                    val tx = Mathx.lerp(0f, c.tx, u)
                    val ty = Mathx.lerp(0.9f, c.ty, u)
                    val tz = Mathx.lerp(0f, c.tz, u)
                    c.ex = ex; c.ey = ey; c.ez = ez; c.tx = tx; c.ty = ty; c.tz = tz
                    c.fovY = Mathx.lerp(40f, c.fovY, u)
                }
            }
            Phase.RUN -> chase(g, dt, g.shake)
            Phase.CRASHING, Phase.GAME_OVER -> {
                crashPull = (crashPull + dt).coerceAtMost(4f)
                c.ex = lastEye[0] + sin(t * 0.6f) * 0.3f
                c.ey = lastEye[1] + crashPull * 0.5f
                c.ez = lastEye[2] + crashPull * 1.2f
                c.tx = lastTarget[0]; c.ty = lastTarget[1]; c.tz = lastTarget[2]
                shake(g.shake, t)
            }
            Phase.FINALE, Phase.VICTORY -> finaleShot(g)
        }
        if (g.phase != Phase.CRASHING && g.phase != Phase.GAME_OVER) crashPull = 0f
        if (g.phase == Phase.RUN || g.phase == Phase.COUNTDOWN) {
            lastEye[0] = c.ex; lastEye[1] = c.ey; lastEye[2] = c.ez
            lastTarget[0] = c.tx; lastTarget[1] = c.ty; lastTarget[2] = c.tz
        }
        c.rollDeg = roll
        c.near = 0.6f
        c.far = if (g.look.altitude > 0.9f || g.phase == Phase.FINALE || g.phase == Phase.VICTORY) 26000f else 3000f
        c.update()
    }

    private fun chase(g: Game, dt: Float, shakeAmount: Float) {
        val c = camera
        val p = g.player
        val v = p.visual
        val wc = v.weight(VehicleMode.CAR)
        val wp = v.weight(VehicleMode.PLANE)
        val wr = v.weight(VehicleMode.ROCKET)
        var dist = wc * 7.6f + wp * 10.5f + wr * 13.0f
        var height = wc * 2.9f + wp * 3.6f + wr * 4.2f
        val lookAhead = wc * 18f + wp * 28f + wr * 38f
        val lookY = wc * 1.0f + wp * 1.4f + wr * 1.8f
        var fovBase = wc * 60f + wp * 64f + wr * 66f

        val xf = if (g.xf.active) sin(PI.toFloat() * Mathx.clamp01(g.xf.t)) else 0f
        dist += 3.2f * xf
        height += 0.9f * xf
        fovBase += 6f * xf

        camX = Mathx.damp(camX, p.x * 0.72f, 9f, dt)
        camY = Mathx.damp(camY, p.y * 0.86f + height, 7f, dt)
        roll = Mathx.damp(roll, p.roll * 0.30f, 6f, dt)
        fov = Mathx.damp(fov, fovBase + 11f * p.boostFactor + (p.speed - g.leg.speedStart) * 0.06f, 5f, dt)

        c.ex = camX; c.ey = camY; c.ez = dist
        c.tx = p.x * 0.55f + camX * 0.45f
        c.ty = p.y * 0.8f + lookY
        c.tz = -lookAhead
        c.fovY = fov
        shake(shakeAmount, g.clock)
    }

    private fun shake(amount: Float, t: Float) {
        if (amount <= 0.001f) return
        val c = camera
        val a = amount * amount.coerceAtMost(1.5f) * 0.45f
        val dx = (sin(t * 61f) + sin(t * 37f + 1.3f)) * 0.5f * a
        val dy = (sin(t * 53f + 0.7f) + sin(t * 29f)) * 0.5f * a
        c.ex += dx; c.ey += dy; c.tx += dx * 0.5f; c.ty += dy * 0.5f
    }

    private fun menuShot(g: Game) {
        val c = camera
        val ang = 0.55f + g.menuTime * 0.33f
        val r = 11.5f
        c.ex = sin(ang) * r
        c.ez = cos(ang) * r
        c.ey = 3.1f + sin(g.menuTime * 0.4f) * 0.4f
        // aim low so the vehicle sits in the upper-middle of the frame, clear of the Play button
        c.tx = 0f; c.ty = 0.1f; c.tz = 0f
        c.fovY = 38f
        roll = 0f
    }

    private fun finaleShot(g: Game) {
        val c = camera
        val p = g.player
        val t = g.finaleT
        // swing from behind the rocket around to a low front view of the car as it drives off
        val swing = Mathx.smoother((t - 1.0f) / 6.0f)
        val az = swing * 2.55f - 0.15f * (1f - swing)
        val r = Mathx.lerp(14f, 10.5f, swing)
        val h = Mathx.lerp(4.4f, 1.9f, swing) + p.y * 0.5f
        c.ex = sin(az) * r
        c.ez = cos(az) * r
        c.ey = h
        c.tx = 0f
        c.ty = p.y + 0.8f
        c.tz = Mathx.lerp(-8f, 0f, swing)
        c.fovY = Mathx.lerp(66f, 46f, swing)
        if (g.phase == Phase.VICTORY) {
            // slide the camera left so the car drives on the right, beside the results panel
            val d = -4.2f * Mathx.smoother(g.phaseTime / 1.4f)
            val rx = cos(az); val rz = -sin(az)
            c.ex += rx * d; c.ez += rz * d; c.tx += rx * d; c.tz += rz * d
        }
        roll = Mathx.damp(roll, 0f, 3f, 0.016f)
        shake(g.shake, g.clock)
    }
}
