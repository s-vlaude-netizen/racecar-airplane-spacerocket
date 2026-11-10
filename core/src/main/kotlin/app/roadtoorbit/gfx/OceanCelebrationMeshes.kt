package app.roadtoorbit.gfx

import app.roadtoorbit.gfx.Col.rgb
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The welcome of Atlantis: merfolk who cheer along the avenue (MERFOLK_0..2: standing on the tip of their tail, arms raised,
 * 3.3 m tall, facing -Z), a little white fish that the finale tints in every colour and throws about as confetti
 * (CONFETTI_FISH: 1.2 m long, nose toward -Z, centred) and a garland of flags across the avenue (BUNTING: in the XY plane
 * between two poles at x = +-14, standing on y = 0, 11 m high).
 */
internal object OceanCelebrationMeshes {
    private val SKIN = intArrayOf(rgb(0xF2C6A0), rgb(0xB9794A), rgb(0x8A5A3C))
    private val HAIR = intArrayOf(rgb(0x2B1B3D), rgb(0xE25AA0), rgb(0x2FA38A))
    private val TAIL = intArrayOf(rgb(0x14B8A6), rgb(0xE0409A), rgb(0xFF8A1A))
    private val TAIL_LIGHT = intArrayOf(rgb(0x7FF0E0), rgb(0xFF9AD0), rgb(0xFFD070))
    private val BAND = intArrayOf(rgb(0xFFD84A), rgb(0x4FD0FF), rgb(0xFFFFFF))
    private val GOLD = rgb(0xFFD44A)
    private val DARK = rgb(0x1B1B2A)
    private val RAINBOW = intArrayOf(rgb(0xFF4A4A), rgb(0xFF9A2E), rgb(0xFFE04A), rgb(0x5CE07A), rgb(0x4FD0FF), rgb(0x6A7CFF), rgb(0xE05CFF))

    fun build(id: MeshId, b: MeshBuilder) {
        when (id) {
            MeshId.MERFOLK_0 -> merfolk(b, 0)
            MeshId.MERFOLK_1 -> merfolk(b, 1)
            MeshId.MERFOLK_2 -> merfolk(b, 2)
            MeshId.CONFETTI_FISH -> fish(b)
            MeshId.BUNTING -> bunting(b)
            else -> b.box(1f, 1f, 1f, rgb(0xFF00FF))
        }
    }

