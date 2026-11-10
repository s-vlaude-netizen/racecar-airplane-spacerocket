package app.roadtoorbit.gfx

import app.roadtoorbit.gfx.AirfieldSpec.BANK_DROP
import app.roadtoorbit.gfx.AirfieldSpec.BANK_HALF
import app.roadtoorbit.gfx.AirfieldSpec.RUNWAY_HALF
import app.roadtoorbit.gfx.AirfieldSpec.SEG
import app.roadtoorbit.gfx.AirfieldSpec.SHOULDER_HALF
import app.roadtoorbit.gfx.Col.rgb
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The island airstrip of HOMECOMING and the people who wait on it: the runway in segments that are laid one after the other (the
 * same way the sunken highway is), the terminal, tower and hangar, palms and a windsock, people who wave, balloons and confetti.
 * Sizes and origins are in [AirfieldSpec]; the models are in a low-poly, flat-shaded style with vertex colours, like all of them.
 */
internal object OceanAirfieldMeshes {
    private val CONCRETE = rgb(0x8C929B)
    private val CONCRETE_DARK = rgb(0x7B818A)
    private val WHITE = rgb(0xF3F3EE)
    private val GRASS_A = rgb(0x62AE5C)
    private val GRASS_B = rgb(0x58A253)
    private val SAND = rgb(0xE8D6A2)
    private val SAND_WET = rgb(0xCDB98B)
    private val LIGHT_YELLOW = rgb(0xFFF2B0)
    private val GREEN_LIGHT = rgb(0x3DDB6E)
    private val RED = rgb(0xE5382C)
    private val DARK = rgb(0x1B1F2A)
    private val SKIN = intArrayOf(rgb(0xF2C6A0), rgb(0xC98A5C), rgb(0x7A4B30))
    private val HAIR = intArrayOf(rgb(0x2A1D17), rgb(0x6B3A1E), rgb(0x1A1A22))
    private val SHIRT = intArrayOf(rgb(0xE53935), rgb(0xFFC83A), rgb(0x2FB36B))
    private val LEGS = intArrayOf(rgb(0x2B4A8C), rgb(0xFFC83A), rgb(0xC9B27A))

    private const val R2D = (180.0 / PI).toFloat()

    /** The grass reaches this far from the middle of the strip, then comes the sand of the beach. */
    private const val GRASS_HALF = 43f

    /** A tapered tube from one point (radius [r0]) to another (radius [r1]). */
    private fun MeshBuilder.limb(
        x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, r0: Float, r1: Float, sides: Int, c0: Int, c1: Int = c0,
    ): MeshBuilder {
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-5f) return this
        val yaw = atan2(dx, dz) * R2D
        val pitch = atan2(dy, sqrt(dx * dx + dz * dz)) * R2D
        push().translate((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2).rotateY(yaw).rotateX(90f - pitch)
            .cylinder(r1, r0, len, sides, c1, c0, smooth = true, capTop = false, capBottom = false).pop()
        return this
    }

    fun build(id: MeshId, b: MeshBuilder) {
        when (id) {
            MeshId.STRIP_SEG -> strip(b, 0)
            MeshId.STRIP_START -> strip(b, 1)
            MeshId.STRIP_END -> strip(b, 2)
            MeshId.STRIP_SEG_GLOW -> stripLights(b)
            MeshId.TERMINAL -> terminal(b)
            MeshId.CONTROL_TOWER -> tower(b)
            MeshId.HANGAR -> hangar(b)
            MeshId.PALM -> palm(b)
            MeshId.WINDSOCK -> windsock(b)
            MeshId.PERSON_0 -> person(b, 0)
            MeshId.PERSON_1 -> person(b, 1)
            MeshId.PERSON_2 -> person(b, 2)
            MeshId.BALLOON -> balloon(b)
            MeshId.CONFETTI_PAPER -> paper(b)
            else -> b.box(1f, 1f, 1f, rgb(0xFF00FF))
        }
    }

    // ---- the runway ---------------------------------------------------------------------------------------------------------

