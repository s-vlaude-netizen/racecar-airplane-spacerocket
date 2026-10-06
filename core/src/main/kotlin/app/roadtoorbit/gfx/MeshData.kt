package app.roadtoorbit.gfx

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * CPU-side triangle soup. Every three consecutive vertices form one triangle (no index buffer –
 * the low-poly, flat-shaded style duplicates nearly every vertex anyway).
 *
 * GPU layout per vertex (28 bytes): position (3 x float), normal (3 x float), colour (4 x ubyte RGBA).
 */
class MeshData(initialCapacity: Int = 256) {
    var count = 0
        private set
    var pos = FloatArray(initialCapacity * 3)
        private set
    var nrm = FloatArray(initialCapacity * 3)
        private set
    var col = IntArray(initialCapacity)
        private set

    val triangleCount: Int get() = count / 3
    val byteSize: Int get() = count * STRIDE

    fun clear() {
        count = 0
    }

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, color: Int) {
        if (count == col.size) grow()
        val i = count * 3
        pos[i] = x; pos[i + 1] = y; pos[i + 2] = z
        nrm[i] = nx; nrm[i + 1] = ny; nrm[i + 2] = nz
        col[count] = color
        count++
    }

    /** Appends all vertices of [other]. */
    fun append(other: MeshData) {
        for (v in 0 until other.count) {
            val i = v * 3
            vertex(other.pos[i], other.pos[i + 1], other.pos[i + 2], other.nrm[i], other.nrm[i + 1], other.nrm[i + 2], other.col[v])
        }
    }

    private fun grow() {
        val n = (col.size * 2).coerceAtLeast(64)
        pos = pos.copyOf(n * 3)
        nrm = nrm.copyOf(n * 3)
        col = col.copyOf(n)
    }

    /** Writes the interleaved GPU representation into [buf] (cleared first, flipped after). */
    fun writeTo(buf: ByteBuffer) {
        buf.clear()
        for (v in 0 until count) {
            val i = v * 3
            buf.putFloat(pos[i]); buf.putFloat(pos[i + 1]); buf.putFloat(pos[i + 2])
            buf.putFloat(nrm[i]); buf.putFloat(nrm[i + 1]); buf.putFloat(nrm[i + 2])
            val c = col[v]
            buf.put(((c shr 16) and 0xFF).toByte())
            buf.put(((c shr 8) and 0xFF).toByte())
            buf.put((c and 0xFF).toByte())
            buf.put(((c ushr 24) and 0xFF).toByte())
        }
        buf.flip()
    }

    /** Allocates a direct native-order buffer sized for the current contents and fills it. */
    fun toByteBuffer(): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(maxOf(byteSize, STRIDE)).order(ByteOrder.nativeOrder())
        writeTo(buf)
        return buf
    }

    /** Axis-aligned bounds as [minX, minY, minZ, maxX, maxY, maxZ]; zeros for an empty mesh. */
    fun bounds(): FloatArray {
        if (count == 0) return FloatArray(6)
        val b = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (v in 0 until count) {
            val i = v * 3
            for (k in 0 until 3) {
                if (pos[i + k] < b[k]) b[k] = pos[i + k]
                if (pos[i + k] > b[3 + k]) b[3 + k] = pos[i + k]
            }
        }
        return b
    }

    companion object {
        const val STRIDE = 28
        const val OFFSET_NORMAL = 12
        const val OFFSET_COLOR = 24
    }
}