    private fun merfolk(b: MeshBuilder, v: Int) {
        val tail = TAIL[v]; val tailLt = TAIL_LIGHT[v]; val skin = SKIN[v]; val hair = HAIR[v]; val band = BAND[v]
        // the fluke at the tip, spread in a V
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 0.24f, 0.2f, 0f).rotateZ(side * -38f).scale(0.34f, 0.06f, 0.42f).sphere(1f, 4, 8, tailLt, tail, smooth = true).pop()
        }
        // the tail: scaly rings, narrow at the tip and wide at the waist, swaying a little
        val rings = 5
        for (k in 0 until rings) {
            val f = k / (rings - 1f)
            val y = 0.42f + f * 1.05f
            val r = 0.10f + 0.20f * f
            val c = if (k % 2 == 0) tail else tailLt
            b.push().translate(0.07f * sin(k * 1.3f), y, 0f).cylinder(r + 0.025f, r, 0.26f, 10, c, Col.mul(c, 0.8f), smooth = true).pop()
        }
        // the waist band and the body
        b.push().translate(0f, 1.52f, 0f).cylinder(0.315f, 0.315f, 0.13f, 12, band, Col.mul(band, 0.85f), smooth = true).pop()
        b.push().translate(0f, 2.0f, 0f).cylinder(0.30f, 0.27f, 0.85f, 12, skin, Col.mul(skin, 0.9f), smooth = true).pop()
        b.push().translate(0f, 2.14f, 0f).cylinder(0.325f, 0.325f, 0.3f, 12, band, Col.mul(band, 0.85f), smooth = true).pop() // the shell top
        // the arms, raised in a cheer, with hands
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 0.31f, 2.3f, 0f).rotateZ(side * -38f).translate(0f, 0.4f, 0f).cylinder(0.07f, 0.075f, 0.8f, 8, skin, Col.mul(skin, 0.9f), smooth = true).pop()
            // the tip of the arm: from the pivot 0.82 m along the rotated direction
            val a = (side * -38f) * (PI.toFloat() / 180f)
            val hx = side * 0.31f - sin(a) * 0.82f
            val hy = 2.3f + cos(a) * 0.82f
            b.push().translate(hx, hy, 0f).sphere(0.095f, 4, 8, skin, Col.mul(skin, 0.9f), smooth = true).pop()
        }
        // the head, hair, eyes
        b.push().translate(0f, 2.66f, 0f).sphere(0.21f, 6, 12, skin, Col.mul(skin, 0.9f), smooth = true).pop()
        b.push().translate(0f, 2.71f, 0.03f).sphere(0.235f, 5, 12, hair, hair, smooth = true, latFromDeg = 8f, latToDeg = 90f).pop()
        b.push().translate(0f, 2.38f, 0.15f).rotateX(180f).cone(0.2f, 0.78f, 8, Col.mul(hair, 0.8f), hair, smooth = false, cap = false).pop() // long hair down the back
        for (side in intArrayOf(-1, 1)) b.push().translate(side * 0.075f, 2.68f, -0.195f).sphere(0.032f, 3, 6, DARK, DARK, smooth = true).pop()
        if (v == 0) {
            // a crown
            b.push().translate(0f, 2.9f, 0f).cylinder(0.2f, 0.2f, 0.07f, 10, GOLD, Col.mul(GOLD, 0.8f), smooth = false).pop()
            for (k in 0 until 5) {
                val a = k * 2f * PI.toFloat() / 5f
                b.push().translate(cos(a) * 0.16f, 3.0f, sin(a) * 0.16f).cone(0.05f, 0.14f, 5, GOLD, GOLD, smooth = false, cap = false).pop()
            }
        }
        if (v == 1) {
            // a pennant on a stick in the right hand
            val a = -38f * (PI.toFloat() / 180f)
            val hx = 0.31f - sin(a) * 0.82f
            val hy = 2.3f + cos(a) * 0.82f
            b.push().translate(hx, hy + 0.35f, 0f).box(0.04f, 0.9f, 0.04f, rgb(0x6A4B2A)).pop()
            b.tri(hx, hy + 0.78f, 0f, hx + 0.6f, hy + 0.62f, 0f, hx, hy + 0.46f, 0f, rgb(0xFF4A8A))
            b.tri(hx, hy + 0.78f, 0f, hx, hy + 0.46f, 0f, hx + 0.6f, hy + 0.62f, 0f, rgb(0xFF4A8A))
        }
    }

    /** One small fish, white so that the material's tint paints it. */
    private fun fish(b: MeshBuilder) {
        val white = rgb(0xFFFFFF)
        val belly = rgb(0xDDE4EE)
        b.push().scale(0.2f, 0.34f, 0.62f).sphere(1f, 5, 10, white, belly, smooth = true).pop()
        b.push().prismZY(floatArrayOf(0.5f, 0f, 1.05f, 0.42f, 0.95f, 0f, 1.05f, -0.42f), 0.035f, white, white, white).pop() // the tail
        b.push().prismZY(floatArrayOf(0.15f, 0.26f, -0.1f, 0.58f, -0.3f, 0.26f), 0.035f, white, white, white).pop() // the dorsal fin
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 0.13f, 0.08f, -0.36f).sphere(0.05f, 3, 6, DARK, DARK, smooth = true).pop()
            b.push().translate(side * 0.18f, -0.1f, -0.05f).rotateZ(side * -30f).scale(0.02f, 0.2f, 0.16f).sphere(1f, 3, 6, white, white, smooth = false).pop() // the side fin
        }
    }

    /** Two poles and a sagging garland of triangular flags in all the colours of the rainbow. */
    private fun bunting(b: MeshBuilder) {
        val half = 14f
        val top = 10.6f
        val sag = 2.4f
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * half, 5.6f, 0f).box(0.3f, 11.2f, 0.3f, rgb(0xE8E2D0)).pop()
            b.push().translate(side * half, 11.3f, 0f).sphere(0.32f, 4, 8, GOLD, GOLD, smooth = true).pop()
        }
        fun yAt(x: Float): Float { val u = x / half; return top - sag * (1f - u * u) }
        // the string
        val steps = 28
        for (k in 0 until steps) {
            val x0 = -half + 2f * half * k / steps
            val x1 = -half + 2f * half * (k + 1) / steps
            val y0 = yAt(x0); val y1 = yAt(x1)
            val len = hypot(x1 - x0, y1 - y0)
            val ang = (atan2(y1 - y0, x1 - x0) * 180f / PI.toFloat())
            b.push().translate((x0 + x1) / 2f, (y0 + y1) / 2f, 0f).rotateZ(ang).box(len + 0.02f, 0.07f, 0.07f, rgb(0x3A3A48)).pop()
        }
        // the flags, hanging from the string, both sides visible
        val flags = 19
        for (k in 0 until flags) {
            val x = -half + 1.2f + (2f * half - 2.4f) * k / (flags - 1f)
            val y = yAt(x)
            val c = RAINBOW[k % RAINBOW.size]
            b.tri(x - 0.55f, y, 0f, x + 0.55f, y, 0f, x, y - 1.15f, 0f, c)
            b.tri(x - 0.55f, y, 0f, x, y - 1.15f, 0f, x + 0.55f, y, 0f, Col.mul(c, 0.85f))
        }
    }
}