    /** A quad lying flat at height [y], facing up (x0 < x1, z0 the far edge < z1 the near edge). */
    private fun flat(b: MeshBuilder, x0: Float, z0: Float, x1: Float, z1: Float, y: Float, c: Int) {
        b.quad(x0, y, z1, x1, y, z1, x1, y, z0, x0, y, z0, c)
    }

    /** The bank on side [side]: from the foot of the shoulder (x = [xi], height [yi]) out and down to ([xo], [yo]), between z0 (far) and z1 (near). */
    private fun bank(b: MeshBuilder, side: Int, xi: Float, yi: Float, xo: Float, yo: Float, z0: Float, z1: Float, c: Int) {
        if (side > 0) b.quad(xi, yi, z1, xo, yo, z1, xo, yo, z0, xi, yi, z0, c)
        else b.quad(-xi, yi, z0, -xo, yo, z0, -xo, yo, z1, -xi, yi, z1, c)
    }

    private fun tone(c: Int, k: Int, amount: Float): Int = Col.mul(c, 1f + amount * (((k * 7919 + 13) % 23) / 11f - 1f))

    /**
     * One segment of the strip (near edge at z = 0, far edge at z = -24, the top at y = 0): the runway with its edge lines and the
     * dashes of its middle line, grass, sand and the bank that goes down into the sea. [kind] 1 is the start (the island slopes up out of the
     * sea for 12 m, then the runway begins with the white bars of its threshold and a row of green lights), 2 the end (the runway stops half way
     * and the island slopes away into the sea).
     */
    private fun strip(b: MeshBuilder, kind: Int) {
        val zNear = if (kind == 1) -12f else 0f // where the runway begins in this segment
        val zEnd = if (kind == 2) -12f else -SEG // and where it ends
        // the paving in slabs of 6 m, each a little different
        var k = 0
        var z = zNear
        while (z > zEnd + 0.01f) {
            val z1 = z; val z0 = maxOf(z - 6f, zEnd)
            for (i in 0 until 4) {
                val x0 = -RUNWAY_HALF + i * (RUNWAY_HALF / 2f); val x1 = x0 + RUNWAY_HALF / 2f
                flat(b, x0, z0, x1, z1, 0f, tone(if ((i + k) % 2 == 0) CONCRETE else CONCRETE_DARK, k * 5 + i, 0.05f))
            }
            z = z0; k++
        }
        // the edge lines and the dashes of the middle line
        for (side in intArrayOf(-1, 1)) flat(b, if (side < 0) -RUNWAY_HALF + 0.7f else RUNWAY_HALF - 1.1f, zEnd, if (side < 0) -RUNWAY_HALF + 1.1f else RUNWAY_HALF - 0.7f, zNear, 0.012f, WHITE)
        if (kind == 0) flat(b, -0.2f, -7.5f, 0.2f, -4.5f, 0.012f, WHITE)
        if (kind != 2) flat(b, -0.2f, -19.5f, 0.2f, -16.5f, 0.012f, WHITE)
        if (kind == 1) {
            // the threshold: eight bars a side, and a row of green lights across the beginning of the runway
            for (i in 0 until 8) for (side in intArrayOf(-1, 1)) {
                val x0 = 1.6f + i * 1.25f
                flat(b, if (side < 0) -x0 - 0.8f else x0, -22f, if (side < 0) -x0 else x0 + 0.8f, -14f, 0.012f, WHITE)
            }
            for (i in -5..5) b.push().translate(i * 2.2f, 0.12f, zNear - 0.3f).box(0.4f, 0.24f, 0.4f, GREEN_LIGHT).pop()
        }
        // beside the runway: grass with mown stripes, a strip of sand, the bank
        for (side in intArrayOf(-1, 1)) {
            var zz = zNear
            var j = 0
            while (zz > zEnd + 0.01f) {
                val z0 = maxOf(zz - 6f, zEnd)
                val xa = RUNWAY_HALF; val xb = GRASS_HALF
                flat(b, if (side < 0) -xb else xa, z0, if (side < 0) -xa else xb, zz, 0f, if ((j + (if (side < 0) 1 else 0)) % 2 == 0) GRASS_A else GRASS_B)
                zz = z0; j++
            }
            flat(b, if (side < 0) -SHOULDER_HALF else GRASS_HALF, zEnd, if (side < 0) -GRASS_HALF else SHOULDER_HALF, zNear, 0f, SAND)
            bank(b, side, SHOULDER_HALF, 0f, BANK_HALF, -BANK_DROP, zEnd, zNear, SAND_WET)
        }
        if (kind == 1) {
            // the front of the island: it slopes up out of the sea over the first 12 m
            b.quad(-SHOULDER_HALF, -BANK_DROP, 0f, SHOULDER_HALF, -BANK_DROP, 0f, SHOULDER_HALF, 0f, -12f, -SHOULDER_HALF, 0f, -12f, SAND)
            b.tri(SHOULDER_HALF, 0f, -12f, BANK_HALF, -BANK_DROP, -12f, BANK_HALF, -BANK_DROP, 0f, SAND_WET)
            b.tri(SHOULDER_HALF, 0f, -12f, BANK_HALF, -BANK_DROP, 0f, SHOULDER_HALF, -BANK_DROP, 0f, SAND_WET)
            b.tri(-SHOULDER_HALF, 0f, -12f, -BANK_HALF, -BANK_DROP, 0f, -BANK_HALF, -BANK_DROP, -12f, SAND_WET)
            b.tri(-SHOULDER_HALF, 0f, -12f, -SHOULDER_HALF, -BANK_DROP, 0f, -BANK_HALF, -BANK_DROP, 0f, SAND_WET)
        }
        if (kind == 2) {
            // the end of the island: everything beyond the end of the runway slopes down into the sea
            b.quad(-SHOULDER_HALF, 0f, -12f, SHOULDER_HALF, 0f, -12f, SHOULDER_HALF, -BANK_DROP, -SEG, -SHOULDER_HALF, -BANK_DROP, -SEG, SAND)
            // (the two sloping banks on either side of it)
            b.tri(SHOULDER_HALF, 0f, -12f, BANK_HALF, -BANK_DROP, -12f, BANK_HALF, -BANK_DROP, -SEG, SAND_WET)
            b.tri(SHOULDER_HALF, 0f, -12f, BANK_HALF, -BANK_DROP, -SEG, SHOULDER_HALF, -BANK_DROP, -SEG, SAND_WET)
            b.tri(-SHOULDER_HALF, 0f, -12f, -BANK_HALF, -BANK_DROP, -SEG, -BANK_HALF, -BANK_DROP, -12f, SAND_WET)
            b.tri(-SHOULDER_HALF, 0f, -12f, -SHOULDER_HALF, -BANK_DROP, -SEG, -BANK_HALF, -BANK_DROP, -SEG, SAND_WET)
        }
    }

