package app.roadtoorbit.gfx

import app.roadtoorbit.math.Mat4
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural low-poly mesh construction with a small transform stack.
 *
 * All primitives are emitted CCW-outward (OpenGL front faces) into a [MeshData]. The current
 * transform may contain rotation, translation and (non-uniform) scale; mirrored transforms are
 * handled by flipping triangle winding so faces never disappear under back-face culling.
 */
class MeshBuilder(capacity: Int = 512) {
    val data = MeshData(capacity)

    private val stack = Array(24) { FloatArray(16) }
    private var depth = 0
    private val normalMat = FloatArray(9)
    private var normalDirty = true
    private var mirrored = false

    // scratch for triangle emission
    private val p = FloatArray(9)
    private val n = FloatArray(9)
    private val c = IntArray(3)

    init {
        Mat4.identity(stack[0])
    }

    private val m: FloatArray get() = stack[depth]

    // ---- transform stack ---------------------------------------------------------------------

    fun push(): MeshBuilder {
        check(depth < stack.size - 1) { "MeshBuilder stack overflow" }
        Mat4.copy(stack[depth + 1], stack[depth])
        depth++
        touch()
        return this
    }

    fun pop(): MeshBuilder {
        check(depth > 0) { "MeshBuilder stack underflow" }
        depth--
        touch()
        return this
    }

    fun translate(x: Float, y: Float, z: Float): MeshBuilder {
        Mat4.translate(m, x, y, z); return this
    }

    fun rotateX(deg: Float): MeshBuilder {
        Mat4.rotateX(m, deg); touch(); return this
    }

    fun rotateY(deg: Float): MeshBuilder {
        Mat4.rotateY(m, deg); touch(); return this
    }

    fun rotateZ(deg: Float): MeshBuilder {
        Mat4.rotateZ(m, deg); touch(); return this
    }

    fun scale(s: Float): MeshBuilder = scale(s, s, s)

    fun scale(sx: Float, sy: Float, sz: Float): MeshBuilder {
        Mat4.scale(m, sx, sy, sz); touch(); return this
    }

    private fun touch() {
        normalDirty = true
    }

    private fun refreshNormalMatrix() {
        if (!normalDirty) return
        Mat4.normalMatrix(normalMat, m)
        // determinant sign of the upper 3x3 tells us whether the transform mirrors geometry
        val a = m[0]; val b = m[4]; val cc = m[8]
        val d = m[1]; val e = m[5]; val f = m[9]
        val g = m[2]; val h = m[6]; val i = m[10]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + cc * (d * h - e * g)
        mirrored = det < 0f
        normalDirty = false
    }

    // ---- low level emission ------------------------------------------------------------------

    /** Emits the triangle held in the scratch arrays [p], [n], [c] through the current transform. */
    private fun flush() {
        refreshNormalMatrix()
        val order = if (mirrored) orderMirrored else orderNormal
        for (k in 0 until 3) {
            val s = order[k] * 3
            val px = p[s]; val py = p[s + 1]; val pz = p[s + 2]
            val nx = n[s]; val ny = n[s + 1]; val nz = n[s + 2]
            val wx = m[0] * px + m[4] * py + m[8] * pz + m[12]
            val wy = m[1] * px + m[5] * py + m[9] * pz + m[13]
            val wz = m[2] * px + m[6] * py + m[10] * pz + m[14]
            var tx = normalMat[0] * nx + normalMat[3] * ny + normalMat[6] * nz
            var ty = normalMat[1] * nx + normalMat[4] * ny + normalMat[7] * nz
            var tz = normalMat[2] * nx + normalMat[5] * ny + normalMat[8] * nz
            val l = sqrt(tx * tx + ty * ty + tz * tz)
            if (l > 1e-8f) {
                tx /= l; ty /= l; tz /= l
            }
            data.vertex(wx, wy, wz, tx, ty, tz, c[order[k]])
        }
    }

    private fun setP(k: Int, x: Float, y: Float, z: Float) {
        p[k * 3] = x; p[k * 3 + 1] = y; p[k * 3 + 2] = z
    }

