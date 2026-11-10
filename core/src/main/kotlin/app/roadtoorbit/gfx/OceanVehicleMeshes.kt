package app.roadtoorbit.gfx

import app.roadtoorbit.gfx.Col.rgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The models of the extra parts of the vehicle's ocean skin (pontoons, propeller, conning tower, periscope, portholes,
 * searchlight, light beam). See [OceanSpec] for what each one has to be like; [VehicleModel] poses, scales and tints them.
 *
 * The part table multiplies the colours baked in here by a tint, so the models are mostly light and neutral where the part
 * is meant to take the colour of its owner (the tower is white and becomes teal) and carry their own accents where those
 * stay the same in every outfit (window glass, orange wing and float tips, the blades' bands).
 */
internal object OceanVehicleMeshes {
    private val WHITE = rgb(0xF6F8FB)
    private val ORANGE = rgb(0xFF7A1A)
    private val NAVY = rgb(0x1B3C78)
    private val STEEL = rgb(0xC4CDD9)
    private val STEEL_DARK = rgb(0x56627A)
    private val GLASS_DARK = rgb(0x16294A)

    fun build(id: MeshId, b: MeshBuilder) {
        when (id) {
            MeshId.PONTOON -> pontoon(b)
            MeshId.PROP -> prop(b)
            MeshId.TOWER -> tower(b)
            MeshId.PERISCOPE -> periscope(b)
            MeshId.PORTHOLE -> porthole(b)
            MeshId.SEARCHLIGHT -> searchlight(b)
            MeshId.LIGHT_CONE -> lightCone(b)
            else -> b.box(1f, 1f, 1f, rgb(0xFF00FF)) // not one of the vehicle's parts
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PONTOON: a float 1 x 1 x 1 m, centred, the pointed and slightly upturned bow at -Z. The part table also uses it as the
    // seaplane's elliptical wing (turned to lie along X), where the orange ends become the wing tips.
    // ---------------------------------------------------------------------------------------------

    private fun pontoon(b: MeshBuilder) {
        val rows = 15
        val cols = 14
        val g = Grid(rows, cols)
        val peak = 6f / (rows - 1) // the widest station, 3/7 of the way from the bow
        for (r in 0 until rows) {
            val s = r / (rows - 1f) // 0 at the bow tip, 1 at the stern tip
            val env = if (s < peak) sin(PI.toFloat() / 2f * s / peak).pow(0.75f) else maxOf(0f, cos(PI.toFloat() / 2f * (s - peak) / (1f - peak))).pow(0.8f)
            val bow = if (s < 0.30f) 0.16f * (1f - s / 0.30f) * (1f - s / 0.30f) else 0f
            val stern = if (s > 0.78f) 0.05f * ((s - 0.78f) / 0.22f) * ((s - 0.78f) / 0.22f) else 0f
            val cy = bow + stern
            for (c in 0 until cols) {
                val a = (2.0 * PI * c / cols).toFloat()
                g.set(r, c, 0.5f * env * superE(cos(a), 0.78f), cy + 0.5f * env * superE(sin(a), 0.78f), -0.5f + s)
            }
        }
        g.emit(b) { r, c ->
            val s = (r + 0.5f) / (rows - 1f)
            val a = (2.0 * PI * (c + 0.5f) / cols).toFloat()
            val h = sin(a)
            when {
                s < 0.10f || s > 0.92f -> ORANGE // bow and stern tips
                h < -0.55f -> NAVY // keel
                h < -0.12f -> ORANGE // stripe along the waterline
                else -> WHITE
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PROP: three blades in the XY plane (radius 1) with a hub and a spinner pointing to -Z. The blades have dark bodies with
    // orange tips and the spinner has three white stripes, so that the turning can be seen.
    // ---------------------------------------------------------------------------------------------

    private fun prop(b: MeshBuilder) {
        // hub and spinner: a surface of revolution about Z, from the back plate to the tip of the cone
        val profile = arrayOf(
            floatArrayOf(0.14f, 0.15f), floatArrayOf(0.10f, 0.20f), floatArrayOf(0.0f, 0.215f), floatArrayOf(-0.08f, 0.205f),
            floatArrayOf(-0.17f, 0.165f), floatArrayOf(-0.26f, 0.105f), floatArrayOf(-0.33f, 0.045f), floatArrayOf(-0.375f, 0f),
        )
        val cols = 12
        val g = Grid(profile.size, cols)
        for (r in profile.indices) for (c in 0 until cols) {
            val a = (2.0 * PI * c / cols).toFloat()
            g.set(r, c, profile[r][1] * cos(a), profile[r][1] * sin(a), profile[r][0])
        }
        g.emit(b) { r, c ->
            when {
                r == 0 -> STEEL_DARK
                r < 3 -> if (c % 4 >= 2) WHITE else ORANGE // the barrel
                else -> if (c % 4 >= 2) WHITE else ORANGE
            }
        }
        for (k in 0 until 3) blade(b, 90f + 120f * k)
    }

    /** One blade, pointing at [angleDeg] in the XY plane: a twisted, tapered paddle (flat shaded) with an orange tip. */
    private fun blade(b: MeshBuilder, angleDeg: Float) {
        val rs = floatArrayOf(0.15f, 0.42f, 0.72f, 0.90f, 0.995f)
        val chord = floatArrayOf(0.13f, 0.20f, 0.235f, 0.21f, 0.12f)
        val pitch = floatArrayOf(40f, 29f, 20f, 15f, 13f)
        val thick = 0.034f
        val a = angleDeg * (PI.toFloat() / 180f)
        val rx = cos(a); val ry = sin(a)
        val tx = -ry; val ty = rx
        val g = Grid(rs.size, 4)
        for (i in rs.indices) {
            val ph = pitch[i] * (PI.toFloat() / 180f)
            val cp = cos(ph); val sp = sin(ph)
            for (c in 0 until 4) {
                val u0 = (if (c == 0 || c == 3) -0.5f else 0.5f) * chord[i]
                val v0 = (if (c < 2) -0.5f else 0.5f) * thick
                val u = u0 * cp - v0 * sp
                val v = u0 * sp + v0 * cp
                g.set(i, c, rs[i] * rx + u * tx, rs[i] * ry + u * ty, v)
            }
        }
        g.emit(b, smooth = false) { r, _ -> if (r >= 3) ORANGE else rgb(0x34415C) }
        // the tip: a flat cap
        val n = rs.size - 1
        g.flatQuad(b, n, 0, 1, 2, 3, rx, ry, 0f, ORANGE)
    }

    // ---------------------------------------------------------------------------------------------
    // TOWER: the conning tower (sail), 0.5 wide, 1 high, 1.6 long, standing on y = 0. White, so the part tint paints it; the
    // bridge windows on the raked front are dark glass.
    // ---------------------------------------------------------------------------------------------

    private fun tower(b: MeshBuilder) {
        // heights and, per height, the half length, the offset along z and the half width of the horizontal section
        val ys = floatArrayOf(0f, 0.16f, 0.38f, 0.60f, 0.79f, 0.93f, 1f, 1f)
        val half = floatArrayOf(0.80f, 0.75f, 0.64f, 0.52f, 0.42f, 0.34f, 0.27f, 0f)
        val off = floatArrayOf(0f, 0.03f, 0.07f, 0.11f, 0.13f, 0.14f, 0.14f, 0.14f)
        val wid = floatArrayOf(0.25f, 0.245f, 0.22f, 0.19f, 0.16f, 0.125f, 0.095f, 0f)
        val cols = 16
        val g = Grid(ys.size, cols)
        for (r in ys.indices) for (c in 0 until cols) {
            val a = (2.0 * PI * c / cols).toFloat()
            g.set(r, c, wid[r] * superE(cos(a), 0.7f), ys[r], off[r] + half[r] * superE(sin(a), 0.7f))
        }
        g.emit(b) { r, c ->
            val a = (2.0 * PI * (c + 0.5f) / cols).toFloat()
            val front = sin(a) < -0.9f // the ring is walked from +X round through +Z, so -Z is at three quarters of the way
            if (front && r in 2..3) GLASS_DARK else if (r == 6) STEEL else WHITE
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PERISCOPE: a tube standing on y = 0, 1 m high, with a head that looks toward -Z.
    // ---------------------------------------------------------------------------------------------

    private fun periscope(b: MeshBuilder) {
        // base collar
        b.push().translate(0f, 0.06f, 0f).cylinder(0.072f, 0.105f, 0.12f, 12, STEEL_DARK, STEEL_DARK, smooth = true).pop()
        // the tube, with two collars on it
        b.push().translate(0f, 0.47f, 0f).cylinder(0.052f, 0.052f, 0.74f, 10, STEEL, rgb(0xA3AEBD), smooth = true).pop()
        b.push().translate(0f, 0.30f, 0f).cylinder(0.068f, 0.068f, 0.05f, 12, STEEL_DARK, STEEL_DARK, smooth = true).pop()
        b.push().translate(0f, 0.66f, 0f).cylinder(0.068f, 0.068f, 0.05f, 12, STEEL_DARK, STEEL_DARK, smooth = true).pop()
        // the head: a small box with a rounded top and a lens at the front
        b.push().translate(0f, 0.905f, -0.03f).box(0.16f, 0.15f, 0.30f, STEEL, STEEL, STEEL, STEEL_DARK).pop()
        b.push().translate(0f, 0.98f, -0.03f).scale(0.085f, 0.04f, 0.16f).sphere(1f, 3, 10, STEEL, STEEL, smooth = true, latFromDeg = 0f, latToDeg = 90f).pop()
        b.push().translate(0f, 0.905f, -0.184f).rotateX(90f).cylinder(0.058f, 0.058f, 0.03f, 10, GLASS_DARK, rgb(0x2F7FB0), smooth = false).pop()
        b.push().translate(0f, 0.97f, -0.17f).box(0.14f, 0.02f, 0.08f, STEEL_DARK).pop()
    }

    // ---------------------------------------------------------------------------------------------
    // PORTHOLE: a round window facing -Z: a glass disc of radius 0.5 (bright, meant to glow), a thin frame round it and a short
    // housing behind.
    // ---------------------------------------------------------------------------------------------

    private fun porthole(b: MeshBuilder) {
        val seg = 18
        val glassHi = rgb(0xEFFFFF)
        val glassLo = rgb(0x58D8EE)
        // the glass, a fan facing -Z
        for (i in 0 until seg) {
            val a0 = (2.0 * PI * i / seg).toFloat()
            val a1 = (2.0 * PI * (i + 1) / seg).toFloat()
            facing(b, 0f, 0f, 0.5f * cos(a0), 0.5f * sin(a0), 0.5f * cos(a1), 0.5f * sin(a1), -0.04f, glassHi, glassLo, glassLo)
        }
        // the frame (a torus, its hole axis along Z)
        b.push().translate(0f, 0f, -0.02f).torus(0.545f, 0.058f, seg, 6, STEEL, STEEL_DARK).pop()
        // the housing, a short drum behind the glass
        b.push().translate(0f, 0f, 0.16f).rotateX(90f).cylinder(0.53f, 0.53f, 0.36f, seg, STEEL_DARK, STEEL_DARK, smooth = true, capTop = true, capBottom = false).pop()
    }

    // ---------------------------------------------------------------------------------------------
    // SEARCHLIGHT: a round lamp facing -Z, radius 0.5: a bell-shaped housing, a bright rim and a flat glowing face.
    // ---------------------------------------------------------------------------------------------

    private fun searchlight(b: MeshBuilder) {
        val cols = 20
        // housing: from the back (z = +0.40) to the front rim (z = -0.10)
        val profile = arrayOf(
            floatArrayOf(0.43f, 0f), floatArrayOf(0.40f, 0.22f), floatArrayOf(0.34f, 0.34f), floatArrayOf(0.20f, 0.44f), floatArrayOf(0.02f, 0.50f), floatArrayOf(-0.10f, 0.50f),
        )
        val g = Grid(profile.size, cols)
        for (r in profile.indices) for (c in 0 until cols) {
            val a = (2.0 * PI * c / cols).toFloat()
            g.set(r, c, profile[r][1] * cos(a), profile[r][1] * sin(a), profile[r][0])
        }
        g.emit(b) { _, c -> if (c % 5 == 0) STEEL else STEEL_DARK }
        // the rim and the glass
        b.push().translate(0f, 0f, -0.115f).torus(0.475f, 0.045f, cols, 5, STEEL, rgb(0x8793A8)).pop()
        val face = -0.125f
        for (i in 0 until cols) {
            val a0 = (2.0 * PI * i / cols).toFloat()
            val a1 = (2.0 * PI * (i + 1) / cols).toFloat()
            // outer ring of the face (warm), then the white core
            facing(b, 0.21f * cos(a0), 0.21f * sin(a0), 0.45f * cos(a0), 0.45f * sin(a0), 0.45f * cos(a1), 0.45f * sin(a1), face, rgb(0xFFFFFF), rgb(0xFFD98A), rgb(0xFFD98A))
            facing(b, 0.21f * cos(a0), 0.21f * sin(a0), 0.45f * cos(a1), 0.45f * sin(a1), 0.21f * cos(a1), 0.21f * sin(a1), face, rgb(0xFFFFFF), rgb(0xFFD98A), rgb(0xFFFFFF))
            facing(b, 0f, 0f, 0.21f * cos(a0), 0.21f * sin(a0), 0.21f * cos(a1), 0.21f * sin(a1), face, rgb(0xFFFFFF), rgb(0xFFFFFF), rgb(0xFFFFFF))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // LIGHT_CONE: the beam of a lamp. The tip is at the origin, the cone opens toward -Z to a radius of 0.35 at z = -1 and its
    // vertex alpha fades from 0.55 at the tip to 0. Drawn additively, so both sides of the surface are emitted (the camera
    // sees the beam from behind the lamp, i.e. from inside).
    // ---------------------------------------------------------------------------------------------

    private fun lightCone(b: MeshBuilder) {
        val rows = 8
        val cols = 16
        val g = Grid(rows, cols)
        for (r in 0 until rows) {
            val t = r / (rows - 1f)
            val alpha = 0.55f * (1f - t).pow(1.6f)
            for (c in 0 until cols) {
                val a = (2.0 * PI * c / cols).toFloat()
                g.set(r, c, 0.35f * t * cos(a), 0.35f * t * sin(a), -t)
                g.setColor(r, c, Col.rgba(0xFFFFFF, alpha))
            }
        }
        g.emit(b, doubleSided = true)
    }

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    /** Signed power, the shape function of a superellipse. */
    private fun superE(v: Float, e: Float): Float = (if (v < 0f) -1f else 1f) * abs(v).pow(e)

    /** A triangle in the plane z = [z], wound so that it faces -Z, whichever way round its corners are given. */
    private fun facing(b: MeshBuilder, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, z: Float, c0: Int, c1: Int, c2: Int) {
        val cross = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0)
        if (cross > 0f) b.tri(x0, y0, z, x2, y2, z, x1, y1, z, c0, c2, c1) else b.tri(x0, y0, z, x1, y1, z, x2, y2, z, c0, c1, c2)
    }

    /**
     * A surface made of [rows] rings of [cols] points (each ring wraps round), turned into triangles that face outward (away from the
     * middle of their ring) with smooth normals taken from the neighbouring points. Rings may collapse to a point (a cap or a tip).
     */
    private class Grid(val rows: Int, val cols: Int) {
        private val px = FloatArray(rows * cols)
        private val py = FloatArray(rows * cols)
        private val pz = FloatArray(rows * cols)
        private val nx = FloatArray(rows * cols)
        private val ny = FloatArray(rows * cols)
        private val nz = FloatArray(rows * cols)
        private val vcol = IntArray(rows * cols)
        private val cxs = FloatArray(rows)
        private val cys = FloatArray(rows)
        private val czs = FloatArray(rows)
        private var ready = false

        fun set(r: Int, c: Int, x: Float, y: Float, z: Float) {
            val i = r * cols + c
            px[i] = x; py[i] = y; pz[i] = z
            ready = false
        }

        /** Gives one point its own colour (used when [emit] is called without a per-quad colour function). */
        fun setColor(r: Int, c: Int, color: Int) {
            vcol[r * cols + c] = color
        }

        private fun prepare() {
            if (ready) return
            for (r in 0 until rows) {
                var sx = 0f; var sy = 0f; var sz = 0f
                for (c in 0 until cols) { val i = r * cols + c; sx += px[i]; sy += py[i]; sz += pz[i] }
                cxs[r] = sx / cols; cys[r] = sy / cols; czs[r] = sz / cols
            }
            for (r in 0 until rows) for (c in 0 until cols) {
                val i = r * cols + c
                val a = r * cols + (c + cols - 1) % cols
                val d = r * cols + (c + 1) % cols
                val up = (maxOf(r - 1, 0)) * cols + c
                val dn = (minOf(r + 1, rows - 1)) * cols + c
                val tx = px[d] - px[a]; val ty = py[d] - py[a]; val tz = pz[d] - pz[a]
                val ux = px[dn] - px[up]; val uy = py[dn] - py[up]; val uz = pz[dn] - pz[up]
                var ox = ty * uz - tz * uy
                var oy = tz * ux - tx * uz
                var oz = tx * uy - ty * ux
                var l = sqrt(ox * ox + oy * oy + oz * oz)
                if (l < 1e-7f) {
                    // a collapsed ring (the tip of a cone, a pole): the normal runs along the axis, away from the neighbouring ring
                    val nr = if (r == 0) 1 else if (r == rows - 1) rows - 2 else if (r > 0) r - 1 else r
                    ox = cxs[r] - cxs[nr]; oy = cys[r] - cys[nr]; oz = czs[r] - czs[nr]
                    l = sqrt(ox * ox + oy * oy + oz * oz)
                    if (l < 1e-7f) { ox = 0f; oy = 1f; oz = 0f; l = 1f }
                } else {
                    // outward: away from the middle of the ring
                    val ex = px[i] - cxs[r]; val ey = py[i] - cys[r]; val ez = pz[i] - czs[r]
                    if (ox * ex + oy * ey + oz * ez < 0f) { ox = -ox; oy = -oy; oz = -oz }
                }
                nx[i] = ox / l; ny[i] = oy / l; nz[i] = oz / l
            }
            ready = true
        }

        /**
         * Emits every quad. [color] picks the colour of the quad between ring r and r + 1 at column c; without it the points'
         * own colours ([setColor]) are blended across the surface.
         */
        fun emit(b: MeshBuilder, smooth: Boolean = true, doubleSided: Boolean = false, color: ((Int, Int) -> Int)? = null) {
            prepare()
            for (r in 0 until rows - 1) for (c in 0 until cols) {
                val c1 = (c + 1) % cols
                val i0 = r * cols + c
                val i1 = r * cols + c1
                val i2 = (r + 1) * cols + c1
                val i3 = (r + 1) * cols + c
                val col = color?.invoke(r, c) ?: 0
                // the geometric normal of the quad, to be matched with the outward direction
                val gx = (py[i2] - py[i0]) * (pz[i3] - pz[i1]) - (pz[i2] - pz[i0]) * (py[i3] - py[i1])
                val gy = (pz[i2] - pz[i0]) * (px[i3] - px[i1]) - (px[i2] - px[i0]) * (pz[i3] - pz[i1])
                val gz = (px[i2] - px[i0]) * (py[i3] - py[i1]) - (py[i2] - py[i0]) * (px[i3] - px[i1])
                val mx = (px[i0] + px[i1] + px[i2] + px[i3]) / 4f - (cxs[r] + cxs[r + 1]) / 2f
                val my = (py[i0] + py[i1] + py[i2] + py[i3]) / 4f - (cys[r] + cys[r + 1]) / 2f
                val mz = (pz[i0] + pz[i1] + pz[i2] + pz[i3]) / 4f - (czs[r] + czs[r + 1]) / 2f
                var sgn = gx * (nx[i0] + nx[i1] + nx[i2] + nx[i3]) + gy * (ny[i0] + ny[i1] + ny[i2] + ny[i3]) + gz * (nz[i0] + nz[i1] + nz[i2] + nz[i3])
                if (abs(sgn) < 1e-9f) sgn = gx * mx + gy * my + gz * mz
                val flip = sgn < 0f
                val own = color == null
                tri(b, i0, i1, i2, flip, smooth, col, own)
                tri(b, i0, i2, i3, flip, smooth, col, own)
                if (doubleSided) {
                    tri(b, i0, i1, i2, !flip, smooth, col, own, inverted = true)
                    tri(b, i0, i2, i3, !flip, smooth, col, own, inverted = true)
                }
            }
        }

        private fun tri(b: MeshBuilder, a: Int, bb: Int, c: Int, flip: Boolean, smooth: Boolean, col: Int, own: Boolean, inverted: Boolean = false) {
            // skip triangles that collapsed to a line or a point (the rings at a tip)
            if (same(a, bb) || same(bb, c) || same(a, c)) return
            val i = a; val j = if (flip) c else bb; val k = if (flip) bb else c
            val s = if (inverted) -1f else 1f
            val ci = if (own) vcol[i] else col
            val cj = if (own) vcol[j] else col
            val ck = if (own) vcol[k] else col
            if (smooth) {
                b.triSmooth(
                    px[i], py[i], pz[i], s * nx[i], s * ny[i], s * nz[i],
                    px[j], py[j], pz[j], s * nx[j], s * ny[j], s * nz[j],
                    px[k], py[k], pz[k], s * nx[k], s * ny[k], s * nz[k], ci, cj, ck,
                )
            } else {
                b.tri(px[i], py[i], pz[i], px[j], py[j], pz[j], px[k], py[k], pz[k], ci, cj, ck)
            }
        }

        private fun same(i: Int, j: Int): Boolean =
            abs(px[i] - px[j]) < 1e-6f && abs(py[i] - py[j]) < 1e-6f && abs(pz[i] - pz[j]) < 1e-6f

        /** A flat quad of ring [r] (corners [c0]..[c3]) that faces the direction (ox, oy, oz). */
        fun flatQuad(b: MeshBuilder, r: Int, c0: Int, c1: Int, c2: Int, c3: Int, ox: Float, oy: Float, oz: Float, col: Int) {
            val i0 = r * cols + c0; val i1 = r * cols + c1; val i2 = r * cols + c2; val i3 = r * cols + c3
            val gx = (py[i1] - py[i0]) * (pz[i2] - pz[i0]) - (pz[i1] - pz[i0]) * (py[i2] - py[i0])
            val gy = (pz[i1] - pz[i0]) * (px[i2] - px[i0]) - (px[i1] - px[i0]) * (pz[i2] - pz[i0])
            val gz = (px[i1] - px[i0]) * (py[i2] - py[i0]) - (py[i1] - py[i0]) * (px[i2] - px[i0])
            if (gx * ox + gy * oy + gz * oz >= 0f) {
                b.quad(px[i0], py[i0], pz[i0], px[i1], py[i1], pz[i1], px[i2], py[i2], pz[i2], px[i3], py[i3], pz[i3], col)
            } else {
                b.quad(px[i0], py[i0], pz[i0], px[i3], py[i3], pz[i3], px[i2], py[i2], pz[i2], px[i1], py[i1], pz[i1], col)
            }
        }
    }
}
