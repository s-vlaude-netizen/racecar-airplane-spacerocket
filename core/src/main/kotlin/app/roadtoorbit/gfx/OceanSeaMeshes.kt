package app.roadtoorbit.gfx

import app.roadtoorbit.gfx.Col.rgb
import app.roadtoorbit.math.Rng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The models of the sea and the dive: sea stacks, ships, gulls, rival seaplanes, islands, the dive gate, start buoys,
 * mines, jellyfish, sharks, pearls, whales, fish, kelp and coral. See [OceanSpec] for what each one has to be like.
 *
 * Same conventions as [WorldMeshes] and [MarsMeshes]: hazards and pickups are authored around their collision centre,
 * scenery stands on y = 0 with its middle on x = 0, z = 0, everything that moves along the route points to -Z, colours are
 * baked into vertex colours (a glowing part just gets a very bright colour: the game draws the whole mesh with one material).
 * Everything is deterministic: models that need "random" detail draw it from a [Rng] seeded per model.
 */
internal object OceanSeaMeshes {
    fun build(id: MeshId, b: MeshBuilder) {
        when (id) {
            MeshId.SEASTACK_0 -> seaStack(b, 0)
            MeshId.SEASTACK_1 -> seaStack(b, 1)
            MeshId.SEASTACK_2 -> seaStack(b, 2)
            MeshId.SHIP_0 -> sailboat(b)
            MeshId.SHIP_1 -> trawler(b)
            MeshId.SHIP_2 -> containerShip(b)
            MeshId.FLOCK_0 -> flock(b, 0)
            MeshId.FLOCK_1 -> flock(b, 1)
            MeshId.FLOCK_2 -> flock(b, 2)
            MeshId.FLOCK_3 -> flock(b, 3)
            MeshId.SEAPLANE_0 -> seaplane(b, rgb(0xF7C521), rgb(0xD8352A), rgb(0xFFFFFF))
            MeshId.SEAPLANE_1 -> seaplane(b, rgb(0xDF3B2E), rgb(0xFFFFFF), rgb(0xFFD23A))
            MeshId.SEAPLANE_2 -> seaplane(b, rgb(0x35B864), rgb(0xFFFFFF), rgb(0xFFD23A))
            MeshId.ISLAND_0 -> island(b, 0)
            MeshId.ISLAND_1 -> island(b, 1)
            MeshId.ISLAND_2 -> island(b, 2)
            MeshId.DIVE_GATE_BIG -> diveGate(b, true)
            MeshId.DIVE_GATE_SMALL -> diveGate(b, false)
            MeshId.START_BUOYS -> startBuoys(b)
            MeshId.MINE_0 -> mine(b, false)
            MeshId.MINE_1 -> mine(b, true)
            MeshId.JELLY_0 -> jelly(b, 0)
            MeshId.JELLY_1 -> jelly(b, 1)
            MeshId.SHARK_0 -> shark(b, false)
            MeshId.SHARK_1 -> shark(b, true)
            MeshId.MANTA -> manta(b)
            MeshId.PEARL -> pearl(b)
            MeshId.WHALE -> whale(b)
            MeshId.SCHOOL_0 -> school(b, 0)
            MeshId.SCHOOL_1 -> school(b, 1)
            MeshId.KELP_0 -> kelp(b, 0)
            MeshId.KELP_1 -> kelp(b, 1)
            MeshId.CORAL_0 -> coral(b, 0)
            MeshId.CORAL_1 -> coral(b, 1)
            MeshId.CORAL_2 -> coral(b, 2)
            else -> b.box(1f, 1f, 1f, rgb(0xFF00FF)) // not a model of the sea or the dive
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // small tools shared by the models
    // ---------------------------------------------------------------------------------------------------------

    private class V(val x: Float, val y: Float, val z: Float)

    private const val R2D = (180.0 / PI).toFloat()
    private const val TAU = (2.0 * PI).toFloat()

    private val WHITE = rgb(0xF6F8FA)
    private val FOAM = rgb(0xF2FAFF)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v

    private fun smooth(t: Float): Float {
        val x = clamp01(t)
        return x * x * (3f - 2f * x)
    }

    /** A box that runs from one point to another, with the given cross-section (its width stays horizontal where it can). */
    private fun MeshBuilder.beam(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, w: Float, h: Float, color: Int): MeshBuilder {
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-5f) return this
        val yaw = atan2(dx, dz) * R2D
        val pitch = atan2(dy, sqrt(dx * dx + dz * dz)) * R2D
        push().translate((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2).rotateY(yaw).rotateX(-pitch).box(w, h, len, color).pop()
        return this
    }

    /** A tapered tube from one point (radius [r0]) to another (radius [r1]). */
    private fun MeshBuilder.limb(
        x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, r0: Float, r1: Float, sides: Int,
        c0: Int, c1: Int = c0, smooth: Boolean = false, capStart: Boolean = false, capEnd: Boolean = false,
    ): MeshBuilder {
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-5f) return this
        val yaw = atan2(dx, dz) * R2D
        val pitch = atan2(dy, sqrt(dx * dx + dz * dz)) * R2D
        push().translate((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2).rotateY(yaw).rotateX(90f - pitch)
            .cylinder(r1, r0, len, sides, c1, c0, smooth, capTop = capEnd, capBottom = capStart).pop()
        return this
    }

    /** A triangle whose winding is chosen so that it faces away from [ref] (a point inside the shape). */
    private fun MeshBuilder.triOut(ref: V, a: V, b: V, c: V, ca: Int, cb: Int = ca, cc: Int = ca) {
        val ux = b.x - a.x; val uy = b.y - a.y; val uz = b.z - a.z
        val vx = c.x - a.x; val vy = c.y - a.y; val vz = c.z - a.z
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        val gx = (a.x + b.x + c.x) / 3f - ref.x
        val gy = (a.y + b.y + c.y) / 3f - ref.y
        val gz = (a.z + b.z + c.z) / 3f - ref.z
        if (nx * gx + ny * gy + nz * gz >= 0f) tri(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, ca, cb, cc)
        else tri(a.x, a.y, a.z, c.x, c.y, c.z, b.x, b.y, b.z, ca, cc, cb)
    }

    /** A flat quad visible from both sides (sails, wings, leaves). */
    private fun MeshBuilder.sheet(a: V, b: V, c: V, d: V, ca: Int, cb: Int = ca, cc: Int = ca, cd: Int = ca) {
        quad(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z, ca, cb, cc, cd)
        quad(a.x, a.y, a.z, d.x, d.y, d.z, c.x, c.y, c.z, b.x, b.y, b.z, ca, cd, cc, cb)
    }

    /** A flat triangle visible from both sides. */
    private fun MeshBuilder.sheet3(a: V, b: V, c: V, ca: Int, cb: Int = ca, cc: Int = ca) {
        tri(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, ca, cb, cc)
        tri(a.x, a.y, a.z, c.x, c.y, c.z, b.x, b.y, b.z, ca, cc, cb)
    }

    /**
     * A double sided triangle whose normals on both faces are tilted up by [lift]: thin upright sheets (sails, leaves) would
     * otherwise be lit only by the dim sideways ambient light on their shaded face and come out grey.
     */
    private fun MeshBuilder.sheetLit3(a: V, b: V, c: V, lift: Float, ca: Int, cb: Int = ca, cc: Int = ca) {
        val ux = b.x - a.x; val uy = b.y - a.y; val uz = b.z - a.z
        val vx = c.x - a.x; val vy = c.y - a.y; val vz = c.z - a.z
        var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
        val l = sqrt(nx * nx + ny * ny + nz * nz)
        if (l < 1e-9f) return
        nx /= l; ny /= l; nz /= l
        val fl = sqrt(nx * nx + (ny + lift) * (ny + lift) + nz * nz)
        val bl = sqrt(nx * nx + (-ny + lift) * (-ny + lift) + nz * nz)
        triSmooth(
            a.x, a.y, a.z, nx / fl, (ny + lift) / fl, nz / fl, b.x, b.y, b.z, nx / fl, (ny + lift) / fl, nz / fl,
            c.x, c.y, c.z, nx / fl, (ny + lift) / fl, nz / fl, ca, cb, cc,
        )
        triSmooth(
            a.x, a.y, a.z, -nx / bl, (-ny + lift) / bl, -nz / bl, c.x, c.y, c.z, -nx / bl, (-ny + lift) / bl, -nz / bl,
            b.x, b.y, b.z, -nx / bl, (-ny + lift) / bl, -nz / bl, ca, cc, cb,
        )
    }

    /** A double sided quad with tilted normals, see [sheetLit3]. */
    private fun MeshBuilder.sheetLit(a: V, b: V, c: V, d: V, lift: Float, ca: Int, cb: Int = ca, cc: Int = ca, cd: Int = ca) {
        sheetLit3(a, b, c, lift, ca, cb, cc)
        sheetLit3(a, c, d, lift, ca, cc, cd)
    }

    private fun centreOf(ring: Array<V>): V {
        var sx = 0f; var sy = 0f; var sz = 0f
        for (p in ring) { sx += p.x; sy += p.y; sz += p.z }
        return V(sx / ring.size, sy / ring.size, sz / ring.size)
    }

    /** A closed polygon of [sides] points around the vertical axis through ([cx], [cz]) at height [y]; [jit] scales each radius. */
    private fun ringPoints(cx: Float, y: Float, cz: Float, r: Float, sides: Int, rot: Float, jit: FloatArray? = null): Array<V> =
        Array(sides) { i ->
            val a = rot + TAU * i / sides
            val rr = r * (jit?.get(i) ?: 1f)
            V(cx + rr * cos(a), y, cz + rr * sin(a))
        }

    /** Skins a stack of rings (all with the same number of points), flat shaded; [color] gives each face its colour. */
    private fun MeshBuilder.loftFlat(rings: List<Array<V>>, color: (Int, Int) -> Int, capStart: Int? = null, capEnd: Int? = null) {
        val n = rings[0].size
        val centres = rings.map { centreOf(it) }
        for (k in 0 until rings.size - 1) {
            val r0 = rings[k]; val r1 = rings[k + 1]
            val ref = V((centres[k].x + centres[k + 1].x) / 2, (centres[k].y + centres[k + 1].y) / 2, (centres[k].z + centres[k + 1].z) / 2)
            for (i in 0 until n) {
                val j = (i + 1) % n
                val c = color(k, i)
                triOut(ref, r0[i], r1[i], r1[j], c)
                triOut(ref, r0[i], r1[j], r0[j], c)
            }
        }
        if (capStart != null) for (i in 0 until n) triOut(centres[1], centres[0], rings[0][i], rings[0][(i + 1) % n], capStart)
        if (capEnd != null) {
            val last = rings.size - 1
            for (i in 0 until n) triOut(centres[last - 1], centres[last], rings[last][i], rings[last][(i + 1) % n], capEnd)
        }
    }

    /** Skins a stack of rings with smooth shading and a colour per point (sharks, whales, jellyfish bells). */
    private fun MeshBuilder.loftSmooth(rings: List<Array<V>>, color: (Int, Int) -> Int, capStart: Int? = null, capEnd: Int? = null) {
        val n = rings[0].size
        val m = rings.size
        val centres = rings.map { centreOf(it) }
        val nx = Array(m) { FloatArray(n) }
        val ny = Array(m) { FloatArray(n) }
        val nz = Array(m) { FloatArray(n) }
        // area weighted face normals, turned to point away from the axis
        for (k in 0 until m - 1) {
            val ref = V((centres[k].x + centres[k + 1].x) / 2, (centres[k].y + centres[k + 1].y) / 2, (centres[k].z + centres[k + 1].z) / 2)
            for (i in 0 until n) {
                val j = (i + 1) % n
                val p00 = rings[k][i]; val p01 = rings[k][j]; val p10 = rings[k + 1][i]; val p11 = rings[k + 1][j]
                val d1x = p11.x - p00.x; val d1y = p11.y - p00.y; val d1z = p11.z - p00.z
                val d2x = p01.x - p10.x; val d2y = p01.y - p10.y; val d2z = p01.z - p10.z
                var fx = d1y * d2z - d1z * d2y
                var fy = d1z * d2x - d1x * d2z
                var fz = d1x * d2y - d1y * d2x
                val gx = (p00.x + p01.x + p10.x + p11.x) / 4f - ref.x
                val gy = (p00.y + p01.y + p10.y + p11.y) / 4f - ref.y
                val gz = (p00.z + p01.z + p10.z + p11.z) / 4f - ref.z
                if (fx * gx + fy * gy + fz * gz < 0f) { fx = -fx; fy = -fy; fz = -fz }
                for ((kk, ii) in listOf(k to i, k to j, k + 1 to i, k + 1 to j)) {
                    nx[kk][ii] += fx; ny[kk][ii] += fy; nz[kk][ii] += fz
                }
            }
        }
        for (k in 0 until m) for (i in 0 until n) {
            val l = sqrt(nx[k][i] * nx[k][i] + ny[k][i] * ny[k][i] + nz[k][i] * nz[k][i])
            if (l > 1e-9f) { nx[k][i] /= l; ny[k][i] /= l; nz[k][i] /= l } else ny[k][i] = 1f
        }
        fun smoothTri(k0: Int, i0: Int, k1: Int, i1: Int, k2: Int, i2: Int) {
            val a = rings[k0][i0]; val bb = rings[k1][i1]; val c = rings[k2][i2]
            val ux = bb.x - a.x; val uy = bb.y - a.y; val uz = bb.z - a.z
            val vx = c.x - a.x; val vy = c.y - a.y; val vz = c.z - a.z
            val fx = uy * vz - uz * vy; val fy = uz * vx - ux * vz; val fz = ux * vy - uy * vx
            val sx = nx[k0][i0] + nx[k1][i1] + nx[k2][i2]
            val sy = ny[k0][i0] + ny[k1][i1] + ny[k2][i2]
            val sz = nz[k0][i0] + nz[k1][i1] + nz[k2][i2]
            if (fx * sx + fy * sy + fz * sz >= 0f) {
                triSmooth(
                    a.x, a.y, a.z, nx[k0][i0], ny[k0][i0], nz[k0][i0],
                    bb.x, bb.y, bb.z, nx[k1][i1], ny[k1][i1], nz[k1][i1],
                    c.x, c.y, c.z, nx[k2][i2], ny[k2][i2], nz[k2][i2],
                    color(k0, i0), color(k1, i1), color(k2, i2),
                )
            } else {
                triSmooth(
                    a.x, a.y, a.z, nx[k0][i0], ny[k0][i0], nz[k0][i0],
                    c.x, c.y, c.z, nx[k2][i2], ny[k2][i2], nz[k2][i2],
                    bb.x, bb.y, bb.z, nx[k1][i1], ny[k1][i1], nz[k1][i1],
                    color(k0, i0), color(k2, i2), color(k1, i1),
                )
            }
        }
        for (k in 0 until m - 1) for (i in 0 until n) {
            val j = (i + 1) % n
            smoothTri(k, i, k + 1, i, k + 1, j)
            smoothTri(k, i, k + 1, j, k, j)
        }
        if (capStart != null) for (i in 0 until n) triOut(centres[1], centres[0], rings[0][i], rings[0][(i + 1) % n], capStart)
        if (capEnd != null) for (i in 0 until n) triOut(centres[m - 2], centres[m - 1], rings[m - 1][i], rings[m - 1][(i + 1) % n], capEnd)
    }

    /** A flat polygon facing up (or [down]), fanned from its first point. */
    private fun MeshBuilder.flatFan(centre: V, rim: List<V>, centreColor: Int, rimColor: Int, down: Boolean = false) {
        val ref = V(centre.x, centre.y + if (down) 5f else -5f, centre.z)
        for (i in rim.indices) triOut(ref, centre, rim[i], rim[(i + 1) % rim.size], centreColor, rimColor, rimColor)
    }

    // ---------------------------------------------------------------------------------------------------------
    // sea stacks (SEASTACK_0..2): unit pillars of dark basalt, base on y = 0, radius <= 1.1, height 1;
    // the game scales them to roughly (3.5, 14..30, 3.5), so everything round is stretched ~6x upwards
    // ---------------------------------------------------------------------------------------------------------

    private val BASALT = intArrayOf(rgb(0x3B4048), rgb(0x2E333B), rgb(0x4B4F56), rgb(0x363A42), rgb(0x585A5E), rgb(0x42464E), rgb(0x2B2F36))
    private val LIME = rgb(0xEFEBDD)
    private val ALGAE = rgb(0x31503F)

    /**
     * One craggy column made of strata between y0 and y1: each band is a slightly different slab (own rotation, jitter and
     * step) so the sides show horizontal layers and ledges; the topmost [capBands] are white with bird droppings running down.
     */
    private fun stackColumn(
        b: MeshBuilder, rng: Rng, y0: Float, y1: Float, bands: Int, sides: Int,
        radius: (Float) -> Float, offX: (Float) -> Float, offZ: (Float) -> Float,
        capBands: Int, shift: Int, craggy: Float = 0.10f, topCap: Boolean = true,
    ) {
        val rings = ArrayList<Array<V>>()
        val colors = IntArray(bands)
        // band boundaries are not evenly spaced: thick and thin layers
        val cut = FloatArray(bands + 1)
        var acc = 0f
        val th = FloatArray(bands) { 0.6f + rng.float() * 0.9f }
        for (j in 0 until bands) { cut[j] = acc; acc += th[j] }
        cut[bands] = acc
        for (j in 0..bands) cut[j] = y0 + (y1 - y0) * cut[j] / acc
        for (j in 0 until bands) {
            colors[j] = BASALT[(j * 2 + shift) % BASALT.size]
            val rot = rng.float() * 0.7f
            val jit = FloatArray(sides) { 1f + craggy * (rng.float() * 2f - 1f) }
            val step = 1f + (rng.float() - 0.5f) * 0.10f // a ledge or an overhang between two layers
            for (e in 0..1) {
                val y = if (e == 0) cut[j] else cut[j + 1] - 0.002f
                val t = (y - y0) / (y1 - y0)
                val r = radius(t) * (if (e == 0) step else 1f)
                rings.add(ringPoints(offX(t), y, offZ(t), r, sides, rot, jit))
            }
        }
        val faceNoise = FloatArray(bands * sides) { rng.float() }
        b.loftFlat(
            rings,
            { k, i ->
                val j = k / 2
                if (k % 2 == 1) Col.mul(colors[j], 0.78f) // the ledge between two layers
                else {
                    var c = Col.mul(colors[j], 0.88f + 0.24f * faceNoise[j * sides + i])
                    val fromTop = bands - 1 - j
                    if (fromTop < capBands) c = Col.mix(LIME, Col.mul(LIME, 0.92f), faceNoise[j * sides + i])
                    else if (fromTop == capBands && faceNoise[j * sides + i] > 0.45f) c = LIME // droppings running down the rock
                    else if (fromTop == capBands + 1 && faceNoise[j * sides + i] > 0.82f) c = LIME
                    if (j == 0) c = Col.mix(c, ALGAE, 0.55f) // weed and wet rock at the waterline
                    c
                }
            },
            capStart = null,
            capEnd = if (topCap) LIME else null,
        )
    }

    /** A white fringe of surf around the foot of a stack, flat on the water. */
    private fun surfRing(b: MeshBuilder, rng: Rng, inner: Float, outer: Float, y: Float, n: Int = 22) {
        val rim = ArrayList<V>()
        val inn = ArrayList<V>()
        for (i in 0 until n) {
            val a = TAU * i / n
            val ro = outer * (0.86f + 0.14f * rng.float()) * (if (i % 2 == 0) 1f else 0.93f)
            rim.add(V(cos(a) * ro, y, sin(a) * ro))
            inn.add(V(cos(a) * inner, y, sin(a) * inner))
        }
        val ref = V(0f, -5f, 0f)
        for (i in 0 until n) {
            val j = (i + 1) % n
            b.triOut(ref, inn[i], rim[i], rim[j], FOAM, Col.mix(FOAM, rgb(0xB9E6F5), 0.35f), Col.mix(FOAM, rgb(0xB9E6F5), 0.35f))
            b.triOut(ref, inn[i], rim[j], inn[j], FOAM)
        }
    }

    private fun seaStack(b: MeshBuilder, variant: Int) {
        val rng = Rng(7700L + variant * 101L)
        when (variant) {
            0 -> {
                // a tall, slender, leaning needle
                stackColumn(
                    b, rng, 0f, 0.98f, 11, 9,
                    radius = { t -> 0.36f + 0.56f * (1f - t).pow(1.35f) },
                    offX = { t -> 0.26f * t.pow(1.6f) }, offZ = { t -> -0.10f * t },
                    capBands = 2, shift = 0,
                )
                // the crown ends in a little white horn
                b.push().translate(0.26f, 0.985f, -0.10f).cone(0.30f, 0.06f, 9, LIME, LIME, smooth = false, cap = false).pop()
                surfRing(b, rng, 0.82f, 1.08f, 0.004f)
            }
            1 -> {
                // a broad shoulder that splits into two horns, with a notch between them
                stackColumn(
                    b, rng, 0f, 0.58f, 7, 10,
                    radius = { t -> 0.68f + 0.24f * (1f - t).pow(1.5f) },
                    offX = { t -> 0.05f * t }, offZ = { _ -> 0f },
                    capBands = 0, shift = 2, topCap = false,
                )
                stackColumn(
                    b, rng, 0.575f, 1.0f, 6, 8,
                    radius = { t -> 0.20f + 0.30f * (1f - t).pow(1.2f) },
                    offX = { t -> -0.30f - 0.08f * t }, offZ = { t -> 0.02f * t },
                    capBands = 2, shift = 1,
                )
                stackColumn(
                    b, rng, 0.575f, 0.84f, 5, 8,
                    radius = { t -> 0.16f + 0.26f * (1f - t).pow(1.1f) },
                    offX = { t -> 0.36f + 0.05f * t }, offZ = { t -> 0.06f - 0.06f * t },
                    capBands = 2, shift = 4,
                )
                // the wide ledge on top of the shoulder is bare rock with a few white splashes
                for (k in 0 until 6) {
                    val a = rng.float() * TAU
                    val rr = 0.5f + 0.2f * rng.float()
                    b.push().translate(cos(a) * rr, 0.585f, sin(a) * rr).scale(0.10f, 0.012f, 0.08f).icosphere(1f, 0, LIME).pop()
                }
                surfRing(b, rng, 0.9f, 1.09f, 0.004f)
            }
            else -> {
                // a stocky tower: narrow waist worn by the waves, a wide overhanging crown, a pinnacle next to it
                stackColumn(
                    b, rng, 0f, 0.62f, 7, 10,
                    radius = { t -> 0.58f + 0.36f * (1f - t).pow(2.2f) },
                    offX = { t -> -0.04f * t }, offZ = { t -> 0.03f * t },
                    capBands = 0, shift = 3, topCap = false,
                )
                stackColumn(
                    b, rng, 0.615f, 0.97f, 6, 10,
                    radius = { t -> if (t < 0.45f) lerp(0.62f, 0.86f, smooth(t / 0.45f)) else lerp(0.86f, 0.74f, (t - 0.45f) / 0.55f) },
                    offX = { t -> -0.04f + 0.1f * t }, offZ = { _ -> 0.03f },
                    capBands = 2, shift = 5, craggy = 0.07f,
                )
                stackColumn(
                    b, rng, 0f, 0.70f, 8, 7,
                    radius = { t -> 0.12f + 0.17f * (1f - t).pow(1.3f) },
                    offX = { t -> -0.60f - 0.04f * t }, offZ = { t -> -0.18f + 0.05f * t },
                    capBands = 1, shift = 0, craggy = 0.14f,
                )
                surfRing(b, rng, 0.9f, 1.09f, 0.004f)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // ships: standing on the waterline (y = 0), bow toward -Z, filling OceanSpec.SHIP_HALF
    // ---------------------------------------------------------------------------------------------------------

    /** One cross-section of a hull: the half beam [b], the height of the sheer line above the water [h] and the keel depth [d] below it. */
    private class Sec(val z: Float, val b: Float, val h: Float, val d: Float)

    // the side of a hull from the sheer line to the keel: x as a fraction of the half beam, y in units of h (above water) or d (below)
    private val HULL_X = floatArrayOf(1.00f, 1.03f, 1.00f, 0.96f, 0.80f, 0.45f, 0.0f)
    private val HULL_Y = floatArrayOf(1.00f, 0.60f, 0.25f, 0.0f, -0.55f, -0.92f, -1.0f)

    private fun hullPoint(s: Sec, j: Int, side: Float): V {
        val y = if (HULL_Y[j] >= 0f) HULL_Y[j] * s.h else HULL_Y[j] * s.d
        return V(side * s.b * HULL_X[j], y, s.z)
    }

    /**
     * The hull skin from a list of sections. [segColors] colours the six strips of the side from the sheer line down to the
     * keel. With [bulwark] > 0 the sides rise that much above a working deck (a trawler), otherwise the deck is flush with the
     * sheer line.
     */
    private fun MeshBuilder.hullShell(secs: List<Sec>, segColors: IntArray, deckColor: Int, railColor: Int, innerColor: Int, bulwark: Float = 0f, rail: Float = 0.16f) {
        for (k in 0 until secs.size - 1) {
            val s0 = secs[k]; val s1 = secs[k + 1]
            val ref = V(0f, (s0.h + s1.h) * 0.2f, (s0.z + s1.z) / 2)
            for (side in floatArrayOf(1f, -1f)) {
                for (j in 0 until 6) {
                    val a = hullPoint(s0, j, side); val bb = hullPoint(s0, j + 1, side)
                    val c = hullPoint(s1, j + 1, side); val d = hullPoint(s1, j, side)
                    triOut(ref, a, bb, c, segColors[j]); triOut(ref, a, c, d, segColors[j])
                }
                if (bulwark > 0f) {
                    // rail on top of the bulwark, its inner wall, and the working deck lower down
                    val g0 = hullPoint(s0, 0, side); val g1 = hullPoint(s1, 0, side)
                    val i0 = V(side * maxOf(s0.b - rail, 0f), g0.y, s0.z); val i1 = V(side * maxOf(s1.b - rail, 0f), g1.y, s1.z)
                    triOut(ref, g0, i0, i1, railColor); triOut(ref, g0, i1, g1, railColor)
                    val f0 = V(i0.x, g0.y - bulwark, s0.z); val f1 = V(i1.x, g1.y - bulwark, s1.z)
                    triOut(V(0f, g0.y, ref.z), i0, f0, f1, innerColor); triOut(V(0f, g0.y, ref.z), i0, f1, i1, innerColor)
                }
            }
            if (bulwark > 0f) {
                val l0 = V(-maxOf(s0.b - rail, 0f), s0.h - bulwark, s0.z); val r0 = V(maxOf(s0.b - rail, 0f), s0.h - bulwark, s0.z)
                val l1 = V(-maxOf(s1.b - rail, 0f), s1.h - bulwark, s1.z); val r1 = V(maxOf(s1.b - rail, 0f), s1.h - bulwark, s1.z)
                val below = V(0f, -5f, ref.z)
                triOut(below, l0, r0, r1, deckColor); triOut(below, l0, r1, l1, deckColor)
            } else {
                val l0 = hullPoint(s0, 0, -1f); val r0 = hullPoint(s0, 0, 1f)
                val l1 = hullPoint(s1, 0, -1f); val r1 = hullPoint(s1, 0, 1f)
                val below = V(0f, -5f, ref.z)
                triOut(below, l0, r0, r1, deckColor); triOut(below, l0, r1, l1, deckColor)
            }
        }
    }

    /** The flat end of a hull (the transom): one polygon through the whole section. */
    private fun MeshBuilder.transom(s: Sec, color: Int, towardPlusZ: Boolean) {
        val pts = ArrayList<V>()
        for (j in 0..6) pts.add(hullPoint(s, j, 1f))
        for (j in 5 downTo 1) pts.add(hullPoint(s, j, -1f))
        val c = V(0f, s.h * 0.3f, s.z)
        val ref = V(0f, s.h * 0.3f, s.z + if (towardPlusZ) -3f else 3f)
        for (i in pts.indices) triOut(ref, c, pts[i], pts[(i + 1) % pts.size], color)
    }

    /** White water along the waterline of a hull, a little wider than the hull and a little longer: it also hides the seam to the sea. */
    private fun MeshBuilder.waterline(secs: List<Sec>, extra: Float, bow: Float, stern: Float, y: Float = 0.05f) {
        val pts = ArrayList<V>()
        pts.add(V(0f, y, secs[0].z - bow))
        for (s in secs.drop(1)) pts.add(V(s.b * 0.96f + extra, y, s.z))
        pts.add(V(0f, y, secs.last().z + stern))
        for (s in secs.drop(1).reversed()) pts.add(V(-(s.b * 0.96f + extra), y, s.z))
        val c = V(0f, y, (secs[0].z + secs.last().z) / 2)
        val rim = Col.mix(FOAM, rgb(0xA9DDF2), 0.45f)
        val ref = V(0f, -5f, c.z)
        for (i in pts.indices) triOut(ref, c, pts[i], pts[(i + 1) % pts.size], FOAM, rim, rim)
    }

    private fun sailboat(b: MeshBuilder) {
        val white = rgb(0xF4F6F8)
        val red = rgb(0xD32B27)
        val anti = rgb(0x7E1B1E)
        val teak = rgb(0xC79A5E)
        val steel = rgb(0xCBD3DC)
        val secs = listOf(
            Sec(-4.75f, 0.00f, 1.55f, 0.00f), Sec(-4.25f, 0.42f, 1.42f, 0.35f), Sec(-3.25f, 1.02f, 1.28f, 0.60f),
            Sec(-1.85f, 1.62f, 1.14f, 0.78f), Sec(-0.25f, 1.93f, 1.05f, 0.85f), Sec(1.35f, 1.98f, 1.00f, 0.82f),
            Sec(2.95f, 1.90f, 0.97f, 0.70f), Sec(4.2f, 1.65f, 0.97f, 0.50f), Sec(4.8f, 1.45f, 1.0f, 0.35f),
        )
        b.hullShell(secs, intArrayOf(white, red, white, anti, anti, anti), teak, white, white)
        b.transom(secs.last(), white, true)
        b.waterline(secs, 0.2f, 0.25f, 0.2f)
        // the cabin top, with windows along both sides
        b.push().translate(0f, 1.34f, -0.9f).box(2.0f, 0.5f, 3.3f, white, white, Col.mul(white, 0.9f), white).pop()
        b.push().translate(0f, 1.66f, -0.8f).box(1.5f, 0.2f, 2.9f, teak, teak, teak, teak).pop()
        for (side in intArrayOf(-1, 1)) for (k in 0 until 3) {
            b.push().translate(side * 1.01f, 1.4f, -2.0f + k * 1.0f).box(0.05f, 0.24f, 0.62f, rgb(0x1B2A3D)).pop()
        }
        b.push().translate(0f, 1.2f, -3.3f).box(0.8f, 0.14f, 0.8f, white).pop() // foredeck hatch
        b.push().translate(0f, 1.15f, 0.9f).box(1.0f, 0.4f, 0.3f, white).pop() // companionway
        // mast, boom, spreaders and rigging
        b.limb(0f, 1.1f, -1.0f, 0f, 11.8f, -1.0f, 0.10f, 0.05f, 6, steel, steel)
        b.limb(0f, 2.3f, -1.0f, 0f, 2.3f, 3.6f, 0.06f, 0.06f, 5, steel, steel)
        for ((y, w) in listOf(5.5f to 1.5f, 8.8f to 1.0f)) b.beam(-w / 2, y, -1.0f, w / 2, y, -1.0f, 0.05f, 0.05f, steel)
        b.beam(0f, 11.8f, -1.0f, 0f, 1.45f, -4.75f, 0.035f, 0.035f, rgb(0x9AA4B0))
        b.beam(0f, 11.8f, -1.0f, 0f, 1.25f, 4.75f, 0.035f, 0.035f, rgb(0x9AA4B0))
        b.push().translate(0f, 11.86f, -1.0f).sphere(0.12f, 3, 6, rgb(0xFFF4C0), rgb(0xFFF4C0), smooth = true).pop()
        // the main sail: a belly toward +X, white with a grey foot; a red patch near the head and a red tape along the foot
        val sailC = rgb(0xFBFBF7)
        val sailFoot = rgb(0xDDE3EA)
        val rows = 5; val cols = 4
        fun sailPt(s: Float, t: Float): V {
            val zl = lerp(3.55f, -0.92f, s)
            return V(0.55f * sin(PI.toFloat() * t) * (1f - s).pow(0.6f), lerp(2.42f, 11.45f, s), lerp(-0.97f, zl, t))
        }
        for (r in 0 until rows) for (c in 0 until cols) {
            val s0 = r / rows.toFloat(); val s1 = (r + 1) / rows.toFloat(); val t0 = c / cols.toFloat(); val t1 = (c + 1) / cols.toFloat()
            val col0 = Col.mix(sailFoot, sailC, s0); val col1 = Col.mix(sailFoot, sailC, s1)
            b.sheetLit(sailPt(s0, t0), sailPt(s0, t1), sailPt(s1, t1), sailPt(s1, t0), 1.2f, col0, col0, col1, col1)
        }
        for (c in 0 until cols) {
            val t0 = c / cols.toFloat(); val t1 = (c + 1) / cols.toFloat()
            b.sheetLit(sailPt(0f, t0), sailPt(0f, t1), sailPt(0.06f, t1), sailPt(0.06f, t0), 1.2f, red)
        }
        // the jib
        fun jibPt(s: Float, t: Float): V { // s along the luff (tack to head), t from the luff to the leech
            val head = V(-0.05f, 10.5f, -1.05f); val tack = V(0f, 1.45f, -4.55f); val clew = V(-0.05f, 2.35f, -0.85f)
            val lx = lerp(tack.x, head.x, s); val ly = lerp(tack.y, head.y, s); val lz = lerp(tack.z, head.z, s)
            val ex = lerp(clew.x, head.x, s); val ey = lerp(clew.y, head.y, s); val ez = lerp(clew.z, head.z, s)
            return V(lerp(lx, ex, t) - 0.42f * sin(PI.toFloat() * t) * (1f - s * 0.5f), lerp(ly, ey, t), lerp(lz, ez, t))
        }
        for (r in 0 until 4) for (c in 0 until 3) {
            val s0 = r / 4f; val s1 = (r + 1) / 4f; val t0 = c / 3f; val t1 = (c + 1) / 3f
            val col0 = Col.mix(sailFoot, sailC, s0); val col1 = Col.mix(sailFoot, sailC, s1)
            b.sheetLit(jibPt(s0, t0), jibPt(s0, t1), jibPt(s1, t1), jibPt(s1, t0), 1.2f, col0, col0, col1, col1)
        }
        // a pennant at the masthead and an ensign at the stern, an outboard on the transom
        b.sheet3(V(0f, 11.9f, -1.0f), V(0f, 11.5f, -1.0f), V(0f, 11.7f, 0.45f), red)
        b.limb(0f, 1.0f, 4.8f, 0f, 2.7f, 4.8f, 0.035f, 0.03f, 4, steel, steel)
        b.sheet(V(0f, 2.7f, 4.8f), V(0f, 2.7f, 5.0f), V(0f, 2.2f, 5.0f), V(0f, 2.2f, 4.8f), red)
        b.push().translate(0.7f, 0.95f, 4.82f).box(0.34f, 0.7f, 0.3f, rgb(0x30343C)).pop()
        for (side in intArrayOf(-1, 1)) b.limb(side * 1.5f, 0.97f, 3.6f, side * 1.5f, 1.6f, 3.6f, 0.03f, 0.03f, 4, steel, steel)
        b.beam(-1.5f, 1.6f, 3.6f, 1.5f, 1.6f, 3.6f, 0.03f, 0.03f, steel)
    }

    private fun trawler(b: MeshBuilder) {
        val blue = rgb(0x1E5AB4)
        val red = rgb(0xCB3A2D)
        val white = rgb(0xF4F5F2)
        val deck = rgb(0x6E7B74)
        val inner = rgb(0xBFD0DD)
        val orange = rgb(0xF08A24)
        val secs = listOf(
            Sec(-9.9f, 0.00f, 3.3f, 0.00f), Sec(-9.2f, 0.75f, 3.1f, 0.8f), Sec(-7.6f, 1.75f, 2.7f, 1.3f),
            Sec(-5.0f, 2.7f, 2.35f, 1.5f), Sec(-2.0f, 3.1f, 2.1f, 1.6f), Sec(1.0f, 3.1f, 2.0f, 1.6f),
            Sec(4.0f, 3.1f, 1.95f, 1.5f), Sec(7.0f, 3.0f, 1.9f, 1.3f), Sec(9.0f, 2.85f, 1.9f, 1.1f), Sec(9.95f, 2.7f, 1.9f, 0.9f),
        )
        b.hullShell(secs, intArrayOf(blue, blue, red, red, red, red), deck, white, inner, bulwark = 0.95f)
        b.transom(secs.last(), Col.mul(blue, 0.85f), true)
        b.waterline(secs, 0.12f, 0.0f, 0.0f)
        // the covered foredeck with the wheelhouse on it
        b.push().translate(0f, 2.42f, -7.5f).box(5.6f, 0.2f, 4.9f, rgb(0x8B9690), rgb(0x8B9690), rgb(0x8B9690), rgb(0x8B9690)).pop()
        b.push().translate(0f, 4.0f, -5.2f).box(4.4f, 3.0f, 3.6f, white, white, Col.mul(white, 0.88f), white).pop()
        b.push().translate(0f, 5.65f, -5.2f).box(4.9f, 0.3f, 4.1f, rgb(0xD9DEE3)).pop() // roof, overhanging
        b.push().translate(0f, 4.55f, -7.02f).box(4.0f, 0.95f, 0.08f, rgb(0x16283E)).pop() // front windows
        for (side in intArrayOf(-1, 1)) b.push().translate(side * 2.22f, 4.55f, -5.2f).box(0.08f, 0.95f, 3.0f, rgb(0x16283E)).pop()
        b.push().translate(0f, 4.55f, -3.37f).box(3.4f, 0.95f, 0.08f, rgb(0x16283E)).pop()
        b.push().translate(0f, 3.0f, -3.37f).box(3.6f, 0.12f, 0.12f, orange).pop() // a stripe round the house
        // funnel, mast and radar on the roof
        b.push().translate(0.0f, 6.5f, -4.0f).cylinder(0.42f, 0.5f, 1.6f, 8, red, red, smooth = true).pop()
        b.push().translate(0.0f, 7.25f, -4.0f).cylinder(0.5f, 0.42f, 0.18f, 8, rgb(0x1E2024), rgb(0x1E2024), smooth = true).pop()
        b.limb(0f, 5.8f, -6.3f, 0f, 9.8f, -6.3f, 0.09f, 0.05f, 5, rgb(0xCBD3DC), rgb(0xCBD3DC))
        b.push().translate(0f, 9.0f, -6.3f).box(1.7f, 0.12f, 0.3f, rgb(0xEEF1F4)).pop()
        b.push().translate(0f, 9.8f, -6.3f).sphere(0.16f, 3, 6, rgb(0xFF3B2F), rgb(0xFF3B2F), smooth = true).pop()
        // lifebuoys on the house
        for (side in intArrayOf(-1, 1)) b.push().translate(side * 2.27f, 4.1f, -4.3f).rotateY(90f).torus(0.34f, 0.09f, 12, 5, orange, white).pop()
        // the net drum, a crate stack and a heap of yellow floats on the working deck
        b.push().translate(0f, 2.15f, 1.6f).rotateZ(90f).cylinder(0.62f, 0.62f, 3.8f, 10, orange, Col.mul(orange, 0.85f), smooth = true).pop()
        for (side in intArrayOf(-1, 1)) b.push().translate(side * 2.0f, 1.45f, 1.6f).box(0.3f, 1.0f, 1.3f, rgb(0x59626B)).pop()
        b.push().translate(-1.3f, 1.95f, -1.6f).box(1.2f, 0.9f, 1.3f, rgb(0xD6A24B)).pop()
        b.push().translate(-1.3f, 2.85f, -1.5f).box(1.0f, 0.9f, 1.1f, rgb(0xB5833A)).pop()
        val rng = Rng(515L)
        for (k in 0 until 9) {
            val gx = (k % 3 - 1) * 0.78f + (rng.float() - 0.5f) * 0.2f
            val gz = 5.4f + (k / 3) * 0.78f + (rng.float() - 0.5f) * 0.2f
            val gy = 1.35f + 0.42f + (if (k % 2 == 0) 0f else 0.1f)
            b.push().translate(gx, gy, gz).sphere(0.42f, 4, 7, rgb(0xFFE03A), rgb(0xFFC21A), smooth = true).pop()
        }
        for (k in 0 until 3) b.push().translate((k - 1) * 0.8f, 2.05f, 5.9f).sphere(0.42f, 4, 7, rgb(0xFF8A1F), rgb(0xE86A10), smooth = true).pop()
        b.push().translate(0.9f, 1.55f, 4.3f).scale(1.1f, 0.5f, 1.2f).icosphere(0.8f, 1, rgb(0x2E6A52), 0.12f, 3, 0.1f).pop() // net heap
        // the A-frame gantry over the stern: two legs, a cross-bar, a block and a cable
        val gantry = rgb(0xF2791E)
        for (side in intArrayOf(-1, 1)) {
            b.limb(side * 2.55f, 1.2f, 8.4f, side * 0.45f, 8.7f, 9.3f, 0.2f, 0.16f, 5, gantry, gantry)
        }
        b.beam(-1.9f, 4.4f, 8.78f, 1.9f, 4.4f, 8.78f, 0.22f, 0.22f, gantry)
        b.beam(-0.5f, 8.7f, 9.3f, 0.5f, 8.7f, 9.3f, 0.3f, 0.3f, rgb(0x2B3036))
        b.limb(0f, 8.6f, 9.3f, 0f, 6.3f, 9.3f, 0.04f, 0.04f, 4, rgb(0x2B3036))
        b.push().translate(0f, 6.1f, 9.3f).box(0.34f, 0.5f, 0.3f, rgb(0xFFC21A)).pop()
        b.limb(0f, 8.7f, 9.3f, 0f, 2.6f, 1.6f, 0.03f, 0.03f, 4, rgb(0x3B4048))
        // rails along the stern, a stern light
        b.push().translate(0f, 2.55f, 9.82f).box(5.0f, 0.1f, 0.1f, white).pop()
        for (side in intArrayOf(-1, 1)) b.push().translate(side * 2.4f, 2.2f, 9.85f).box(0.1f, 0.6f, 0.1f, white).pop()
    }

    private val CONTAINER_COLORS = intArrayOf(
        rgb(0xC9362C), rgb(0x2F73C4), rgb(0xE98A24), rgb(0x2FA85E), rgb(0xEBC52A), rgb(0xD5DADF), rgb(0x1FA7A8), rgb(0x8D4B2F), rgb(0x7B3FA0),
    )

    private fun containerShip(b: MeshBuilder) {
        val dark = rgb(0x28323F)
        val red = rgb(0xB9312A)
        val white = rgb(0xF1F3F4)
        val deck = rgb(0x5E6A63)
        val secs = listOf(
            Sec(-27.9f, 0.00f, 5.4f, 0.00f), Sec(-27.0f, 1.1f, 4.7f, 1.2f), Sec(-24.0f, 2.5f, 4.0f, 1.9f), Sec(-20.0f, 3.8f, 3.6f, 2.3f),
            Sec(-14.0f, 4.6f, 3.45f, 2.5f), Sec(-6.0f, 4.75f, 3.4f, 2.5f), Sec(6.0f, 4.75f, 3.4f, 2.5f), Sec(14.0f, 4.75f, 3.4f, 2.5f),
            Sec(20.0f, 4.55f, 3.4f, 2.4f), Sec(24.0f, 4.15f, 3.4f, 2.0f), Sec(27.0f, 3.7f, 3.4f, 1.6f), Sec(27.9f, 3.4f, 3.4f, 1.3f),
        )
        b.hullShell(secs, intArrayOf(dark, dark, red, red, red, red), deck, rgb(0x3A4553), Col.mul(dark, 1.2f), bulwark = 0.9f, rail = 0.14f)
        b.transom(secs.last(), Col.mul(dark, 0.9f), true)
        b.waterline(secs, 0.22f, 0.0f, 0.0f)
        val deckY = 3.4f - 0.9f // the working deck amidships
        fun deckAt(z: Float): Float { // the deck rises toward the bow with the sheer line
            for (k in 0 until secs.size - 1) if (z >= secs[k].z && z <= secs[k + 1].z) return lerp(secs[k].h, secs[k + 1].h, (z - secs[k].z) / (secs[k + 1].z - secs[k].z)) - 0.9f
            return deckY
        }
        // the bow: anchor winches, a mast with a flag
        for (k in 0 until 2) b.push().translate(-1.1f + k * 2.2f, deckAt(-25.4f) + 0.4f, -25.4f).cylinder(0.42f, 0.48f, 0.8f, 8, rgb(0xF08A24), Col.mul(rgb(0xF08A24), 0.8f), smooth = true).pop()
        b.push().translate(0f, deckAt(-24.2f) + 0.25f, -24.2f).box(2.6f, 0.5f, 1.2f, rgb(0x59626B)).pop()
        b.limb(0f, deckAt(-23.0f), -23.0f, 0f, 7.6f, -23.0f, 0.12f, 0.07f, 5, white, white)
        b.sheet(V(0f, 7.6f, -23.0f), V(0f, 7.6f, -22.2f), V(0f, 6.9f, -22.2f), V(0f, 6.9f, -23.0f), rgb(0xD83A2E))
        // containers: six bays of three columns, one to four high, colours from a seeded hash
        val rng = Rng(2024L)
        val bayZ = floatArrayOf(-19.0f, -12.8f, -6.6f, -0.4f, 5.8f, 12.0f)
        val colX = floatArrayOf(-2.9f, 0f, 2.9f)
        for (bay in bayZ.indices) for (col in 0 until 3) {
            val maxH = if (bay == 0) 2 else if (bay == 5) 3 else 4
            val high = 1 + rng.int(maxH) + if (col == 1 && bay in 1..4) 1 else 0
            for (lvl in 0 until minOf(high, maxH)) {
                val c = CONTAINER_COLORS[rng.int(CONTAINER_COLORS.size)]
                val shade = 0.9f + rng.float() * 0.2f
                val cc = Col.mul(c, shade)
                b.push().translate(colX[col], deckY + 1.3f + lvl * 2.6f, bayZ[bay]).box(2.68f, 2.56f, 6.0f, Col.mul(cc, 1.12f), cc, Col.mul(cc, 0.82f), Col.mul(cc, 0.6f)).pop()
                // the door end of each container (the stern-facing side) in a darker shade, with a lock bar
                b.push().translate(colX[col], deckY + 1.3f + lvl * 2.6f, bayZ[bay] + 3.01f).box(2.4f, 2.3f, 0.04f, Col.mul(cc, 0.72f)).pop()
            }
        }
        // the white bridge tower at the stern: three decks, a wide bridge, a red funnel, a radar mast
        b.push().translate(0f, 6.4f, 18.4f).box(8.0f, 6.0f, 6.6f, white, white, Col.mul(white, 0.9f), white).pop()
        b.push().translate(0f, 10.2f, 17.9f).box(7.2f, 2.4f, 5.4f, white, white, Col.mul(white, 0.92f), white).pop()
        b.push().translate(0f, 12.0f, 17.4f).box(9.2f, 1.3f, 3.6f, white, white, Col.mul(white, 0.9f), white).pop() // bridge with its wings
        b.push().translate(0f, 12.05f, 19.22f).box(8.8f, 0.7f, 0.06f, rgb(0x14283C)).pop() // bridge windows, aft
        b.push().translate(0f, 12.05f, 15.58f).box(8.8f, 0.7f, 0.06f, rgb(0x14283C)).pop() // bridge windows, forward
        for (k in 0 until 3) b.push().translate(0f, 4.4f + k * 1.8f, 21.74f).box(6.8f, 0.7f, 0.06f, rgb(0x2A3B52)).pop() // window rows, aft
        for (k in 0 until 2) b.push().translate(0f, 10.0f + k * 0.0f, 20.64f).box(5.9f, 0.6f, 0.06f, rgb(0x2A3B52)).pop()
        b.push().translate(0f, 12.85f, 18.6f).cylinder(1.3f, 1.5f, 2.3f, 8, red, Col.mul(red, 0.85f), smooth = true).pop()
        b.push().translate(0f, 13.9f, 18.6f).cylinder(1.3f, 1.3f, 0.2f, 8, rgb(0x1E2024), rgb(0x1E2024), smooth = true).pop()
        b.limb(0f, 12.65f, 16.4f, 0f, 14.0f, 16.4f, 0.1f, 0.06f, 5, white, white)
        b.push().translate(0f, 13.55f, 16.4f).box(2.2f, 0.12f, 0.35f, rgb(0xD9DEE3)).pop()
        // the stern deck: a free-fall lifeboat on its slip, bollards and rails
        b.push().translate(0f, 4.0f, 24.8f).rotateX(-14f).scale(1.0f, 0.9f, 2.6f).sphere(1f, 4, 10, rgb(0xFF7A1A), rgb(0xE05A0A), smooth = true).pop()
        b.push().translate(0f, 3.75f, 24.7f).rotateX(-14f).box(1.6f, 0.15f, 4.4f, rgb(0x59626B)).pop()
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 3.6f, deckY + 0.6f, 24.5f).cylinder(0.25f, 0.3f, 0.7f, 6, rgb(0x2B3036), rgb(0x2B3036), smooth = false).pop()
            b.push().translate(side * 4.0f, deckY + 0.55f, 21.0f).box(0.12f, 1.1f, 0.12f, white).pop()
        }
        b.push().translate(0f, 3.15f + 0.4f, 27.0f).box(7.0f, 0.1f, 0.1f, white).pop()
    }

    // ---------------------------------------------------------------------------------------------------------
    // flocks of gulls and terns (FLOCK_0..3): about nine birds in a loose spiral inside a 3.4 m sphere
    // ---------------------------------------------------------------------------------------------------------

    private class Bird(val back: Int, val belly: Int, val wing: Int, val tip: Int, val cap: Int, val bill: Int, val span: Float, val sweep: Float, val fork: Float)

    /** One bird at the origin, flying toward -Z: a body, a head with a bill, a tail and two bent wings (the wings are double sided sheets). */
    private fun bird(b: MeshBuilder, k: Bird) {
        val s = k.span / 1.7f
        b.push().scale(s)
        // body and head
        b.push().scale(0.12f, 0.105f, 0.32f).sphere(1f, 4, 7, k.back, k.belly, smooth = true).pop()
        b.push().translate(0f, 0.035f, -0.31f).sphere(0.085f, 3, 6, k.cap, k.cap, smooth = true).pop()
        b.push().translate(0f, 0.025f, -0.42f).rotateX(-90f).cone(0.03f, 0.12f, 4, k.bill, k.bill, smooth = false, cap = false).pop()
        // the tail: a forked wedge
        val tc = k.wing
        b.sheet(V(-0.05f, 0.02f, 0.24f), V(0.05f, 0.02f, 0.24f), V(0.05f + k.fork * 0.07f, 0.01f, 0.58f), V(-0.05f - k.fork * 0.07f, 0.01f, 0.58f), tc)
        if (k.fork > 0.2f) b.sheet3(V(-0.05f, 0.02f, 0.4f), V(0.05f, 0.02f, 0.4f), V(0f, 0.02f, 0.5f), Col.mul(tc, 0.7f))
        // the wings: the arm rises to the wrist, the hand droops to the tip, swept back
        for (side in floatArrayOf(-1f, 1f)) {
            val sh0 = V(side * 0.08f, 0.04f, -0.22f); val sh1 = V(side * 0.08f, 0.0f, 0.16f)
            val wr0 = V(side * 0.55f, 0.20f, -0.22f + k.sweep * 0.10f); val wr1 = V(side * 0.52f, 0.17f, 0.02f + k.sweep * 0.10f)
            val tp0 = V(side * 0.86f, 0.05f, -0.04f + k.sweep * 0.32f); val tp1 = V(side * 0.80f, 0.02f, 0.11f + k.sweep * 0.30f)
            val mid0 = V(lerp(wr0.x, tp0.x, 0.55f), lerp(wr0.y, tp0.y, 0.55f), lerp(wr0.z, tp0.z, 0.55f))
            val mid1 = V(lerp(wr1.x, tp1.x, 0.55f), lerp(wr1.y, tp1.y, 0.55f), lerp(wr1.z, tp1.z, 0.55f))
            b.sheet(sh0, wr0, wr1, sh1, Col.mix(k.back, k.wing, 0.5f), k.wing, k.wing, k.belly)
            b.sheet(wr0, mid0, mid1, wr1, k.wing)
            b.sheet(mid0, tp0, tp1, mid1, k.tip)
        }
        b.pop()
    }

    private fun flock(b: MeshBuilder, variant: Int) {
        val gullWhite = Bird(rgb(0xDDE3E8), rgb(0xFFFFFF), rgb(0xFFFFFF), rgb(0x16181C), rgb(0xFFFFFF), rgb(0xFFB020), 1.75f, 0.0f, 0.0f)
        val gullGrey = Bird(rgb(0xB9C2CC), rgb(0xF8F9FA), rgb(0xF1F3F5), rgb(0x16181C), rgb(0xFFFFFF), rgb(0xFFC030), 1.6f, 0.2f, 0.0f)
        val ternDark = Bird(rgb(0x424956), rgb(0xE8EBEE), rgb(0x586070), rgb(0x20242C), rgb(0x14161A), rgb(0xFF5A2A), 1.65f, 0.55f, 1.0f)
        val ternSlate = Bird(rgb(0x4F5E73), rgb(0xF2F4F6), rgb(0x6B7A90), rgb(0x1B2028), rgb(0x14161A), rgb(0xFF7A2A), 1.5f, 0.45f, 1.0f)
        val kind = arrayOf(gullWhite, gullGrey, ternDark, ternSlate)[variant]
        val count = intArrayOf(9, 8, 10, 9)[variant]
        val turns = floatArrayOf(1.6f, 1.25f, 2.1f, 1.45f)[variant]
        val rng = Rng(3300L + variant * 7L)
        for (i in 0 until count) {
            val f = (i + 0.5f) / count
            val ang = (f * turns * TAU) + variant * 0.9f
            val r = 0.3f + 1.5f * f.pow(0.8f) * (0.85f + 0.2f * rng.float())
            val y = (f - 0.5f) * 1.8f * (0.8f + 0.4f * rng.float()) + (rng.float() - 0.5f) * 0.3f
            val x = cos(ang) * r
            val z = sin(ang) * r
            val size = 0.88f + 0.22f * rng.float()
            b.push().translate(x, y, z).rotateY(180f - ang * R2D).rotateZ(14f + 10f * rng.float()).rotateX((rng.float() - 0.5f) * 8f).scale(size)
            bird(b, kind)
            b.pop()
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // rival seaplanes (SEAPLANE_0..2): a high-wing floatplane, centred in OceanSpec.SEAPLANE_HALF, nose toward -Z
    // ---------------------------------------------------------------------------------------------------------

    private fun seaplane(b: MeshBuilder, body: Int, accent: Int, trim: Int) {
        val dark = rgb(0x2A2D34)
        val glass = rgb(0x2C4A66)
        val steel = rgb(0xB8C0CA)
        // fuselage, nose cowling and tail boom
        b.push().translate(0f, 0.02f, -0.15f).scale(0.42f, 0.38f, 1.95f).sphere(1f, 5, 10, Col.mul(body, 1.08f), Col.mul(body, 0.82f), smooth = true).pop()
        b.push().translate(0f, 0.05f, -1.95f).rotateX(-90f).cylinder(0.36f, 0.4f, 0.55f, 10, dark, dark, smooth = true, capTop = false, capBottom = false).pop()
        b.push().translate(0f, 0.05f, -2.2f).rotateX(-90f).cone(0.19f, 0.34f, 10, accent, accent, smooth = true, cap = false).pop()
        b.limb(0f, 0.12f, 1.2f, 0f, 0.22f, 2.5f, 0.28f, 0.08f, 8, body, Col.mul(body, 0.9f), smooth = true)
        // cabin glass
        b.push().translate(0f, 0.3f, -0.55f).scale(0.34f, 0.26f, 0.7f).sphere(1f, 4, 8, rgb(0x9CC6E8), glass, smooth = true, latFromDeg = 0f, latToDeg = 90f).pop()
        for (side in intArrayOf(-1, 1)) b.push().translate(side * 0.405f, 0.17f, -0.55f).box(0.05f, 0.2f, 0.9f, glass).pop()
        // the high wing, with coloured tips and a stripe along the front edge
        b.push().translate(0f, 0.5f, -0.35f).box(3.2f, 0.1f, 0.95f, body, body, Col.mul(body, 0.8f), Col.mul(body, 0.7f)).pop()
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 1.72f, 0.5f, -0.35f).box(0.56f, 0.1f, 0.95f, accent, accent, Col.mul(accent, 0.8f), Col.mul(accent, 0.7f)).pop()
            b.push().translate(side * 1.0f, 0.555f, -0.7f).box(1.5f, 0.02f, 0.14f, trim).pop()
            b.push().translate(side * 1.99f, 0.5f, -0.35f).box(0.06f, 0.1f, 0.25f, if (side < 0) rgb(0xFF2A2A) else rgb(0x2AFF6A)).pop() // wing tip lights
        }
        // wing struts
        for (side in intArrayOf(-1, 1)) {
            b.beam(side * 0.3f, -0.05f, -0.1f, side * 1.3f, 0.46f, -0.2f, 0.07f, 0.04f, steel)
            b.beam(side * 0.3f, -0.05f, -0.6f, side * 1.3f, 0.46f, -0.5f, 0.07f, 0.04f, steel)
        }
        b.push().translate(0f, 0.42f, -0.35f).box(0.5f, 0.2f, 0.6f, body).pop()
        // tail: fin and rudder, tail plane
        b.push().translate(0f, 0.06f, 0f).prismZY(floatArrayOf(1.85f, 0.1f, 2.55f, 0.1f, 2.48f, 0.62f, 2.2f, 0.56f), 0.07f, accent, accent, accent).pop()
        b.push().translate(0f, 0.22f, 2.2f).box(1.7f, 0.05f, 0.6f, body, body, Col.mul(body, 0.8f), Col.mul(body, 0.7f)).pop()
        for (side in intArrayOf(-1, 1)) b.push().translate(side * 0.88f, 0.22f, 2.26f).box(0.16f, 0.055f, 0.5f, accent).pop()
        // two floats on struts
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 0.95f, -0.53f, 0f).scale(0.2f, 0.16f, 1.62f).sphere(1f, 4, 10, Col.mul(trim, 1.0f), Col.mul(trim, 0.7f), smooth = true).pop()
            b.push().translate(side * 0.95f, -0.52f, -1.45f).rotateX(-28f).scale(0.16f, 0.14f, 0.3f).sphere(1f, 3, 6, trim, trim, smooth = true).pop()
            b.push().translate(side * 0.95f, -0.405f, 0f).box(0.05f, 0.04f, 2.3f, accent).pop() // a stripe along the deck of the float
            b.beam(side * 0.2f, -0.18f, -0.65f, side * 0.93f, -0.42f, -0.75f, 0.06f, 0.05f, steel)
            b.beam(side * 0.2f, -0.18f, 0.75f, side * 0.93f, -0.42f, 0.85f, 0.06f, 0.05f, steel)
        }
        // the propeller: two blades and a spinner
        for (a in floatArrayOf(25f, 205f)) b.push().translate(0f, 0.05f, -2.3f).rotateZ(a).translate(0f, 0.3f, 0f).box(0.13f, 0.62f, 0.035f, dark).pop()
        b.push().translate(0f, 0.05f, -2.24f).sphere(0.09f, 3, 6, accent, accent, smooth = true).pop()
    }

    // ---------------------------------------------------------------------------------------------------------
    // more tools: plates, leaves, torus arcs, coloured spheres
    // ---------------------------------------------------------------------------------------------------------

    /** A thin convex plate: the polygon [pts] (all in one plane with the normal ([nx], [ny], [nz])) thickened by [th], with colours for its two faces and its rim. */
    private fun MeshBuilder.slab(pts: List<V>, nx: Float, ny: Float, nz: Float, th: Float, top: Int, bottom: Int = top, edge: Int = top) {
        val n = pts.size
        val l = sqrt(nx * nx + ny * ny + nz * nz)
        val ux = nx / l; val uy = ny / l; val uz = nz / l
        val h = th / 2f
        val up = pts.map { V(it.x + ux * h, it.y + uy * h, it.z + uz * h) }
        val dn = pts.map { V(it.x - ux * h, it.y - uy * h, it.z - uz * h) }
        val c = centreOf(pts.toTypedArray())
        val above = V(c.x + ux, c.y + uy, c.z + uz)
        val below = V(c.x - ux, c.y - uy, c.z - uz)
        for (i in 1 until n - 1) {
            triOut(below, up[0], up[i], up[i + 1], top)
            triOut(above, dn[0], dn[i], dn[i + 1], bottom)
        }
        for (i in 0 until n) {
            val j = (i + 1) % n
            triOut(c, up[i], dn[i], dn[j], edge)
            triOut(c, up[i], dn[j], up[j], edge)
        }
    }

    /**
     * A long leaf (palm frond, kelp blade) of [segs] segments from [base] along the unit vector [dir], [side] across it, widest
     * in the middle and pointed at both ends, bent so that the tip is displaced by [bend], folded into a shallow V.
     */
    private fun MeshBuilder.leaf(
        base: V, dir: V, side: V, length: Float, width: Float, bend: V, segs: Int, fold: Float,
        cMid: Int, cEdge: Int, cTip: Int, lift: Float = 0.8f,
    ) {
        val c = Array(segs + 1) { k ->
            val s = k.toFloat() / segs
            V(base.x + dir.x * length * s + bend.x * s * s, base.y + dir.y * length * s + bend.y * s * s, base.z + dir.z * length * s + bend.z * s * s)
        }
        val w = FloatArray(segs + 1) { k -> width * sin(PI.toFloat() * (k.toFloat() / segs).pow(0.75f)).coerceAtLeast(0f) }
        val l = Array(segs + 1) { k -> V(c[k].x - side.x * w[k], c[k].y - side.y * w[k] - fold * w[k], c[k].z - side.z * w[k]) }
        val r = Array(segs + 1) { k -> V(c[k].x + side.x * w[k], c[k].y + side.y * w[k] - fold * w[k], c[k].z + side.z * w[k]) }
        for (k in 0 until segs) {
            val s0 = k.toFloat() / segs; val s1 = (k + 1f) / segs
            val m0 = Col.mix(cMid, cTip, s0); val m1 = Col.mix(cMid, cTip, s1)
            val e0 = Col.mix(cEdge, cTip, s0); val e1 = Col.mix(cEdge, cTip, s1)
            sheetLit(c[k], r[k], r[k + 1], c[k + 1], lift, m0, e0, e1, m1)
            sheetLit(c[k], c[k + 1], l[k + 1], l[k], lift, m0, m1, e1, e0)
        }
    }

    private fun unit(x: Float, y: Float, z: Float): V {
        val l = sqrt(x * x + y * y + z * z)
        return V(x / l, y / l, z / l)
    }

    /** A piece of a torus whose axis is along Z (a ring in the XY plane) from angle [u0] to [u1], smooth shaded; the outer side takes [color], the inner [colorInner]. */
    private fun MeshBuilder.torusArc(major: Float, minor: Float, u0: Float, u1: Float, uSteps: Int, vSteps: Int, color: Int, colorInner: Int = color) {
        fun px(u: Float, v: Float) = (major + minor * cos(v)) * cos(u)
        fun py(u: Float, v: Float) = (major + minor * cos(v)) * sin(u)
        fun pz(v: Float) = minor * sin(v)
        for (i in 0 until uSteps) {
            val ua = lerp(u0, u1, i.toFloat() / uSteps); val ub = lerp(u0, u1, (i + 1f) / uSteps)
            for (j in 0 until vSteps) {
                val va = TAU * j / vSteps; val vb = TAU * (j + 1) / vSteps
                val ca = Col.mix(colorInner, color, 0.5f + 0.5f * cos(va)); val cb = Col.mix(colorInner, color, 0.5f + 0.5f * cos(vb))
                // A = (ua, va), B = (ub, va), C = (ub, vb), D = (ua, vb)
                triSmooth(
                    px(ua, va), py(ua, va), pz(va), cos(va) * cos(ua), cos(va) * sin(ua), sin(va),
                    px(ub, va), py(ub, va), pz(va), cos(va) * cos(ub), cos(va) * sin(ub), sin(va),
                    px(ub, vb), py(ub, vb), pz(vb), cos(vb) * cos(ub), cos(vb) * sin(ub), sin(vb), ca, ca, cb,
                )
                triSmooth(
                    px(ua, va), py(ua, va), pz(va), cos(va) * cos(ua), cos(va) * sin(ua), sin(va),
                    px(ub, vb), py(ub, vb), pz(vb), cos(vb) * cos(ub), cos(vb) * sin(ub), sin(vb),
                    px(ua, vb), py(ua, vb), pz(vb), cos(vb) * cos(ua), cos(vb) * sin(ua), sin(vb), ca, cb, cb,
                )
            }
        }
    }

    /** A smooth UV sphere whose colour comes from a function of latitude (-pi/2..pi/2) and longitude (0..2 pi). */
    private fun MeshBuilder.colorSphere(r: Float, lat: Int, lon: Int, color: (Float, Float) -> Int) {
        val half = PI.toFloat() / 2f
        for (i in 0 until lat) {
            val fa = -half + PI.toFloat() * i / lat; val fb = -half + PI.toFloat() * (i + 1) / lat
            for (j in 0 until lon) {
                val ga = TAU * j / lon; val gb = TAU * (j + 1) / lon
                val ax = cos(fa) * cos(ga); val ay = sin(fa); val az = cos(fa) * sin(ga)
                val bx = cos(fa) * cos(gb); val bz = cos(fa) * sin(gb)
                val cx = cos(fb) * cos(gb); val cy = sin(fb); val cz = cos(fb) * sin(gb)
                val dx = cos(fb) * cos(ga); val dz = cos(fb) * sin(ga)
                val poleA = abs(abs(fa) - half) < 1e-4f
                val poleB = abs(abs(fb) - half) < 1e-4f
                val ca = color(fa, ga); val cb = color(fa, gb); val cc = color(fb, gb); val cd = color(fb, ga)
                if (!poleB) triSmooth(ax * r, ay * r, az * r, ax, ay, az, dx * r, cy * r, dz * r, dx, cy, dz, cx * r, cy * r, cz * r, cx, cy, cz, ca, cd, cc)
                if (!poleA) triSmooth(ax * r, ay * r, az * r, ax, ay, az, cx * r, cy * r, cz * r, cx, cy, cz, bx * r, ay * r, bz * r, bx, ay, bz, ca, cc, cb)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // islands (ISLAND_0..2): standing on the waterline, with a pale turquoise shallow disc around them at y = 0.3
    // ---------------------------------------------------------------------------------------------------------

    /** The outline of an island: a polar shape ([rx], [rz]) made lumpy by three sines, optionally with a bay bitten into it. */
    private class Shape(
        val rx: Float, val rz: Float, val a1: Float, val p1: Float, val a2: Float, val p2: Float, val a3: Float, val p3: Float,
        val bite: Float = 0f, val biteAt: Float = 0f,
    ) {
        fun k(theta: Float): Float {
            var w = 1f + a1 * sin(2f * theta + p1) + a2 * sin(3f * theta + p2) + a3 * sin(5f * theta + p3)
            if (bite > 0f) {
                val d = atan2(sin(theta - biteAt), cos(theta - biteAt))
                w *= 1f - bite * exp(-(d / 0.5f) * (d / 0.5f))
            }
            return w
        }

        fun at(theta: Float, scale: Float, y: Float) = V(cos(theta) * rx * k(theta) * scale, y, sin(theta) * rz * k(theta) * scale)
    }

    private val SAND = rgb(0xF4E5B4)
    private val SAND_WET = rgb(0xCDB57E)
    private val PALM_A = rgb(0x2F9440)
    private val PALM_B = rgb(0x1F7432)
    private val PALM_TIP = rgb(0x7CCB52)

    /** A pale turquoise disc around an island that fades into sea blue, flat at [y]. */
    private fun shallows(b: MeshBuilder, sh: Shape, y: Float, outer: Float, n: Int = 40) {
        val scales = floatArrayOf(0.8f, 1.0f, lerp(1f, outer, 0.35f), lerp(1f, outer, 0.7f), outer)
        val cols = intArrayOf(rgb(0xB9F7E6), rgb(0xA3F3E3), rgb(0x6EE5DB), rgb(0x40C8D4), rgb(0x2AA2CE))
        val ref = V(0f, -5f, 0f)
        for (k in 0 until scales.size - 1) for (i in 0 until n) {
            val a0 = TAU * i / n; val a1 = TAU * (i + 1) / n
            val p00 = sh.at(a0, scales[k], y); val p01 = sh.at(a1, scales[k], y)
            val p10 = sh.at(a0, scales[k + 1], y); val p11 = sh.at(a1, scales[k + 1], y)
            b.triOut(ref, p00, p10, p11, cols[k], cols[k + 1], cols[k + 1])
            b.triOut(ref, p00, p11, p01, cols[k], cols[k + 1], cols[k])
        }
    }

    /** The beach: wet sand at the water, dry sand above, as rings; the surf is a white line along the edge. */
    private fun beach(b: MeshBuilder, rng: Rng, sh: Shape, n: Int = 40) {
        val sc = floatArrayOf(1.03f, 0.99f, 0.87f, 0.70f)
        val ys = floatArrayOf(0.10f, 0.42f, 0.84f, 0.98f)
        val cs = intArrayOf(SAND_WET, Col.mix(SAND_WET, SAND, 0.6f), SAND, Col.mul(SAND, 1.02f))
        val rings = ArrayList<Array<V>>()
        for (k in sc.indices) rings.add(Array(n) { i -> sh.at(TAU * i / n, sc[k], ys[k]) })
        val noise = FloatArray(sc.size * n) { 0.94f + 0.12f * rng.float() }
        b.loftFlat(rings, { k, i -> Col.mul(cs[k], noise[k * n + i]) }, capEnd = SAND)
        val ref = V(0f, -5f, 0f)
        val edge = Col.mix(FOAM, rgb(0x9FE9E8), 0.6f)
        for (i in 0 until n) {
            val a0 = TAU * i / n; val a1 = TAU * (i + 1) / n
            val p00 = sh.at(a0, 0.985f, 0.34f); val p01 = sh.at(a1, 0.985f, 0.34f)
            val p10 = sh.at(a0, 1.075f, 0.34f); val p11 = sh.at(a1, 1.075f, 0.34f)
            b.triOut(ref, p00, p10, p11, FOAM, edge, edge); b.triOut(ref, p00, p11, p01, FOAM, edge, FOAM)
        }
    }

    /** A lumpy green hill, an elliptical dome of [rows] rings standing on [y0]. */
    private fun hill(b: MeshBuilder, rng: Rng, cx: Float, cz: Float, rx: Float, rz: Float, h: Float, y0: Float, low: Int, high: Int, n: Int = 16, rows: Int = 5) {
        val phase = rng.float() * TAU
        val rings = ArrayList<Array<V>>()
        for (k in 0..rows) {
            val ph = (k.toFloat() / rows) * PI.toFloat() / 2f
            val sr = cos(ph)
            val y = y0 + h * sin(ph).pow(0.85f)
            rings.add(Array(n) { i ->
                val a = TAU * i / n
                val wob = 1f + 0.13f * sin(3f * a + phase) + 0.08f * sin(5f * a + phase * 1.7f) + 0.07f * (rng.float() - 0.5f)
                V(cx + cos(a) * rx * sr * wob, y + (if (k < rows) 0.35f * (rng.float() - 0.5f) else 0f), cz + sin(a) * rz * sr * wob)
            })
        }
        val noise = FloatArray(rows * n) { 0.88f + 0.24f * rng.float() }
        b.loftFlat(rings, { k, i ->
            val t = (k + 0.5f) / rows
            var c = Col.mix(low, high, t.pow(0.8f))
            if (noise[k * n + i] < 0.93f) c = Col.mix(c, rgb(0x1F6B31), 0.45f) // dark jungle patches
            Col.mul(c, 0.9f + 0.15f * noise[k * n + i])
        }, capEnd = high)
    }

    private fun palm(b: MeshBuilder, rng: Rng, x: Float, y: Float, z: Float, h: Float, leanYaw: Float, lean: Float) {
        val trunkA = rgb(0xA27B4C)
        val trunkB = rgb(0x70533A)
        val n = 6
        val pts = Array(n + 1) { k ->
            val t = k.toFloat() / n
            V(x + cos(leanYaw) * lean * h * t * t, y + h * t, z + sin(leanYaw) * lean * h * t * t)
        }
        for (k in 0 until n) {
            val r0 = 0.36f - 0.17f * k / n; val r1 = 0.36f - 0.17f * (k + 1f) / n
            b.limb(pts[k].x, pts[k].y, pts[k].z, pts[k + 1].x, pts[k + 1].y, pts[k + 1].z, r0, r1, 5, if (k % 2 == 0) trunkA else trunkB)
        }
        val top = pts[n]
        val fronds = 8
        val phase = rng.float() * TAU
        for (f in 0 until fronds) {
            val a = phase + TAU * f / fronds + (rng.float() - 0.5f) * 0.35f
            val len = 3.2f + rng.float() * 1.1f
            val dir = unit(cos(a) * 0.93f, 0.34f + 0.2f * (f % 2), sin(a) * 0.93f)
            val side = V(-sin(a), 0f, cos(a))
            b.leaf(V(top.x, top.y + 0.05f, top.z), dir, side, len, 0.62f, V(0f, -len * (0.55f + 0.15f * rng.float()), 0f), 2, 0.35f, PALM_A, PALM_B, PALM_TIP)
        }
        for (k in 0 until 3) {
            val a = phase + k * 2.1f
            b.push().translate(top.x + cos(a) * 0.28f, top.y - 0.25f, top.z + sin(a) * 0.28f).sphere(0.21f, 3, 5, rgb(0x6B4A2A), rgb(0x4A3320), smooth = true).pop()
        }
    }

    private fun rock(b: MeshBuilder, x: Float, y: Float, z: Float, r: Float, seed: Int, color: Int) {
        b.push().translate(x, y + 0.55f * r, z).scale(1f, 0.7f, 0.9f).icosphere(r, 1, color, 0.3f, seed, 0.12f).pop()
    }

    private fun island(b: MeshBuilder, variant: Int) {
        val rng = Rng(5100L + variant * 13L)
        val grey = rgb(0x8F897C)
        when (variant) {
            0 -> {
                // a small round cay: one low hill, a ring of palms and rocks at the water
                val sh = Shape(15.0f, 14.5f, 0.07f, 0.4f, 0.05f, 1.3f, 0.03f, 2.1f)
                shallows(b, sh, 0.3f, 1.42f)
                beach(b, rng, sh)
                hill(b, rng, -1.0f, 0.8f, 7.6f, 6.6f, 3.3f, 0.8f, rgb(0x3A9B3F), rgb(0x78CC52))
                val spots = listOf(0.3f to 6.5f, 1.45f to 5.5f, 2.5f to 6.8f, 3.7f to 5.8f, 5.0f to 7.0f)
                for ((a, h) in spots) { val p = sh.at(a, 0.68f, 0.95f); palm(b, rng, p.x, 0.9f, p.z, h, a + 1.2f, 0.16f + 0.1f * rng.float()) }
                for (k in 0 until 4) { val p = sh.at(0.8f + k * 1.7f, 0.97f, 0.3f); rock(b, p.x, 0.4f, p.z, 0.8f + 0.5f * rng.float(), k, grey) }
            }
            1 -> {
                // a long island with a high hill at one end and a low one at the other
                val sh = Shape(21.0f, 13.5f, 0.06f, 1.1f, 0.06f, 0.2f, 0.035f, 2.6f)
                shallows(b, sh, 0.3f, 1.3f)
                beach(b, rng, sh)
                hill(b, rng, -8.0f, 0.5f, 9.4f, 7.2f, 7.0f, 0.8f, rgb(0x2F8B3C), rgb(0x6DC44F), 18, 6)
                hill(b, rng, 11.0f, -1.0f, 5.6f, 5.0f, 3.8f, 0.8f, rgb(0x3A9B3F), rgb(0x84D05A))
                val spots = listOf(0.35f to 6.0f, 1.2f to 7.0f, 2.2f to 5.5f, 3.6f to 6.5f, 4.7f to 5.8f, 5.7f to 7.0f)
                for ((a, h) in spots) { val p = sh.at(a, 0.80f, 0.95f); palm(b, rng, p.x, 0.9f, p.z, h, a + 1.0f, 0.14f + 0.12f * rng.float()) }
                for (k in 0 until 5) { val p = sh.at(0.5f + k * 1.25f, 0.96f, 0.3f); rock(b, p.x, 0.4f, p.z, 0.8f + 0.7f * rng.float(), k + 5, grey) }
            }
            else -> {
                // a crescent around a bay (open toward +Z, where the player comes from) with a tall hill and a rocky crown
                val sh = Shape(24.0f, 22.0f, 0.05f, 0.7f, 0.04f, 2.0f, 0.03f, 0.9f, bite = 0.62f, biteAt = PI.toFloat() / 2f)
                val wide = Shape(24.0f, 22.0f, 0.05f, 0.7f, 0.04f, 2.0f, 0.03f, 0.9f)
                shallows(b, wide, 0.3f, 1.22f, 48)
                beach(b, rng, sh, 48)
                hill(b, rng, -2.0f, -7.0f, 12.5f, 9.0f, 8.6f, 0.8f, rgb(0x2E873A), rgb(0x6CC24E), 18, 6)
                for (k in 0 until 3) rock(b, -3.0f + k * 2.6f, 8.9f - k * 0.5f, -7.5f + k * 1.2f, 1.6f - 0.2f * k, 20 + k, Col.mul(grey, 0.9f))
                hill(b, rng, 14.0f, 4.0f, 4.6f, 4.2f, 2.8f, 0.8f, rgb(0x3A9B3F), rgb(0x84D05A), 12, 4)
                val spots = listOf(-0.3f to 6.5f, 0.3f to 5.5f, 2.35f to 6.0f, 3.8f to 7.0f, 4.7f to 5.8f, 5.6f to 6.2f, 3.2f to 5.2f)
                for ((a, h) in spots) { val p = sh.at(a, 0.82f, 0.95f); palm(b, rng, p.x, 0.9f, p.z, h, a + 1.4f, 0.12f + 0.12f * rng.float()) }
                for (k in 0 until 5) { val p = sh.at(0.2f + k * 1.3f + (if (k > 2) 0.9f else 0f), 0.97f, 0.3f); rock(b, p.x, 0.4f, p.z, 0.8f + 0.6f * rng.float(), k + 11, grey) }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // the gates the waterplane dives through (DIVE_GATE_BIG, DIVE_GATE_SMALL): a lifebuoy ring in the XY plane
    // ---------------------------------------------------------------------------------------------------------

    private fun diveGate(b: MeshBuilder, big: Boolean) {
        val r = if (big) 15f else 9f
        val q = if (big) 1.3f else 0.9f
        val segs = if (big) 24 else 16
        val orange = rgb(0xFF7A1A)
        val white = rgb(0xFAFAF4)
        b.push().scale(1f, 1f, 1.15f)
        for (s in 0 until segs) {
            val c = if (s % 2 == 0) orange else white
            b.torusArc(r, q, TAU * s / segs, TAU * (s + 1) / segs, 2, 6, c, Col.mul(c, 0.78f))
        }
        b.pop()
        // the glowing lining of the opening
        b.torusArc(r - q * 0.95f, q * 0.3f, 0f, TAU, segs * 2, 5, rgb(0x6FF4FF), rgb(0xE6FFFF))
        // eight lights on short stalks around the outside
        for (k in 0 until 8) {
            val a = TAU * (k + 0.5f) / 8f
            val ca = cos(a); val sa = sin(a)
            val r0 = r + q * 0.9f; val r1 = r + q + (if (big) 1.1f else 0.8f)
            b.limb(ca * r0, sa * r0, 0f, ca * r1, sa * r1, 0f, q * 0.22f, q * 0.16f, 6, rgb(0xE8ECF0), rgb(0xE8ECF0))
            val lr = if (big) 0.62f else 0.42f
            b.push().translate(ca * (r1 + lr * 0.5f), sa * (r1 + lr * 0.5f), 0f).sphere(lr, 4, 8, rgb(0xFFFBC8), rgb(0xFFE15A), smooth = true).pop()
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // the start line of the waterplane (START_BUOYS): two striped spar buoys, a chequered banner between them
    // ---------------------------------------------------------------------------------------------------------

    private fun startBuoys(b: MeshBuilder) {
        val red = rgb(0xE5382C)
        val white = rgb(0xF9F9F4)
        val black = rgb(0x16191E)
        val steel = rgb(0x3A424C)
        for (side in intArrayOf(-1, 1)) {
            val x = side * 7.4f
            for (k in 0 until 8) { // eight bands, a little thinner toward the top
                val r0 = 0.40f - 0.012f * k; val r1 = 0.40f - 0.012f * (k + 1)
                val c = if (k % 2 == 0) red else white
                b.push().translate(x, 0.45f + k * 0.9f, 0f).cylinder(r1, r0, 0.9f, 10, Col.mul(c, 1.05f), Col.mul(c, 0.9f), smooth = true, capTop = false, capBottom = k == 0).pop()
            }
            b.push().translate(x, 7.78f, 0f).cylinder(0.4f, 0.31f, 0.34f, 10, steel, Col.mul(steel, 0.8f), smooth = true).pop()
            b.limb(x, 7.9f, 0f, x, 9.0f, 0f, 0.1f, 0.07f, 6, steel, steel)
            b.push().translate(x, 9.14f, 0f).cylinder(0.3f, 0.3f, 0.3f, 8, steel, steel, smooth = true).pop()
            b.push().translate(x, 9.24f, 0f).sphere(0.26f, 4, 8, rgb(0xFFFBC8), rgb(0xFFE15A), smooth = true).pop()
            b.push().translate(x, 9.4f, 0f).cone(0.2f, 0.2f, 8, red, red, smooth = false, cap = false).pop()
        }
        // the banner: a white board with black squares on both faces and a red edge
        val cols = 28; val rows = 4
        val w = 14.0f / cols; val hh = 1.6f / rows
        b.push().translate(0f, 8.4f, 0f).box(14.0f, 1.6f, 0.06f, white).pop()
        for (i in 0 until cols) for (j in 0 until rows) {
            if ((i + j) % 2 != 0) continue
            val x0 = -7.0f + i * w; val x1 = x0 + w
            val y0 = 7.6f + j * hh; val y1 = y0 + hh
            b.quad(x0, y0, 0.035f, x1, y0, 0.035f, x1, y1, 0.035f, x0, y1, 0.035f, black)
            b.quad(x1, y0, -0.035f, x0, y0, -0.035f, x0, y1, -0.035f, x1, y1, -0.035f, black)
        }
        b.push().translate(0f, 9.24f, 0f).box(14.0f, 0.16f, 0.1f, red).pop()
        b.push().translate(0f, 7.56f, 0f).box(14.0f, 0.16f, 0.1f, red).pop()
    }

    // ---------------------------------------------------------------------------------------------------------
    // sea mines (MINE_0, MINE_1): unit models, hull sphere of radius 1 around the origin
    // ---------------------------------------------------------------------------------------------------------

    private fun mine(b: MeshBuilder, modern: Boolean) {
        val steel = rgb(0x59616B)
        if (!modern) {
            // the classic: a dark rusty ball with ten horns tipped in warning orange, a lamp on top and a broken chain below
            val iron = rgb(0x2B3037)
            val rust = rgb(0x70412A)
            b.colorSphere(1f, 7, 12) { lat, lon -> if (sin(lat * 4f + lon * 3f) > 0.8f) rust else Col.mix(Col.mul(iron, 0.8f), iron, 0.5f + 0.5f * sin(lat)) }
            val n = 12
            for (i in 0 until n) {
                val y = 1f - (2f * i + 1f) / n
                if (abs(y) > 0.85f) continue
                val r = sqrt(1f - y * y)
                val a = i * 2.39996f
                val dx = cos(a) * r; val dz = sin(a) * r
                b.push().translate(dx * 1.17f, y * 1.17f, dz * 1.17f).rotateY(atan2(dx, dz) * R2D).rotateX(acos(y) * R2D)
                    .cone(0.16f, 0.42f, 6, rgb(0xFF5A1F), rgb(0x4A4F57), smooth = false, cap = false).pop()
            }
            b.push().translate(0f, 1.04f, 0f).cylinder(0.2f, 0.28f, 0.14f, 8, steel, steel, smooth = true).pop()
            b.push().translate(0f, 1.24f, 0f).sphere(0.21f, 4, 8, rgb(0xFFE24A), rgb(0xFF3A1E), smooth = true).pop()
            val chain = rgb(0x6A4B38)
            b.push().translate(0f, -1.03f, 0f).torus(0.1f, 0.035f, 6, 4, chain).pop()
            for (k in 0 until 5) {
                b.push().translate(0.02f * k, -1.2f - 0.2f * k, 0f).rotateY(if (k % 2 == 0) 0f else 90f).rotateZ(if (k == 4) 28f else 0f)
                    .scale(0.8f, 1.35f, 1f).torus(0.1f, 0.034f, 6, 4, chain).pop()
            }
        } else {
            // the modern one: a grey ball with a glowing red band and four horns
            b.colorSphere(1f, 7, 12) { lat, _ -> Col.mix(rgb(0x6A7480), rgb(0xB4BEC9), (lat / (PI.toFloat() / 2f)) * 0.5f + 0.5f) }
            b.push().cylinder(1.03f, 1.03f, 0.3f, 16, rgb(0xFF2A24), rgb(0xFF2A24), smooth = true, capTop = false, capBottom = false).pop()
            for (k in 0 until 2) {
                b.push().translate(0f, if (k == 0) 0.17f else -0.17f, 0f)
                    .cylinder(1.02f, 1.02f, 0.04f, 16, rgb(0xFFF1E6), rgb(0xFFF1E6), smooth = true, capTop = false, capBottom = false).pop()
            }
            for (k in 0 until 4) {
                val az = k * (PI.toFloat() / 2f) + PI.toFloat() / 4f
                val el = 0.75f
                val dx = cos(el) * sin(az); val dy = sin(el); val dz = cos(el) * cos(az)
                b.push().translate(dx * 1.2f, dy * 1.2f, dz * 1.2f).rotateY(atan2(dx, dz) * R2D).rotateX(acos(dy) * R2D)
                    .cone(0.2f, 0.5f, 8, rgb(0xFF3A2A), rgb(0x38404A), smooth = false, cap = false).pop()
            }
            b.push().translate(0f, 1.0f, 0f).cylinder(0.3f, 0.34f, 0.1f, 10, rgb(0x2D343C), rgb(0x2D343C), smooth = true).pop()
            b.push().translate(0f, 1.08f, 0f).sphere(0.11f, 3, 6, rgb(0xFF4A3A), rgb(0xFF4A3A), smooth = true).pop()
            b.push().translate(0f, -1.04f, 0f).torus(0.12f, 0.04f, 8, 4, rgb(0x2D343C)).pop()
            b.limb(0f, -1.1f, 0f, 0f, -1.7f, 0f, 0.05f, 0.04f, 4, rgb(0x2D343C))
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // jellyfish (JELLY_0 pink, JELLY_1 cyan and violet): a ribbed bell with a frilled rim, oral arms, trailing tentacles
    // ---------------------------------------------------------------------------------------------------------

    private fun jelly(b: MeshBuilder, variant: Int) {
        val pink = variant == 0
        val domeA = if (pink) rgb(0xFF5FB4) else rgb(0x4FE3FF)
        val domeB = if (pink) rgb(0xFFA3DC) else rgb(0x9A7BFF)
        val rim = if (pink) rgb(0xFF2E94) else rgb(0x6A4DFF)
        val arm = if (pink) rgb(0xFF8BCB) else rgb(0xB8A4FF)
        val tentC = if (pink) rgb(0xFF4DA6) else rgb(0x5FD3FF)
        val tentTip = if (pink) rgb(0xFFC2EA) else rgb(0xD9C8FF)
        val n = 20
        val prof = arrayOf(
            floatArrayOf(0f, 2.45f), floatArrayOf(0.5f, 2.4f), floatArrayOf(1.15f, 2.15f), floatArrayOf(1.8f, 1.7f),
            floatArrayOf(2.35f, 1.1f), floatArrayOf(2.7f, 0.5f), floatArrayOf(2.86f, 0.05f), floatArrayOf(2.92f, -0.3f),
        )
        val rings = prof.mapIndexed { k, p ->
            Array(n) { i ->
                val a = TAU * i / n
                val rib = if (k == 0) 1f else 1f + 0.035f * (if (i % 2 == 0) 1f else -1f)
                val frill = if (k >= prof.size - 2 && i % 2 == 1) -0.28f else 0f // the scalloped hem
                V(cos(a) * p[0] * rib, p[1] + frill, sin(a) * p[0] * rib)
            }
        }
        b.loftSmooth(rings, { k, i -> Col.mix(if (i % 2 == 0) domeA else domeB, rim, (k.toFloat() / (prof.size - 1)).pow(2f)) }, capEnd = Col.mul(domeA, 0.6f))
        for (j in 0 until 4) { // thick oral arms from the middle of the underside
            val a0 = j * (PI.toFloat() / 2f) + 0.4f
            var px = cos(a0) * 0.3f; var py = -0.1f; var pz = sin(a0) * 0.3f
            for (k in 0 until 5) {
                val aa = a0 + 0.5f * sin(k * 1.3f + j)
                val nx = cos(aa) * (0.35f + 0.22f * (k + 1)); val nz = sin(aa) * (0.35f + 0.22f * (k + 1)); val ny = -0.1f - 0.78f * (k + 1)
                b.limb(px, py, pz, nx, ny, nz, 0.24f - 0.036f * k, 0.24f - 0.036f * (k + 1), 5, Col.mix(arm, tentTip, k / 5f), Col.mix(arm, tentTip, (k + 1f) / 5f), smooth = true)
                px = nx; py = ny; pz = nz
            }
        }
        for (j in 0 until 14) { // long thin tentacles from the rim
            val a = TAU * j / 14f + 0.2f
            var px = cos(a) * 2.5f; var py = -0.2f; var pz = sin(a) * 2.5f
            for (k in 0 until 4) {
                val nx = px * 0.97f + 0.35f * sin(k * 1.7f + j * 2.1f); val nz = pz * 0.97f + 0.35f * cos(k * 1.4f + j); val ny = py - 1.1f
                b.limb(px, py, pz, nx, ny, nz, 0.075f - 0.014f * k, 0.075f - 0.014f * (k + 1), 3, Col.mix(tentC, tentTip, k / 4f), Col.mix(tentC, tentTip, (k + 1f) / 4f))
                px = nx; py = ny; pz = nz
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // sharks (SHARK_0 great white, SHARK_1 hammerhead) and the manta ray: nose toward -Z, centred in OceanSpec.SHARK_HALF
    // ---------------------------------------------------------------------------------------------------------

    private class Stn(val z: Float, val rx: Float, val ry: Float, val cy: Float)

    private fun shark(b: MeshBuilder, hammer: Boolean) {
        val grey = if (hammer) rgb(0x8B8F7A) else rgb(0x6E7D8D)
        val white = rgb(0xEDF1F3)
        val fin = Col.mul(grey, 0.85f)
        val black = rgb(0x0E1114)
        val st = if (!hammer) listOf(
            Stn(-2.70f, 0.02f, 0.02f, -0.30f), Stn(-2.55f, 0.16f, 0.15f, -0.30f), Stn(-2.2f, 0.36f, 0.34f, -0.28f), Stn(-1.6f, 0.52f, 0.50f, -0.25f),
            Stn(-0.8f, 0.60f, 0.58f, -0.24f), Stn(0.0f, 0.55f, 0.54f, -0.22f), Stn(0.8f, 0.42f, 0.42f, -0.18f), Stn(1.5f, 0.27f, 0.29f, -0.12f),
            Stn(2.0f, 0.15f, 0.17f, -0.06f), Stn(2.3f, 0.09f, 0.12f, 0.0f),
        ) else listOf(
            Stn(-2.0f, 0.08f, 0.09f, -0.24f), Stn(-1.75f, 0.25f, 0.20f, -0.24f), Stn(-1.2f, 0.42f, 0.38f, -0.22f), Stn(-0.5f, 0.52f, 0.48f, -0.2f),
            Stn(0.3f, 0.50f, 0.48f, -0.17f), Stn(1.0f, 0.38f, 0.38f, -0.12f), Stn(1.7f, 0.24f, 0.26f, -0.06f), Stn(2.2f, 0.13f, 0.16f, 0.0f),
            Stn(2.45f, 0.08f, 0.11f, 0.03f),
        )
        val m = 10
        val rings = st.map { s -> Array(m) { i -> val a = TAU * i / m; V(s.rx * cos(a), s.cy + s.ry * sin(a), s.z) } }
        b.loftSmooth(rings, { _, i -> Col.mix(white, grey, smooth((sin(TAU * i / m) + 0.15f) / 0.5f)) }, capStart = if (hammer) grey else null, capEnd = grey)
        // dorsal fin, and a tail with a long upper lobe
        val dz0 = if (hammer) -0.7f else -0.9f
        b.push().prismZY(floatArrayOf(dz0, 0.2f, dz0 + 0.85f, 0.2f, dz0 + 0.35f, if (hammer) 0.78f else 0.86f), 0.07f, fin, fin, fin).pop()
        val tz = if (hammer) 0.1f else 0f
        b.push().prismZY(floatArrayOf(2.2f + tz, 0f, 2.45f + tz, 0.1f, 2.78f + tz, 0.78f, 2.2f + tz, 0.3f), 0.07f, fin, fin, fin).pop()
        b.push().prismZY(floatArrayOf(2.2f + tz, -0.25f, 2.62f + tz, -0.62f, 2.45f + tz, 0.1f, 2.2f + tz, 0f), 0.07f, fin, fin, fin).pop()
        val span = if (hammer) 0.5f else 0.62f
        for (side in floatArrayOf(-1f, 1f)) { // long swept pectoral fins, angled down
            b.push().translate(side * 0.40f, if (hammer) -0.5f else -0.6f, if (hammer) -0.55f else -0.95f).rotateZ(-side * 115f)
                .prismZY(floatArrayOf(-0.15f, 0f, 0.5f, 0f, 0.72f, span), 0.06f, fin, fin, fin).pop()
        }
        if (hammer) {
            val y = -0.2f
            val head = listOf(
                V(0f, y, -2.62f), V(-0.62f, y, -2.5f), V(-1.18f, y, -2.32f), V(-1.2f, y, -2.0f), V(-0.5f, y, -1.78f),
                V(0.5f, y, -1.78f), V(1.2f, y, -2.0f), V(1.18f, y, -2.32f), V(0.62f, y, -2.5f),
            )
            b.slab(head, 0f, 1f, 0f, 0.2f, grey, white, Col.mix(grey, white, 0.5f))
            for (side in floatArrayOf(-1f, 1f)) b.push().translate(side * 1.12f, y, -2.22f).sphere(0.09f, 3, 6, black, black, smooth = true).pop()
        } else {
            for (side in floatArrayOf(-1f, 1f)) b.push().translate(side * 0.30f, -0.2f, -2.1f).sphere(0.06f, 3, 6, black, black, smooth = true).pop()
            b.sheet(V(-0.22f, -0.58f, -2.45f), V(0.22f, -0.58f, -2.45f), V(0.3f, -0.6f, -1.95f), V(-0.3f, -0.6f, -1.95f), rgb(0x8A1C20)) // a red mouth underneath
        }
    }

    private fun manta(b: MeshBuilder) {
        val top = rgb(0x26405C)
        val patch = rgb(0xEAF1F6)
        val belly = rgb(0xE3EAEF)
        val cols = 9
        class P(val le: V, val te: V, val top: V, val bot: V)
        val pts = (-cols..cols).map { i ->
            val s = abs(i).toFloat() / cols
            val x = 3.15f * i / cols
            val droop = -0.15f * s * s
            val z0 = -1.55f + 1.2f * s.pow(1.1f)
            val z1 = 0.95f - 1.3f * s.pow(1.5f)
            val zm = lerp(z0, z1, 0.38f)
            val a = 0.45f * (1f - s).pow(0.9f) + 0.03f
            P(V(x, droop, z0), V(x, droop, z1), V(x, droop + a, zm), V(x, droop - 0.3f * a, zm))
        }
        val up = V(0f, -9f, 0f)
        val dn = V(0f, 9f, 0f)
        for (i in 0 until pts.size - 1) {
            val p = pts[i]; val q = pts[i + 1]
            val s = abs(i + 0.5f - cols) / cols
            val ct = if (s in 0.1f..0.5f) patch else top // white shoulder patches
            b.triOut(up, p.le, p.top, q.top, ct); b.triOut(up, p.le, q.top, q.le, ct)
            b.triOut(up, p.top, p.te, q.te, top); b.triOut(up, p.top, q.te, q.top, top)
            b.triOut(dn, p.le, p.bot, q.bot, belly); b.triOut(dn, p.le, q.bot, q.le, belly)
            b.triOut(dn, p.bot, p.te, q.te, belly); b.triOut(dn, p.bot, q.te, q.bot, belly)
        }
        for (side in floatArrayOf(-1f, 1f)) { // curled cephalic fins and eyes
            b.limb(side * 0.45f, 0.05f, -1.5f, side * 0.5f, 0.0f, -1.85f, 0.14f, 0.1f, 5, top, top)
            b.limb(side * 0.5f, 0.0f, -1.85f, side * 0.4f, -0.05f, -2.15f, 0.1f, 0.04f, 5, top, top)
            b.push().translate(side * 0.7f, 0.17f, -1.28f).sphere(0.07f, 3, 6, rgb(0x0E1218), rgb(0x0E1218), smooth = true).pop()
        }
        b.limb(0f, 0.02f, 0.8f, 0f, 0f, 2.2f, 0.07f, 0.02f, 4, rgb(0x1B2B3C), rgb(0x1B2B3C))
    }

    // ---------------------------------------------------------------------------------------------------------
    // the pearl pickup (PEARL): an iridescent pearl in a half-open scallop, within +-1.1 m
    // ---------------------------------------------------------------------------------------------------------

    private fun star(b: MeshBuilder, x: Float, y: Float, z: Float, s: Float) {
        val c = rgb(0xFFFBD6)
        b.sheet(V(x, y + s, z), V(x + s * 0.28f, y, z), V(x, y - s, z), V(x - s * 0.28f, y, z), c)
        b.sheet(V(x, y + s, z), V(x, y, z + s * 0.28f), V(x, y - s, z), V(x, y, z - s * 0.28f), c)
        b.sheet(V(x - s, y, z), V(x, y + s * 0.28f, z), V(x + s, y, z), V(x, y - s * 0.28f, z), c)
    }

    private fun pearl(b: MeshBuilder) {
        val nacre = rgb(0xFFEFF0)
        val shellA = rgb(0xF29A86)
        val shellB = rgb(0xFFD0C0)
        b.push().translate(0f, 0.08f, 0.05f)
        b.colorSphere(0.72f, 7, 12) { lat, lon -> // its colour drifts between pink, lavender and aqua
            val t = 0.5f + 0.5f * sin(lon * 2f + lat * 3f)
            Col.mix(Col.mix(rgb(0xFFF0F6), rgb(0xE6DCFF), t), rgb(0xD8FAF2), (0.5f + 0.5f * sin(lat * 4f - lon)).pow(3f) * 0.8f)
        }
        b.pop()
        // the lower valve: a ribbed bowl closed by its nacre face
        b.push().translate(0f, -0.2f, 0f).scale(0.95f, 0.5f, 0.85f)
        b.sphere(1f, 4, 10, shellB, shellA, smooth = true, latFromDeg = -90f, latToDeg = 0f)
        b.disc(1f, 10, nacre, Col.mul(nacre, 0.92f))
        b.pop()
        // the upper valve, opened about the hinge at the back
        b.push().translate(0f, -0.12f, -0.8f).rotateX(-38f).translate(0f, 0f, 0.8f).scale(0.9f, 0.4f, 0.8f)
        b.sphere(1f, 4, 10, shellA, shellB, smooth = true, latFromDeg = 0f, latToDeg = 90f)
        b.push().rotateX(180f).disc(1f, 10, nacre, Col.mul(nacre, 0.92f)).pop()
        b.pop()
        star(b, 0.5f, 0.75f, 0.75f, 0.2f)
        star(b, -0.75f, 0.55f, 0.85f, 0.18f)
        star(b, 0.05f, 0.85f, -0.35f, 0.2f)
    }

    // ---------------------------------------------------------------------------------------------------------
    // the humpback (WHALE): 28 m long, a pale grooved belly, long pectoral fins, flukes
    // ---------------------------------------------------------------------------------------------------------

    private fun whale(b: MeshBuilder) {
        val top = rgb(0x3F5A70)
        val side = rgb(0x5D7F96)
        val belly = rgb(0xD5E2EA)
        val groove = rgb(0xAFC3D0)
        val st = listOf(
            Stn(-14f, 0.1f, 0.1f, -0.3f), Stn(-13.3f, 0.9f, 0.7f, -0.4f), Stn(-12f, 1.5f, 1.2f, -0.4f), Stn(-10f, 1.85f, 1.6f, -0.35f),
            Stn(-7f, 2.0f, 1.9f, -0.3f), Stn(-4f, 2.05f, 2.0f, -0.25f), Stn(-1f, 1.95f, 1.9f, -0.15f), Stn(2f, 1.7f, 1.65f, -0.05f),
            Stn(5f, 1.35f, 1.35f, 0.1f), Stn(8f, 0.9f, 1.0f, 0.3f), Stn(10f, 0.55f, 0.7f, 0.55f), Stn(11.3f, 0.28f, 0.42f, 0.75f),
        )
        val m = 14
        val rings = st.map { s -> Array(m) { i -> val a = TAU * i / m; V(s.rx * cos(a), s.cy + s.ry * sin(a), s.z) } }
        b.loftSmooth(rings, { k, i ->
            val sa = sin(TAU * i / m)
            if (sa < -0.3f) (if (st[k].z < -2f && i % 2 == 0) groove else belly) else if (sa < 0.2f) side else top
        }, capEnd = top)
        for (sx in floatArrayOf(-1f, 1f)) {
            // the flukes with a notch in the middle of the trailing edge
            b.slab(listOf(V(0f, 0.8f, 11.0f), V(sx * 3.4f, 1.1f, 13.6f), V(0f, 0.8f, 12.7f)), 0f, 1f, 0f, 0.25f, top, belly, side)
            // the long pectoral fins, white below
            b.slab(listOf(V(sx * 1.9f, -1.0f, -4.8f), V(sx * 2.0f, -1.4f, -3.0f), V(sx * 3.45f, -2.6f, 0.3f)), sx * 0.84f, 2.28f, 0.46f, 0.3f, side, belly, belly)
            b.push().translate(sx * 1.78f, -0.45f, -10.2f).sphere(0.15f, 3, 6, rgb(0x10161C), rgb(0x10161C), smooth = true).pop()
        }
        b.push().prismZY(floatArrayOf(3.2f, 1.35f, 5.2f, 1.35f, 4.3f, 2.7f), 0.22f, top, top, top).pop()
    }

    // ---------------------------------------------------------------------------------------------------------
    // schools of small fish (SCHOOL_0 silvery blue ball, SCHOOL_1 yellow-orange ribbon), kelp and coral
    // ---------------------------------------------------------------------------------------------------------

    private fun fish(b: MeshBuilder, back: Int, belly: Int, tail: Int) {
        b.push().scale(0.17f, 0.2f, 0.46f).sphere(1f, 3, 6, back, belly, smooth = true).pop()
        b.sheet(V(0f, 0f, 0.38f), V(0f, 0.22f, 0.72f), V(0f, 0f, 0.6f), V(0f, -0.22f, 0.72f), tail)
        b.sheet3(V(0f, 0.17f, -0.1f), V(0f, 0.38f, 0.22f), V(0f, 0.17f, 0.3f), tail)
    }

    private fun school(b: MeshBuilder, variant: Int) {
        val rng = Rng(8800L + variant)
        for (i in 0 until 24) {
            var x = 0f; var y = 0f; var z = 0f
            if (variant == 0) { // a loose ball
                var px = 0f; var py = 0f; var pz = 0f
                do { px = rng.range(-1f, 1f); py = rng.range(-1f, 1f); pz = rng.range(-1f, 1f) } while (px * px + py * py + pz * pz > 1f)
                x = px * 4.6f; y = py * 3.2f; z = pz * 4.6f
            } else { // a ribbon winding along z
                val t = (i / 23f - 0.5f) * 2f
                z = t * 3.6f; x = 2.2f * sin(t * 3.2f) + rng.range(-0.7f, 0.7f); y = 1.1f * cos(t * 2.4f) + rng.range(-0.6f, 0.6f)
            }
            val k = 0.9f + 0.25f * rng.float()
            b.push().translate(x, y, z).rotateY(rng.range(-20f, 20f)).rotateX(rng.range(-8f, 8f)).scale(k)
            if (variant == 0) fish(b, Col.mul(rgb(0x3E7AB8), 0.9f + 0.2f * rng.float()), rgb(0xE9F0F6), rgb(0x6EA3DA))
            else if (i % 3 == 0) fish(b, rgb(0xFF8A1F), rgb(0xFFC07A), rgb(0xFF5A2A))
            else fish(b, rgb(0xFFC61F), rgb(0xFFE27A), rgb(0xFF8A1F))
            b.pop()
        }
    }

    private fun kelp(b: MeshBuilder, variant: Int) {
        val rng = Rng(9100L + variant * 5L)
        val stalks = if (variant == 0) 4 else 3
        val olive = if (variant == 0) rgb(0x6C7B2A) else rgb(0x8DB83A)
        val bright = if (variant == 0) rgb(0xA3A83A) else rgb(0xC9DC4C)
        val bladder = if (variant == 0) rgb(0xC8B43A) else rgb(0xEBCB4C)
        for (s in 0 until stalks) {
            val a0 = TAU * s / stalks + rng.float()
            val bx = cos(a0) * 1.1f; val bz = sin(a0) * 1.1f
            val height = (if (variant == 0) 14f else 10f) + rng.float() * 4f
            val amp = 0.8f + rng.float() * 0.9f
            val ph = rng.float() * TAU
            val nodes = 9
            val pts = Array(nodes + 1) { k ->
                val t = k.toFloat() / nodes
                V(bx + amp * sin(t * 5.2f + ph) * (0.3f + 0.7f * t), height * t, bz + amp * 0.8f * cos(t * 4.4f + ph) * (0.3f + 0.7f * t))
            }
            for (k in 0 until nodes) {
                val c0 = Col.mix(olive, bright, k.toFloat() / nodes); val c1 = Col.mix(olive, bright, (k + 1f) / nodes)
                b.limb(pts[k].x, pts[k].y, pts[k].z, pts[k + 1].x, pts[k + 1].y, pts[k + 1].z, 0.13f - 0.06f * k / nodes, 0.13f - 0.06f * (k + 1f) / nodes, 4, c0, c1)
                if (k % 2 == 1) { // a float and a long blade at every second node
                    val a = ph + k * 2.4f
                    b.push().translate(pts[k].x, pts[k].y, pts[k].z).icosphere(0.26f, 0, bladder).pop()
                    b.leaf(pts[k], unit(cos(a), 0.7f, sin(a)), V(-sin(a), 0f, cos(a)), if (variant == 0) 2.2f else 2.8f, if (variant == 0) 0.42f else 0.6f, V(0f, 0.8f, 0f), 2, 0.2f, olive, bright, bright, 0.9f)
                }
            }
        }
    }

    private fun coral(b: MeshBuilder, variant: Int) {
        val rng = Rng(9500L + variant * 3L)
        b.push().translate(0f, if (variant == 0) 0.95f else 0.65f, 0f).scale(1f, 0.25f, 1f).icosphere(if (variant == 0) 3.2f else 2.7f, 1, rgb(0x6E6A62), 0.2f, variant + 3, 0.1f).pop()
        when (variant) {
            0 -> { // a table coral: a stem and three flattened plates
                val stem = rgb(0xB5528E)
                b.push().translate(0f, 0.9f, 0f).cylinder(0.7f, 1.0f, 1.8f, 8, stem, Col.mul(stem, 0.8f), smooth = true).pop()
                val radii = floatArrayOf(3.9f, 2.9f, 1.9f)
                val ys = floatArrayOf(1.8f, 2.9f, 3.9f)
                for (k in 0 until 3) {
                    val top = if (k % 2 == 0) rgb(0xFF5CA8) else rgb(0xFF86C0)
                    val edge = rgb(0xFFA24A)
                    val ring = { y: Float, rr: Float, wob: Float -> Array(16) { i -> val a = TAU * i / 16; val rw = rr * (1f + wob * (if (i % 2 == 0) 1f else -1f)); V(cos(a) * rw, y, sin(a) * rw) } }
                    val r = radii[k]
                    val rings = listOf(ring(ys[k] - 0.35f, r * 0.55f, 0f), ring(ys[k] - 0.1f, r, 0.06f), ring(ys[k] + 0.18f, r * 0.96f, 0.05f), ring(ys[k] + 0.3f, r * 0.7f, 0f))
                    b.loftFlat(rings, { kk, _ -> if (kk == 0) Col.mul(top, 0.5f) else if (kk == 1) edge else top }, capEnd = Col.mul(top, 1.05f))
                }
            }
            1 -> { // a branching staghorn
                val lo = rgb(0xFF8328)
                val hi = rgb(0xFFD84A)
                fun branch(x: Float, y: Float, z: Float, dx: Float, dy: Float, dz: Float, len: Float, r: Float, depth: Int) {
                    val ex = x + dx * len; val ey = y + dy * len; val ez = z + dz * len
                    b.limb(x, y, z, ex, ey, ez, r, r * 0.75f, 4, Col.mix(hi, lo, depth / 3f), Col.mix(hi, lo, (depth - 0.7f) / 3f))
                    if (depth == 0) { b.push().translate(ex, ey, ez).sphere(r * 0.8f, 2, 4, hi, hi, smooth = true).pop(); return }
                    for (c in 0 until 3) {
                        val d = unit(dx + rng.range(-0.9f, 0.9f), dy * 0.8f + 0.35f, dz + rng.range(-0.9f, 0.9f))
                        branch(ex, ey, ez, d.x, d.y, d.z, len * 0.78f, r * 0.72f, depth - 1)
                    }
                }
                branch(0f, 0.8f, 0f, 0f, 1f, 0f, 2.2f, 0.42f, 3)
            }
            else -> { // organ pipes
                val teal = rgb(0x1FC9B7)
                val purple = rgb(0x9B4DCA)
                val lip = rgb(0xA6FFF0)
                val inner = rgb(0x0B3F49)
                for (k in 0 until 9) {
                    val a = TAU * k / 9f + rng.float() * 0.6f
                    val rr = if (k == 0) 0f else 1.0f + 1.2f * rng.float()
                    val h = 2.2f + 4.4f * rng.float()
                    val r = 0.38f + 0.3f * rng.float()
                    val c = if (k % 3 == 2) purple else teal
                    b.push().translate(cos(a) * rr, 0.4f + h / 2f, sin(a) * rr).cylinder(r * 0.95f, r, h, 8, lip, c, smooth = true, capTop = false).pop()
                    b.push().translate(cos(a) * rr, 0.4f + h - 0.06f, sin(a) * rr).cylinder(r * 0.72f, r * 0.72f, 0.06f, 8, inner, inner, smooth = false, capBottom = false).pop()
                }
            }
        }
    }
}
