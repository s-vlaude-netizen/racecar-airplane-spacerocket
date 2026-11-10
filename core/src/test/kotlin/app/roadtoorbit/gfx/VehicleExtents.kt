package app.roadtoorbit.gfx

import app.roadtoorbit.math.Mat4

/** How far the vehicle's drawn body and parts reach in each form, for the tests that compare what is drawn with what is hit. */
internal object VehicleExtents {
    private val cache = HashMap<Pair<VehicleMode, Skin>, FloatArray>()
    private val pm = FloatArray(16)

    /** minx, miny, minz, maxx, maxy, maxz of the vehicle in [mode] with [skin], relative to its origin (the ground under the car), at rest. */
    fun of(lib: MeshLibrary, mode: VehicleMode, skin: Skin): FloatArray = cache.getOrPut(mode to skin) {
        val body = MeshData(VehicleModel.maxBodyVertices + 64)
        VehicleModel.buildBody(body, mode, mode, 0f, skin)
        val b = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        fun grow(d: MeshData, m: FloatArray?) {
            for (i in 0 until d.count) {
                val x = d.pos[i * 3]; val y = d.pos[i * 3 + 1]; val z = d.pos[i * 3 + 2]
                val wx = if (m == null) x else m[0] * x + m[4] * y + m[8] * z + m[12]
                val wy = if (m == null) y else m[1] * x + m[5] * y + m[9] * z + m[13]
                val wz = if (m == null) z else m[2] * x + m[6] * y + m[10] * z + m[14]
                b[0] = minOf(b[0], wx); b[3] = maxOf(b[3], wx)
                b[1] = minOf(b[1], wy); b[4] = maxOf(b[4], wy)
                b[2] = minOf(b[2], wz); b[5] = maxOf(b[5], wz)
            }
        }
        grow(body, null)
        for (part in VehicleModel.partsFor(skin)) {
            val a = part.pose(mode)
            if (maxOf(a.sx, a.sy, a.sz) < 0.05f) continue // folded away in this form
            Mat4.setTrs(pm, a.x, a.y, a.z, a.rx, a.ry, a.rz, a.sx, a.sy, a.sz)
            grow(lib.data(part.mesh), pm)
        }
        b
    }
}
