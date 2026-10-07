package app.roadtoorbit.gfx

import app.roadtoorbit.gfx.Col.rgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The models that only exist on Mars: the colony's rovers, haulers and crawlers (the traffic), red-rock mesas and
 * hoodoos, a dust devil and a colony dome (the scenery), flying saucers (the hazards of the sky leg), and the two bodies
 * in Mars' sky: Mars itself and its moon Phobos. Same conventions as [WorldMeshes]: collision objects are authored
 * around their collision centre, scenery stands on y = 0, vehicles face -Z.
 */
internal object MarsMeshes {
    private val WHITE = rgb(0xEDEBE6)
    private val DARK = rgb(0x24262C)
    private val STEEL = rgb(0xA9B1BD)
    private val STEEL_DARK = rgb(0x5B6372)
    private val GLASS = rgb(0x28384C)
    private val GLASS_LIGHT = rgb(0x5E8FB8)
    private val SOLAR = rgb(0x1E3270)
    private val SOLAR_LIGHT = rgb(0x3A5DB8)
    private val TIRE = rgb(0x1B1C20)
    private val HUB = rgb(0xC9CFD8)

    /** Strata of red rock, light to dark. */
    private val STRATA = intArrayOf(rgb(0xB5653A), rgb(0xA8512F), rgb(0xCB8C57), rgb(0x8E4129), rgb(0xD6A56D), rgb(0x9C4B2E))

    fun build(id: MeshId, b: MeshBuilder) {
        when (id) {
            MeshId.ROVER_0 -> rover(b, WHITE, rgb(0xFF7A1A))
            MeshId.ROVER_1 -> rover(b, rgb(0xE9EEF2), rgb(0x1FB5A8))
            MeshId.ROVER_2 -> rover(b, rgb(0xF0D37A), rgb(0x3B3F55))
            MeshId.HAULER_0 -> hauler(b, WHITE, rgb(0xE8641E))
            MeshId.HAULER_1 -> hauler(b, rgb(0xE9EEF2), rgb(0x1FB5A8))
            MeshId.HAULER_2 -> hauler(b, rgb(0xF0D37A), rgb(0xB23A2E))
            MeshId.CRAWLER_0 -> crawler(b, WHITE, rgb(0xE8641E))
            MeshId.CRAWLER_1 -> crawler(b, rgb(0xE9EEF2), rgb(0x5B8CE0))
            MeshId.CRAWLER_2 -> crawler(b, rgb(0xF0D37A), rgb(0x2B7A5C))
            MeshId.MESA_0 -> mesa(b, 0)
            MeshId.MESA_1 -> mesa(b, 1)
            MeshId.MESA_2 -> mesa(b, 2)
            MeshId.DEVIL -> dustDevil(b)
            MeshId.DOME -> dome(b)
            MeshId.SPIRE_0 -> spire(b, 7, 0)
            MeshId.SPIRE_1 -> spire(b, 6, 1)
            MeshId.SPIRE_2 -> spire(b, 8, 2)
            MeshId.SAUCER_0 -> saucer(b, rgb(0xC9CFD8), rgb(0x5E6677), rgb(0x6BFF9A))
            MeshId.SAUCER_1 -> saucer(b, rgb(0xBFA9DC), rgb(0x5B4A7A), rgb(0xFF5CE0))
            MeshId.SAUCER_2 -> saucer(b, rgb(0xE0C070), rgb(0x8A6A28), rgb(0x5CF0FF))
            MeshId.SAUCER_3 -> saucer(b, rgb(0x8A8F9A), rgb(0x3A3D46), rgb(0xFF5050))
            MeshId.MARS -> mars(b)
            MeshId.PHOBOS -> phobos(b)
            else -> b.box(1f, 1f, 1f, rgb(0xFF00FF))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // colony vehicles (the traffic of the car leg); sized like the sedan, truck and van they replace
    // ---------------------------------------------------------------------------------------------

    /** Six-wheeled rover: 1.9 m wide, 4.2 m long, collision centre 0.65 m above the ground. */
    private fun rover(b: MeshBuilder, paint: Int, accent: Int) {
        val g = -0.65f
        b.push().translate(0f, g + 0.55f, 0.1f).box(1.7f, 0.42f, 3.5f, paint, Col.mul(paint, 0.92f), Col.mul(paint, 0.8f), DARK).pop()
        b.push().translate(0f, g + 0.84f, -1.3f).box(1.5f, 0.2f, 1.3f, Col.mul(paint, 1.05f)).pop()
        // accent stripe along both flanks and across the tail
        for (side in intArrayOf(-1, 1)) b.push().translate(side * 0.86f, g + 0.55f, 0.1f).box(0.04f, 0.14f, 3.3f, accent).pop()
        b.push().translate(0f, g + 0.7f, 1.86f).box(1.7f, 0.1f, 0.06f, accent).pop()
        // glass cabin pod
        b.push().translate(0f, g + 0.86f, -0.1f).scale(0.8f, 0.62f, 1.0f).sphere(1f, 5, 12, rgb(0xB7E4F6), rgb(0x3D6E8F), smooth = true, latFromDeg = 0f, latToDeg = 90f).pop()
        // a solar wing standing up over the tail (seen from behind like a sail), a camera mast over the nose
        b.push().translate(0f, g + 1.28f, 1.45f).rotateX(-8f).box(1.55f, 0.85f, 0.05f, SOLAR_LIGHT, SOLAR, SOLAR, SOLAR).pop()
        b.push().translate(0f, g + 1.28f, 1.43f).box(0.05f, 0.85f, 0.05f, rgb(0x8E9BC4)).pop()
        b.push().translate(0f, g + 0.95f, 1.4f).box(0.1f, 0.35f, 0.1f, STEEL_DARK).pop()
        b.push().translate(0.55f, g + 1.12f, -0.9f).cylinder(0.035f, 0.035f, 0.95f, 6, STEEL, STEEL, smooth = false).pop()
        b.push().translate(0.55f, g + 1.62f, -0.9f).box(0.28f, 0.12f, 0.14f, accent).pop()
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 0.6f, g + 0.66f, -1.78f).box(0.4f, 0.14f, 0.06f, rgb(0xFFF3C8)).pop()
            b.push().translate(side * 0.65f, g + 0.64f, 1.86f).box(0.36f, 0.12f, 0.06f, rgb(0xE02020)).pop()
        }
        for (sx in intArrayOf(-1, 1)) for (z in floatArrayOf(-1.3f, 0f, 1.3f)) WorldMeshes.wheel(b, sx * 0.93f, g + 0.34f, z, 0.34f, 0.28f)
    }

