package app.roadtoorbit.math

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Column-major 4x4 matrix helpers operating on plain FloatArrays (16 floats).
 *
 * Layout and call semantics mirror android.opengl.Matrix (translate/rotate/scale post-multiply),
 * so the same code behaves identically on the JVM (tests, offline renderer) and on a device.
 */
object Mat4 {
    const val DEG2RAD = (PI / 180.0).toFloat()
    const val RAD2DEG = (180.0 / PI).toFloat()

    fun identity(m: FloatArray) {
        m.fill(0f)
        m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
    }

    fun copy(dst: FloatArray, src: FloatArray) {
        System.arraycopy(src, 0, dst, 0, 16)
    }

    /** out = a * b. [out] must not alias [a] or [b]. */
    fun multiply(out: FloatArray, a: FloatArray, b: FloatArray) {
        for (c in 0 until 4) {
            val b0 = b[c * 4]
            val b1 = b[c * 4 + 1]
            val b2 = b[c * 4 + 2]
            val b3 = b[c * 4 + 3]
            for (r in 0 until 4) {
                out[c * 4 + r] = a[r] * b0 + a[4 + r] * b1 + a[8 + r] * b2 + a[12 + r] * b3
            }
        }
    }

    /** m = m * T(x, y, z) */
    fun translate(m: FloatArray, x: Float, y: Float, z: Float) {
        for (i in 0 until 4) {
            m[12 + i] += m[i] * x + m[4 + i] * y + m[8 + i] * z
        }
    }

    /** m = m * S(x, y, z) */
    fun scale(m: FloatArray, x: Float, y: Float, z: Float) {
        for (i in 0 until 4) {
            m[i] *= x
            m[4 + i] *= y
            m[8 + i] *= z
        }
    }

    /** m = m * R(axis, angle). The axis does not need to be normalised. */
    fun rotate(m: FloatArray, degrees: Float, ax: Float, ay: Float, az: Float) {
        val len = sqrt(ax * ax + ay * ay + az * az)
        if (len < 1e-8f) return
        val x = ax / len
        val y = ay / len
        val z = az / len
        val rad = degrees * DEG2RAD
        val c = cos(rad)
        val s = sin(rad)
        val t = 1f - c
        val r00 = t * x * x + c
        val r01 = t * x * y - s * z
        val r02 = t * x * z + s * y
        val r10 = t * x * y + s * z
        val r11 = t * y * y + c
        val r12 = t * y * z - s * x
        val r20 = t * x * z - s * y
        val r21 = t * y * z + s * x
        val r22 = t * z * z + c
        for (i in 0 until 4) {
            val a0 = m[i]
            val a1 = m[4 + i]
            val a2 = m[8 + i]
            m[i] = a0 * r00 + a1 * r10 + a2 * r20
            m[4 + i] = a0 * r01 + a1 * r11 + a2 * r21
            m[8 + i] = a0 * r02 + a1 * r12 + a2 * r22
        }
    }

    fun rotateX(m: FloatArray, degrees: Float) = rotate(m, degrees, 1f, 0f, 0f)
    fun rotateY(m: FloatArray, degrees: Float) = rotate(m, degrees, 0f, 1f, 0f)
    fun rotateZ(m: FloatArray, degrees: Float) = rotate(m, degrees, 0f, 0f, 1f)

    /** Builds T * Ry * Rx * Rz * S (yaw, pitch, roll order) into [m]. */
    fun setTrs(
        m: FloatArray,
        tx: Float, ty: Float, tz: Float,
        pitchDeg: Float, yawDeg: Float, rollDeg: Float,
        sx: Float, sy: Float, sz: Float,
    ) {
        identity(m)
        translate(m, tx, ty, tz)
        if (yawDeg != 0f) rotateY(m, yawDeg)
        if (pitchDeg != 0f) rotateX(m, pitchDeg)
        if (rollDeg != 0f) rotateZ(m, rollDeg)
        if (sx != 1f || sy != 1f || sz != 1f) scale(m, sx, sy, sz)
    }

    fun perspective(m: FloatArray, fovYDegrees: Float, aspect: Float, near: Float, far: Float) {
        val f = 1f / tan(fovYDegrees * DEG2RAD * 0.5f)
        m.fill(0f)
        m[0] = f / aspect
        m[5] = f
        m[10] = (far + near) / (near - far)
        m[11] = -1f
        m[14] = 2f * far * near / (near - far)
    }

    fun lookAt(
        m: FloatArray,
        ex: Float, ey: Float, ez: Float,
        cx: Float, cy: Float, cz: Float,
        ux: Float, uy: Float, uz: Float,
    ) {
        var fx = cx - ex
        var fy = cy - ey
        var fz = cz - ez
        val fl = 1f / sqrt(fx * fx + fy * fy + fz * fz)
        fx *= fl; fy *= fl; fz *= fl

        var sx = fy * uz - fz * uy
        var sy = fz * ux - fx * uz
        var sz = fx * uy - fy * ux
        val sl = 1f / sqrt(sx * sx + sy * sy + sz * sz)
        sx *= sl; sy *= sl; sz *= sl

        val tx = sy * fz - sz * fy
        val ty = sz * fx - sx * fz
        val tz = sx * fy - sy * fx

        m[0] = sx; m[1] = tx; m[2] = -fx; m[3] = 0f
        m[4] = sy; m[5] = ty; m[6] = -fy; m[7] = 0f
        m[8] = sz; m[9] = tz; m[10] = -fz; m[11] = 0f
        m[12] = -(sx * ex + sy * ey + sz * ez)
        m[13] = -(tx * ex + ty * ey + tz * ez)
        m[14] = fx * ex + fy * ey + fz * ez
        m[15] = 1f
    }

    /**
     * Normal matrix (3x3, column-major) for a model matrix that is rotation * non-uniform scale.
     * Each column of the upper 3x3 is divided by its squared length, which equals R * S^-1.
     */
    fun normalMatrix(out3: FloatArray, model: FloatArray) {
        for (c in 0 until 3) {
            val x = model[c * 4]
            val y = model[c * 4 + 1]
            val z = model[c * 4 + 2]
            val l2 = x * x + y * y + z * z
            val k = if (l2 > 1e-12f) 1f / l2 else 0f
            out3[c * 3] = x * k
            out3[c * 3 + 1] = y * k
            out3[c * 3 + 2] = z * k
        }
    }

    /** Squared length of the longest basis column; ~0 means the matrix collapsed (scale 0). */
    fun maxColumnLengthSq(m: FloatArray): Float {
        var best = 0f
        for (c in 0 until 3) {
            val x = m[c * 4]
            val y = m[c * 4 + 1]
            val z = m[c * 4 + 2]
            val l2 = x * x + y * y + z * z
            if (l2 > best) best = l2
        }
        return best
    }

    /** Transforms a point by [m]; writes xyz to [out] (w ignored). */
    fun transformPoint(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12]
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13]
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14]
    }

    /** Transforms a direction by the upper 3x3 of [m]. */
    fun transformDir(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
        out[0] = m[0] * x + m[4] * y + m[8] * z
        out[1] = m[1] * x + m[5] * y + m[9] * z
        out[2] = m[2] * x + m[6] * y + m[10] * z
    }
}