    private fun setN(k: Int, x: Float, y: Float, z: Float) {
        n[k * 3] = x; n[k * 3 + 1] = y; n[k * 3 + 2] = z
    }

    /** Flat-shaded triangle in local space (normal from winding). */
    fun tri(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        ca: Int, cb: Int = ca, cc: Int = ca,
    ) {
        setP(0, ax, ay, az); setP(1, bx, by, bz); setP(2, cx, cy, cz)
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val vx = cx - ax; val vy = cy - ay; val vz = cz - az
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val l = sqrt(nx * nx + ny * ny + nz * nz)
        if (l < 1e-12f) return // degenerate
        nx /= l; ny /= l; nz /= l
        for (k in 0 until 3) setN(k, nx, ny, nz)
        c[0] = ca; c[1] = cb; c[2] = cc
        flush()
    }

    /** Triangle with explicit per-vertex normals (smooth shading). */
    fun triSmooth(
        ax: Float, ay: Float, az: Float, anx: Float, any: Float, anz: Float,
        bx: Float, by: Float, bz: Float, bnx: Float, bny: Float, bnz: Float,
        cx: Float, cy: Float, cz: Float, cnx: Float, cny: Float, cnz: Float,
        ca: Int, cb: Int = ca, cc: Int = ca,
    ) {
        setP(0, ax, ay, az); setP(1, bx, by, bz); setP(2, cx, cy, cz)
        setN(0, anx, any, anz); setN(1, bnx, bny, bnz); setN(2, cnx, cny, cnz)
        c[0] = ca; c[1] = cb; c[2] = cc
        flush()
    }

    /** CCW quad a-b-c-d (flat). Colours apply per corner. */
    fun quad(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        ca: Int, cb: Int = ca, cc: Int = ca, cd: Int = ca,
    ) {
        tri(ax, ay, az, bx, by, bz, cx, cy, cz, ca, cb, cc)
        tri(ax, ay, az, cx, cy, cz, dx, dy, dz, ca, cc, cd)
    }

    // ---- primitives --------------------------------------------------------------------------

    /** Axis-aligned box centred on the origin. */
    fun box(sx: Float, sy: Float, sz: Float, color: Int): MeshBuilder =
        box(sx, sy, sz, color, color, color, color)

    /**
     * Box with a vertical gradient on the side faces: [top] on +Y, [bottom] on -Y, side faces fade
     * from [sideTop] (upper edge) to [sideBottom] (lower edge).
     */
    fun box(sx: Float, sy: Float, sz: Float, top: Int, sideTop: Int, sideBottom: Int, bottom: Int): MeshBuilder {
        val hx = sx / 2; val hy = sy / 2; val hz = sz / 2
        // +X
        quad(hx, -hy, hz, hx, -hy, -hz, hx, hy, -hz, hx, hy, hz, sideBottom, sideBottom, sideTop, sideTop)
        // -X
        quad(-hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz, sideBottom, sideBottom, sideTop, sideTop)
        // +Y
        quad(-hx, hy, hz, hx, hy, hz, hx, hy, -hz, -hx, hy, -hz, top)
        // -Y
        quad(-hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz, bottom)
        // +Z
        quad(-hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, hy, hz, sideBottom, sideBottom, sideTop, sideTop)
        // -Z
        quad(hx, -hy, -hz, -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz, sideBottom, sideBottom, sideTop, sideTop)
        return this
    }