    /** The lights of the runway (drawn bright): a lamp every 6 m along both edges and a flush light in the middle line. */
    private fun stripLights(b: MeshBuilder) {
        for (k in 0 until 4) {
            val z = -3f - k * 6f
            for (side in intArrayOf(-1, 1)) b.push().translate(side * (RUNWAY_HALF + 0.6f), 0.16f, z).box(0.5f, 0.32f, 0.5f, LIGHT_YELLOW).pop()
            flat(b, -0.15f, z - 0.15f, 0.15f, z + 0.15f, 0.02f, LIGHT_YELLOW)
        }
    }

    // ---- the buildings --------------------------------------------------------------------------------------------------------

    /** The terminal: a long white block with bands of glass in front, a roof that overhangs, a canopy on posts over the entrance and flags on the roof. */
    private fun terminal(b: MeshBuilder) {
        val white = rgb(0xEEF0F3); val glass = rgb(0x7DB8DC); val roof = rgb(0x39445A); val trim = rgb(0xD5DAE2)
        b.push().translate(0f, 3.5f, 0f).box(46f, 7f, 13f, white, white, Col.mul(white, 0.85f), Col.mul(white, 0.7f)).pop()
        b.push().translate(0f, 2.0f, -6.55f).box(43f, 2.8f, 0.2f, glass).pop()
        b.push().translate(0f, 5.3f, -6.55f).box(43f, 1.9f, 0.2f, glass).pop()
        for (k in -10..10) b.push().translate(k * 2.1f, 3.6f, -6.62f).box(0.16f, 5.6f, 0.2f, trim).pop() // the mullions
        b.push().translate(0f, 7.3f, 0f).box(48f, 0.6f, 16f, roof).pop()
        b.push().translate(0f, 9.0f, 1.0f).box(30f, 2.8f, 8f, white, white, Col.mul(white, 0.85f), Col.mul(white, 0.7f)).pop() // the upper floor
        b.push().translate(0f, 9.2f, -3.05f).box(27f, 1.6f, 0.2f, glass).pop()
        b.push().translate(0f, 10.55f, 1.0f).box(31f, 0.5f, 9f, roof).pop()
        // the canopy over the entrance
        b.push().translate(0f, 4.3f, -9.0f).box(18f, 0.35f, 5f, rgb(0xE5382C)).pop()
        for (sx in intArrayOf(-8, 8)) for (sz in intArrayOf(-10, -7)) b.push().translate(sx.toFloat(), 2.15f, sz.toFloat() - 0.2f).cylinder(0.14f, 0.14f, 4.3f, 8, trim, trim, smooth = true).pop()
        // flags on the roof
        val cols = intArrayOf(rgb(0xFF4A4A), rgb(0xFFD84A), rgb(0x4FD0FF))
        for ((i, x) in intArrayOf(-21, 0, 21).withIndex()) {
            b.push().translate(x.toFloat(), 9.6f, 0f).cylinder(0.08f, 0.08f, 4.8f, 6, WHITE, WHITE, smooth = true).pop()
            val y = 11.7f
            b.tri(x.toFloat(), y, 0f, x + 1.7f, y - 0.4f, 0f, x.toFloat(), y - 0.9f, 0f, cols[i])
            b.tri(x.toFloat(), y, 0f, x.toFloat(), y - 0.9f, 0f, x + 1.7f, y - 0.4f, 0f, Col.mul(cols[i], 0.85f))
        }
    }

