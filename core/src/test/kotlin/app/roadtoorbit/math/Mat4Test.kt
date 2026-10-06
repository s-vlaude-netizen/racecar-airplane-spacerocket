package app.roadtoorbit.math

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Mat4Test {
    private fun near(a: Float, b: Float, eps: Float = 1e-4f) = assertTrue(abs(a - b) <= eps, "expected $b got $a")

    @Test fun lookAtPutsTheEyeAtTheOriginLookingDownMinusZ() {
        val v = FloatArray(16)
        Mat4.lookAt(v, 3f, 4f, 5f, 3f, 4f, -10f, 0f, 1f, 0f)
        val out = FloatArray(3)
        Mat4.transformPoint(v, 3f, 4f, 5f, out)
        near(out[0], 0f); near(out[1], 0f); near(out[2], 0f)
        Mat4.transformPoint(v, 3f, 4f, -10f, out) // a point straight ahead
        near(out[0], 0f); near(out[1], 0f); near(out[2], -15f)
        Mat4.transformPoint(v, 4f, 4f, 0f, out) // a point to the world's +X sits to the camera's right (+X)
        assertTrue(out[0] > 0f)
    }

    @Test fun perspectiveMapsNearAndFarPlanes() {
        val p = FloatArray(16)
        Mat4.perspective(p, 60f, 2f, 1f, 100f)
        fun ndcZ(z: Float): Float {
            val clipZ = p[10] * z + p[14]
            val clipW = p[11] * z
            return clipZ / clipW
        }
        near(ndcZ(-1f), -1f); near(ndcZ(-100f), 1f, 1e-3f)
    }

    @Test fun rotateAndTranslateComposeInOrder() {
        val m = FloatArray(16)
        Mat4.identity(m)
        Mat4.translate(m, 5f, 0f, 0f)
        Mat4.rotateY(m, 90f) // rotate first (applied to the point), then translate
        val out = FloatArray(3)
        Mat4.transformPoint(m, 1f, 0f, 0f, out) // +X rotated 90° about Y becomes -Z
        near(out[0], 5f); near(out[1], 0f); near(out[2], -1f)
    }

    @Test fun trsAppliesYawPitchRollOrderAndScale() {
        val m = FloatArray(16)
        Mat4.setTrs(m, 1f, 2f, 3f, 0f, 0f, 0f, 2f, 3f, 4f)
        val out = FloatArray(3)
        Mat4.transformPoint(m, 1f, 1f, 1f, out)
        near(out[0], 3f); near(out[1], 5f); near(out[2], 7f)
    }

    @Test fun multiplyMatchesSequentialTransforms() {
        val a = FloatArray(16); val b = FloatArray(16); val c = FloatArray(16)
        Mat4.setTrs(a, 1f, 0f, 0f, 0f, 30f, 0f, 1f, 1f, 1f)
        Mat4.setTrs(b, 0f, 2f, 0f, 0f, 0f, 45f, 1f, 1f, 1f)
        Mat4.multiply(c, a, b)
        val p1 = FloatArray(3); val p2 = FloatArray(3); val p3 = FloatArray(3)
        Mat4.transformPoint(b, 0.3f, 0.2f, 0.1f, p1)
        Mat4.transformPoint(a, p1[0], p1[1], p1[2], p2)
        Mat4.transformPoint(c, 0.3f, 0.2f, 0.1f, p3)
        for (k in 0 until 3) near(p2[k], p3[k])
    }

    @Test fun normalMatrixKeepsNormalsPerpendicularUnderNonUniformScale() {
        val m = FloatArray(16)
        Mat4.setTrs(m, 0f, 0f, 0f, 0f, 0f, 30f, 4f, 1f, 1f)
        val nm = FloatArray(9)
        Mat4.normalMatrix(nm, m)
        // a surface with tangent t and normal n stays perpendicular after transforming t by M and n by the normal matrix
        val t = floatArrayOf(1f, 1f, 0f)
        val n = floatArrayOf(1f, -1f, 0f)
        val tt = FloatArray(3); Mat4.transformDir(m, t[0], t[1], t[2], tt)
        val nn = floatArrayOf(nm[0] * n[0] + nm[3] * n[1] + nm[6] * n[2], nm[1] * n[0] + nm[4] * n[1] + nm[7] * n[2], nm[2] * n[0] + nm[5] * n[1] + nm[8] * n[2])
        near(tt[0] * nn[0] + tt[1] * nn[1] + tt[2] * nn[2], 0f)
    }

    @Test fun rngIsDeterministicAndUniformish() {
        val a = Rng(42); val b = Rng(42)
        repeat(100) { assertEquals(a.nextLong(), b.nextLong()) }
        val r = Rng(7)
        var sum = 0f
        repeat(10000) { val f = r.float(); assertTrue(f >= 0f && f < 1f); sum += f }
        assertTrue(abs(sum / 10000f - 0.5f) < 0.02f)
        repeat(1000) { assertTrue(r.int(5) in 0..4) }
    }
}
