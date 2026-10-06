package app.roadtoorbit.gfx

import app.roadtoorbit.math.Mat4
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Look-at camera with roll. [update] rebuilds the view/projection matrices and the sky basis. */
class Camera {
    var ex = 0f; var ey = 2f; var ez = 8f
    var tx = 0f; var ty = 1f; var tz = -10f
    var rollDeg = 0f
    var fovY = 62f
    var near = 0.5f
    var far = 2500f
    var aspect = 16f / 9f

    val view = FloatArray(16)
    val proj = FloatArray(16)

    // camera basis in world space (used by the sky shader)
    val right = FloatArray(3)
    val up = FloatArray(3)
    val fwd = FloatArray(3)

    val tanHalfFov: Float get() = tan(fovY * Mat4.DEG2RAD * 0.5f)

    fun update() {
        var fx = tx - ex; var fy = ty - ey; var fz = tz - ez
        val fl = sqrt(fx * fx + fy * fy + fz * fz).coerceAtLeast(1e-6f)
        fx /= fl; fy /= fl; fz /= fl

        // right = fwd x worldUp, then roll the up vector around the forward axis
        var rx = fy * 0f - fz * 1f
        var ry = fz * 0f - fx * 0f
        var rz = fx * 1f - fy * 0f
        var rl = sqrt(rx * rx + ry * ry + rz * rz)
        if (rl < 1e-5f) { // looking straight up/down: pick an arbitrary right vector
            rx = 1f; ry = 0f; rz = 0f; rl = 1f
        }
        rx /= rl; ry /= rl; rz /= rl
        // up0 = right x fwd
        val u0x = ry * fz - rz * fy
        val u0y = rz * fx - rx * fz
        val u0z = rx * fy - ry * fx
        val r = rollDeg * Mat4.DEG2RAD
        val c = cos(r)
        val s = sin(r)
        val ux = u0x * c + rx * s
        val uy = u0y * c + ry * s
        val uz = u0z * c + rz * s

        Mat4.lookAt(view, ex, ey, ez, tx, ty, tz, ux, uy, uz)
        Mat4.perspective(proj, fovY, aspect, near, far)

        fwd[0] = fx; fwd[1] = fy; fwd[2] = fz
        // recompute an orthonormal basis from the rolled up vector
        var sx = fy * uz - fz * uy
        var sy = fz * ux - fx * uz
        var sz = fx * uy - fy * ux
        val sl = sqrt(sx * sx + sy * sy + sz * sz).coerceAtLeast(1e-6f)
        sx /= sl; sy /= sl; sz /= sl
        right[0] = sx; right[1] = sy; right[2] = sz
        up[0] = sy * fz - sz * fy
        up[1] = sz * fx - sx * fz
        up[2] = sx * fy - sy * fx
    }
}