    /**
     * Cylinder / truncated cone along Y, centred on the origin. A zero radius yields a cone tip.
     * [smooth] interpolates normals around the circumference.
     */
    fun cylinder(
        rTop: Float, rBottom: Float, height: Float, segments: Int,
        colorTop: Int, colorBottom: Int = colorTop,
        smooth: Boolean = true, capTop: Boolean = true, capBottom: Boolean = true,
    ): MeshBuilder {
        val hy = height / 2
        val slope = (rBottom - rTop) / height
        val step = (2.0 * PI / segments).toFloat()
        for (i in 0 until segments) {
            val a0 = i * step
            val a1 = (i + 1) * step
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)
            val b0x = rBottom * c0; val b0z = rBottom * s0
            val b1x = rBottom * c1; val b1z = rBottom * s1
            val t0x = rTop * c0; val t0z = rTop * s0
            val t1x = rTop * c1; val t1z = rTop * s1
            if (smooth) {
                val n0 = norm3(c0, slope, s0)
                val n1 = norm3(c1, slope, s1)
                if (rTop > 1e-6f) {
                    triSmooth(
                        b0x, -hy, b0z, n0[0], n0[1], n0[2],
                        t0x, hy, t0z, n0[0], n0[1], n0[2],
                        t1x, hy, t1z, n1[0], n1[1], n1[2],
                        colorBottom, colorTop, colorTop,
                    )
                }
                if (rBottom > 1e-6f) {
                    triSmooth(
                        b0x, -hy, b0z, n0[0], n0[1], n0[2],
                        t1x, hy, t1z, n1[0], n1[1], n1[2],
                        b1x, -hy, b1z, n1[0], n1[1], n1[2],
                        colorBottom, colorTop, colorBottom,
                    )
                }
            } else {
                if (rTop > 1e-6f) tri(b0x, -hy, b0z, t0x, hy, t0z, t1x, hy, t1z, colorBottom, colorTop, colorTop)
                if (rBottom > 1e-6f) tri(b0x, -hy, b0z, t1x, hy, t1z, b1x, -hy, b1z, colorBottom, colorTop, colorBottom)
            }
            if (capTop && rTop > 1e-6f) tri(0f, hy, 0f, t1x, hy, t1z, t0x, hy, t0z, colorTop)
            if (capBottom && rBottom > 1e-6f) tri(0f, -hy, 0f, b0x, -hy, b0z, b1x, -hy, b1z, colorBottom)
        }
        return this
    }

    /** Cone along +Y with its base at -height/2 and its tip at +height/2. */
    fun cone(radius: Float, height: Float, segments: Int, colorTip: Int, colorBase: Int = colorTip, smooth: Boolean = false, cap: Boolean = true): MeshBuilder =
        cylinder(0f, radius, height, segments, colorTip, colorBase, smooth, capTop = false, capBottom = cap)

    /**
     * UV sphere (or a latitude band of one). Latitudes run from [latFromDeg] to [latToDeg]
     * (-90 = south pole, 90 = north pole); [colorTop]/[colorBottom] blend with latitude.
     */
    fun sphere(
        radius: Float, latSegments: Int, lonSegments: Int,
        colorTop: Int, colorBottom: Int = colorTop,
        smooth: Boolean = true, latFromDeg: Float = -90f, latToDeg: Float = 90f,
    ): MeshBuilder {
        val lat0 = latFromDeg * Mat4.DEG2RAD
        val lat1 = latToDeg * Mat4.DEG2RAD
        for (i in 0 until latSegments) {
            val fa = lat0 + (lat1 - lat0) * i / latSegments
            val fb = lat0 + (lat1 - lat0) * (i + 1) / latSegments
            val ta = (i.toFloat() / latSegments)
            val tb = ((i + 1).toFloat() / latSegments)
            val colA = Col.mix(colorBottom, colorTop, ta)
            val colB = Col.mix(colorBottom, colorTop, tb)
            for (j in 0 until lonSegments) {
                val ga = (2.0 * PI * j / lonSegments).toFloat()
                val gb = (2.0 * PI * (j + 1) / lonSegments).toFloat()
                // corners: A=(i,j) B=(i,j+1) C=(i+1,j+1) D=(i+1,j)
                val ax = cos(fa) * cos(ga); val ay = sin(fa); val az = cos(fa) * sin(ga)
                val bx = cos(fa) * cos(gb); val by = sin(fa); val bz = cos(fa) * sin(gb)
                val cx = cos(fb) * cos(gb); val cy = sin(fb); val cz = cos(fb) * sin(gb)
                val dx = cos(fb) * cos(ga); val dy = sin(fb); val dz = cos(fb) * sin(ga)
                // emit A-D-C and A-C-B (CCW from outside); skip degenerate pole triangles
                val poleA = abs(abs(fa) - PI.toFloat() / 2) < 1e-4f
                val poleB = abs(abs(fb) - PI.toFloat() / 2) < 1e-4f
                if (smooth) {
                    if (!poleB) triSmooth(
                        ax * radius, ay * radius, az * radius, ax, ay, az,
                        dx * radius, dy * radius, dz * radius, dx, dy, dz,
                        cx * radius, cy * radius, cz * radius, cx, cy, cz, colA, colB, colB,
                    )
                    if (!poleA) triSmooth(
                        ax * radius, ay * radius, az * radius, ax, ay, az,
                        cx * radius, cy * radius, cz * radius, cx, cy, cz,
                        bx * radius, by * radius, bz * radius, bx, by, bz, colA, colB, colA,
                    )
                } else {
                    if (!poleB) tri(ax * radius, ay * radius, az * radius, dx * radius, dy * radius, dz * radius, cx * radius, cy * radius, cz * radius, colA, colB, colB)
                    if (!poleA) tri(ax * radius, ay * radius, az * radius, cx * radius, cy * radius, cz * radius, bx * radius, by * radius, bz * radius, colA, colB, colA)
                }
            }
        }
        return this
    }

    /**
     * Subdivided icosahedron – the workhorse for rocks, asteroids, planets and cloud puffs.
     * [jitter] displaces vertices radially with smooth value noise (consistent across shared
     * vertices) so each [seed] gives a different lumpy silhouette. Flat shaded.
     */
    fun icosphere(radius: Float, subdivisions: Int, color: Int, jitter: Float = 0f, seed: Int = 0, colorVariation: Float = 0f): MeshBuilder {
        val t = ((1.0 + sqrt(5.0)) / 2.0).toFloat()
        val v = arrayOf(
            floatArrayOf(-1f, t, 0f), floatArrayOf(1f, t, 0f), floatArrayOf(-1f, -t, 0f), floatArrayOf(1f, -t, 0f),
            floatArrayOf(0f, -1f, t), floatArrayOf(0f, 1f, t), floatArrayOf(0f, -1f, -t), floatArrayOf(0f, 1f, -t),
            floatArrayOf(t, 0f, -1f), floatArrayOf(t, 0f, 1f), floatArrayOf(-t, 0f, -1f), floatArrayOf(-t, 0f, 1f),
        )
        for (a in v) normalize3(a)
        val faces = intArrayOf(
            0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11,
            1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9,
            4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1,
        )
        for (f in 0 until 20) {
            subdivideTri(v[faces[f * 3]], v[faces[f * 3 + 1]], v[faces[f * 3 + 2]], subdivisions, radius, color, jitter, seed, colorVariation)
        }
        return this
    }

    private fun subdivideTri(a: FloatArray, b: FloatArray, cc: FloatArray, level: Int, radius: Float, color: Int, jitter: Float, seed: Int, colorVariation: Float) {
        if (level <= 0) {
            val pa = displaced(a, radius, jitter, seed)
            val pb = displaced(b, radius, jitter, seed)
            val pc = displaced(cc, radius, jitter, seed)
            var col = color
            if (colorVariation > 0f) {
                val h = hash3((a[0] + b[0] + cc[0]) * 3.1f, (a[1] + b[1] + cc[1]) * 3.1f, (a[2] + b[2] + cc[2]) * 3.1f, seed + 77)
                col = Col.mul(color, 1f + (h - 0.5f) * 2f * colorVariation)
            }
            tri(pa[0], pa[1], pa[2], pb[0], pb[1], pb[2], pc[0], pc[1], pc[2], col)
            return
        }
        val ab = mid(a, b); val bc = mid(b, cc); val ca = mid(cc, a)
        subdivideTri(a, ab, ca, level - 1, radius, color, jitter, seed, colorVariation)
        subdivideTri(b, bc, ab, level - 1, radius, color, jitter, seed, colorVariation)
        subdivideTri(cc, ca, bc, level - 1, radius, color, jitter, seed, colorVariation)
        subdivideTri(ab, bc, ca, level - 1, radius, color, jitter, seed, colorVariation)
    }

    private fun displaced(u: FloatArray, radius: Float, jitter: Float, seed: Int): FloatArray {
        var r = radius
        if (jitter > 0f) {
            val lumps = valueNoise3(u[0] * 1.6f + seed * 7.3f, u[1] * 1.6f, u[2] * 1.6f + seed * 3.1f)
            val fine = valueNoise3(u[0] * 4.3f + 11f, u[1] * 4.3f + seed * 1.7f, u[2] * 4.3f)
            r *= 1f + jitter * ((lumps - 0.5f) * 1.6f + (fine - 0.5f) * 0.6f)
        }
        return floatArrayOf(u[0] * r, u[1] * r, u[2] * r)
    }

    /**
     * Torus with its hole axis along Z (a ring you fly through when travelling along -Z).
     * [colorOuter]/[colorInner] let the ring shade between rim and inside.
     */
    fun torus(majorRadius: Float, minorRadius: Float, majorSegments: Int, minorSegments: Int, color: Int, colorInner: Int = color): MeshBuilder {
        for (i in 0 until majorSegments) {
            val u0 = (2.0 * PI * i / majorSegments).toFloat()
            val u1 = (2.0 * PI * (i + 1) / majorSegments).toFloat()
            for (j in 0 until minorSegments) {
                val v0 = (2.0 * PI * j / minorSegments).toFloat()
                val v1 = (2.0 * PI * (j + 1) / minorSegments).toFloat()
                val c00 = Col.mix(colorInner, color, 0.5f + 0.5f * cos(v0))
                val c01 = Col.mix(colorInner, color, 0.5f + 0.5f * cos(v1))
                // corners: A=(u0,v0) B=(u1,v0) C=(u1,v1) D=(u0,v1)
                val a = torusPoint(majorRadius, minorRadius, u0, v0)
                val b = torusPoint(majorRadius, minorRadius, u1, v0)
                val cc = torusPoint(majorRadius, minorRadius, u1, v1)
                val d = torusPoint(majorRadius, minorRadius, u0, v1)
                val na = torusNormal(u0, v0); val nb = torusNormal(u1, v0)
                val nc = torusNormal(u1, v1); val nd = torusNormal(u0, v1)
                triSmooth(
                    a[0], a[1], a[2], na[0], na[1], na[2],
                    b[0], b[1], b[2], nb[0], nb[1], nb[2],
                    cc[0], cc[1], cc[2], nc[0], nc[1], nc[2], c00, c00, c01,
                )
                triSmooth(
                    a[0], a[1], a[2], na[0], na[1], na[2],
                    cc[0], cc[1], cc[2], nc[0], nc[1], nc[2],
                    d[0], d[1], d[2], nd[0], nd[1], nd[2], c00, c01, c01,
                )
            }
        }
        return this
    }

    private fun torusPoint(r: Float, q: Float, u: Float, v: Float): FloatArray {
        val rr = r + q * cos(v)
        return floatArrayOf(rr * cos(u), rr * sin(u), q * sin(v))
    }

    private fun torusNormal(u: Float, v: Float): FloatArray =
        floatArrayOf(cos(v) * cos(u), cos(v) * sin(u), sin(v))

    /** Flat disc in the XZ plane facing +Y with a radial colour (and alpha) gradient. */
    fun disc(radius: Float, segments: Int, colorCenter: Int, colorEdge: Int = colorCenter): MeshBuilder {
        val step = (2.0 * PI / segments).toFloat()
        for (i in 0 until segments) {
            val a0 = i * step
            val a1 = (i + 1) * step
            tri(0f, 0f, 0f, radius * cos(a1), 0f, radius * sin(a1), radius * cos(a0), 0f, radius * sin(a0), colorCenter, colorEdge, colorEdge)
        }
        return this
    }

    /**
     * Convex polygon extruded along X (thickness [thick], centred). The polygon lies in the (z, y)
     * plane and must be listed CCW when z points right and y points up (i.e. seen from -X).
     * Used for fins, wings and ramps.
     */
    fun prismZY(poly: FloatArray, thick: Float, colorSide: Int, colorEdge: Int = colorSide, colorBottom: Int = colorEdge): MeshBuilder {
        val count = poly.size / 2
        val hx = thick / 2
        // cap at -X (input order), cap at +X (reversed)
        for (i in 1 until count - 1) {
            tri(-hx, poly[1], poly[0], -hx, poly[i * 2 + 1], poly[i * 2], -hx, poly[(i + 1) * 2 + 1], poly[(i + 1) * 2], colorSide)
            tri(hx, poly[1], poly[0], hx, poly[(i + 1) * 2 + 1], poly[(i + 1) * 2], hx, poly[i * 2 + 1], poly[i * 2], colorSide)
        }
        // side walls, one quad per polygon edge
        for (i in 0 until count) {
            val j = (i + 1) % count
            val z0 = poly[i * 2]; val y0 = poly[i * 2 + 1]
            val z1 = poly[j * 2]; val y1 = poly[j * 2 + 1]
            val col = if (y0 <= 1e-4f && y1 <= 1e-4f) colorBottom else colorEdge
            quad(-hx, y0, z0, hx, y0, z0, hx, y1, z1, -hx, y1, z1, col)
        }
        return this
    }

    // ---- misc --------------------------------------------------------------------------------

    fun build(): MeshData = data

    // ---- noise helpers (shared with scenery generation) --------------------------------------

    private val orderNormal = intArrayOf(0, 1, 2)
    private val orderMirrored = intArrayOf(0, 2, 1)

    companion object {
        fun normalize3(a: FloatArray) {
            val l = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
            a[0] /= l; a[1] /= l; a[2] /= l
        }

        private fun norm3(x: Float, y: Float, z: Float): FloatArray {
            val l = sqrt(x * x + y * y + z * z)
            return floatArrayOf(x / l, y / l, z / l)
        }

        private fun mid(a: FloatArray, b: FloatArray): FloatArray {
            val r = floatArrayOf((a[0] + b[0]) * 0.5f, (a[1] + b[1]) * 0.5f, (a[2] + b[2]) * 0.5f)
            normalize3(r)
            return r
        }

        /** Deterministic hash in [0, 1) of a 3D position + seed. */
        fun hash3(x: Float, y: Float, z: Float, seed: Int): Float {
            var h = x.toRawBits() * 0x27d4eb2d xor y.toRawBits() * 0x165667b1 xor z.toRawBits() * 0x2c1b3c6d xor seed * 0x1b873593
            h = h xor (h ushr 15)
            h *= -0x7a143595
            h = h xor (h ushr 13)
            h *= -0x3d4d51cb
            h = h xor (h ushr 16)
            return (h ushr 8) / 16777216f
        }

        /** Smooth value noise in [0, 1) on an integer lattice. */
        fun valueNoise3(x: Float, y: Float, z: Float): Float {
            val xi = floor(x); val yi = floor(y); val zi = floor(z)
            val xf = x - xi; val yf = y - yi; val zf = z - zi
            val u = xf * xf * (3 - 2 * xf); val v = yf * yf * (3 - 2 * yf); val w = zf * zf * (3 - 2 * zf)
            fun h(dx: Int, dy: Int, dz: Int) = hash3(xi + dx, yi + dy, zi + dz, 1337)
            val x00 = h(0, 0, 0) + (h(1, 0, 0) - h(0, 0, 0)) * u
            val x10 = h(0, 1, 0) + (h(1, 1, 0) - h(0, 1, 0)) * u
            val x01 = h(0, 0, 1) + (h(1, 0, 1) - h(0, 0, 1)) * u
            val x11 = h(0, 1, 1) + (h(1, 1, 1) - h(0, 1, 1)) * u
            val y0 = x00 + (x10 - x00) * v
            val y1 = x01 + (x11 - x01) * v
            return y0 + (y1 - y0) * w
        }
    }
}
