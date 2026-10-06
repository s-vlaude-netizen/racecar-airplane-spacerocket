package app.roadtoorbit.gfx

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Bakes the space nebula into a small cube map on the CPU once, so the sky shader only needs one
 * texture fetch per pixel instead of dozens of noise evaluations (a big saving on mid-range GPUs).
 * Faces follow the OpenGL cube-map convention (+X, -X, +Y, -Y, +Z, -Z).
 */
object NebulaCube {
    const val SIZE = 96

    /** RGBA8 pixels for the six faces, each SIZE*SIZE*4 bytes. */
    fun generate(size: Int = SIZE): Array<ByteArray> = Array(6) { face(it, size) }

    private fun face(face: Int, n: Int): ByteArray {
        val out = ByteArray(n * n * 4)
        for (row in 0 until n) {
            for (col in 0 until n) {
                val sc = 2f * (col + 0.5f) / n - 1f
                val tc = 2f * (row + 0.5f) / n - 1f
                var x: Float; var y: Float; var z: Float
                when (face) {
                    0 -> { x = 1f; y = -tc; z = -sc }
                    1 -> { x = -1f; y = -tc; z = sc }
                    2 -> { x = sc; y = 1f; z = tc }
                    3 -> { x = sc; y = -1f; z = -tc }
                    4 -> { x = sc; y = -tc; z = 1f }
                    else -> { x = -sc; y = -tc; z = -1f }
                }
                val l = sqrt(x * x + y * y + z * z)
                x /= l; y /= l; z /= l
                val rgb = nebula(x, y, z)
                val i = (row * n + col) * 4
                out[i] = to8(rgb[0]); out[i + 1] = to8(rgb[1]); out[i + 2] = to8(rgb[2]); out[i + 3] = 0xFF.toByte()
            }
        }
        return out
    }

    private fun to8(v: Float): Byte = (min(1f, max(0f, v)) * 255f + 0.5f).toInt().toByte()

    private fun smooth(e0: Float, e1: Float, v: Float): Float {
        val t = min(1f, max(0f, (v - e0) / (e1 - e0)))
        return t * t * (3f - 2f * t)
    }

    private fun noise(x: Float, y: Float, z: Float) = MeshBuilder.valueNoise3(x, y, z)

    /** Colour of the nebula in direction (x, y, z), scaled so 1.0 = the brightest haze (see the sky shader). */
    private fun nebula(x: Float, y: Float, z: Float): FloatArray {
        val qx = x * 4.4f; val qy = y * 4.4f; val qz = z * 4.4f
        val wv = noise(qx * 0.9f + 11f, qy * 0.9f + 11f, qz * 0.9f + 11f)
        val n1 = noise(qx + wv * 2.2f + 3f, qy + wv * 2.2f + 1f, qz + wv * 2.2f + 7f)
        val n2 = noise(qx * 2.6f + wv * 2.6f + 9f, qy * 2.6f + wv * 2.6f + 4f, qz * 2.6f + wv * 2.6f + 2f)
        val neb = smooth(0.42f, 0.78f, n1 * 0.7f + n2 * 0.45f)
        // purple <-> teal haze; stored at 2x so 8 bits keep the dark gradients smooth
        val r = (0.34f + (0.06f - 0.34f) * n2) * neb * 2f
        val g = (0.10f + (0.38f - 0.10f) * n2) * neb * 2f
        val b = (0.50f + (0.55f - 0.50f) * n2) * neb * 2f
        return floatArrayOf(r, g, b)
    }

    /** For tests: the largest colour step between horizontally adjacent texels inside the faces. */
    internal fun maxInteriorJump(faces: Array<ByteArray>, n: Int): Float {
        var worst = 0f
        for (f in 0 until 6) for (row in 0 until n) for (col in 1 until n) for (ch in 0 until 3) {
            val a = (faces[f][(row * n + col) * 4 + ch].toInt() and 0xFF) / 255f
            val b = (faces[f][(row * n + col - 1) * 4 + ch].toInt() and 0xFF) / 255f
            worst = max(worst, abs(a - b))
        }
        return worst
    }

    /** For tests: the largest colour step between texels that touch across the four side-face seams. */
    internal fun maxEdgeJump(faces: Array<ByteArray>, n: Int): Float {
        var worst = 0f
        fun px(f: Int, col: Int, row: Int, ch: Int) = (faces[f][(row * n + col) * 4 + ch].toInt() and 0xFF) / 255f
        for (row in 0 until n) for (ch in 0 until 3) {
            worst = max(worst, abs(px(4, n - 1, row, ch) - px(0, 0, row, ch)))   // +Z | +X
            worst = max(worst, abs(px(0, n - 1, row, ch) - px(5, 0, row, ch)))   // +X | -Z
            worst = max(worst, abs(px(5, n - 1, row, ch) - px(1, 0, row, ch)))   // -Z | -X
            worst = max(worst, abs(px(1, n - 1, row, ch) - px(4, 0, row, ch)))   // -X | +Z
        }
        return worst
    }
}