    /** The control tower: a low building, a shaft, a glass cab with a roof and a mast with a red light. */
    private fun tower(b: MeshBuilder) {
        val white = rgb(0xF0F2F5); val glass = rgb(0x6FB0D6); val dark = rgb(0x39445A)
        b.push().translate(0f, 2.0f, 0f).box(7f, 4f, 7f, white, white, Col.mul(white, 0.85f), Col.mul(white, 0.7f)).pop()
        b.push().translate(0f, 11.0f, 0f).cylinder(1.9f, 2.2f, 14f, 12, white, Col.mul(white, 0.85f), smooth = true).pop() // the shaft, from 4 m to 18 m
        b.push().translate(0f, 18.6f, 0f).cylinder(3.6f, 3.0f, 1.2f, 12, dark, dark, smooth = true).pop() // the floor of the cab
        b.push().translate(0f, 19.7f, 0f).cylinder(3.35f, 3.35f, 1.7f, 12, glass, Col.mul(glass, 0.85f), smooth = true).pop()
        b.push().translate(0f, 21.0f, 0f).cylinder(3.9f, 3.5f, 0.5f, 12, dark, dark, smooth = true).pop() // the roof
        b.push().translate(0f, 22.7f, 0f).cylinder(0.1f, 0.14f, 3f, 6, WHITE, RED, smooth = true).pop() // the mast
        b.push().translate(0f, 24.2f, 0f).sphere(0.28f, 4, 8, RED, RED, smooth = true).pop()
    }