    /** Cargo hauler: 2.5 m wide, 7.4 m long, 3.2 m high, collision centre 1.6 m above the ground. */
    private fun hauler(b: MeshBuilder, paint: Int, accent: Int) {
        val g = -1.6f
        b.push().translate(0f, g + 1.2f, -2.6f).box(2.2f, 1.7f, 1.9f, paint, Col.mul(paint, 0.95f), Col.mul(paint, 0.8f), DARK).pop()
        b.push().translate(0f, g + 1.6f, -3.58f).box(1.9f, 0.6f, 0.06f, GLASS_LIGHT).pop()
        b.push().translate(0f, g + 0.55f, 0.2f).box(2.0f, 0.4f, 6.8f, rgb(0x2B2D33)).pop()
        // the cargo container with ribs
        b.push().translate(0f, g + 2.0f, 1.1f).box(2.5f, 2.4f, 4.6f, accent, Col.mul(accent, 0.92f), Col.mul(accent, 0.78f), Col.mul(accent, 0.6f)).pop()
        for (k in 0 until 6) {
            for (side in intArrayOf(-1, 1)) b.push().translate(side * 1.27f, g + 2.0f, -0.9f + k * 0.8f).box(0.05f, 2.2f, 0.12f, Col.mul(accent, 0.7f)).pop()
        }
        b.push().translate(0f, g + 3.18f, 1.1f).box(2.2f, 0.05f, 4.2f, paint).pop()
        // beacon on the cab
        b.push().translate(0f, g + 2.15f, -2.5f).cylinder(0.14f, 0.18f, 0.2f, 8, rgb(0xFFC040), rgb(0xFF8A00), smooth = true).pop()
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 0.8f, g + 0.82f, -3.62f).box(0.5f, 0.2f, 0.08f, rgb(0xFFF3C8)).pop()
            b.push().translate(side * 0.9f, g + 1.0f, 3.52f).box(0.4f, 0.2f, 0.08f, rgb(0xE02020)).pop()
        }
        for (sx in intArrayOf(-1, 1)) for (z in floatArrayOf(-2.5f, 1.0f, 2.6f)) WorldMeshes.wheel(b, sx * 1.15f, g + 0.5f, z, 0.5f, 0.34f)
    }

    /** Habitat crawler: 2.1 m wide, 5.2 m long, 2.2 m high, collision centre 1.1 m above the ground. */
    private fun crawler(b: MeshBuilder, paint: Int, accent: Int) {
        val g = -1.1f
        b.push().translate(0f, g + 1.05f, 0.2f).box(2.0f, 1.5f, 4.6f, paint, Col.mul(paint, 0.95f), Col.mul(paint, 0.8f), DARK).pop()
        b.push().translate(0f, g + 1.05f, 0.2f).box(2.04f, 0.3f, 4.2f, accent, accent, accent, accent).pop()
        b.push().translate(0f, g + 1.45f, -2.12f).box(1.7f, 0.42f, 0.06f, GLASS_LIGHT).pop()
        for (side in intArrayOf(-1, 1)) for (k in 0 until 4) {
            b.push().translate(side * 1.02f, g + 1.45f, -1.2f + k * 1.15f).box(0.04f, 0.34f, 0.7f, GLASS_LIGHT).pop()
        }
        b.push().translate(0f, g + 1.8f, 0.5f).scale(1f, 0.5f, 1.5f).sphere(0.95f, 4, 12, Col.mul(paint, 1.05f), Col.mul(paint, 0.85f), smooth = true, latFromDeg = 0f, latToDeg = 90f).pop()
        b.push().translate(0.55f, g + 2.0f, 1.4f).rotateX(-40f).cylinder(0.4f, 0.08f, 0.14f, 10, rgb(0xE8EDF4), rgb(0xB0B8C6), smooth = true).pop()
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 0.7f, g + 0.7f, -2.34f).box(0.4f, 0.16f, 0.06f, rgb(0xFFF3C8)).pop()
            b.push().translate(side * 0.8f, g + 0.9f, 2.52f).box(0.3f, 0.3f, 0.06f, rgb(0xE02020)).pop()
        }
        for (sx in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) WorldMeshes.wheel(b, sx * 1.0f, g + 0.46f, sz * 1.5f, 0.46f, 0.34f)
    }

    // ---------------------------------------------------------------------------------------------
    // scenery
    // ---------------------------------------------------------------------------------------------

    /** A flat-topped butte of banded red rock standing on y = 0, 10 - 16 m high. */
    private fun mesa(b: MeshBuilder, variant: Int) {
        val tiers = intArrayOf(4, 5, 4)[variant % 3]
        val baseR = floatArrayOf(9f, 7f, 11f)[variant % 3]
        val total = floatArrayOf(12f, 16f, 10f)[variant % 3]
        val segs = intArrayOf(10, 9, 11)[variant % 3]
        var y = 0f
        var r = baseR
        for (t in 0 until tiers) {
            val h = total / tiers * (if (t == 0) 1.3f else 0.9f)
            val rTop = r * (if (t == tiers - 1) 0.9f else 0.8f + 0.05f * ((t + variant) % 3))
            val c = STRATA[(t * 2 + variant) % STRATA.size]
            b.push().translate(0f, y + h / 2f, 0f).rotateY(t * 17f + variant * 31f)
                .cylinder(rTop, r, h, segs, Col.mul(c, 1.12f), Col.mul(c, 0.82f), smooth = false).pop()
            y += h
            r = rTop
        }
        // the cap rock overhangs a little
        b.push().translate(0f, y + 0.5f, 0f).cylinder(r * 1.06f, r * 1.1f, 1.0f, segs, Col.mul(STRATA[3], 1.1f), Col.mul(STRATA[3], 0.8f), smooth = false).pop()
        // talus slope around the foot
        b.push().translate(0f, 0.9f, 0f).cylinder(baseR * 0.86f, baseR * 1.28f, 1.8f, segs, Col.mul(STRATA[0], 0.95f), Col.mul(STRATA[0], 0.75f), smooth = false).pop()
    }

    /** A swaying column of dust, 21 m high, widening toward the top. */
    private fun dustDevil(b: MeshBuilder) {
        val n = 12
        val h = 21f / n
        fun radius(f: Float) = 0.45f + 3.1f * f * f
        for (k in 0 until n) {
            val f0 = k / n.toFloat()
            val f1 = (k + 1) / n.toFloat()
            val c = Col.mix(rgb(0xB06E44), rgb(0xE6C096), f0)
            b.push().translate(sin(k * 0.9f) * 0.9f * f0, k * h + h / 2f, cos(k * 0.9f) * 0.9f * f0)
                .cylinder(radius(f1), radius(f0), h + 0.15f, 12, Col.mul(c, 1.04f), Col.mul(c, 0.9f), smooth = true, capTop = false, capBottom = false).pop()
        }
    }

    /** Colony outpost: a white dome, an airlock tunnel, an antenna and two solar arrays, standing on y = 0. */
    private fun dome(b: MeshBuilder) {
        b.push().translate(0f, 0.2f, 0f).cylinder(6.6f, 6.8f, 0.4f, 20, rgb(0x8A7E74), rgb(0x6E645C), smooth = false).pop()
        b.push().translate(0f, 0.4f, 0f).sphere(5.2f, 7, 22, rgb(0xF4F4F0), rgb(0xC9CDD4), smooth = true, latFromDeg = 0f, latToDeg = 90f).pop()
        // ribs
        for (k in 0 until 6) {
            b.push().translate(0f, 0.4f, 0f).rotateY(k * 30f).torus(5.22f, 0.07f, 24, 4, rgb(0x9AA2AE)).pop()
        }
        // airlock tunnel with a door
        b.push().translate(0f, 1.4f, 5.9f).box(2.8f, 2.4f, 3.4f, rgb(0xE6E8EC), rgb(0xD2D6DD), rgb(0xB0B6C0), rgb(0x8A909C)).pop()
        b.push().translate(0f, 1.2f, 7.62f).box(1.4f, 1.9f, 0.08f, rgb(0xFF8A2A)).pop()
        // antenna with a red light
        b.push().translate(-2.4f, 6.4f, -1.2f).cylinder(0.08f, 0.1f, 4.2f, 6, STEEL, STEEL, smooth = false).pop()
        b.push().translate(-2.4f, 8.6f, -1.2f).box(0.3f, 0.3f, 0.3f, rgb(0xFF3A2A)).pop()
        // solar arrays on posts
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 9.4f, 0f, 1.0f)
            b.push().translate(0f, 0.7f, 0f).cylinder(0.12f, 0.16f, 1.4f, 6, STEEL_DARK, STEEL_DARK, smooth = false).pop()
            b.push().translate(0f, 1.9f, 0f).rotateX(-28f).box(4.6f, 0.1f, 3.0f, SOLAR_LIGHT, SOLAR, SOLAR, SOLAR).pop()
            b.pop()
        }
    }

    /** Red-rock hoodoo: unit radius at the foot and unit height, standing on y = 0 (the entity scales it). */
    private fun spire(b: MeshBuilder, segs: Int, variant: Int) {
        val c0 = STRATA[variant % STRATA.size]
        val c1 = STRATA[(variant + 2) % STRATA.size]
        val c2 = STRATA[(variant + 4) % STRATA.size]
        fun tier(y: Float, rTop: Float, rBot: Float, h: Float, c: Int, dx: Float, dz: Float, rot: Float) {
            b.push().translate(dx, y, dz).rotateY(rot).cylinder(rTop, rBot, h, segs, Col.mul(c, 1.1f), Col.mul(c, 0.78f), smooth = false).pop()
        }
        tier(0.12f, 0.80f, 1.0f, 0.26f, c0, 0f, 0f, 0f)
        tier(0.38f, 0.56f, 0.80f, 0.28f, c1, 0.04f, 0.02f, 12f)
        tier(0.63f, 0.40f, 0.56f, 0.24f, c2, -0.05f, 0.03f, -20f)
        tier(0.82f, 0.31f, 0.40f, 0.16f, c0, 0.03f, -0.03f, 35f)
        b.push().translate(0.02f, 0.945f, -0.01f).scale(0.62f, 0.075f, 0.58f).icosphere(1f, 1, Col.mul(STRATA[3], 1.05f), 0.22f, 5 + variant, 0.1f).pop()
    }

    /** Flying saucer, 6 m across and about 1.5 m thick, centred on its collision centre. */
    private fun saucer(b: MeshBuilder, hull: Int, under: Int, lights: Int) {
        b.push().scale(3.0f, 0.55f, 3.0f).sphere(1f, 6, 22, hull, under, smooth = true).pop()
        b.push().translate(0f, 0.3f, 0f).scale(1.3f, 1.0f, 1.3f).sphere(1f, 5, 14, rgb(0xBFEFFF), rgb(0x3C86B0), smooth = true, latFromDeg = 0f, latToDeg = 90f).pop()
        b.push().translate(0f, -0.52f, 0f).cylinder(1.1f, 1.5f, 0.35f, 14, Col.mul(under, 1.1f), Col.mul(under, 0.8f), smooth = true).pop()
        // a ring of running lights, which turn with the saucer
        val n = 12
        for (k in 0 until n) {
            val a = (k * 2.0 * PI / n).toFloat()
            val c = if (k % 2 == 0) lights else Col.mul(lights, 0.55f)
            b.push().translate(cos(a) * 2.78f, -0.02f, sin(a) * 2.78f).rotateY(-Math.toDegrees(a.toDouble()).toFloat()).box(0.34f, 0.2f, 0.34f, c).pop()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Mars and Phobos
    // ---------------------------------------------------------------------------------------------

    private fun smoothstep(e0: Float, e1: Float, v: Float): Float {
        val t = ((v - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Rust-red desert with dark basalt plains, bright dust and white polar caps; flat shaded. */
    private fun mars(b: MeshBuilder) {
        val data = MeshBuilder(20000)
        data.icosphere(1f, 4, rgb(0xFFFFFF), 0.0f, 0, 0f)
        val src = data.data
        for (i in 0 until src.triangleCount) {
            val v0 = i * 3
            val cx = (src.pos[v0 * 3] + src.pos[(v0 + 1) * 3] + src.pos[(v0 + 2) * 3]) / 3f
            val cy = (src.pos[v0 * 3 + 1] + src.pos[(v0 + 1) * 3 + 1] + src.pos[(v0 + 2) * 3 + 1]) / 3f
            val cz = (src.pos[v0 * 3 + 2] + src.pos[(v0 + 1) * 3 + 2] + src.pos[(v0 + 2) * 3 + 2]) / 3f
            val field = MeshBuilder.valueNoise3(cx * 2.1f + 5f, cy * 2.1f, cz * 2.1f) * 0.7f + MeshBuilder.valueNoise3(cx * 6f, cy * 6f + 3f, cz * 6f) * 0.3f
            val maria = MeshBuilder.valueNoise3(cx * 3.3f + 11f, cy * 3.3f, cz * 3.3f + 4f)
            val dust = MeshBuilder.valueNoise3(cx * 4.1f + 7f, cy * 4.1f + 2f, cz * 4.1f + 9f)
            var col = Col.mix(rgb(0xB65A33), rgb(0x92422A), field)
            if (maria > 0.58f) col = Col.mix(col, rgb(0x5A3328), ((maria - 0.58f) * 2.8f).coerceAtMost(1f))
            if (dust > 0.64f) col = Col.mix(col, rgb(0xE3AC7C), ((dust - 0.64f) * 3f).coerceAtMost(1f))
            val lat = abs(cy)
            if (lat > 0.9f) col = rgb(0xF3EFEA) else if (lat > 0.84f) col = Col.mix(col, rgb(0xF3EFEA), 0.5f)
            b.tri(
                src.pos[v0 * 3], src.pos[v0 * 3 + 1], src.pos[v0 * 3 + 2],
                src.pos[(v0 + 1) * 3], src.pos[(v0 + 1) * 3 + 1], src.pos[(v0 + 1) * 3 + 2],
                src.pos[(v0 + 2) * 3], src.pos[(v0 + 2) * 3 + 1], src.pos[(v0 + 2) * 3 + 2], col,
            )
        }
    }

    /**
     * Phobos: a dark, lumpy rock with one huge crater (Stickney) facing the camera (+Z) and light streaks. The cap
     * around the top (+Y) is left a perfect sphere: the finale's landing ground lies just above it, and a lump there
     * would rise through the vehicle.
     */
    private fun phobos(b: MeshBuilder) {
        val data = MeshBuilder(20000)
        data.icosphere(1f, 4, rgb(0xFFFFFF), 0.0f, 0, 0f)
        val src = data.data
        val crater = floatArrayOf(0.30f, 0.22f, 0.93f)
        val cl = sqrt(crater[0] * crater[0] + crater[1] * crater[1] + crater[2] * crater[2])
        for (k in 0..2) crater[k] /= cl

        fun radiusAt(x: Float, y: Float, z: Float): Float {
            val l = sqrt(x * x + y * y + z * z)
            val ux = x / l; val uy = y / l; val uz = z / l
            val lumps = MeshBuilder.valueNoise3(ux * 1.7f + 3f, uy * 1.7f, uz * 1.7f + 8f)
            val fine = MeshBuilder.valueNoise3(ux * 5.2f, uy * 5.2f + 1f, uz * 5.2f)
            val flat = 1f - smoothstep(0.45f, 0.8f, uy) // nothing near the top sticks out
            var r = 1f + (0.08f * (lumps - 0.5f) * 2f + 0.025f * (fine - 0.5f) * 2f) * flat
            val d = kotlin.math.acos((ux * crater[0] + uy * crater[1] + uz * crater[2]).coerceIn(-1f, 1f))
            if (d < 0.62f) {
                val bowl = 1f - smoothstep(0f, 0.46f, d)
                val rim = smoothstep(0.34f, 0.46f, d) * (1f - smoothstep(0.46f, 0.62f, d))
                r += (-0.16f * bowl + 0.045f * rim) * flat
            }
            return r
        }

        for (i in 0 until src.triangleCount) {
            val v0 = i * 3
            val px = FloatArray(3); val py = FloatArray(3); val pz = FloatArray(3)
            for (k in 0..2) {
                val x = src.pos[(v0 + k) * 3]; val y = src.pos[(v0 + k) * 3 + 1]; val z = src.pos[(v0 + k) * 3 + 2]
                val l = sqrt(x * x + y * y + z * z)
                val r = radiusAt(x, y, z)
                px[k] = x / l * r; py[k] = y / l * r; pz[k] = z / l * r
            }
            val cx = (px[0] + px[1] + px[2]) / 3f
            val cy = (py[0] + py[1] + py[2]) / 3f
            val cz = (pz[0] + pz[1] + pz[2]) / 3f
            val cl2 = sqrt(cx * cx + cy * cy + cz * cz)
            val ux = cx / cl2; val uy = cy / cl2; val uz = cz / cl2
            val n1 = MeshBuilder.valueNoise3(ux * 3.2f + 1f, uy * 3.2f, uz * 3.2f + 5f)
            val n2 = MeshBuilder.valueNoise3(ux * 11f, uy * 11f + 4f, uz * 11f)
            val shade = 0.40f + 0.22f * n1 + (n2 - 0.5f) * 0.10f
            var col = Col.make(shade * 1.0f, shade * 0.90f, shade * 0.82f)
            // grooves: pale streaks running along the equator
            val groove = abs(sin(uy * 23f + n1 * 4f))
            if (groove > 0.93f) col = Col.mix(col, rgb(0x9C8E80), 0.5f)
            val d = kotlin.math.acos((ux * crater[0] + uy * crater[1] + uz * crater[2]).coerceIn(-1f, 1f))
            if (d < 0.46f) col = Col.mul(col, 0.8f)
            b.tri(px[0], py[0], pz[0], px[1], py[1], pz[1], px[2], py[2], pz[2], col)
        }
    }
}
