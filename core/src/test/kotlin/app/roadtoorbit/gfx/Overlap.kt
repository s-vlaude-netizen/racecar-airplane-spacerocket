package app.roadtoorbit.gfx

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Exact intersection tests between a triangle and an axis-aligned box (the separating axis test of Akenine-Moller). */
internal object Overlap {
    /** A box: centre (cx, cy, cz) and half extents (hx, hy, hz). */
    class Box(var cx: Float = 0f, var cy: Float = 0f, var cz: Float = 0f, var hx: Float = 0f, var hy: Float = 0f, var hz: Float = 0f) {
        fun set(cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float): Box {
            this.cx = cx; this.cy = cy; this.cz = cz; this.hx = hx; this.hy = hy; this.hz = hz
            return this
        }
    }

    /** Whether the triangle (a, b, c) touches the box [b]. */
    fun triangleBox(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float,
        box: Box,
    ): Boolean {
        val hx = box.hx; val hy = box.hy; val hz = box.hz
        val v0x = ax - box.cx; val v0y = ay - box.cy; val v0z = az - box.cz
        val v1x = bx - box.cx; val v1y = by - box.cy; val v1z = bz - box.cz
        val v2x = cx - box.cx; val v2y = cy - box.cy; val v2z = cz - box.cz

        // the box's own axes: the triangle's extent along each
        if (min(v0x, min(v1x, v2x)) > hx || max(v0x, max(v1x, v2x)) < -hx) return false
        if (min(v0y, min(v1y, v2y)) > hy || max(v0y, max(v1y, v2y)) < -hy) return false
        if (min(v0z, min(v1z, v2z)) > hz || max(v0z, max(v1z, v2z)) < -hz) return false

        val e0x = v1x - v0x; val e0y = v1y - v0y; val e0z = v1z - v0z
        val e1x = v2x - v1x; val e1y = v2y - v1y; val e1z = v2z - v1z
        val e2x = v0x - v2x; val e2y = v0y - v2y; val e2z = v0z - v2z

        // the triangle's plane
        val nx = e0y * e1z - e0z * e1y
        val ny = e0z * e1x - e0x * e1z
        val nz = e0x * e1y - e0y * e1x
        val d = nx * v0x + ny * v0y + nz * v0z
        if (abs(d) > hx * abs(nx) + hy * abs(ny) + hz * abs(nz)) return false

        // the nine edge x axis directions
        return axis(0f, -e0z, e0y, v0x, v0y, v0z, v1x, v1y, v1z, v2x, v2y, v2z, hx, hy, hz) &&
            axis(0f, -e1z, e1y, v0x, v0y, v0z, v1x, v1y, v1z, v2x, v2y, v2z, hx, hy, hz) &&
            axis(0f, -e2z, e2y, v0x, v0y, v0z, v1x, v1y, v1z, v2x, v2y, v2z, hx, hy, hz) &&
            axis(e0z, 0f, -e0x, v0x, v0y, v0z, v1x, v1y, v1z, v2x, v2y, v2z, hx, hy, hz) &&
            axis(e1z, 0f, -e1x, v0x, v0y, v0z, v1x, v1y, v1z, v2x, v2y, v2z, hx, hy, hz) &&
            axis(e2z, 0f, -e2x, v0x, v0y, v0z, v1x, v1y, v1z, v2x, v2y, v2z, hx, hy, hz) &&
            axis(-e0y, e0x, 0f, v0x, v0y, v0z, v1x, v1y, v1z, v2x, v2y, v2z, hx, hy, hz) &&
            axis(-e1y, e1x, 0f, v0x, v0y, v0z, v1x, v1y, v1z, v2x, v2y, v2z, hx, hy, hz) &&
            axis(-e2y, e2x, 0f, v0x, v0y, v0z, v1x, v1y, v1z, v2x, v2y, v2z, hx, hy, hz)
    }

    /** True when the axis (ax, ay, az) does not separate the triangle from the box (centred on the origin). */
    private fun axis(
        ax: Float, ay: Float, az: Float,
        v0x: Float, v0y: Float, v0z: Float, v1x: Float, v1y: Float, v1z: Float, v2x: Float, v2y: Float, v2z: Float,
        hx: Float, hy: Float, hz: Float,
    ): Boolean {
        val p0 = ax * v0x + ay * v0y + az * v0z
        val p1 = ax * v1x + ay * v1y + az * v1z
        val p2 = ax * v2x + ay * v2y + az * v2z
        val r = hx * abs(ax) + hy * abs(ay) + hz * abs(az)
        return !(min(p0, min(p1, p2)) > r || max(p0, max(p1, p2)) < -r)
    }
}