    /** An arched hangar, 24 m long along Z with an open front toward -Z. */
    private fun hangar(b: MeshBuilder) {
        val wall = rgb(0x9DA7B4); val roof = rgb(0x6F7C8C)
        val h0 = 4f; val r = 11f; val flatten = 0.55f
        b.push().translate(0f, h0 / 2f, 0f).box(22f, h0, 24f, wall, wall, Col.mul(wall, 0.85f), Col.mul(wall, 0.7f)).pop()
        // the arch: strips of roof from the right wall over the top to the left wall
        val n = 12
        for (k in 0 until n) {
            val a0 = PI.toFloat() * k / n; val a1 = PI.toFloat() * (k + 1) / n
            val x0 = r * cos(a0); val y0 = h0 + r * flatten * sin(a0)
            val x1 = r * cos(a1); val y1 = h0 + r * flatten * sin(a1)
            b.quad(x0, y0, 12f, x0, y0, -12f, x1, y1, -12f, x1, y1, 12f, if (k % 2 == 0) roof else Col.mul(roof, 0.92f))
            // the end walls under the arch (front: toward -Z, with a big dark door; back)
            b.tri(0f, h0, -12f, x1, y1, -12f, x0, y0, -12f, wall)
            b.tri(0f, h0, 12f, x0, y0, 12f, x1, y1, 12f, wall)
        }
        b.push().translate(0f, 3.3f, -12.06f).box(16f, 6.6f, 0.12f, DARK).pop() // the open door
        b.push().translate(0f, 7.0f, -12.1f).box(18f, 0.4f, 0.2f, RED).pop()
    }

    /** A palm tree: a trunk that leans a little, a crown of drooping fronds and a few coconuts. */
    private fun palm(b: MeshBuilder) {
        val bark = rgb(0x8A6A47); val barkDark = rgb(0x6F5236)
        val leafA = rgb(0x4FB35A); val leafB = rgb(0x2F8A45)
        val segs = 6
        for (k in 0 until segs) {
            val f = k / (segs - 1f)
            b.push().translate(0.55f * f * f, 0.65f + k * 1.2f, 0f).rotateZ(-6f * f).cylinder(0.26f - 0.07f * f, 0.30f - 0.07f * f, 1.3f, 8, if (k % 2 == 0) bark else barkDark, barkDark, smooth = true).pop()
        }
        val cx = 0.6f; val cy = 7.7f
        for (k in 0 until 9) {
            val az = k * 40f + 12f
            b.push().translate(cx, cy, 0f).rotateY(az).rotateX(-18f - 4f * (k % 3)).translate(0f, 0f, -1.5f).scale(0.5f, 0.07f, 1.75f).sphere(1f, 3, 6, leafA, leafB, smooth = false).pop()
        }
        for (k in 0 until 3) {
            val a = k * 2.1f
            b.push().translate(cx + cos(a) * 0.3f, cy - 0.35f, sin(a) * 0.3f).sphere(0.2f, 4, 8, rgb(0x6B4A2B), rgb(0x4A331D), smooth = true).pop()
        }
    }

    /** A windsock: a white pole and an orange and white sock that points toward +X. */
    private fun windsock(b: MeshBuilder) {
        b.push().translate(0f, 3.25f, 0f).cylinder(0.08f, 0.1f, 6.5f, 8, WHITE, WHITE, smooth = true).pop()
        b.push().translate(0.15f, 6.45f, 0f).rotateY(90f).torus(0.58f, 0.04f, 10, 4, rgb(0x707880)).pop()
        val orange = rgb(0xFF7A1A)
        for (k in 0 until 4) {
            val r0 = 0.55f - 0.1f * k; val r1 = 0.55f - 0.1f * (k + 1)
            b.push().translate(0.15f + 0.325f + k * 0.65f, 6.45f, 0f).rotateZ(-90f).cylinder(r1, r0, 0.65f, 10, if (k % 2 == 0) orange else WHITE, if (k % 2 == 0) Col.mul(orange, 0.85f) else Col.mul(WHITE, 0.9f), smooth = true, capTop = false, capBottom = false).pop()
        }
    }

    // ---- the people ---------------------------------------------------------------------------------------------------------

