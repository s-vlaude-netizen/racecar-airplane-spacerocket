package app.roadtoorbit.gfx

import app.roadtoorbit.game.Bot
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.Levels
import app.roadtoorbit.game.Phase
import app.roadtoorbit.math.Mat4
import app.roadtoorbit.math.Mathx
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where the rocket turns into the car (the landing on the Moon and on Phobos, the landing of the submarine on the seabed) the
 * vehicle must never sink into the ground: no part of the body or of its parts may be lower than the ground at any moment of
 * the transformation, however the parts glide between their poses.
 */
class VehicleGroundTest {
    private val lib = MeshLibrary()
    private val body = MeshData(VehicleModel.maxBodyVertices + 64)
    private val vm = FloatArray(16)
    private val pm = FloatArray(16)
    private val wm = FloatArray(16)

    private fun lowest(data: MeshData, m: FloatArray): Float {
        var low = Float.MAX_VALUE
        for (i in 0 until data.count) {
            val x = data.pos[i * 3]; val y = data.pos[i * 3 + 1]; val z = data.pos[i * 3 + 2]
            low = minOf(low, m[1] * x + m[5] * y + m[9] * z + m[13])
        }
        return low
    }

    /** The lowest point of the vehicle (body and parts, in the pose [v] has now) in the world, for a vehicle matrix [vehicle]. */
    private fun lowestPoint(v: VehicleVisual, vehicle: FloatArray): Pair<Float, String> {
        val t = if (v.from == v.to) 0f else v.morph
        VehicleModel.buildBody(body, v.from, v.to, t, v.skin)
        var low = lowest(body, vehicle)
        var what = "body"
        for ((k, part) in VehicleModel.partsFor(v.skin).withIndex()) {
            val p = VehicleModel.partProgress(part, t)
            val a = part.pose(v.from)
            val b = if (v.from == v.to) a else part.pose(v.to)
            val sx = Mathx.lerp(a.sx, b.sx, p); val sy = Mathx.lerp(a.sy, b.sy, p); val sz = Mathx.lerp(a.sz, b.sz, p)
            if (maxOf(sx, sy, sz) < 0.05f) continue // folded away
            Mat4.setTrs(
                pm, Mathx.lerp(a.x, b.x, p), Mathx.lerp(a.y, b.y, p), Mathx.lerp(a.z, b.z, p),
                Mathx.lerp(a.rx, b.rx, p), Mathx.lerp(a.ry, b.ry, p), Mathx.lerp(a.rz, b.rz, p), sx, sy, sz,
            )
            Mat4.multiply(wm, vehicle, pm)
            val l = lowest(lib.data(part.mesh), wm)
            if (l < low) { low = l; what = "part $k (${part.mesh})" }
        }
        return low to what
    }

    @Test
    fun theVehicleNeverSinksIntoTheGroundWhileTheRocketTurnsIntoTheCar() {
        // (the levels in which the rocket turns into the car: the Moon, Mars, the deep; homecoming ends with a plane that lands)
        for (level in Levels.ALL.filter { l -> l.legs.toList().zipWithNext().any { (a, b) -> a.mode == VehicleMode.ROCKET && b.mode == VehicleMode.CAR } || l.finale == app.roadtoorbit.game.Finale.LANDING }) {
            val game = Game(7)
            game.level = level
            val input = GameInput()
            val bot = Bot(game)
            game.startRun(7)
            val dt = 1f / 60f
            var t = 0f
            var worst = Float.MAX_VALUE
            var worstAt = ""
            var frames = 0
            while (t < 400f && !(game.phase == Phase.VICTORY && game.phaseTime > 2f) && game.phase != Phase.GAME_OVER) {
                bot.control(input, dt)
                game.update(dt, input)
                while (game.pollSfx() != null) { /* drain */ }
                t += dt
                val v = game.player.visual
                val turning = v.from == VehicleMode.ROCKET && v.to == VehicleMode.CAR
                if (!turning || v.morph >= 1f) continue
                val p = game.player
                Mat4.setTrs(vm, p.x, p.y + p.bounce, 0f, p.pitch, p.yaw, p.roll, 1f, 1f, 1f)
                val ground = if (game.phase == Phase.FINALE || game.phase == Phase.VICTORY) game.finaleGroundY else game.look.groundY
                val (low, what) = lowestPoint(v, vm)
                val clearance = low - ground
                frames++
                if (clearance < worst) { worst = clearance; worstAt = "${level.name}: morph %.2f, y %.2f, pitch %.1f, $what".format(v.morph, p.y, p.pitch) }
            }
            println("${level.name}: lowest clearance during the rocket -> car transformation: %.3f m over $frames frames ($worstAt)".format(worst))
            assertTrue(frames > 20, "${level.name}: the transformation into the car was seen for $frames frames")
            assertTrue(worst > -0.03f, "${level.name}: the vehicle sinks %.2f m into the ground ($worstAt)".format(-worst))
        }
    }
}
