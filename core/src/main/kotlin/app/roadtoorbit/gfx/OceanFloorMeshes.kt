package app.roadtoorbit.gfx

import app.roadtoorbit.gfx.Col.rgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The models of the seabed and Atlantis: submersible traffic, containers, kelp tufts, crabs, the sunken highway, vents,
 * wrecks, ruins, the lost city and its gate. See [OceanSpec] for what each one has to be like. Same conventions as
 * [WorldMeshes]: hazards are authored around their collision centre, scenery stands on y = 0, everything that moves
 * along the route has its nose toward -Z. Everything is deterministic: the only "random" numbers come from [Kit.h].
 */
internal object OceanFloorMeshes {
    fun build(id: MeshId, b: MeshBuilder) {
        when (id) {
            MeshId.SUBCAR_0, MeshId.SUBCAR_1, MeshId.SUBCAR_2 -> SeabedTraffic.car(b, id.ordinal - MeshId.SUBCAR_0.ordinal)
            MeshId.SUBHAULER_0, MeshId.SUBHAULER_1, MeshId.SUBHAULER_2 -> SeabedTraffic.hauler(b, id.ordinal - MeshId.SUBHAULER_0.ordinal)
            MeshId.SUBVAN_0, MeshId.SUBVAN_1, MeshId.SUBVAN_2 -> SeabedTraffic.van(b, id.ordinal - MeshId.SUBVAN_0.ordinal)
            MeshId.CONTAINER -> SeabedProps.container(b)
            MeshId.KELP_TUFT -> SeabedProps.tuft(b)
            MeshId.CRAB_0 -> SeabedProps.crab(b, 0)
            MeshId.CRAB_1 -> SeabedProps.crab(b, 1)
            MeshId.ROAD_SEA -> SeabedRoad.road(b)
            MeshId.ROAD_SEA_GLOW -> SeabedRoad.glow(b)
            MeshId.VENT -> SeabedScenery.vent(b)
            MeshId.WRECK_0 -> SeabedScenery.wreckCargo(b)
            MeshId.WRECK_1 -> SeabedScenery.wreckSub(b)
            MeshId.RUINS_0, MeshId.RUINS_1, MeshId.RUINS_2 -> SeabedScenery.ruins(b, id.ordinal - MeshId.RUINS_0.ordinal)
            MeshId.ATLANTIS -> Atlantis.city(b)
            MeshId.ATLANTIS_LIGHTS -> Atlantis.lights(b)
            MeshId.ATLANTIS_GATE -> SeabedScenery.gate(b)
            else -> b.box(1f, 1f, 1f, rgb(0xFF00FF)) // not modelled yet
        }
    }
}

/** Small tools shared by the seabed models. */
private object Kit {
    val RAD2DEG = (180.0 / PI).toFloat()

    /** Deterministic hash of up to three integers, in [0, 1): the only source of "randomness" in these models. */
    fun h(a: Int, b: Int = 0, c: Int = 0): Float {
        var x = a * 0x27d4eb2d + b * 0x165667b1 + c * 0x2c1b3c6d + 0x1b873593
        x = x xor (x ushr 15); x *= -0x7a143595
        x = x xor (x ushr 13); x *= -0x3d4d51cb
        x = x xor (x ushr 16)
        return (x ushr 8) / 16777216f
    }

    /** [c] made a little brighter or darker, by a deterministic amount from [key]. */
    fun tone(c: Int, key: Int, amt: Float): Int = Col.mul(c, 1f + (h(key, 7, 3) - 0.5f) * 2f * amt)

    /** A tapered cylinder from point A (radius [r0]) to point B (radius [r1]). */
    fun strut(
        b: MeshBuilder, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float,
        r0: Float, r1: Float, c0: Int, c1: Int = c0, seg: Int = 6, smooth: Boolean = false, cap0: Boolean = true, cap1: Boolean = true,
    ) {
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-5f) return
        val horiz = sqrt(dx * dx + dz * dz)
        val pitch = atan2(horiz, dy) * RAD2DEG
        val yaw = atan2(dx, dz) * RAD2DEG
        b.push().translate((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2).rotateY(yaw).rotateX(pitch)
        b.cylinder(r1, r0, len, seg, c1, c0, smooth = smooth, capTop = cap1, capBottom = cap0)
        b.pop()
    }

    /** A thin box lying along the ground between two points (cracks, strips). */
    fun strip(b: MeshBuilder, x0: Float, z0: Float, x1: Float, z1: Float, y: Float, w: Float, hgt: Float, c: Int) {
        val dx = x1 - x0; val dz = z1 - z0
        val len = sqrt(dx * dx + dz * dz)
        if (len < 1e-5f) return
        b.push().translate((x0 + x1) / 2, y, (z0 + z1) / 2).rotateY(atan2(dx, dz) * RAD2DEG).box(w, hgt, len, c).pop()
    }

    /** An up-facing quad on the ground plane (x0 < x1, z0 < z1). */
    fun flat(b: MeshBuilder, x0: Float, z0: Float, x1: Float, z1: Float, y: Float, c: Int) {
        b.quad(x0, y, z1, x1, y, z1, x1, y, z0, x0, y, z0, c)
    }

    /** A lumpy low mound: a fan of triangles with a ragged outline, flat in the middle, [apex] above the base height [y]. */
    fun mound(b: MeshBuilder, cx: Float, cz: Float, rx: Float, rz: Float, y: Float, apex: Float, c: Int, seed: Int, n: Int = 8) {
        val px = FloatArray(n); val pz = FloatArray(n)
        for (i in 0 until n) {
            val a = (2.0 * PI * i / n).toFloat()
            val k = 0.72f + 0.5f * h(seed, i, 5)
            px[i] = cx + cos(a) * rx * k; pz[i] = cz + sin(a) * rz * k
        }
        for (i in 0 until n) {
            val j = (i + 1) % n
            b.tri(cx, y + apex, cz, px[j], y, pz[j], px[i], y, pz[i], Col.mul(c, 0.94f + 0.12f * h(seed, i, 9)), Col.mul(c, 0.92f), Col.mul(c, 0.92f))
        }
    }

    /** A squashed lumpy ball (sand drifts, rocks). */
    fun lump(b: MeshBuilder, x: Float, y: Float, z: Float, rx: Float, ry: Float, rz: Float, c: Int, seed: Int, jitter: Float = 0.22f, sub: Int = 1) {
        b.push().translate(x, y, z).scale(rx, ry, rz).icosphere(1f, sub, c, jitter, seed, 0.1f).pop()
    }

    /** A sector of a flat ring in the XY plane (axis along Z): radii [rIn]..[rOut], angles [a0]..[a1] in degrees (0 = +X, 90 = +Y), extruded z0..z1. The two ends stay open. */
    fun ringSector(b: MeshBuilder, rIn: Float, rOut: Float, z0: Float, z1: Float, a0: Float, a1: Float, segs: Int, cOut: Int, cIn: Int = cOut, cFace: Int = cOut) {
        val step = (a1 - a0) / segs
        for (i in 0 until segs) {
            val t0 = Math.toRadians((a0 + i * step).toDouble()).toFloat()
            val t1 = Math.toRadians((a0 + (i + 1) * step).toDouble()).toFloat()
            val c0 = cos(t0); val s0 = sin(t0); val c1 = cos(t1); val s1 = sin(t1)
            b.quad(rOut * c0, rOut * s0, z0, rOut * c1, rOut * s1, z0, rOut * c1, rOut * s1, z1, rOut * c0, rOut * s0, z1, cOut)
            b.quad(rIn * c0, rIn * s0, z0, rIn * c0, rIn * s0, z1, rIn * c1, rIn * s1, z1, rIn * c1, rIn * s1, z0, cIn)
            b.quad(rIn * c0, rIn * s0, z1, rOut * c0, rOut * s0, z1, rOut * c1, rOut * s1, z1, rIn * c1, rIn * s1, z1, cFace)
            b.quad(rIn * c0, rIn * s0, z0, rIn * c1, rIn * s1, z0, rOut * c1, rOut * s1, z0, rOut * c0, rOut * s0, z0, cFace)
        }
    }

    /** A fluted column shaft standing on y0: [n] flutes (each a ridge and a groove), radius r0 at the foot and r1 at the top. */
    fun fluted(b: MeshBuilder, x: Float, y0: Float, z: Float, height: Float, r0: Float, r1: Float, n: Int, cRidge: Int, cGroove: Int, capTop: Boolean = true) {
        val m = n * 2
        val y1 = y0 + height
        for (j in 0 until m) {
            val k = (j + 1) % m
            val a0 = (2.0 * PI * j / m).toFloat(); val a1 = (2.0 * PI * (j + 1) / m).toFloat()
            val q0 = if (j % 2 == 0) 1f else 0.9f
            val q1 = if (k % 2 == 0) 1f else 0.9f
            val col0 = if (j % 2 == 0) cRidge else cGroove
            val col1 = if (k % 2 == 0) cRidge else cGroove
            val bx0 = x + cos(a0) * r0 * q0; val bz0 = z + sin(a0) * r0 * q0
            val bx1 = x + cos(a1) * r0 * q1; val bz1 = z + sin(a1) * r0 * q1
            val tx0 = x + cos(a0) * r1 * q0; val tz0 = z + sin(a0) * r1 * q0
            val tx1 = x + cos(a1) * r1 * q1; val tz1 = z + sin(a1) * r1 * q1
            b.tri(bx0, y0, bz0, tx0, y1, tz0, tx1, y1, tz1, Col.mul(col0, 0.9f), col0, col1)
            b.tri(bx0, y0, bz0, tx1, y1, tz1, bx1, y0, bz1, Col.mul(col0, 0.9f), col1, Col.mul(col1, 0.9f))
            if (capTop) b.tri(x, y1, z, tx1, y1, tz1, tx0, y1, tz0, cRidge)
        }
    }

    fun rad(deg: Float): Float = deg * 0.017453292f

    /** A tube lofted through [rings] (x, y pairs, counter-clockwise seen from +Z) at depths [zs], two-sided: [outer] picks the colour of each outside quad, [skip] leaves holes. */
    fun loft(b: MeshBuilder, rings: List<FloatArray>, zs: FloatArray, closed: Boolean, outer: (Int, Int) -> Int, inner: Int, skip: (Int, Int) -> Boolean) {
        val n = rings[0].size / 2
        val edges = if (closed) n else n - 1
        for (i in 0 until rings.size - 1) for (j in 0 until edges) {
            if (skip(i, j)) continue
            val j1 = (j + 1) % n
            val r0 = rings[i]; val r1 = rings[i + 1]
            b.quad(r0[2 * j], r0[2 * j + 1], zs[i], r0[2 * j1], r0[2 * j1 + 1], zs[i], r1[2 * j1], r1[2 * j1 + 1], zs[i + 1], r1[2 * j], r1[2 * j + 1], zs[i + 1], outer(i, j))
            b.quad(r0[2 * j], r0[2 * j + 1], zs[i], r1[2 * j], r1[2 * j + 1], zs[i + 1], r1[2 * j1], r1[2 * j1 + 1], zs[i + 1], r0[2 * j1], r0[2 * j1 + 1], zs[i], inner)
        }
    }