    /** A person who waves: one arm up, one down; [v] picks the clothes (a red shirt, a yellow dress, a green shirt with a cap). */
    private fun person(b: MeshBuilder, v: Int) {
        val skin = SKIN[v]; val hair = HAIR[v]; val shirt = SHIRT[v]; val legs = LEGS[v]
        if (v == 1) {
            // a dress
            b.push().translate(0f, 0.45f, 0f).cylinder(0.14f, 0.33f, 0.62f, 12, shirt, Col.mul(shirt, 0.85f), smooth = true).pop()
            for (side in intArrayOf(-1, 1)) b.push().translate(side * 0.1f, 0.2f, 0f).cylinder(0.065f, 0.075f, 0.42f, 8, skin, Col.mul(skin, 0.9f), smooth = true).pop()
        } else {
            for (side in intArrayOf(-1, 1)) b.push().translate(side * 0.1f, 0.43f, 0f).cylinder(0.075f, 0.095f, 0.86f, 8, legs, Col.mul(legs, 0.8f), smooth = true).pop()
        }
        b.push().translate(0f, 1.16f, 0f).cylinder(0.17f, 0.19f, 0.62f, 10, shirt, Col.mul(shirt, 0.85f), smooth = true).pop()
        b.push().translate(0f, 1.48f, 0f).sphere(0.18f, 4, 8, shirt, shirt, smooth = true, latFromDeg = 0f, latToDeg = 90f).pop()
        // the arm that hangs, the arm that waves, and their hands
        b.limb(-0.2f, 1.42f, 0f, -0.27f, 0.74f, 0f, 0.055f, 0.045f, 6, shirt, skin)
        b.push().translate(-0.27f, 0.7f, 0f).sphere(0.06f, 3, 6, skin, skin, smooth = true).pop()
        b.limb(0.2f, 1.42f, 0f, 0.42f, 2.12f, -0.03f, 0.055f, 0.045f, 6, shirt, skin)
        b.push().translate(0.42f, 2.2f, -0.03f).sphere(0.075f, 3, 6, skin, skin, smooth = true).pop()
        // the head, the hair, the eyes
        b.push().translate(0f, 1.63f, 0f).sphere(0.12f, 5, 10, skin, Col.mul(skin, 0.92f), smooth = true).pop()
        b.push().translate(0f, 1.67f, 0.02f).sphere(0.13f, 4, 10, hair, hair, smooth = true, latFromDeg = 15f, latToDeg = 90f).pop()
        for (side in intArrayOf(-1, 1)) b.push().translate(side * 0.045f, 1.645f, -0.108f).sphere(0.016f, 3, 5, DARK, DARK, smooth = true).pop()
        if (v == 2) b.push().translate(0f, 1.75f, -0.03f).cylinder(0.12f, 0.135f, 0.06f, 10, rgb(0x2D6CDF), rgb(0x2D6CDF), smooth = true).pop() // a cap
        if (v == 2) b.push().translate(0f, 1.73f, -0.14f).box(0.2f, 0.025f, 0.12f, rgb(0x2D6CDF)).pop()
    }

    // ---- balloons and confetti -----------------------------------------------------------------------------------------------

    /** A party balloon in white (the material tints it), with a knot and a string. */
    private fun balloon(b: MeshBuilder) {
        val white = rgb(0xFFFFFF); val belly = rgb(0xDDE4EE)
        b.push().scale(0.55f, 0.68f, 0.55f).sphere(1f, 7, 12, white, belly, smooth = true).pop()
        b.push().translate(0f, -0.7f, 0f).rotateX(180f).cone(0.09f, 0.14f, 6, white, belly, smooth = true, cap = true).pop()
        b.push().translate(0f, -1.56f, 0f).box(0.012f, 1.68f, 0.012f, rgb(0xD8D8D8)).pop()
    }

    /** A strip of confetti paper, flat, white (tinted by the material), seen from both sides. */
    private fun paper(b: MeshBuilder) {
        val white = rgb(0xFFFFFF)
        b.quad(-0.17f, 0f, 0.085f, 0.17f, 0f, 0.085f, 0.17f, 0f, -0.085f, -0.17f, 0f, -0.085f, white)
        b.quad(-0.17f, 0f, -0.085f, 0.17f, 0f, -0.085f, 0.17f, 0f, 0.085f, -0.17f, 0f, 0.085f, Col.mul(white, 0.9f))
    }
}
