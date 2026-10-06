package app.roadtoorbit.game

import app.roadtoorbit.gfx.VehicleMode
import app.roadtoorbit.gfx.VehicleModel
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A model-predictive test pilot: every 0.1 s it simulates the ship's lateral/vertical response toward
 * a set of candidate targets against the predicted hazard positions and steers for the safest (and
 * most rewarding) one. Good enough to prove each leg is winnable and to produce demo footage.
 */
class Bot(
    private val game: Game,
    private val useBoost: Boolean = false,
    private val skill: Float = 1f,
    /** Seconds between a decision and it taking effect (human reaction time). */
    private val latency: Float = 0f,
    /** Seconds between re-planning rounds. */
    private val interval: Float = 0.1f,
    /** Random steering error (0..1) imitating a thumb on glass. */
    private val noise: Float = 0f,
) {
    private val rnd = java.util.Random(99)
    private var clock = 0f
    private var pendingX = 0f
    private var pendingY = 0f
    private var pendingAt = -1f
    private var wobbleX = 0f
    private var wobbleY = 0f
    private var targetX = 0f
    private var targetY = 0f
    private var decideIn = 0f
    private var transformAt = 55f

    fun control(input: GameInput, dt: Float) {
        val p = game.player
        clock += dt
        decideIn -= dt
        if (decideIn <= 0f) {
            decideIn = interval
            val oldX = targetX; val oldY = targetY
            plan()
            if (latency > 0f) {
                // the decision only takes effect after the reaction delay
                pendingX = targetX; pendingY = targetY; pendingAt = clock + latency
                targetX = oldX; targetY = oldY
            }
        }
        if (pendingAt >= 0f && clock >= pendingAt) {
            targetX = pendingX; targetY = pendingY; pendingAt = -1f
        }
        if (noise > 0f) {
            wobbleX += (rnd.nextGaussian().toFloat() * noise - wobbleX) * 0.15f
            wobbleY += (rnd.nextGaussian().toFloat() * noise - wobbleY) * 0.15f
        }
        val leg = game.leg
        val gainX = 0.55f
        input.steerX = ((targetX - p.x) * gainX + wobbleX).coerceIn(-1f, 1f)
        input.steerY = if (leg.mode == VehicleMode.CAR) 0f else ((targetY - p.y) * 0.6f + wobbleY).coerceIn(-1f, 1f)
        input.boost = useBoost && p.boostMeter > 0.5f
        if (game.transformReady && game.distanceToGate < transformAt) input.requestTransform()
    }

    private class Candidate(val x: Float, val y: Float)

    private fun plan() {
        val p = game.player
        val leg = game.leg
        val mode = leg.mode
        val ext = VehicleModel.hitHalfExtents(mode)
        val phx = ext[0]; val phy = ext[1]; val phz = ext[2]
        val (maxX, minY, maxY, latSpeed, vertSpeed, accel) = when (mode) {
            VehicleMode.CAR -> Dyn(Tuning.CAR_MAX_X, 0f, 0f, Tuning.CAR_LATERAL_SPEED, 0f, Tuning.CAR_LATERAL_ACCEL)
            VehicleMode.PLANE -> Dyn(Tuning.PLANE_MAX_X, Tuning.PLANE_MIN_Y, Tuning.PLANE_MAX_Y, Tuning.PLANE_LATERAL_SPEED, Tuning.PLANE_VERTICAL_SPEED, Tuning.PLANE_ACCEL)
            VehicleMode.ROCKET -> Dyn(Tuning.ROCKET_MAX_X, Tuning.ROCKET_MIN_Y, Tuning.ROCKET_MAX_Y, Tuning.ROCKET_LATERAL_SPEED, Tuning.ROCKET_VERTICAL_SPEED, Tuning.ROCKET_ACCEL)
        }

        val cands = ArrayList<Candidate>()
        if (mode == VehicleMode.CAR) {
            var x = -maxX
            while (x <= maxX + 0.01f) { cands.add(Candidate(x, 0f)); x += 0.75f }
        } else {
            var x = -maxX
            while (x <= maxX + 0.01f) {
                var y = minY
                while (y <= maxY + 0.01f) { cands.add(Candidate(x, y)); y += 2.5f }
                x += 2.5f
            }
        }

        val horizon = 2.6f
        val step = 0.1f
        val steps = (horizon / step).toInt()
        val speed = max(p.speed, leg.speedStart)
        val relevant = game.entities.filter {
            !it.hit && it.z < phz + 4f && (it.kind.category == Category.HAZARD || it.kind.category == Category.PICKUP) &&
                it.z > -(speed * horizon + 60f)
        }

        var best: Candidate? = null
        var bestCost = Float.MAX_VALUE
        for (c in cands) {
            var cost = 0f
            var x = p.x; var y = p.y; var vx = p.vx; var vy = p.vy
            var dead = false
            for (s in 1..steps) {
                val t = s * step
                // move toward the candidate with the real acceleration limits
                val tvx = ((c.x - x) * 4f).coerceIn(-latSpeed, latSpeed)
                vx = approach(vx, tvx, accel * step)
                x = (x + vx * step).coerceIn(-maxX, maxX)
                if (mode != VehicleMode.CAR) {
                    val tvy = ((c.y - y) * 4f).coerceIn(-vertSpeed, vertSpeed)
                    vy = approach(vy, tvy, accel * step)
                    y = (y + vy * step).coerceIn(minY, maxY)
                }
                val cy = y + 0.62f
                for (e in relevant) {
                    val ez = e.z + (speed * e.parallax - e.speed) * t
                    val ex = e.x + e.vx * t
                    val ey = game.entityY(e) + e.vy * t
                    if (e.kind.category == Category.HAZARD) {
                        if (hits(e, ex, ey, ez, x, cy, phx * (1.1f + 0.1f * (1f - skill)), phy * 1.1f, phz * 1.0f + 0.5f)) {
                            cost += 1000f / s
                            dead = true
                        } else {
                            // soft clearance preference
                            val gap = clearance(e, ex, ey, ez, x, cy, phx, phy, phz)
                            if (gap < 1.5f) cost += (1.5f - gap) * 6f
                        }
                    }
                }
            }
            // pickups: value of the candidate position when pickups pass by
            for (e in relevant) {
                if (e.kind.category != Category.PICKUP) continue
                val tArrive = -e.z / speed
                if (tArrive < 0f || tArrive > horizon) continue
                val ex = e.x; val ey = game.entityY(e)
                val dxy = hypot(c.x - ex, if (mode == VehicleMode.CAR) 0f else (c.y + 0.62f - ey))
                if (dxy < 2.2f) cost -= if (e.kind == Kind.REPAIR) 7f else 3f
            }
            // prefer not to wander
            cost += hypot(c.x - p.x, if (mode == VehicleMode.CAR) 0f else (c.y - p.y)) * 0.12f
            // gentle pull toward the centre line when nothing matters
            cost += abs(c.x) * 0.02f
            if (cost < bestCost) { bestCost = cost; best = c }
        }
        val b = best ?: return
        targetX = b.x
        targetY = b.y
    }

    private data class Dyn(val maxX: Float, val minY: Float, val maxY: Float, val lat: Float, val vert: Float, val accel: Float)

    private fun approach(v: Float, target: Float, step: Float): Float =
        if (abs(target - v) <= step) target else v + (if (target > v) step else -step)

    private fun hits(e: Entity, ex: Float, ey: Float, ez: Float, px: Float, py: Float, phx: Float, phy: Float, phz: Float): Boolean {
        if (e.radius > 0f) {
            val cx = px.coerceIn(ex - 1e6f, ex + 1e6f)
            val qx = ex.coerceIn(px - phx, px + phx)
            val qy = ey.coerceIn(py - phy, py + phy)
            val qz = ez.coerceIn(-phz, phz)
            val dx = ex - qx; val dy = ey - qy; val dz = ez - qz
            return dx * dx + dy * dy + dz * dz < (e.radius + 0.3f) * (e.radius + 0.3f) && cx == cx
        }
        return abs(ex - px) < e.hx + phx + 0.2f && abs(ey - py) < e.hy + phy + 0.2f && abs(ez) < e.hz + phz
    }

    private fun clearance(e: Entity, ex: Float, ey: Float, ez: Float, px: Float, py: Float, phx: Float, phy: Float, phz: Float): Float {
        if (abs(ez) > e.hz + e.radius + phz + 2f) return 99f
        return if (e.radius > 0f) hypot(ex - px, ey - py) - e.radius - min(phx, phy)
        else hypot(max(0f, abs(ex - px) - e.hx - phx), max(0f, abs(ey - py) - e.hy - phy))
    }
}