    /** Builds [parts] into a scratch builder, lifts the result so that its lowest point is y = 0 and appends it to [b]. */
    fun grounded(b: MeshBuilder, parts: (MeshBuilder) -> Unit) {
        val s = MeshBuilder(8192)
        parts(s)
        val d = s.data
        var minY = Float.MAX_VALUE
        for (v in 0 until d.count) minY = min(minY, d.pos[v * 3 + 1])
        for (v in 0 until d.count) d.pos[v * 3 + 1] -= minY
        b.data.append(d)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// The traffic of the seabed: deep-sea vehicles on the highway
// ---------------------------------------------------------------------------------------------------------------------

private object SeabedTraffic {
    private val DARK = rgb(0x1E2229)
    private val STEEL = rgb(0x9AA6B4)
    private val STEEL_DARK = rgb(0x4B5563)
    private val TIRE = rgb(0x14171C)
    private val GLASS_TOP = rgb(0xBDF3FF)
    private val GLASS_BOT = rgb(0x2B7F9E)
    private val LAMP = rgb(0xFFF6D0)
    private val LAMP_CYAN = rgb(0xD6FFFB)
    private val TAIL = rgb(0xFF3B2A)
    private val AMBER = rgb(0xFFB020)
    private val WHITE = rgb(0xEEF1EE)
    private val GREY = rgb(0x8E98A6)
    private val BRASS = rgb(0x8A7347)

    /** Yellow / white, teal / white, red-orange / grey (never plain orange: the player's car is orange). */
    private class Paint(val main: Int, val trim: Int)

    private val PAINTS = arrayOf(
        Paint(rgb(0xFFC21A), WHITE),
        Paint(rgb(0x14D8C9), WHITE),
        Paint(rgb(0xF2482B), GREY),
    )

    /** A fat, flat-faced tyre with a coloured hub on its outer face (axis along X). */
    private fun tyre(b: MeshBuilder, x: Float, y: Float, z: Float, r: Float, w: Float, hub: Int, seg: Int = 10) {
        val out = if (x > 0f) -1f else 1f // rotateZ(90) sends local +Y to world -X, so the outer face of a wheel on the right is local -Y
        b.push().translate(x, y, z).rotateZ(90f)
        b.cylinder(r, r, w, seg, TIRE, TIRE, smooth = false)
        b.push().translate(0f, out * w * 0.51f, 0f).cylinder(r * 0.66f, r * 0.66f, 0.04f, seg, hub, hub, smooth = false).pop()
        b.push().translate(0f, out * w * 0.54f, 0f).cylinder(r * 0.24f, r * 0.24f, 0.05f, 6, DARK, DARK, smooth = false).pop()
        b.pop()
    }

    /** A thruster duct along Z: a short open tube, a ring on its rear lip, a dark throat and a three-bladed propeller. */
    private fun duct(b: MeshBuilder, x: Float, y: Float, zRear: Float, r: Float, len: Float, shell: Int) {
        b.push().translate(x, y, zRear - len / 2).rotateX(90f).cylinder(r, r, len, 10, shell, Col.mul(shell, 0.8f), smooth = true, capTop = false, capBottom = false).pop()
        b.push().translate(x, y, zRear).torus(r, r * 0.2f, 10, 5, STEEL, STEEL_DARK).pop()
        b.push().translate(x, y, zRear - len * 0.55f).rotateX(90f).cylinder(r * 0.92f, r * 0.92f, 0.02f, 10, DARK, DARK, smooth = false).pop()
        for (k in 0 until 3) {
            b.push().translate(x, y, zRear - len * 0.38f).rotateZ(k * 120f + 25f).translate(r * 0.45f, 0f, 0f).box(r * 0.9f, r * 0.34f, 0.03f, STEEL).pop()
        }
        b.push().translate(x, y, zRear - len * 0.34f).icosphere(r * 0.2f, 0, STEEL_DARK).pop()
    }

    /** The mini-sub buggy: 1.9 m wide, 4.2 m long, a flattened egg of a hull between four fat tyres under a glass bubble. */
    fun car(b: MeshBuilder, v: Int) {
        val p = PAINTS[v]
        val g = -0.65f
        for (sx in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) tyre(b, sx * 0.73f, g + 0.4f, sz * 1.3f, 0.4f, 0.44f, p.main)
        b.push().translate(0f, g + 0.47f, 0f).box(1.0f, 0.18f, 3.6f, DARK).pop()
        // the hull
        b.push().translate(0f, g + 0.83f, 0f).scale(0.68f, 0.42f, 2.0f).sphere(1f, 6, 14, Col.mul(p.main, 1.12f), Col.mul(p.main, 0.6f), smooth = true).pop()
        // sponsons between the wheels, fenders over them
        for (sx in intArrayOf(-1, 1)) {
            b.push().translate(sx * 0.74f, g + 0.6f, 0f).box(0.42f, 0.3f, 1.7f, p.trim, Col.mul(p.trim, 0.92f), Col.mul(p.trim, 0.7f), DARK).pop()
            for (sz in intArrayOf(-1, 1)) b.push().translate(sx * 0.74f, g + 0.86f, sz * 1.3f).box(0.5f, 0.1f, 0.95f, p.main, Col.mul(p.main, 0.9f), Col.mul(p.main, 0.7f), DARK).pop()
        }
        // the glass bubble over the cockpit, with its frame
        b.push().translate(0f, g + 1.1f, -0.45f).scale(1f, 1f, 1.5f).cylinder(0.52f, 0.56f, 0.1f, 12, p.trim, p.trim, smooth = false).pop()
        b.push().translate(0f, g + 1.14f, -0.45f).scale(0.5f, 0.5f, 0.78f).sphere(1f, 5, 12, GLASS_TOP, GLASS_BOT, smooth = true, latFromDeg = 0f, latToDeg = 90f).pop()
        // the tail: a fin with an amber beacon, a thruster duct each side
        b.push().translate(0f, g + 1.1f, 1.2f).prismZY(floatArrayOf(-0.5f, 0f, 0.62f, 0f, 0.75f, 0.58f, 0.15f, 0.58f), 0.07f, p.trim, p.trim, DARK).pop()
        Kit.strut(b, 0f, g + 1.68f, 1.3f, 0f, g + 2.0f, 1.3f, 0.025f, 0.025f, STEEL_DARK)
        b.push().translate(0f, g + 2.04f, 1.3f).icosphere(0.07f, 0, AMBER).pop()
        for (sx in intArrayOf(-1, 1)) duct(b, sx * 0.42f, g + 0.8f, 2.1f, 0.21f, 0.5f, p.trim)
        for (sx in intArrayOf(-1, 1)) {
            b.push().translate(sx * 0.26f, g + 1.08f, 1.58f).box(0.2f, 0.1f, 0.08f, TAIL).pop() // tail lights
            b.push().translate(sx * 0.2f, g + 0.86f, -1.95f).icosphere(0.11f, 1, LAMP).pop() // running lights
            b.push().translate(sx * 0.9f, g + 0.86f, -1.76f).box(0.14f, 0.1f, 0.1f, LAMP).pop()
        }
    }

    /** The cargo crawler: 2.5 m wide, 7.4 m long, 3.2 m high: a glass-domed cab and a big hexagonal cargo pod on a long chassis. */
    fun hauler(b: MeshBuilder, v: Int) {
        val p = PAINTS[v]
        val pod = if (v == 2) rgb(0xB4BCC6) else rgb(0xE6EAEC)
        val g = -1.6f
        for (sx in intArrayOf(-1, 1)) for (z in floatArrayOf(-2.5f, 1.0f, 2.55f)) tyre(b, sx * 1.07f, g + 0.5f, z, 0.5f, 0.36f, p.main, 12)
        b.push().translate(0f, g + 0.7f, 0f).box(2.1f, 0.45f, 6.9f, DARK).pop()
        // the cab
        b.push().translate(0f, g + 1.3f, -2.65f).box(2.2f, 0.9f, 2.0f, p.main, Col.mul(p.main, 0.95f), Col.mul(p.main, 0.78f), DARK).pop()
        b.push().translate(0f, g + 1.74f, -2.62f).scale(1f, 1f, 1.12f).cylinder(1.0f, 1.04f, 0.1f, 14, p.trim, p.trim, smooth = false).pop()
        b.push().translate(0f, g + 1.78f, -2.62f).scale(1.0f, 0.85f, 1.1f).sphere(1f, 5, 14, GLASS_TOP, GLASS_BOT, smooth = true, latFromDeg = 0f, latToDeg = 90f).pop()
        b.push().translate(0f, g + 0.85f, -3.58f).box(2.3f, 0.3f, 0.3f, DARK).pop()
        for (sx in intArrayOf(-1, 1)) {
            b.push().translate(sx * 0.7f, g + 1.18f, -3.68f).cylinder(0.2f, 0.2f, 0.06f, 8, LAMP, LAMP, smooth = false).pop()
            b.push().translate(sx * 0.7f, g + 1.18f, -3.68f).rotateX(90f).cylinder(0.2f, 0.2f, 0.06f, 8, LAMP, LAMP, smooth = false).pop()
        }
        // the pod: a hexagonal tank, flat on top, with bands of paint and end rings
        val pz = 0.8f
        val py = g + 1.965f
        b.push().translate(0f, py, pz).rotateX(90f).cylinder(1.2f, 1.2f, 4.5f, 6, pod, pod, smooth = false).pop()
        for (zz in floatArrayOf(-1.1f, 0.8f, 2.7f)) {
            b.push().translate(0f, py, pz + (zz - pz)).rotateX(90f).cylinder(1.25f, 1.25f, 0.3f, 6, p.main, p.main, smooth = false, capTop = false, capBottom = false).pop()
        }
        b.push().translate(0f, py, -1.45f).rotateX(90f).cylinder(1.28f, 1.28f, 0.12f, 6, p.trim, p.trim, smooth = false).pop()
        b.push().translate(0f, py, 3.05f).rotateX(90f).cylinder(1.29f, 1.29f, 0.14f, 6, p.main, p.main, smooth = false).pop()
        b.push().translate(0f, py + 0.2f, 3.13f).rotateX(90f).cylinder(0.62f, 0.62f, 0.05f, 12, p.trim, p.trim, smooth = false).pop()
        b.push().translate(0f, py + 0.2f, 3.17f).rotateX(90f).cylinder(0.42f, 0.42f, 0.04f, 12, p.main, p.main, smooth = false).pop()
        // hatches and pipes on the roof, a beacon on the cab
        b.push().translate(0f, py + 1.06f, 0.4f).box(0.9f, 0.1f, 1.1f, STEEL_DARK).pop()
        for (sx in intArrayOf(-1, 1)) Kit.strut(b, sx * 0.55f, py + 1.04f, 1.8f, sx * 0.55f, py + 1.04f, 3.0f, 0.1f, 0.1f, STEEL, STEEL, 6, smooth = true)
        Kit.strut(b, 0f, g + 2.2f, -2.62f, 0f, g + 2.5f, -2.62f, 0.05f, 0.05f, STEEL_DARK)
        b.push().translate(0f, g + 2.56f, -2.62f).icosphere(0.13f, 1, AMBER).pop()
        // the stern: two big nozzles with a cold blue throat, tail lamps
        for (sx in intArrayOf(-1, 1)) {
            b.push().translate(sx * 0.66f, g + 1.2f, 3.32f).rotateX(90f).cylinder(0.4f, 0.3f, 0.78f, 10, STEEL_DARK, STEEL, smooth = true, capTop = false, capBottom = false).pop()
            b.push().translate(sx * 0.66f, g + 1.2f, 3.5f).rotateX(90f).cylinder(0.3f, 0.3f, 0.02f, 10, rgb(0x6FD8F0), rgb(0x6FD8F0), smooth = false).pop()
            b.push().translate(sx * 1.0f, g + 1.9f, 3.08f).box(0.3f, 0.2f, 0.06f, TAIL).pop()
            b.push().translate(sx * 0.98f, g + 2.5f, 3.08f).box(0.26f, 0.16f, 0.06f, AMBER).pop()
        }
    }

    /** The bathyscaphe on wheels: 2.1 m wide, 5.2 m long, 2.2 m high: an egg-shaped pressure hull with portholes and a floodlight bar. */
    fun van(b: MeshBuilder, v: Int) {
        val p = PAINTS[v]
        val g = -1.1f
        for (sx in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) tyre(b, sx * 0.9f, g + 0.38f, sz * 1.62f, 0.38f, 0.3f, p.main)
        b.push().translate(0f, g + 0.58f, 0f).box(1.7f, 0.3f, 4.9f, DARK).pop()
        // the pressure hull: a belly band and a coloured upper shell
        val hcy = g + 1.2f
        b.push().translate(0f, hcy, 0f).scale(0.92f, 0.9f, 2.25f).sphere(1f, 3, 16, Col.mul(p.trim, 0.75f), Col.mul(p.trim, 0.55f), smooth = true, latFromDeg = -90f, latToDeg = -18f).pop()
        b.push().translate(0f, hcy, 0f).scale(0.92f, 0.9f, 2.25f).sphere(1f, 6, 16, Col.mul(p.main, 1.12f), Col.mul(p.main, 0.82f), smooth = true, latFromDeg = -18f, latToDeg = 90f).pop()
        // portholes along both flanks, a big round viewport at the nose
        for (sx in intArrayOf(-1, 1)) for (z in floatArrayOf(-1.2f, 0.1f, 1.4f)) {
            val dy = 0.2f
            val sxp = 0.92f * sqrt(max(0.05f, 1f - (dy / 0.9f) * (dy / 0.9f) - (z / 2.25f) * (z / 2.25f)))
            val yaw = atan2(z / 5.06f, sxp / 0.846f) * Kit.RAD2DEG
            b.push().translate(sx * (sxp - 0.02f), hcy + dy, z).rotateY(if (sx > 0) yaw else -yaw + 180f)
            b.rotateZ(-90f).cylinder(0.27f, 0.27f, 0.12f, 10, BRASS, BRASS, smooth = false)
            b.push().translate(0f, 0.04f, 0f).cylinder(0.2f, 0.2f, 0.1f, 10, LAMP_CYAN, LAMP_CYAN, smooth = false).pop()
            b.pop()
        }
        b.push().translate(0f, hcy + 0.16f, -2.22f).rotateX(-90f).cylinder(0.34f, 0.36f, 0.2f, 10, BRASS, BRASS, smooth = false).pop()
        b.push().translate(0f, hcy + 0.16f, -2.34f).rotateX(-90f).cylinder(0.26f, 0.26f, 0.06f, 10, LAMP_CYAN, LAMP_CYAN, smooth = false).pop()
        // the roof hatch and the floodlight bar over the nose
        b.push().translate(0f, hcy + 0.9f, 0.5f).cylinder(0.34f, 0.4f, 0.2f, 10, STEEL, STEEL_DARK, smooth = false).pop()
        b.push().translate(0f, hcy + 1.02f, 0.5f).cylinder(0.22f, 0.3f, 0.1f, 10, p.trim, STEEL, smooth = false).pop()
        Kit.strut(b, 0f, hcy + 0.62f, -1.45f, 0f, hcy + 0.86f, -1.45f, 0.06f, 0.06f, STEEL_DARK)
        b.push().translate(0f, hcy + 0.88f, -1.5f).box(1.25f, 0.12f, 0.22f, STEEL_DARK).pop()
        for (k in -1..1) {
            b.push().translate(k * 0.42f, hcy + 0.9f, -1.62f).rotateX(90f).cylinder(0.12f, 0.14f, 0.1f, 8, LAMP, rgb(0x6B6B60), smooth = false).pop()
        }
        // the stern: a big thruster nozzle with a cold blue throat, tail lights, small trim fins
        b.push().translate(0f, hcy - 0.15f, 2.28f).rotateX(90f).cylinder(0.4f, 0.5f, 0.42f, 12, STEEL_DARK, STEEL, smooth = true, capTop = false, capBottom = false).pop()
        b.push().translate(0f, hcy - 0.15f, 2.46f).rotateX(90f).cylinder(0.36f, 0.36f, 0.02f, 12, rgb(0x6FD8F0), rgb(0x6FD8F0), smooth = false).pop()
        for (sx in intArrayOf(-1, 1)) {
            b.push().translate(sx * 0.6f, hcy + 0.4f, 1.9f).box(0.24f, 0.16f, 0.08f, TAIL).pop()
            b.push().translate(sx * 0.98f, hcy - 0.32f, 1.85f).box(0.28f, 0.05f, 0.8f, p.trim).pop()
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Hazards of the seabed: the container, the kelp tuft, the crabs
// ---------------------------------------------------------------------------------------------------------------------

private object SeabedProps {
    private val SAND = rgb(0xC6B78C)

    /** A corrugated wall facing +Z: trapezoid ribs along x from [x0] to [x1], from [y0] to [y1], standing [d] proud of the plane z. */
    private fun ribsZ(b: MeshBuilder, x0: Float, x1: Float, y0: Float, y1: Float, z: Float, d: Float, teeth: Int, c: Int, rust: Int, seed: Int) {
        val w = (x1 - x0) / teeth
        val px = floatArrayOf(0f, 0.3f, 0.42f, 0.58f, 0.7f, 1f)
        val pd = floatArrayOf(0f, 0f, d, d, 0f, 0f)
        for (k in 0 until teeth) {
            val base = Col.mix(c, rust, if (Kit.h(seed, k, 1) > 0.62f) 0.55f * Kit.h(seed, k, 2) + 0.2f else 0f)
            val col = Kit.tone(base, seed * 17 + k, 0.1f)
            for (s in 0 until 5) {
                val xa = x0 + (k + px[s]) * w; val xb = x0 + (k + px[s + 1]) * w
                val za = z + pd[s]; val zb = z + pd[s + 1]
                val shade = if (pd[s] > 0f && pd[s + 1] > 0f) 1.1f else if (pd[s] == pd[s + 1]) 0.78f else 0.94f
                b.quad(xa, y0, za, xb, y0, zb, xb, y1, zb, xa, y1, za, Col.mul(col, shade * 0.85f), Col.mul(col, shade * 0.85f), Col.mul(col, shade), Col.mul(col, shade))
            }
        }
    }

    /** A corrugated roof facing +Y: ribs along z from [z0] to [z1], across x from [x0] to [x1] at height y. */
    private fun ribsY(b: MeshBuilder, x0: Float, x1: Float, z0: Float, z1: Float, y: Float, d: Float, teeth: Int, c: Int, rust: Int, seed: Int) {
        val w = (x1 - x0) / teeth
        val px = floatArrayOf(0f, 0.3f, 0.42f, 0.58f, 0.7f, 1f)
        val pd = floatArrayOf(0f, 0f, d, d, 0f, 0f)
        for (k in 0 until teeth) {
            val base = Col.mix(c, rust, if (Kit.h(seed, k, 3) > 0.7f) 0.5f * Kit.h(seed, k, 4) + 0.2f else 0f)
            val col = Kit.tone(base, seed * 13 + k, 0.1f)
            for (s in 0 until 5) {
                val xa = x0 + (k + px[s]) * w; val xb = x0 + (k + px[s + 1]) * w
                val ya = y + pd[s]; val yb = y + pd[s + 1]
                val shade = if (pd[s] > 0f && pd[s + 1] > 0f) 1.12f else if (pd[s] == pd[s + 1]) 0.8f else 0.95f
                b.quad(xa, ya, z1, xb, yb, z1, xb, yb, z0, xa, ya, z0, Col.mul(col, shade))
            }
        }
    }

    /**
     * A broken-off length of shipping container lying across the lane, half buried in sand: 2.9 m wide (x), 1 m high, 1 m deep.
     * Rusty red corrugated panels, one open end (+x) showing the dark inside, a stencilled white plate and a drift of sand
     * against the side the player comes from (+z).
     */
    fun container(b: MeshBuilder) {
        val red = rgb(0xDA4A2E)
        val rust = rgb(0xB06A32)
        val dark = rgb(0x1B1614)
        val frame = rgb(0x6E3A25)
        b.push().translate(0f, -0.02f, 0f).rotateZ(1.5f).scale(0.975f, 1f, 1f)
        // dark inner slabs (their inside faces are what shows through the open end)
        b.push().translate(0f, 0.385f, -0.03f).box(2.76f, 0.05f, 0.76f, dark).pop()
        b.push().translate(0f, 0.02f, 0.3f).box(2.76f, 0.74f, 0.05f, dark).pop()
        b.push().translate(0f, 0.02f, -0.36f).box(2.76f, 0.74f, 0.05f, dark).pop()
        b.push().translate(-1.36f, 0.02f, -0.03f).box(0.05f, 0.74f, 0.7f, dark).pop()
        b.push().translate(0f, -0.34f, -0.03f).box(2.76f, 0.05f, 0.76f, dark).pop()
        // corrugated skin: roof, and the two long walls (the near one faces the player)
        ribsY(b, -1.4f, 1.4f, -0.4f, 0.34f, 0.41f, 0.045f, 15, red, rust, 1)
        ribsZ(b, -1.4f, 1.4f, -0.36f, 0.4f, 0.34f, 0.05f, 15, red, rust, 2)
        b.push().rotateY(180f)
        ribsZ(b, -1.4f, 1.4f, -0.36f, 0.4f, 0.4f, 0.05f, 15, Col.mul(red, 0.9f), rust, 3)
        b.pop()
        // the closed end (-x): a door panel with its locking bars
        b.push().translate(-1.41f, 0.03f, -0.03f).box(0.05f, 0.78f, 0.72f, Col.mul(red, 0.85f), red, Col.mul(red, 0.7f), rust).pop()
        for (k in 0 until 4) b.push().translate(-1.45f, 0.03f, -0.28f + k * 0.18f).box(0.03f, 0.74f, 0.03f, STEEL_BAR).pop()
        // the frame of the open end (+x): corner posts, a header and a sill, rusty
        for (sz in floatArrayOf(-0.37f, 0.31f)) b.push().translate(1.4f, 0.03f, sz).box(0.1f, 0.8f, 0.1f, frame).pop()
        b.push().translate(1.4f, 0.42f, -0.03f).box(0.1f, 0.08f, 0.78f, frame).pop()
        b.push().translate(1.4f, -0.34f, -0.03f).box(0.1f, 0.08f, 0.78f, frame).pop()
        // corner castings on the roof
        for (sx in intArrayOf(-1, 1)) for (sz in floatArrayOf(-0.38f, 0.32f)) b.push().translate(sx * 1.36f, 0.43f, sz).box(0.2f, 0.08f, 0.16f, frame).pop()
        // a stencilled white plate and a hazard stripe on the roof: reads from afar
        b.push().translate(-0.7f, 0.18f, 0.4f).box(0.5f, 0.22f, 0.02f, rgb(0xF1EAD6)).pop()
        for (k in 0 until 4) b.push().translate(0.2f + k * 0.28f, 0.465f, 0.0f).rotateY(-30f).box(0.1f, 0.012f, 0.62f, rgb(0xF2D13A)).pop()
        b.pop()
        // sand drifted up against the near wall and the ends, spilling into the open end
        Kit.lump(b, -0.8f, -0.27f, 0.3f, 0.5f, 0.17f, 0.15f, SAND, 3)
        Kit.lump(b, -0.1f, -0.26f, 0.3f, 0.7f, 0.2f, 0.15f, Col.mul(SAND, 0.94f), 4)
        Kit.lump(b, 0.8f, -0.27f, 0.3f, 0.52f, 0.18f, 0.15f, SAND, 5)
        Kit.lump(b, 1.1f, -0.25f, 0.05f, 0.25f, 0.17f, 0.4f, Col.mul(SAND, 1.05f), 6)
        Kit.lump(b, -1.1f, -0.26f, -0.02f, 0.25f, 0.17f, 0.4f, Col.mul(SAND, 0.96f), 7)
        Kit.lump(b, 0.3f, -0.34f, -0.38f, 1.1f, 0.12f, 0.12f, Col.mul(SAND, 0.9f), 8)
    }

    private val STEEL_BAR = rgb(0x5B4A3E)

    /**
     * A clump of seaweed: seven tapered blades in a fan, 1.1 m tall (bottom at y = -0.55), dark green at the root and yellow-green at the tips.
     * Each blade is a chain of flattened diamond sections that sways a little in a different direction.
     */
    fun tuft(b: MeshBuilder) {
        val rootCol = rgb(0x2A9A4A)
        val midCol = rgb(0x5CCB52)
        val tipCol = rgb(0xC6F24E)
        val n = 7
        for (k in 0 until n) {
            val a = k * 2.399f // golden angle: an even spread around the root
            val lean = 0.12f + 0.2f * (k % 3) / 2f
            val hgt = 1.1f * (if (k == 0) 1f else 0.78f + 0.2f * Kit.h(k, 1))
            val bx = cos(a) * 0.1f; val bz = sin(a) * 0.1f
            val segs = 5
            val px = FloatArray(segs + 1); val py = FloatArray(segs + 1); val pz = FloatArray(segs + 1)
            for (i in 0..segs) {
                val t = i.toFloat() / segs
                val bend = lean * t * t * 1.3f + 0.04f * sin(t * 5f + k) * t // curves outward and wobbles
                px[i] = bx + cos(a) * bend * hgt
                pz[i] = bz + sin(a) * bend * hgt
                py[i] = -0.55f + hgt * t * (1f - 0.08f * t)
            }
            // sections: a flattened diamond, wide across the fan's direction
            val wdx = -sin(a); val wdz = cos(a)
            for (i in 0 until segs) {
                val w0 = 0.13f * (1f - i.toFloat() / segs) + 0.012f
                val w1 = 0.13f * (1f - (i + 1).toFloat() / segs) + 0.012f
                val t0 = w0 * 0.34f; val t1 = w1 * 0.34f
                val c0 = Col.mix(rootCol, if (i < 2) midCol else tipCol, i / 3f)
                val c1 = Col.mix(rootCol, if (i + 1 < 2) midCol else tipCol, (i + 1) / 3f)
                // tangent -> the thin axis is perpendicular to the width direction and the tangent
                val tx0 = (px[min(i + 1, segs)] - px[max(i - 1, 0)]); val ty0 = (py[min(i + 1, segs)] - py[max(i - 1, 0)]); val tz0 = (pz[min(i + 1, segs)] - pz[max(i - 1, 0)])
                val tx1 = (px[min(i + 2, segs)] - px[i]); val ty1 = (py[min(i + 2, segs)] - py[i]); val tz1 = (pz[min(i + 2, segs)] - pz[i])
                val n0 = thin(tx0, ty0, tz0, wdx, wdz); val n1 = thin(tx1, ty1, tz1, wdx, wdz)
                diamond(b, px[i], py[i], pz[i], w0, t0, wdx, wdz, n0, px[i + 1], py[i + 1], pz[i + 1], w1, t1, n1, c0, c1)
            }
        }
        // a stub of root holdfast
        b.push().translate(0f, -0.53f, 0f).cylinder(0.1f, 0.16f, 0.1f, 7, rootCol, Col.mul(rootCol, 0.7f), smooth = false).pop()
    }

    /** The unit vector perpendicular to a blade's tangent and its (horizontal) width direction. */
    private fun thin(tx: Float, ty: Float, tz: Float, wx: Float, wz: Float): FloatArray {
        // cross(tangent, width) where width = (wx, 0, wz)
        var nx = ty * wz - tz * 0f
        var ny = tz * wx - tx * wz
        var nz = tx * 0f - ty * wx
        val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        nx /= l; ny /= l; nz /= l
        return floatArrayOf(nx, ny, nz)
    }

    /** Four flat faces between two rhombus sections (width vector (wx, 0, wz) times w, thin vector n times t). */
    private fun diamond(
        b: MeshBuilder,
        x0: Float, y0: Float, z0: Float, w0: Float, t0: Float, wx: Float, wz: Float, n0: FloatArray,
        x1: Float, y1: Float, z1: Float, w1: Float, t1: Float, n1: FloatArray, c0: Int, c1: Int,
    ) {
        // section corners: +W, +T, -W, -T
        val a = floatArrayOf(x0 + wx * w0, y0, z0 + wz * w0)
        val bq = floatArrayOf(x0 + n0[0] * t0, y0 + n0[1] * t0, z0 + n0[2] * t0)
        val c = floatArrayOf(x0 - wx * w0, y0, z0 - wz * w0)
        val d = floatArrayOf(x0 - n0[0] * t0, y0 - n0[1] * t0, z0 - n0[2] * t0)
        val a1 = floatArrayOf(x1 + wx * w1, y1, z1 + wz * w1)
        val b1 = floatArrayOf(x1 + n1[0] * t1, y1 + n1[1] * t1, z1 + n1[2] * t1)
        val c1v = floatArrayOf(x1 - wx * w1, y1, z1 - wz * w1)
        val d1 = floatArrayOf(x1 - n1[0] * t1, y1 - n1[1] * t1, z1 - n1[2] * t1)
        fun face(p0: FloatArray, p1: FloatArray, q1: FloatArray, q0: FloatArray) {
            // p0 -> p1 around the section (bottom edge), q1 / q0 the same corners one section up; the orientation is made outward below
            val ex = (p0[0] + p1[0] + q0[0] + q1[0]) / 4f - (x0 + x1) / 2f
            val ey = (p0[1] + p1[1] + q0[1] + q1[1]) / 4f - (y0 + y1) / 2f
            val ez = (p0[2] + p1[2] + q0[2] + q1[2]) / 4f - (z0 + z1) / 2f
            val ux = p1[0] - p0[0]; val uy = p1[1] - p0[1]; val uz = p1[2] - p0[2]
            val vx = q1[0] - p1[0]; val vy = q1[1] - p1[1]; val vz = q1[2] - p1[2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            if (nx * ex + ny * ey + nz * ez >= 0f) {
                b.quad(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], q1[0], q1[1], q1[2], q0[0], q0[1], q0[2], c0, c0, c1, c1)
            } else {
                b.quad(p1[0], p1[1], p1[2], p0[0], p0[1], p0[2], q0[0], q0[1], q0[2], q1[0], q1[1], q1[2], c0, c0, c1, c1)
            }
        }
        face(a, bq, b1, a1); face(bq, c, c1v, b1); face(c, d, d1, c1v); face(d, a, a1, d1)
    }

    private class CrabSpec(
        val shell: Int, val shellDark: Int, val claw: Int, val tip: Int, val leg: Int, val legTip: Int, val eye: Int, val spot: Int,
        val rx: Float, val ry: Float, val rz: Float, val legSpan: Float, val legR: Float, val clawScale: Float, val spiny: Boolean,
    )

    private val CRABS = arrayOf(
        // red rock crab: wide, flat, chunky, with big pincers
        CrabSpec(rgb(0xD8412A), rgb(0x8C2418), rgb(0xE5502C), rgb(0xF4E6C8), rgb(0xB8321F), rgb(0x7A2418), rgb(0x15151B), rgb(0xF08A4A), 1.0f, 0.46f, 0.74f, 1.0f, 0.085f, 1.0f, false),
        // pale ghost / spider crab: rounder, spiny, long thin legs, slender claws
        CrabSpec(rgb(0xE8E1D2), rgb(0xA79F94), rgb(0xE9D8C6), rgb(0xE98A72), rgb(0xDCD3C4), rgb(0xB6AB9C), rgb(0x15151B), rgb(0xD9B79F), 0.82f, 0.4f, 0.72f, 1.5f, 0.05f, 0.82f, true),
    )

    /**
     * A crab (unit model: shell about 1 m across the middle, wider along x than long along z): two big claws toward -z, four legs a
     * side, eye stalks. The feet reach y = -0.95 (the model's origin is its hit sphere's centre, a radius above the sand).
     */
    fun crab(b: MeshBuilder, v: Int) {
        val s = CRABS[v]
        // the shell: a flattened dome, the belly dark
        b.push().translate(0f, 0.05f, 0f).scale(s.rx, s.ry, s.rz).sphere(1f, 5, 12, s.shell, s.shellDark, smooth = true, latFromDeg = -40f, latToDeg = 90f).pop()
        // a raised ridge and bumps on the carapace
        if (s.spiny) {
            for (k in 0 until 7) {
                val a = (k - 3) * 0.5f
                b.push().translate(sin(a) * s.rx * 0.6f, 0.05f + s.ry * cos(a * 0.9f) * 0.82f, cos(a) * s.rz * 0.18f - 0.05f).cone(0.07f, 0.24f, 5, s.shell, s.shellDark, smooth = false, cap = false).pop()
            }
            for (sx in intArrayOf(-1, 1)) b.push().translate(sx * 0.1f, 0.12f, -s.rz * 1.02f).rotateX(-80f).cone(0.05f, 0.34f, 5, s.shell, s.shellDark, smooth = false, cap = false).pop()
        } else {
            for (k in 0 until 6) {
                val ax = (k - 2.5f) * 0.28f
                val az = if (k % 2 == 0) -0.12f else 0.18f
                b.push().translate(ax * s.rx, 0.05f + s.ry * 0.86f, az).icosphere(0.11f, 1, s.spot, 0.15f, k, 0.1f).pop()
            }
            b.push().translate(0f, 0.05f + s.ry * 0.7f, -s.rz * 0.55f).scale(0.8f, 0.14f, 0.12f).icosphere(1f, 1, Col.mul(s.shell, 1.2f), 0.1f, 3, 0f).pop()
        }
        // eyes on stalks
        for (sx in intArrayOf(-1, 1)) {
            Kit.strut(b, sx * 0.24f, 0.25f, -s.rz * 0.85f, sx * 0.3f, 0.54f, -s.rz * 0.92f, 0.04f, 0.035f, s.shell, s.shellDark, 5)
            b.push().translate(sx * 0.3f, 0.58f, -s.rz * 0.94f).icosphere(0.1f, 1, s.eye).pop()
            b.push().translate(sx * 0.3f, 0.6f, -s.rz * 0.94f - 0.07f).icosphere(0.035f, 0, rgb(0xFFFFFF)).pop()
        }
        // eight legs: a short thick upper segment and a long thin one that stands on the seabed
        for (side in intArrayOf(-1, 1)) for (i in 0 until 4) {
            val zf = (i - 1.5f) / 1.5f
            val z0 = zf * s.rz * 0.5f
            val x0 = side * s.rx * (0.84f - 0.14f * zf * zf)
            val kx = x0 + side * s.legSpan * 0.42f
            val ky = 0.36f + 0.06f * (1f - abs(zf))
            val kz = z0 + zf * 0.22f
            val fx = x0 + side * s.legSpan
            val fz = z0 + zf * 0.55f + (if (i < 2) -0.05f else 0.05f)
            Kit.strut(b, x0, 0.04f, z0, kx, ky, kz, s.legR * 1.3f, s.legR * 1.05f, s.leg, s.leg, 5)
            b.push().translate(kx, ky, kz).icosphere(s.legR * 1.35f, 0, s.legTip).pop()
            Kit.strut(b, kx, ky, kz, fx, -0.95f, fz, s.legR * 1.05f, s.legR * 0.4f, s.leg, s.legTip, 5)
        }
        // the claws: an arm, a palm and two fingers that curve toward each other
        val cs = s.clawScale
        for (side in intArrayOf(-1, 1)) {
            val sx0 = side * s.rx * 0.6f; val sz0 = -s.rz * 0.7f
            val ex = side * (s.rx * 0.8f + 0.1f); val ez = sz0 - 0.55f * cs
            Kit.strut(b, sx0, 0.08f, sz0, ex, 0.16f, ez, 0.17f * cs, 0.14f * cs, s.claw, s.claw, 6)
            b.push().translate(ex, 0.16f, ez).icosphere(0.17f * cs, 1, s.claw).pop()
            val px = ex - side * 0.05f; val pz = ez - 0.42f * cs
            b.push().translate(px, 0.16f, pz).scale(0.3f * cs, 0.22f * cs, 0.42f * cs).sphere(1f, 4, 8, Col.mul(s.claw, 1.1f), s.claw, smooth = true).pop()
            for (f in intArrayOf(-1, 1)) {
                val fx0 = px + f * 0.16f * cs; val fz0 = pz - 0.3f * cs
                val mx = px + f * 0.2f * cs; val mz = fz0 - 0.4f * cs
                val tx = px + f * 0.05f * cs; val tz = mz - 0.42f * cs
                Kit.strut(b, fx0, 0.16f, fz0, mx, 0.16f, mz, 0.11f * cs, 0.085f * cs, s.claw, s.claw, 5)
                Kit.strut(b, mx, 0.16f, mz, tx, 0.16f, tz, 0.085f * cs, 0.015f, s.claw, s.tip, 5)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// The sunken highway
// ---------------------------------------------------------------------------------------------------------------------

private object SeabedRoad {
    private val SLAB = rgb(0x2A3446)
    private val TILES = intArrayOf(rgb(0x3A475E), rgb(0x34405A), rgb(0x404E66), rgb(0x2F3B53), rgb(0x3D4A61))
    private val SAND = rgb(0xBBA97E)
    private val SILT = rgb(0x6F8C8D)
    private val CRACK = rgb(0x141A25)
    private val RAIL = rgb(0x2E9A8C)
    private val POST = rgb(0x205A63)
    private val KERB_A = rgb(0x5C7C7A)
    private val KERB_B = rgb(0xC9B89A)
    private val METAL = rgb(0x28444C)
    private val CORAL = intArrayOf(rgb(0xEA6F8E), rgb(0xF29A4B), rgb(0xA66FD0), rgb(0xF4D86A), rgb(0xE8E2CC))
    private const val LEN = 24f

    fun road(b: MeshBuilder) {
        val y = 0.03f
        // the slab (its edge and underside), the lane tiles on top: each slab a slightly different slate
        b.push().translate(0f, 0f, -LEN / 2).box(13.0f, 0.05f, LEN, SLAB).pop()
        val xs = floatArrayOf(-6.5f, -5.9f, -3f, 0f, 3f, 5.9f, 6.5f)
        for (i in 0 until 6) for (k in 0 until 8) {
            Kit.flat(b, xs[i], -(k + 1) * 3f, xs[i + 1], -k * 3f, y, Kit.tone(TILES[(Kit.h(i, k, 11) * 5).toInt().coerceIn(0, 4)], i * 8 + k, 0.05f))
        }
        // sand shoulders
        for (side in intArrayOf(-1, 1)) b.push().translate(side * 8.25f, -0.005f, -LEN / 2).box(3.7f, 0.05f, LEN, SAND, Col.mul(SAND, 0.9f), Col.mul(SAND, 0.7f), Col.mul(SAND, 0.6f)).pop()
        cracks(b, y)
        drifts(b)
        kerbs(b)
        rails(b)
        lamp(b, -8.2f, -12f, false)
        lamp(b, 8.2f, -LEN + 0.5f, false)
    }

    /** Cracks in the slate: chains of thin dark strips, and a couple of heaves. */
    private fun cracks(b: MeshBuilder, y: Float) {
        for (k in 0 until 11) {
            var x = -5.2f + 10.4f * Kit.h(k, 20)
            var z = -1.5f - 21f * Kit.h(k, 21)
            val dir = (Kit.h(k, 22) - 0.5f) * 2.4f + (if (Kit.h(k, 23) > 0.5f) 0f else 1.57f) // mostly across, some along
            for (s in 0 until 3) {
                val a = dir + (Kit.h(k, 30 + s) - 0.5f) * 1.2f
                val l = 0.9f + 1.2f * Kit.h(k, 40 + s)
                val nx = x + sin(a) * l; val nz = z + cos(a) * l
                if (nz > -0.4f || nz < -LEN + 0.4f || abs(nx) > 5.7f) break
                Kit.strip(b, x, z, nx, nz, y + 0.004f, 0.07f - 0.012f * s, 0.012f, CRACK)
                x = nx; z = nz
            }
        }
    }

    /** Sand and silt drifts on the lanes (low, so that the lane lines stay on top) and banks on the shoulders. */
    private fun drifts(b: MeshBuilder) {
        val top = 0.052f
        for (k in 0 until 26) {
            val r = 0.35f + 0.9f * Kit.h(k, 50) * Kit.h(k, 57)
            val x = -5.6f + 11.2f * Kit.h(k, 51)
            val z = -(r * 1.3f + 0.2f) - (LEN - r * 2.6f - 0.4f) * Kit.h(k, 52)
            val edge = abs(x) > 4.4f
            if (!edge && Kit.h(k, 53) > 0.4f) continue // fewer in the middle of the road
            val c = if (Kit.h(k, 56) > 0.4f) Col.mix(SAND, TILES[0], 0.45f) else Col.mix(SILT, TILES[0], 0.4f)
            Kit.mound(b, x, z, r * (1.1f + 0.7f * Kit.h(k, 58)), r * (0.5f + 0.4f * Kit.h(k, 54)), top, 0.008f + 0.008f * Kit.h(k, 55), c, k, 8)
        }
        for (side in intArrayOf(-1, 1)) for (k in 0 until 5) {
            val rz = 1.1f + 0.8f * Kit.h(k, 68 + side)
            val edge = rz * 1.35f + 0.3f
            val z = -edge - (LEN - 2f * edge) * (k + 0.5f * Kit.h(k, 60 + side)) / 5f
            val x = side * (8.2f + 0.6f * Kit.h(k, 62 + side))
            val ry = 0.11f + 0.1f * Kit.h(k, 66 + side)
            Kit.lump(b, x, ry * 0.9f, z, 0.7f + 0.3f * Kit.h(k, 64 + side), ry, rz, Col.mul(Kit.tone(SAND, k + side * 7, 0.06f), 0.85f), k + 3 * side, 0.18f)
        }
    }

    /** Concrete kerb blocks along both edges, furred with algae. */
    private fun kerbs(b: MeshBuilder) {
        for (side in intArrayOf(-1, 1)) for (k in 0 until 8) {
            val c = if (k % 2 == 0) KERB_A else KERB_B
            b.push().translate(side * 6.35f, 0.06f, -(k * 3f + 1.5f)).box(0.55f, 0.12f, 2.9f, Kit.tone(c, k + side * 11, 0.08f), Col.mul(c, 0.9f), Col.mul(c, 0.7f), Col.mul(c, 0.6f)).pop()
        }
    }

    /** One tuft of coral and barnacles: a few little cones. */
    private fun crust(b: MeshBuilder, x: Float, y: Float, z: Float, seed: Int, scale: Float) {
        val n = 2 + (Kit.h(seed, 1) * 2.2f).toInt()
        for (i in 0 until n) {
            val c = CORAL[(Kit.h(seed, 2 + i) * CORAL.size).toInt().coerceIn(0, CORAL.size - 1)]
            val ox = (Kit.h(seed, 10 + i) - 0.5f) * 0.18f * scale; val oz = (Kit.h(seed, 20 + i) - 0.5f) * 0.22f * scale
            val hh = (0.1f + 0.16f * Kit.h(seed, 30 + i)) * scale
            b.push().translate(x + ox, y + hh * 0.5f, z + oz).cone(0.05f * scale + 0.03f * Kit.h(seed, 40 + i), hh, 5, Col.mul(c, 1.15f), Col.mul(c, 0.8f), smooth = false, cap = false).pop()
        }
    }

    /** Guard rails every 3 m, teal-green and crusted with coral and barnacles. */
    private fun rails(b: MeshBuilder) {
        for (side in intArrayOf(-1, 1)) {
            val x = side * 7.2f
            for (k in 0 until 8) {
                val z = -(k * 3f + 1.5f)
                b.push().translate(x, 0.4f, z).box(0.16f, 0.8f, 0.16f, POST, Col.mul(POST, 0.9f), Col.mul(POST, 0.7f), POST).pop()
                crust(b, x, 0.02f, z, k * 3 + side + 100, 1.1f)
                crust(b, x - side * 0.02f, 0.8f, z, k * 5 + side + 200, 0.9f)
            }
            b.push().translate(x - side * 0.06f, 0.62f, -LEN / 2).box(0.1f, 0.3f, LEN, RAIL, RAIL, Col.mul(RAIL, 0.75f), Col.mul(RAIL, 0.75f)).pop()
            for (k in 0 until 10) {
                val z = -(k * 2.3f + 0.8f + 0.5f * Kit.h(k, 70 + side))
                crust(b, x - side * 0.1f, 0.74f, z, k + side * 31 + 300, 0.8f)
                if (k % 3 == 0) {
                    b.push().translate(x - side * 0.1f, 0.5f + 0.08f * Kit.h(k, 80), z).icosphere(0.06f + 0.04f * Kit.h(k, 81), 0, rgb(0xDCD7C0)).pop()
                }
            }
        }
    }

    /** A lamp post: pole, arm and a housing, with a ring of coral at the foot. [globe] draws the glowing head instead. */
    private fun lamp(b: MeshBuilder, x: Float, z: Float, globe: Boolean) {
        val dir = if (x < 0f) 1f else -1f
        if (!globe) {
            b.push().translate(x, 3.4f, z).cylinder(0.12f, 0.18f, 6.8f, 7, METAL, Col.mul(METAL, 0.8f), smooth = false).pop()
            b.push().translate(x + dir * 0.85f, 6.78f, z).box(1.8f, 0.14f, 0.14f, METAL).pop()
            b.push().translate(x + dir * 1.72f, 6.5f, z).cylinder(0.22f, 0.3f, 0.22f, 8, Col.mul(METAL, 1.3f), METAL, smooth = false).pop()
            b.push().translate(x + dir * 1.72f, 7.1f, z).cone(0.3f, 0.3f, 8, METAL, Col.mul(METAL, 1.4f), smooth = false).pop()
            for (k in 0 until 4) crust(b, x + (Kit.h(k, 90) - 0.5f) * 0.2f, 0.1f + k * 0.9f, z + (Kit.h(k, 91) - 0.5f) * 0.2f, 400 + k + (if (x < 0f) 0 else 10), 1.6f)
        }
    }

    /** The glowing parts: lane dashes, centre and edge lines, lamp globes and the studs on the rail posts, just above the surface. */
    fun glow(b: MeshBuilder) {
        val y = 0.0625f // strips 0.05 .. 0.075: the road's top is 0.03, its drifts 0.052
        val cyan = rgb(0x62F2FF)
        val white = rgb(0xE6FFFF)
        val gold = rgb(0xFFD25A)
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 5.85f, y, -LEN / 2).box(0.18f, 0.025f, LEN, cyan).pop()
        }
        for (off in floatArrayOf(-0.14f, 0.14f)) b.push().translate(off, y, -LEN / 2).box(0.1f, 0.025f, LEN, gold).pop()
        for (x in floatArrayOf(-3f, 3f)) for (k in 0 until 2) b.push().translate(x, y, -(k * 12f + 3f)).box(0.16f, 0.025f, 6f, white).pop()
        // studs on the rail posts: cyan on the left, gold on the right, so that the edges can be told apart in the dark
        for (side in intArrayOf(-1, 1)) for (k in 0 until 8) {
            b.push().translate(side * 7.2f, 0.86f, -(k * 3f + 1.5f)).box(0.14f, 0.07f, 0.14f, if (side < 0) cyan else gold).pop()
        }
        // lamp globes
        b.push().translate(-8.2f + 1.72f, 6.82f, -12f).icosphere(0.4f, 1, cyan).pop()
        b.push().translate(8.2f - 1.72f, 6.82f, -LEN + 0.5f).icosphere(0.4f, 1, gold).pop()
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Scenery of the seabed: the vent, the wrecks, the ruins and the gate of Atlantis
// ---------------------------------------------------------------------------------------------------------------------

private object SeabedScenery {
    private val MARBLE = rgb(0xE2DFCF)
    private val MARBLE_DARK = rgb(0xB4B09E)
    private val ALGAE = rgb(0x4F9060)
    private val GOLD = rgb(0xEABD4C)
    private val GLYPH = rgb(0x86FFF2)
    private val ROCK = rgb(0x363640)
    private val RUST = rgb(0x93502D)
    private val SAND = rgb(0xC6B78C)

    private fun lerp(a: FloatArray, zs: FloatArray, z: Float): Float {
        for (i in 0 until zs.size - 1) if (z <= zs[i + 1]) {
            val t = ((z - zs[i]) / (zs[i + 1] - zs[i])).coerceIn(0f, 1f)
            return a[i] + (a[i + 1] - a[i]) * t
        }
        return a[a.size - 1]
    }

    /** A hydrothermal black smoker, about 8 m high with its smoke: rough dark rings, a glowing rim, a pale bacterial mat and tube worms at the foot. */
    fun vent(b: MeshBuilder) {
        val matA = rgb(0xF0E8D4)
        val matB = rgb(0xF5D6C2)
        for (k in 0 until 9) {
            val a = k * 0.7f + Kit.h(k, 1) * 0.4f
            val r = 2.4f + 1.8f * Kit.h(k, 2)
            Kit.mound(b, cos(a) * r, sin(a) * r, 1.3f + 0.9f * Kit.h(k, 3), 1.1f + 0.8f * Kit.h(k, 4), 0.02f, 0.12f, if (k % 3 == 0) matB else matA, k, 8)
        }
        val top = 6.2f
        fun radius(y: Float): Float { val t = (1f - y / top).coerceIn(0f, 1f); return 0.55f + 2.3f * t * sqrt(t) }
        var y = 0f
        for (i in 0 until 7) {
            val h = if (i == 0) 1.4f else (top - 1.4f) / 6f
            val y1 = y + h
            val c = Kit.tone(ROCK, i * 3, 0.25f)
            b.push().translate((Kit.h(i, 5) - 0.5f) * 0.2f, y + h / 2f, (Kit.h(i, 6) - 0.5f) * 0.2f).rotateY(i * 23f)
                .cylinder(radius(y1), radius(y), h, 8, Col.mul(c, 1.3f), Col.mul(c, 0.8f), smooth = false, capTop = false, capBottom = false).pop()
            if (i % 2 == 1) {
                b.push().translate(0f, y1, 0f).cylinder(radius(y1) + 0.16f, radius(y1) + 0.12f, 0.22f, 8, if (i == 3) rgb(0xD6B24A) else RUST, Col.mul(RUST, 0.7f), smooth = false, capTop = false, capBottom = false).pop()
            }
            y = y1
        }
        for (k in 0 until 7) {
            val yy = 0.8f + 4.6f * Kit.h(k, 20)
            val a = k * 2.4f
            val rr = radius(yy)
            Kit.lump(b, cos(a) * rr, yy, sin(a) * rr, 0.4f + 0.3f * Kit.h(k, 21), 0.35f, 0.4f + 0.3f * Kit.h(k, 22), Kit.tone(if (k % 3 == 0) RUST else ROCK, k, 0.3f), k, 0.3f, 0)
        }
        // the glowing mouth, and the black smoke
        b.push().translate(0f, top, 0f).rotateX(90f).torus(0.62f, 0.17f, 8, 5, rgb(0xFF5A1E), rgb(0xFFB347)).pop()
        b.push().translate(0f, top - 0.02f, 0f).cylinder(0.58f, 0.58f, 0.05f, 8, rgb(0xFFD060), rgb(0xFF8A2A), smooth = false).pop()
        Kit.lump(b, 0.1f, 6.9f, 0f, 0.55f, 0.5f, 0.55f, rgb(0x2A2A33), 11, 0.3f, 0)
        Kit.lump(b, 0.25f, 7.35f, 0.1f, 0.5f, 0.45f, 0.5f, rgb(0x30303A), 12, 0.3f, 0)
        Kit.lump(b, -0.1f, 7.7f, 0.1f, 0.32f, 0.28f, 0.32f, rgb(0x383844), 13, 0.3f, 0)
        for (k in 0 until 8) {
            val a = k * 0.8f + 0.3f
            val r = 1.9f + 0.9f * Kit.h(k, 30)
            val hh = 0.6f + 0.5f * Kit.h(k, 31)
            Kit.strut(b, cos(a) * r, 0.1f, sin(a) * r, cos(a) * r + 0.12f, 0.1f + hh, sin(a) * r + 0.1f, 0.06f, 0.045f, rgb(0xF3EEE2), rgb(0xE23A2C), 4)
        }
    }

    /** A cargo ship lying on its port side, 32 m long: torn open on the upper side so that the ribs show, a stumpy bridge, broken masts, rust and sand. */
    fun wreckCargo(b: MeshBuilder) {
        val zs = floatArrayOf(-16f, -14f, -11f, -6f, 0f, 6f, 11f, 14.5f, 16f)
        val ws = floatArrayOf(0.3f, 2.2f, 3.7f, 4.4f, 4.5f, 4.5f, 4.3f, 3.5f, 2.8f)
        val yk = floatArrayOf(3.4f, 1.8f, 0.5f, 0f, 0f, 0f, 0.1f, 0.6f, 1.0f)
        val dk = floatArrayOf(7.4f, 6.9f, 6.6f, 6.5f, 6.5f, 6.5f, 6.6f, 6.8f, 7.0f)
        val rings = zs.indices.map { i ->
            val w = ws[i]; val k = yk[i]; val d = dk[i] - k
            floatArrayOf(-w, dk[i], -w, k + 0.4f * d, -0.88f * w, k + 0.12f * d, -0.55f * w, k, 0f, k, 0.55f * w, k, 0.88f * w, k + 0.12f * d, w, k + 0.4f * d, w, dk[i])
        }
        val steel = rgb(0xA19D90)
        Kit.grounded(b) { s ->
            s.rotateX(6f).rotateZ(78f)
            Kit.loft(s, rings, zs, false,
                { i, j -> Kit.tone(if (j in 2..5) rgb(0x8C3328) else if (j == 0 || j == 7) rgb(0x2F2C30) else rgb(0x7B4634), i * 9 + j, 0.2f) },
                rgb(0x2B1D19),
                { i, j -> (i in 3..4 && (j == 6 || j == 7)) || (i == 3 && j == 5) })
            for (i in 0 until zs.size - 1) {
                s.quad(-ws[i], dk[i], zs[i], -ws[i + 1], dk[i + 1], zs[i + 1], ws[i + 1], dk[i + 1], zs[i + 1], ws[i], dk[i], zs[i], Kit.tone(rgb(0x6B5A4C), i, 0.15f))
                s.quad(-ws[i], dk[i], zs[i], ws[i], dk[i], zs[i], ws[i + 1], dk[i + 1], zs[i + 1], -ws[i + 1], dk[i + 1], zs[i + 1], rgb(0x2B1D19))
            }
            // the ribs the torn plating shows, some broken off and bent
            for (k in 0 until 6) {
                val z = -6f + 2.3f * k
                val w = lerp(ws, zs, z); val kk = lerp(yk, zs, z); val d = lerp(dk, zs, z) - kk
                val px = floatArrayOf(0.55f * w, 0.88f * w, w, w)
                val py = floatArrayOf(kk, kk + 0.12f * d, kk + 0.4f * d, kk + d)
                val keep = if (Kit.h(k, 5) > 0.8f) 1 else if (Kit.h(k, 5) > 0.5f) 2 else 3
                for (m in 0 until keep) {
                    val bend = if (m == keep - 1 && keep < 3) 0.7f * Kit.h(k, 9) else 0f
                    Kit.strut(s, px[m], py[m], z, px[m + 1] + bend, py[m + 1] + bend * 0.5f, z, 0.17f, 0.15f, RUST, RUST, 4, cap0 = false, cap1 = false)
                }
            }
            // the stern: a stumpy bridge and a funnel, hatches and broken masts
            s.push().translate(0f, 6.6f + 1.7f, 10.3f).box(5.6f, 3.4f, 7.6f, steel, Col.mul(steel, 0.9f), Col.mul(steel, 0.7f), Col.mul(steel, 0.5f)).pop()
            s.push().translate(0f, 6.6f + 4.6f, 9.8f).box(4.8f, 2.6f, 5.2f, Col.mul(steel, 1.05f), Col.mul(steel, 0.9f), Col.mul(steel, 0.75f), Col.mul(steel, 0.5f)).pop()
            s.push().translate(0f, 6.6f + 6.8f, 9.2f).box(5.4f, 1.6f, 3.4f, steel, Col.mul(steel, 0.9f), Col.mul(steel, 0.7f), Col.mul(steel, 0.5f)).pop()
            s.push().translate(0f, 6.6f + 6.9f, 9.2f).box(5.5f, 0.6f, 3.5f, rgb(0x1B2530)).pop()
            s.push().translate(0f, 6.6f + 6.8f, 12.8f).rotateX(-8f).cylinder(0.9f, 1.1f, 4.4f, 8, rgb(0x2B2B30), rgb(0x9A3224), smooth = false).pop()
            for (zz in floatArrayOf(-9f, -3f)) s.push().translate(0f, 6.9f, zz).box(4.6f, 0.8f, 4.2f, rgb(0x5E5850)).pop()
            Kit.strut(s, 0f, 6.9f, -10.5f, 0.3f, 12.5f, -10.5f, 0.18f, 0.12f, steel, steel, 6, cap0 = false)
            Kit.strut(s, 0.3f, 12.5f, -10.5f, 2.6f, 15.2f, -10.5f, 0.12f, 0.08f, steel, RUST, 6)
            Kit.strut(s, 0f, 14.6f, 9.2f, 0.4f, 17.4f, 9.2f, 0.16f, 0.1f, steel, RUST, 6)
            for (k in 0 until 7) {
                val z = -12f + 4.2f * k
                val x = if (k % 2 == 0) lerp(ws, zs, z) else lerp(ws, zs, z) * 0.7f
                val yy = lerp(dk, zs, z) - (if (k % 2 == 0) 0.3f else 2.5f)
                Kit.lump(s, x, yy, z, 0.5f, 0.4f, 0.6f, if (k % 3 == 0) ALGAE else rgb(0xD8D2BC), k + 20, 0.3f, 0)
            }
        }
        for (k in 0 until 7) Kit.mound(b, -8f + 2.4f * Kit.h(k, 40), -15f + 5f * k, 2.6f, 3.4f, 0.02f, 1.2f, Kit.tone(SAND, k, 0.06f), k + 60, 8)
    }

    /** A sunken submarine, 28 m long, nose up: a torn hull showing its ribs, the conning tower broken off at a jagged stump, planes, a rudder and a bent propeller. */
    fun wreckSub(b: MeshBuilder) {
        val zs = floatArrayOf(-14f, -12.8f, -10.5f, -7f, -3f, 2f, 7f, 11f, 13.2f, 14.5f)
        val rs = floatArrayOf(0.4f, 1.5f, 2.4f, 3.0f, 3.2f, 3.2f, 3.0f, 2.4f, 1.4f, 0.4f)
        val n = 10
        val rings = rs.map { r -> FloatArray(n * 2) { idx -> val a = (2.0 * PI * (idx / 2) / n).toFloat(); if (idx % 2 == 0) r * cos(a) else r * sin(a) } }
        val steel = rgb(0x6A7472)
        Kit.grounded(b) { s ->
            s.rotateX(15f).rotateZ(34f)
            Kit.loft(s, rings, zs, true,
                { i, j -> Kit.tone(if (j in 5..9) rgb(0x3B6355) else rgb(0x5C6866), i * 10 + j, 0.18f) },
                rgb(0x26201E),
                { i, j -> i in 5..6 && j in 0..2 })
            for (k in 0 until 4) {
                val z = 3f + 2.3f * k
                val r = lerp(rs, zs, z) * 0.97f
                for (m in 0 until 3) {
                    val a0 = (m * 36f + 4f) * 0.017453292f; val a1 = ((m + 1) * 36f + 4f) * 0.017453292f
                    Kit.strut(s, r * cos(a0), r * sin(a0), z, r * cos(a1), r * sin(a1), z, 0.16f, 0.14f, RUST, RUST, 4, cap0 = false, cap1 = false)
                }
            }
            // the conning tower, broken
            s.push().translate(0f, 4.7f, -3.5f).scale(0.9f, 1f, 2.5f).cylinder(0.95f, 1.05f, 3.4f, 8, steel, Col.mul(steel, 0.7f), smooth = false, capTop = false).pop()
            for (k in 0 until 5) {
                val a = k * 1.3f
                val hh = 0.6f + 1.2f * Kit.h(k, 70)
                s.push().translate(0.8f * cos(a), 6.4f + hh / 2f, -3.5f + 2.2f * sin(a)).rotateZ((Kit.h(k, 71) - 0.5f) * 50f).cone(0.3f, hh, 4, steel, Col.mul(steel, 0.6f), smooth = false, cap = false).pop()
            }
            Kit.strut(s, 0.2f, 6.2f, -3f, 2.2f, 7.4f, -1.8f, 0.12f, 0.09f, steel, RUST, 5)
            // planes, rudder and propeller
            for (sx in intArrayOf(-1, 1)) {
                s.push().translate(sx * 4.2f, 0.4f, -9f).rotateZ(sx * 8f).box(3.4f, 0.14f, 1.3f, steel).pop()
                s.push().translate(sx * 3.4f, 0f, 12.6f).box(3.2f, 0.14f, 1.8f, steel).pop()
            }
            s.push().translate(0f, 0f, 13.4f).box(0.14f, 6.4f, 1.6f, steel).pop()
            s.push().translate(0f, 0f, 14.7f).rotateX(90f).cylinder(0.3f, 0.3f, 0.8f, 6, rgb(0xB08A4A), rgb(0xB08A4A), smooth = false).pop()
            for (k in 0 until 4) s.push().translate(0f, 0f, 15.0f).rotateZ(k * 72f + 10f).translate(0.9f, 0f, 0f).box(1.6f, 0.45f, 0.1f, rgb(0xB08A4A)).pop()
            for (k in 0 until 6) Kit.lump(s, 3.1f * cos(k * 1.1f), 3.1f * sin(k * 1.1f), -11f + 4.4f * k, 0.5f, 0.4f, 0.6f, if (k % 2 == 0) ALGAE else rgb(0xD8D2BC), k + 80, 0.3f, 0)
        }
        for (k in 0 until 6) Kit.mound(b, -3f + 6f * Kit.h(k, 41), -10f + 4.4f * k, 2.6f, 3.4f, 0.02f, 1.2f, Kit.tone(SAND, k, 0.06f), k + 90, 8)
    }

    private fun column(b: MeshBuilder, x: Float, y0: Float, z: Float, h: Float, r: Float, broken: Boolean) {
        Kit.fluted(b, x, y0, z, h, r, r * 0.84f, 6, MARBLE, MARBLE_DARK, capTop = !broken)
        b.push().translate(x, y0 + 0.17f, z).cylinder(r * 1.22f, r * 1.28f, 0.34f, 8, MARBLE, MARBLE_DARK, smooth = false).pop()
        if (broken) {
            for (k in 0 until 4) {
                val a = k * 1.57f + 0.4f
                val hh = 0.8f + 0.5f * k
                b.push().translate(x + cos(a) * r * 0.45f, y0 + h + hh / 2f, z + sin(a) * r * 0.45f).cone(r * 0.4f, hh, 4, MARBLE, MARBLE_DARK, smooth = false, cap = false).pop()
            }
        } else {
            val top = y0 + h
            b.push().translate(x, top + 0.14f, z).cylinder(r * 1.0f, r * 0.84f, 0.28f, 8, GOLD, Col.mul(GOLD, 0.8f), smooth = false).pop()
            b.push().translate(x, top + 0.58f, z).cylinder(r * 1.3f, r * 0.9f, 0.6f, 8, MARBLE, MARBLE_DARK, smooth = false).pop()
            b.push().translate(x, top + 1.0f, z).box(r * 2.9f, 0.3f, r * 2.9f, MARBLE).pop()
        }
    }

    private fun steps(b: MeshBuilder, w: Float, d: Float) {
        val step = Col.mix(MARBLE_DARK, ALGAE, 0.35f)
        b.push().translate(0f, 0.3f, 0f).box(w, 0.6f, d, step, step, Col.mul(step, 0.7f), Col.mul(step, 0.6f)).pop()
        b.push().translate(0f, 0.9f, 0f).box(w - 2f, 0.6f, d - 1.4f, Col.mix(MARBLE, ALGAE, 0.2f), MARBLE_DARK, MARBLE_DARK, MARBLE_DARK).pop()
    }

    /** A row of four fluted columns on a stepped platform: the third one broken, an architrave over two of them, fallen drums. */
    private fun ruins0(b: MeshBuilder) {
        steps(b, 24f, 8.4f)
        val xs = floatArrayOf(-7.5f, -2.5f, 2.5f, 7.5f)
        for ((i, x) in xs.withIndex()) column(b, x, 1.2f, 0f, if (i == 2) 5.6f else 8.8f, 0.95f, i == 2)
        b.push().translate(-5f, 11.7f, 0f).box(6.2f, 1.1f, 2.0f, MARBLE, MARBLE_DARK, MARBLE_DARK, MARBLE_DARK).pop()
        b.push().translate(-5f, 12.4f, 0f).box(6.4f, 0.3f, 2.2f, GOLD).pop()
        b.push().translate(8.3f, 11.7f, 0f).box(2.2f, 1.1f, 2.0f, MARBLE, MARBLE_DARK, MARBLE_DARK, MARBLE_DARK).pop()
        b.push().translate(4.2f, 2.0f, 3.2f).rotateY(25f).rotateZ(90f).cylinder(0.8f, 0.82f, 2.2f, 8, MARBLE, MARBLE_DARK, smooth = false).pop()
        b.push().translate(-9.5f, 0.8f, 5.6f).rotateY(-40f).rotateZ(90f).cylinder(0.8f, 0.8f, 2.6f, 8, MARBLE, ALGAE, smooth = false).pop()
        b.push().translate(0.5f, 1.8f, 3.4f).rotateY(15f).box(2.4f, 1.2f, 1.2f, MARBLE_DARK).pop()
        b.push().translate(-12.8f, 0.8f, -2.5f).rotateY(30f).box(1.8f, 1.0f, 1.4f, Col.mix(MARBLE, ALGAE, 0.3f)).pop()
    }

    /** An arch: two fluted columns, a semicircular arch and a lintel with a glowing glyph on both faces. */
    private fun ruins1(b: MeshBuilder) {
        steps(b, 13f, 5f)
        for (sx in intArrayOf(-1, 1)) column(b, sx * 3.1f, 0.6f, 0f, 6.4f, 1.1f, false)
        b.push().translate(0f, 8.15f, 0f)
        Kit.ringSector(b, 2.2f, 3.1f, -1.1f, 1.1f, 0f, 180f, 8, MARBLE, MARBLE_DARK, MARBLE)
        b.pop()
        b.push().translate(0f, 12.25f, 0f).box(10.6f, 2.0f, 2.6f, MARBLE, MARBLE, MARBLE_DARK, MARBLE_DARK).pop()
        b.push().translate(0f, 13.4f, 0f).box(10.8f, 0.3f, 2.8f, GOLD).pop()
        for (sz in intArrayOf(-1, 1)) {
            b.push().translate(0f, 12.25f, sz * 1.32f).torus(0.75f, 0.12f, 8, 4, GLYPH, GLYPH).pop()
            b.push().translate(0f, 12.25f, sz * 1.32f).box(1.9f, 0.14f, 0.08f, GLYPH).pop()
            b.push().translate(0f, 12.25f, sz * 1.32f).box(0.14f, 1.5f, 0.08f, GLYPH).pop()
            for (sx in intArrayOf(-1, 1)) b.push().translate(sx * 3.2f, 12.25f, sz * 1.32f).box(0.9f, 0.9f, 0.08f, GLYPH).pop()
        }
        b.push().translate(5.2f, 1.2f, 4f).rotateY(30f).rotateZ(90f).cylinder(0.8f, 0.8f, 2.4f, 8, MARBLE, ALGAE, smooth = false).pop()
    }

    /** A pedestal with a broken statue of a sea god: no head, one arm gone, a trident with a broken prong. */
    private fun ruins2(b: MeshBuilder) {
        steps(b, 9f, 9f)
        b.push().translate(0f, 3.0f, 0f).box(3.8f, 3.6f, 3.8f, MARBLE, MARBLE, MARBLE_DARK, MARBLE_DARK).pop()
        b.push().translate(0f, 1.55f, 0f).box(4.0f, 0.7f, 4.0f, ALGAE).pop()
        b.push().translate(0f, 4.95f, 0f).box(4.4f, 0.3f, 4.4f, MARBLE).pop()
        b.push().translate(0f, 3.1f, 1.92f).box(2.2f, 1.4f, 0.06f, GOLD).pop()
        b.push().translate(0f, 6.8f, 0f).cylinder(0.75f, 1.2f, 3.4f, 10, MARBLE, MARBLE_DARK, smooth = true).pop()
        b.push().translate(0f, 8.65f, 0f).cylinder(0.85f, 0.85f, 0.3f, 10, GOLD, GOLD, smooth = false).pop()
        b.push().translate(0f, 9.65f, 0f).box(1.7f, 2.0f, 1.0f, MARBLE, MARBLE, MARBLE_DARK, MARBLE_DARK).pop()
        b.push().translate(0f, 10.85f, 0f).cylinder(0.3f, 0.4f, 0.5f, 6, MARBLE_DARK, MARBLE_DARK, smooth = false).pop()
        Kit.strut(b, 0.95f, 10.2f, 0f, 1.5f, 11.2f, 0.2f, 0.28f, 0.22f, MARBLE, MARBLE_DARK, 6)
        b.push().translate(1.5f, 11.2f, 0.2f).icosphere(0.28f, 1, MARBLE).pop()
        Kit.strut(b, -0.95f, 10.2f, 0f, -1.3f, 9.6f, 0.1f, 0.28f, 0.2f, MARBLE, MARBLE_DARK, 6)
        Kit.strut(b, 3.0f, 1.45f, 3.6f, 4.8f, 1.45f, 3.0f, 0.24f, 0.2f, MARBLE, MARBLE_DARK, 6)
        Kit.lump(b, -3.0f, 1.2f, 3.4f, 0.55f, 0.65f, 0.55f, MARBLE, 5, 0.1f, 1)
        // the trident
        Kit.strut(b, 1.6f, 8.5f, 0.2f, 1.6f, 12.2f, 0.2f, 0.09f, 0.09f, GOLD, GOLD, 6)
        b.push().translate(1.6f, 12.2f, 0.2f).box(1.4f, 0.14f, 0.14f, GOLD).pop()
        b.push().translate(1.6f, 12.8f, 0.2f).cone(0.12f, 1.2f, 5, GOLD, Col.mul(GOLD, 0.8f), smooth = false, cap = false).pop()
        b.push().translate(1.0f, 12.65f, 0.2f).cone(0.1f, 0.9f, 5, GOLD, Col.mul(GOLD, 0.8f), smooth = false, cap = false).pop()
        b.push().translate(2.2f, 12.45f, 0.2f).rotateZ(-35f).cone(0.1f, 0.4f, 5, GOLD, Col.mul(GOLD, 0.8f), smooth = false, cap = false).pop()
    }

    fun ruins(b: MeshBuilder, v: Int) = when (v) { 0 -> ruins0(b); 1 -> ruins1(b); else -> ruins2(b) }

    /** The gate of Atlantis: two fluted columns, a lintel with a golden crest and a glowing cyan ring hung under it. 29 m wide, 25 m high. */
    fun gate(b: MeshBuilder) {
        val stone = rgb(0xD9EFEC)
        val dark = rgb(0x9FC2C4)
        val cyan = rgb(0x6FFFF0)
        for (sx in intArrayOf(-1, 1)) {
            val x = sx * 12.9f
            b.push().translate(x, 0.5f, 0f).cylinder(1.6f, 1.6f, 1.0f, 12, stone, dark, smooth = false).pop()
            Kit.fluted(b, x, 1.0f, 0f, 18.4f, 1.45f, 1.25f, 8, stone, dark, capTop = false)
            b.push().translate(x, 19.7f, 0f).cylinder(1.3f, 1.3f, 0.6f, 12, GOLD, GOLD, smooth = false).pop()
            b.push().translate(x, 20.6f, 0f).cylinder(1.6f, 1.3f, 1.2f, 12, stone, dark, smooth = false).pop()
            b.push().translate(x, 21.5f, 0f).box(3.2f, 0.6f, 3.2f, stone, stone, dark, dark).pop()
        }
        b.push().translate(0f, 22.9f, 0f).box(29f, 2.2f, 3.6f, stone, stone, dark, dark).pop()
        b.push().translate(0f, 24.15f, 0f).box(29f, 0.3f, 4.6f, GOLD).pop()
        for (sz in intArrayOf(-1, 1)) b.push().translate(0f, 22.9f, sz * 1.83f).box(26f, 0.5f, 0.06f, cyan).pop()
        for (k in -4..4) {
            val hh = 0.45f + 0.25f * (1f - abs(k) / 4f)
            b.push().translate(k * 3.1f, 24.3f + hh / 2f, 0f).cone(0.55f, hh, 6, GOLD, Col.mul(GOLD, 0.8f), smooth = false, cap = false).pop()
        }
        b.push().translate(0f, 16.8f, 0f).torus(4.8f, 0.45f, 20, 6, cyan, rgb(0xE0FFFF)).pop()
        b.push().translate(0f, 16.8f, 0f).torus(3.0f, 0.2f, 16, 4, GOLD, GOLD).pop()
        b.push().translate(0f, 21.0f, 0f).cylinder(0.4f, 0.4f, 1.6f, 6, GOLD, GOLD, smooth = false).pop()
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Atlantis, the lost city: a great glass dome with a golden spire, six smaller domes joined to it by arched viaducts,
// towers, terraces, a ring wall with an opening toward +Z for the road. The origin is the middle of the city on y = 0.
// ---------------------------------------------------------------------------------------------------------------------

private object Atlantis {
    /** The wall's outer radius, and half the width of its opening at +Z (the road comes in along x = 0). */
    const val WALL_R = 165f
    const val OPEN_HALF = 18f
    private const val TOP = 7.4f
    private const val DOME_R = 55f
    private const val DOME_BASE = 11.4f
    private const val SAT_DIST = 125f
    private const val SAT_R = 20f
    private val STONE = rgb(0xD3ECE8)
    private val STONE_SHADE = rgb(0xA5CBC8)
    private val GOLD = rgb(0xEABD4C)
    private val GLASS_TOP = rgb(0xA6F1E8)
    private val GLASS_BOT = rgb(0x3FA3A6)
    private val TEAL_ROOF = rgb(0x39B3AE)
    private val OPEN_DEG = Math.toDegrees(kotlin.math.asin((OPEN_HALF / WALL_R).toDouble())).toFloat()

    private class Tower(val x: Float, val z: Float, val r: Float, val h: Float, val roofH: Float, val gold: Boolean)
    private class House(val x: Float, val z: Float, val w: Float, val d: Float, val h: Float, val dome: Boolean, val gold: Boolean)

    private val TOWERS: List<Tower> = run {
        val list = ArrayList<Tower>()
        val tr = WALL_R - 3f
        for (side in intArrayOf(-1, 1)) {
            val a = Kit.rad(90f + side * (OPEN_DEG + 3.4f))
            list.add(Tower(tr * cos(a), tr * sin(a), 8f, 54f, 20f, true))
        }
        val span = 360f - 2f * OPEN_DEG
        val n = 22
        for (k in 0 until n) {
            val a = Kit.rad(90f + OPEN_DEG + (k + 1) * span / (n + 1))
            list.add(Tower(tr * cos(a), tr * sin(a), 4.2f + 0.9f * Kit.h(k, 301), 26f + 16f * Kit.h(k, 302), 10f + 5f * Kit.h(k, 303), k % 3 == 0))
        }
        list
    }

    private val HOUSES: List<House> = run {
        val list = ArrayList<House>()
        for (k in 0 until 190) {
            if (list.size >= 52) break
            val a = (2.0 * PI * Kit.h(k, 401)).toFloat()
            val r = 98f + 54f * Kit.h(k, 402)
            val x = r * cos(a); val z = r * sin(a)
            if (abs(x) < 20f && z > 40f) continue // the avenue
            var ok = true
            for (s in 0 until 6) {
                val sa = Kit.rad(s * 60f)
                val dx = x - SAT_DIST * cos(sa); val dz = z - SAT_DIST * sin(sa)
                if (sqrt(dx * dx + dz * dz) < 38f) ok = false
            }
            val deg = Math.toDegrees(kotlin.math.atan2(z.toDouble(), x.toDouble())).toFloat()
            val off = ((deg % 60f) + 60f) % 60f
            if (r < 104f && (off < 7f || off > 53f)) ok = false // keep the viaducts clear
            for (o in list) if (abs(o.x - x) < (o.w + 9f) * 0.5f && abs(o.z - z) < (o.d + 9f) * 0.5f) ok = false
            if (!ok) continue
            list.add(House(x, z, 5f + 6f * Kit.h(k, 403), 5f + 6f * Kit.h(k, 404), 8f + 14f * Kit.h(k, 405) * Kit.h(k, 406), Kit.h(k, 407) > 0.7f, Kit.h(k, 408) > 0.5f))
        }
        list
    }

    /** A wall around the Y axis from angle a0 to a1 (degrees, 0 = +X, 90 = +Z), radii rIn..rOut, height y0..y1; the ends are closed. */
    private fun wallRing(b: MeshBuilder, rIn: Float, rOut: Float, y0: Float, y1: Float, a0: Float, a1: Float, steps: Int, cOut: Int, cIn: Int, cTop: Int) {
        val st = (a1 - a0) / steps
        for (i in 0 until steps) {
            val t0 = Kit.rad(a0 + i * st); val t1 = Kit.rad(a0 + (i + 1) * st)
            val c0 = cos(t0); val s0 = sin(t0); val c1 = cos(t1); val s1 = sin(t1)
            val shade = 0.92f + 0.16f * Kit.h(i, 77)
            b.quad(rOut * c0, y0, rOut * s0, rOut * c0, y1, rOut * s0, rOut * c1, y1, rOut * s1, rOut * c1, y0, rOut * s1, Col.mul(cOut, shade))
            b.quad(rIn * c0, y0, rIn * s0, rIn * c1, y0, rIn * s1, rIn * c1, y1, rIn * s1, rIn * c0, y1, rIn * s0, Col.mul(cIn, shade))
            b.quad(rIn * c0, y1, rIn * s0, rIn * c1, y1, rIn * s1, rOut * c1, y1, rOut * s1, rOut * c0, y1, rOut * s0, cTop)
        }
        for (e in 0..1) {
            val t = Kit.rad(if (e == 0) a0 else a1)
            val c = cos(t); val s = sin(t)
            if (e == 0) b.quad(rIn * c, y0, rIn * s, rIn * c, y1, rIn * s, rOut * c, y1, rOut * s, rOut * c, y0, rOut * s, cIn)
            else b.quad(rIn * c, y0, rIn * s, rOut * c, y0, rOut * s, rOut * c, y1, rOut * s, rIn * c, y1, rIn * s, cIn)
        }
    }

    /** A thin glowing band around the Y axis at radius r, facing outward or inward. */
    private fun bandRing(b: MeshBuilder, r: Float, y0: Float, y1: Float, a0: Float, a1: Float, steps: Int, outward: Boolean, c: Int) {
        val st = (a1 - a0) / steps
        for (i in 0 until steps) {
            val t0 = Kit.rad(a0 + i * st); val t1 = Kit.rad(a0 + (i + 1) * st)
            val ax = r * cos(t0); val az = r * sin(t0); val bx = r * cos(t1); val bz = r * sin(t1)
            if (outward) b.quad(ax, y0, az, ax, y1, az, bx, y1, bz, bx, y0, bz, c)
            else b.quad(ax, y0, az, bx, y0, bz, bx, y1, bz, ax, y1, az, c)
        }
    }

    /** A vertical window quad at (x, y, z) facing the horizontal direction (nx, nz). */
    private fun window(b: MeshBuilder, x: Float, y: Float, z: Float, nx: Float, nz: Float, w: Float, h: Float, c: Int) {
        val tx = nz * w / 2f; val tz = -nx * w / 2f
        b.quad(x - tx, y - h / 2f, z - tz, x + tx, y - h / 2f, z + tz, x + tx, y + h / 2f, z + tz, x - tx, y + h / 2f, z - tz, c)
    }

    private fun viaduct(b: MeshBuilder, a: Float) {
        val r0 = 70f
        val r1 = SAT_DIST - SAT_R - 4f
        val len = r1 - r0
        b.push().translate((r0 + r1) / 2f * cos(a), 0f, (r0 + r1) / 2f * sin(a)).rotateY(90f - a * Kit.RAD2DEG)
        b.push().translate(0f, 6.9f, 0f).box(6f, 1.2f, len, STONE, STONE, STONE_SHADE, STONE_SHADE).pop()
        for (s in intArrayOf(-1, 1)) b.push().translate(s * 2.9f, 7.8f, 0f).box(0.3f, 0.6f, len, GOLD).pop()
        b.push().rotateY(90f)
        for (k in -1..1) {
            b.push().translate(k * len / 3f, 0.8f, 0f)
            Kit.ringSector(b, 4.6f, 5.4f, -3f, 3f, 0f, 180f, 6, STONE_SHADE, STONE_SHADE, STONE)
            b.pop()
        }
        b.pop()
        b.pop()
    }

    fun city(b: MeshBuilder) {
        val a0 = 90f + OPEN_DEG
        val a1 = 450f - OPEN_DEG
        // everything is built 0.7 m too high and sunk at the end: the plinth and the avenue end up almost flush with the seabed, so that a car can drive in
        b.push().translate(0f, -0.7f, 0f)
        b.push().translate(0f, 0.75f, 0f).cylinder(177f, 180f, 0.1f, 48, STONE_SHADE, Col.mul(STONE_SHADE, 0.7f), smooth = false, capBottom = false).pop()
        b.push().translate(0f, 0.8f, 122f).box(22f, 0.1f, 86f, rgb(0xE6F4F0), rgb(0xBFE0DC), rgb(0x9CC4C2), rgb(0x9CC4C2)).pop()
        for (sx in intArrayOf(-1, 1)) b.push().translate(sx * 11.2f, 0.86f, 122f).box(0.5f, 0.1f, 86f, GOLD).pop()
        for (i in 0 until 3) {
            val r = 80f - 6f * i
            b.push().translate(0f, 0.8f + 2.2f * i + 1.1f, 0f).cylinder(r - 0.5f, r, 2.2f, 40, Kit.tone(STONE, i, 0.04f), Col.mul(STONE_SHADE, 0.9f), smooth = false, capBottom = false).pop()
        }
        b.push().translate(0f, TOP - 0.2f, 0f).cylinder(68.6f, 68.6f, 0.5f, 40, GOLD, GOLD, smooth = false, capTop = false, capBottom = false).pop()
        // the great dome, on its drum, with golden ribs and bands, and the spire
        b.push().translate(0f, TOP + 2f, 0f).cylinder(DOME_R + 0.5f, DOME_R + 0.5f, 4f, 36, STONE, STONE_SHADE, smooth = false).pop()
        b.push().translate(0f, DOME_BASE, 0f).sphere(DOME_R, 8, 30, GLASS_TOP, GLASS_BOT, smooth = true, latFromDeg = 0f, latToDeg = 90f).pop()
        for (k in 0 until 3) {
            b.push().translate(0f, DOME_BASE, 0f).rotateY(k * 60f)
            Kit.ringSector(b, DOME_R - 0.1f, DOME_R + 1.1f, -0.6f, 0.6f, 0f, 180f, 10, GOLD)
            b.pop()
        }
        for (h in floatArrayOf(14f, 34f)) {
            val rLow = sqrt(DOME_R * DOME_R - (h - 0.9f) * (h - 0.9f)) + 0.3f
            val rHigh = sqrt(DOME_R * DOME_R - (h + 0.9f) * (h + 0.9f)) + 0.3f
            b.push().translate(0f, DOME_BASE + h, 0f).cylinder(rHigh, rLow, 1.8f, 30, GOLD, GOLD, smooth = false, capTop = false, capBottom = false).pop()
        }
        val apex = DOME_BASE + DOME_R
        b.push().translate(0f, apex + 1.5f, 0f).cylinder(5f, 6f, 3f, 12, GOLD, Col.mul(GOLD, 0.8f), smooth = false).pop()
        b.push().translate(0f, apex + 16f, 0f).cone(3.2f, 26f, 10, GOLD, Col.mul(GOLD, 0.85f), smooth = true).pop()
        // six smaller domes, each joined to the terraces by an arched viaduct
        for (k in 0 until 6) {
            val a = Kit.rad(k * 60f)
            val cx = SAT_DIST * cos(a); val cz = SAT_DIST * sin(a)
            val top = if (k % 2 == 0) GLASS_TOP else rgb(0xB6F5D8)
            b.push().translate(cx, 4.1f, cz).cylinder(SAT_R + 3f, SAT_R + 4f, 6.6f, 20, STONE, STONE_SHADE, smooth = false, capBottom = false).pop()
            b.push().translate(cx, TOP + 2f, cz).cylinder(SAT_R + 0.5f, SAT_R + 0.5f, 4f, 20, STONE, STONE_SHADE, smooth = false).pop()
            b.push().translate(cx, DOME_BASE, cz).sphere(SAT_R, 4, 16, top, GLASS_BOT, smooth = true, latFromDeg = 0f, latToDeg = 90f).pop()
            b.push().translate(cx, DOME_BASE + SAT_R + 6f, cz).cone(1.5f, 12f, 8, GOLD, Col.mul(GOLD, 0.85f), smooth = false).pop()
            b.push().translate(cx, DOME_BASE + 7f, cz).cylinder(SAT_R * 0.92f, SAT_R * 0.95f, 1.2f, 20, GOLD, GOLD, smooth = false, capTop = false, capBottom = false).pop()
            viaduct(b, a)
        }
        // the ring wall with its opening, gold cornice, and the towers
        wallRing(b, WALL_R - 6f, WALL_R, 0.8f, 14f, a0, a1, 54, STONE, STONE_SHADE, STONE)
        wallRing(b, WALL_R - 7f, WALL_R + 1f, 14f, 15.4f, a0, a1, 54, GOLD, Col.mul(GOLD, 0.8f), GOLD)
        for ((i, t) in TOWERS.withIndex()) {
            val c = Kit.tone(STONE, i, 0.06f)
            b.push().translate(t.x, 0.8f + t.h / 2f, t.z).cylinder(t.r * 0.9f, t.r, t.h, 8, c, Col.mul(STONE_SHADE, 0.95f), smooth = false, capBottom = false).pop()
            b.push().translate(t.x, 0.8f + t.h + 0.4f, t.z).cylinder(t.r * 1.15f, t.r * 1.15f, 0.8f, 8, GOLD, GOLD, smooth = false, capTop = false, capBottom = false).pop()
            b.push().translate(t.x, 0.8f + t.h + 0.8f + t.roofH / 2f, t.z).cone(t.r * 1.2f, t.roofH, 8, if (t.gold) GOLD else TEAL_ROOF, Col.mul(if (t.gold) GOLD else TEAL_ROOF, 0.8f), smooth = false, cap = false).pop()
        }
        // the houses of the city and the lamp posts of the avenue
        for ((i, hs) in HOUSES.withIndex()) {
            val c = Kit.tone(STONE, i + 50, 0.07f)
            b.push().translate(hs.x, 0.8f + hs.h / 2f, hs.z).box(hs.w, hs.h, hs.d, c, c, Col.mul(c, 0.78f), Col.mul(c, 0.6f)).pop()
            if (hs.dome) {
                b.push().translate(hs.x, 0.8f + hs.h, hs.z).sphere(min(hs.w, hs.d) * 0.55f, 3, 10, GLASS_TOP, GLASS_BOT, smooth = true, latFromDeg = 0f, latToDeg = 90f).pop()
            } else {
                val rc = if (hs.gold) GOLD else TEAL_ROOF
                b.push().translate(hs.x, 0.8f + hs.h + 2.2f, hs.z).rotateY(45f).cone(max(hs.w, hs.d) * 0.62f, 4.4f, 4, rc, Col.mul(rc, 0.8f), smooth = false).pop()
            }
        }
        for (k in 0 until 12) for (sx in intArrayOf(-1, 1)) b.push().translate(sx * 12.5f, 3.2f, 92f + 6f * k).box(0.5f, 4.8f, 0.5f, STONE_SHADE).pop()
        b.pop()
    }

    /** The glowing parts: windows, lamps and beacon rings, bright cyan, gold and white (same origin as the city; drawn emissive). */
    fun lights(b: MeshBuilder) {
        val cyan = rgb(0x8CFFF2)
        val gold = rgb(0xFFD866)
        val white = rgb(0xF6FFFF)
        val a0 = 90f + OPEN_DEG
        val a1 = 450f - OPEN_DEG
        b.push().translate(0f, -0.7f, 0f)
        bandRing(b, WALL_R + 0.12f, 11.6f, 12.8f, a0, a1, 54, true, cyan)
        bandRing(b, WALL_R - 6.12f, 11.6f, 12.8f, a0, a1, 54, false, cyan)
        // beacon rings round the great dome's base and the small domes'
        b.push().translate(0f, TOP + 3.1f, 0f).cylinder(DOME_R + 0.8f, DOME_R + 0.8f, 0.9f, 36, cyan, cyan, smooth = false, capTop = false, capBottom = false).pop()
        for (k in 0 until 24) {
            val a = Kit.rad(k * 15f + 7.5f)
            window(b, (DOME_R + 0.55f) * cos(a), TOP + 1.6f, (DOME_R + 0.55f) * sin(a), cos(a), sin(a), 2.2f, 2.0f, if (k % 3 == 0) gold else cyan)
        }
        for (k in 0 until 6) {
            val sa = Kit.rad(k * 60f)
            val cx = SAT_DIST * cos(sa); val cz = SAT_DIST * sin(sa)
            b.push().translate(cx, TOP + 3.1f, cz).cylinder(SAT_R + 0.8f, SAT_R + 0.8f, 0.8f, 20, cyan, cyan, smooth = false, capTop = false, capBottom = false).pop()
            for (m in 0 until 10) {
                val a = Kit.rad(m * 36f + 18f)
                window(b, cx + (SAT_R + 0.55f) * cos(a), TOP + 1.6f, cz + (SAT_R + 0.55f) * sin(a), cos(a), sin(a), 1.8f, 1.8f, if (m % 2 == 0) cyan else gold)
            }
            b.push().translate(cx, DOME_BASE + SAT_R + 12.8f, cz).icosphere(0.9f, 0, white).pop()
        }
        // the spire's rings and the beacon at its tip
        val apex = DOME_BASE + DOME_R
        for (yy in floatArrayOf(74f, 82f, 90f)) {
            val rr = 3.2f * (apex + 29.4f - yy) / 26f + 0.25f
            b.push().translate(0f, yy, 0f).cylinder(rr, rr + 0.1f, 1.0f, 10, gold, gold, smooth = false, capTop = false, capBottom = false).pop()
        }
        b.push().translate(0f, apex + 31f, 0f).icosphere(2.6f, 1, white).pop()
        for ((i, t) in TOWERS.withIndex()) {
            val l = sqrt(t.x * t.x + t.z * t.z)
            val nx = t.x / l; val nz = t.z / l
            for (row in 0 until 3) for (col in intArrayOf(-1, 1)) {
                val yy = 0.8f + t.h * (0.3f + 0.22f * row)
                val tx = -nz * col * t.r * 0.45f; val tz = nx * col * t.r * 0.45f
                window(b, t.x + nx * (t.r * 0.9f + 0.12f) + tx, yy, t.z + nz * (t.r * 0.9f + 0.12f) + tz, nx, nz, 1.1f, 2.0f, if ((row + col + i) % 2 == 0) cyan else gold)
            }
            b.push().translate(t.x, 0.8f + t.h + 0.8f + t.roofH + 0.3f, t.z).box(0.9f, 0.9f, 0.9f, gold).pop()
        }
        for ((i, hs) in HOUSES.withIndex()) {
            for (row in 0 until 2) for (col in intArrayOf(-1, 1)) {
                window(b, hs.x + col * hs.w * 0.22f, 0.8f + hs.h * (0.35f + 0.3f * row), hs.z + hs.d / 2f + 0.12f, 0f, 1f, 1.2f, 1.6f, if ((row + col + i) % 3 == 0) gold else cyan)
            }
        }
        for (k in 0 until 12) for (sx in intArrayOf(-1, 1)) b.push().translate(sx * 12.5f, 6.0f, 92f + 6f * k).box(1.1f, 1.1f, 1.1f, gold).pop()
        b.pop()
    }
}
