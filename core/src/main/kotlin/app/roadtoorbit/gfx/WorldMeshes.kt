package app.roadtoorbit.gfx

import app.roadtoorbit.gfx.Col.rgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural low-poly models for everything in the world. Convention: collision objects are
 * authored around their collision centre (so the entity position is the model origin); scenery
 * (trees, rocks, billboards) is authored standing on y = 0.
 */
private fun Mathx_smoothstep(e0: Float, e1: Float, v: Float): Float {
    val t = ((v - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal object WorldMeshes {
    private val ASPHALT = rgb(0x383B43)
    private val ASPHALT_LIGHT = rgb(0x42454E)
    private val WHITE = rgb(0xF4F4F2)
    private val YELLOW = rgb(0xF2C230)
    private val RED = rgb(0xD62F2F)
    private val GRAVEL = rgb(0x7B786C)
    private val STEEL = rgb(0xA9B1BD)
    private val STEEL_DARK = rgb(0x5B6372)
    private val BLACK = rgb(0x17181C)
    private val GLASS = rgb(0x28384C)
    private val GLASS_LIGHT = rgb(0x4B6987)

    private val CAR_PAINTS = intArrayOf(
        rgb(0x2D7BE0), rgb(0xF2B21C), rgb(0x2DB36B), rgb(0xE8E8EE), rgb(0x8A4FD8), rgb(0xEE6B2B),
    )

    fun build(id: MeshId, b: MeshBuilder) {
        when (id) {
            MeshId.ROAD_SEGMENT -> roadSegment(b)
            MeshId.SEDAN_0, MeshId.SEDAN_1, MeshId.SEDAN_2, MeshId.SEDAN_3, MeshId.SEDAN_4, MeshId.SEDAN_5 ->
                sedan(b, CAR_PAINTS[id.ordinal - MeshId.SEDAN_0.ordinal])
            MeshId.TRUCK_0, MeshId.TRUCK_1, MeshId.TRUCK_2 -> truck(b, CAR_PAINTS[(id.ordinal - MeshId.TRUCK_0.ordinal) * 2])
            MeshId.VAN_0, MeshId.VAN_1, MeshId.VAN_2 -> van(b, CAR_PAINTS[(id.ordinal - MeshId.VAN_0.ordinal) * 2 + 1])
            MeshId.BARRIER -> barrier(b)
            MeshId.CONE -> cone(b)
            MeshId.BARREL -> barrel(b)
            MeshId.COIN -> coin(b)
            MeshId.NITRO -> nitro(b)
            MeshId.REPAIR -> repair(b)
            MeshId.PINE_0 -> pine(b, 3, 0)
            MeshId.PINE_1 -> pine(b, 4, 1)
            MeshId.PINE_2 -> pine(b, 2, 2)
            MeshId.ROUND_TREE_0 -> roundTree(b, 0)
            MeshId.ROUND_TREE_1 -> roundTree(b, 1)
            MeshId.ROUND_TREE_2 -> roundTree(b, 2)
            MeshId.ROCK_0 -> b.translate(0f, 0.45f, 0f).icosphere(1f, 1, rgb(0x8A8780), 0.28f, 3, 0.12f)
            MeshId.ROCK_1 -> b.translate(0f, 0.4f, 0f).scale(1.2f, 0.8f, 1f).icosphere(1f, 1, rgb(0x7B7A78), 0.3f, 8, 0.12f)
            MeshId.ROCK_2 -> b.translate(0f, 0.5f, 0f).icosphere(1f, 2, rgb(0x938F86), 0.34f, 15, 0.14f)
            MeshId.BILLBOARD_0, MeshId.BILLBOARD_1, MeshId.BILLBOARD_2, MeshId.BILLBOARD_3 -> billboard(b, id.ordinal - MeshId.BILLBOARD_0.ordinal)
            MeshId.START_ARCH -> startArch(b)
            MeshId.GATE_ARCH_BIG -> gateArch(b, big = true)
            MeshId.GATE_ARCH_SMALL -> gateArch(b, big = false)
            MeshId.RAMP -> ramp(b)

            MeshId.PEAK_0 -> peak(b, 7, 0)
            MeshId.PEAK_1 -> peak(b, 6, 1)
            MeshId.PEAK_2 -> peak(b, 8, 2)
            MeshId.BALLOON_0, MeshId.BALLOON_1, MeshId.BALLOON_2, MeshId.BALLOON_3 -> balloon(b, id.ordinal - MeshId.BALLOON_0.ordinal)
            MeshId.STORM_0 -> storm(b, 0)
            MeshId.STORM_1 -> storm(b, 1)
            MeshId.BOLT -> bolt(b)
            MeshId.JET_0 -> jet(b, rgb(0x59616E), rgb(0xE8B020))
            MeshId.JET_1 -> jet(b, rgb(0x6B4F7A), rgb(0x4FD0FF))
            MeshId.JET_2 -> jet(b, rgb(0x3F6B57), rgb(0xFF6B6B))
            MeshId.RING -> ring(b)
            MeshId.ORB -> orb(b)
            MeshId.CLOUD_0 -> cloud(b, 0)
            MeshId.CLOUD_1 -> cloud(b, 1)
            MeshId.CLOUD_2 -> cloud(b, 2)
            MeshId.PORTAL_BIG -> portal(b, big = true)
            MeshId.PORTAL_SMALL -> portal(b, big = false)

            MeshId.ASTEROID_0 -> asteroid(b, 0)
            MeshId.ASTEROID_1 -> asteroid(b, 1)
            MeshId.ASTEROID_2 -> asteroid(b, 2)
            MeshId.ASTEROID_3 -> asteroid(b, 3)
            MeshId.SATELLITE -> satellite(b)
            MeshId.CRYSTAL -> crystal(b)
            MeshId.PLANET_0 -> planet(b, 0)
            MeshId.PLANET_1 -> planet(b, 1)
            MeshId.PLANET_2 -> planet(b, 2)
            MeshId.PLANET_3 -> planet(b, 3)
            MeshId.PLANET_RING -> b.rotateX(90f).scale(1f, 1f, 0.02f).torus(1.7f, 0.22f, 48, 5, rgb(0xD9C7A0), rgb(0xB8A27A))
            MeshId.EARTH -> earth(b)
            MeshId.MOON -> moon(b)
            else -> b.box(1f, 1f, 1f, rgb(0xFF00FF))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // car leg
    // ---------------------------------------------------------------------------------------------

    /** 24 m of road, near edge at z = 0, far edge at z = -24; repeats seamlessly. */
    private fun roadSegment(b: MeshBuilder) {
        val len = 24f
        val y = 0.03f
        // asphalt slab (slightly different shade each half to hint at texture)
        b.push().translate(0f, y - 0.03f, -len / 2).box(13.0f, 0.06f, len, ASPHALT, ASPHALT, ASPHALT, ASPHALT).pop()
        b.push().translate(-3f, y + 0.001f, -len / 2).box(5.9f, 0.04f, len, ASPHALT_LIGHT, ASPHALT, ASPHALT, ASPHALT).pop()
        b.push().translate(3f, y + 0.001f, -len / 2).box(5.9f, 0.04f, len, ASPHALT_LIGHT, ASPHALT, ASPHALT, ASPHALT).pop()
        // gravel shoulders
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 8.25f, 0.0f, -len / 2).box(3.7f, 0.05f, len, GRAVEL).pop()
        }
        // edge lines
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 5.85f, y + 0.03f, -len / 2).box(0.16f, 0.02f, len, WHITE).pop()
        }
        // centre double yellow
        for (off in floatArrayOf(-0.14f, 0.14f)) {
            b.push().translate(off, y + 0.03f, -len / 2).box(0.1f, 0.02f, len, YELLOW).pop()
        }
        // dashed lane lines: 6 m dash, 6 m gap
        for (x in floatArrayOf(-3f, 3f)) {
            for (k in 0 until 2) {
                b.push().translate(x, y + 0.03f, -(k * 12f + 3f)).box(0.14f, 0.02f, 6f, WHITE).pop()
            }
        }
        // red/white rumble kerbs
        for (side in intArrayOf(-1, 1)) {
            for (k in 0 until 8) {
                val c = if (k % 2 == 0) RED else WHITE
                b.push().translate(side * 6.35f, 0.07f, -(k * 3f + 1.5f)).box(0.55f, 0.12f, 3f, c).pop()
            }
        }
        // guard rails: posts every 3 m and a continuous rail
        for (side in intArrayOf(-1, 1)) {
            val x = side * 7.2f
            for (k in 0 until 8) {
                b.push().translate(x, 0.4f, -(k * 3f + 1.5f)).box(0.14f, 0.8f, 0.14f, STEEL_DARK).pop()
            }
            b.push().translate(x - side * 0.05f, 0.62f, -len / 2).box(0.1f, 0.3f, len, STEEL, STEEL, STEEL_DARK, STEEL_DARK).pop()
        }
        // one lamp post per segment, alternating sides by position in the pattern
        b.push().translate(-8.2f, 0f, -12f)
        b.push().translate(0f, 3.4f, 0f).box(0.18f, 6.8f, 0.18f, STEEL_DARK).pop()
        b.push().translate(0.9f, 6.75f, 0f).box(1.9f, 0.14f, 0.14f, STEEL_DARK).pop()
        b.push().translate(1.75f, 6.62f, 0f).box(0.7f, 0.18f, 0.35f, rgb(0xFFF1B8)).pop()
        b.pop()
        b.push().translate(8.2f, 0f, -24f + 0.01f).rotateY(180f)
        b.push().translate(0f, 3.4f, 0f).box(0.18f, 6.8f, 0.18f, STEEL_DARK).pop()
        b.push().translate(0.9f, 6.75f, 0f).box(1.9f, 0.14f, 0.14f, STEEL_DARK).pop()
        b.push().translate(1.75f, 6.62f, 0f).box(0.7f, 0.18f, 0.35f, rgb(0xFFF1B8)).pop()
        b.pop()
    }

    /** Wheels shared by the traffic models: cylinder axis along X. */
    private fun wheel(b: MeshBuilder, x: Float, y: Float, z: Float, r: Float, w: Float) {
        b.push().translate(x, y, z).rotateZ(90f)
        b.cylinder(r, r, w, 12, rgb(0x1B1C20), rgb(0x1B1C20), smooth = true)
        b.push().translate(0f, if (x > 0) w * 0.51f else -w * 0.51f, 0f).cylinder(r * 0.55f, r * 0.55f, 0.02f, 8, rgb(0xB9C0CB), rgb(0xB9C0CB), smooth = false).pop()
        b.pop()
    }

    private fun sedan(b: MeshBuilder, paint: Int) {
        val g = -0.65f // ground level relative to the model centre
        // body
        b.push().translate(0f, g + 0.52f, 0f).box(1.9f, 0.56f, 4.2f, paint, Col.mul(paint, 0.92f), Col.mul(paint, 0.8f), rgb(0x202226)).pop()
        // hood / trunk slope hints
        b.push().translate(0f, g + 0.82f, -1.2f).box(1.7f, 0.1f, 1.4f, Col.mul(paint, 1.05f)).pop()
        // cabin with glass sides
        b.push().translate(0f, g + 1.1f, 0.25f).box(1.62f, 0.52f, 2.0f, Col.mul(paint, 1.0f), GLASS_LIGHT, GLASS, paint).pop()
        // lights and bumpers
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 0.65f, g + 0.62f, -2.12f).box(0.42f, 0.16f, 0.08f, rgb(0xFFF3C8)).pop()
            b.push().translate(side * 0.7f, g + 0.64f, 2.12f).box(0.4f, 0.14f, 0.08f, rgb(0xE02020)).pop()
        }
        b.push().translate(0f, g + 0.32f, -2.12f).box(1.9f, 0.18f, 0.1f, rgb(0x2A2C32)).pop()
        b.push().translate(0f, g + 0.32f, 2.12f).box(1.9f, 0.18f, 0.1f, rgb(0x2A2C32)).pop()
        for (sx in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) wheel(b, sx * 0.93f, g + 0.34f, sz * 1.3f, 0.34f, 0.26f)
    }

    private fun truck(b: MeshBuilder, paint: Int) {
        val g = -1.6f
        // cab
        b.push().translate(0f, g + 1.25f, -2.55f).box(2.2f, 2.0f, 2.0f, paint, Col.mul(paint, 0.95f), Col.mul(paint, 0.8f), BLACK).pop()
        b.push().translate(0f, g + 1.6f, -3.52f).box(1.9f, 0.7f, 0.06f, GLASS_LIGHT).pop()
        // chassis + cargo box
        b.push().translate(0f, g + 0.55f, 0.2f).box(2.2f, 0.4f, 6.4f, rgb(0x2B2D33)).pop()
        b.push().translate(0f, g + 2.3f, 0.9f).box(2.5f, 2.7f, 5.2f, rgb(0xF0F1F4), rgb(0xE6E8EE), rgb(0xCDD1DA), rgb(0x9AA0AB)).pop()
        b.push().translate(0f, g + 2.3f, 0.9f).box(2.54f, 0.5f, 5.0f, paint, paint, paint, paint).pop()
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 0.8f, g + 0.82f, -3.7f).box(0.5f, 0.2f, 0.08f, rgb(0xFFF3C8)).pop()
            b.push().translate(side * 0.9f, g + 1.0f, 3.52f).box(0.4f, 0.2f, 0.08f, rgb(0xE02020)).pop()
        }
        for (sx in intArrayOf(-1, 1)) for (z in floatArrayOf(-2.4f, 1.4f, 2.8f)) wheel(b, sx * 1.1f, g + 0.5f, z, 0.5f, 0.34f)
    }

    private fun van(b: MeshBuilder, paint: Int) {
        val g = -1.1f
        b.push().translate(0f, g + 1.0f, 0.2f).box(2.0f, 1.7f, 4.6f, paint, Col.mul(paint, 0.95f), Col.mul(paint, 0.8f), BLACK).pop()
        b.push().translate(0f, g + 0.8f, -2.25f).box(1.9f, 0.9f, 0.9f, Col.mul(paint, 1.05f)).pop()
        b.push().translate(0f, g + 1.55f, -1.6f).box(1.76f, 0.6f, 1.5f, Col.mul(paint, 1.0f), GLASS_LIGHT, GLASS, paint).pop()
        b.push().translate(0f, g + 1.1f, 2.51f).box(1.6f, 0.9f, 0.04f, Col.mul(paint, 0.85f)).pop()
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 0.7f, g + 0.7f, -2.72f).box(0.4f, 0.16f, 0.06f, rgb(0xFFF3C8)).pop()
            b.push().translate(side * 0.8f, g + 0.9f, 2.52f).box(0.3f, 0.3f, 0.06f, rgb(0xE02020)).pop()
        }
        for (sx in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) wheel(b, sx * 0.95f, g + 0.36f, sz * 1.5f, 0.36f, 0.26f)
    }

    private fun barrier(b: MeshBuilder) {
        val g = -0.5f
        val poly = floatArrayOf(-0.55f, 0f, 0.55f, 0f, 0.18f, 1.0f, -0.18f, 1.0f)
        for (k in 0 until 3) {
            val c = if (k % 2 == 0) rgb(0xE23B2E) else rgb(0xF2F2EE)
            b.push().translate(-1.0f + k * 1.0f, g, 0f).prismZY(poly, 0.98f, c, c, rgb(0x55575C)).pop()
        }
        // warning light on top
        b.push().translate(0f, g + 1.08f, 0f).box(0.25f, 0.16f, 0.25f, rgb(0xFFB020)).pop()
    }

    private fun cone(b: MeshBuilder) {
        val g = -0.5f
        b.push().translate(0f, g + 0.04f, 0f).box(0.95f, 0.08f, 0.95f, rgb(0x2A2C32)).pop()
        b.push().translate(0f, g + 0.5f, 0f).cylinder(0.04f, 0.4f, 0.84f, 12, rgb(0xFF6B1A), rgb(0xFF5A0A), smooth = true, capBottom = false)
        b.pop()
        b.push().translate(0f, g + 0.5f, 0f).cylinder(0.14f, 0.28f, 0.28f, 12, rgb(0xF4F4F0), rgb(0xF4F4F0), smooth = true, capTop = false, capBottom = false).pop()
    }

    private fun barrel(b: MeshBuilder) {
        val g = -0.7f
        b.push().translate(0f, g + 0.7f, 0f).cylinder(0.56f, 0.6f, 1.4f, 14, rgb(0xE8452E), rgb(0xC63A28), smooth = true).pop()
        for (h in floatArrayOf(0.35f, 1.05f)) {
            b.push().translate(0f, g + h, 0f).cylinder(0.6f, 0.6f, 0.12f, 14, rgb(0xF4F2EC), rgb(0xF4F2EC), smooth = true, capTop = false, capBottom = false).pop()
        }
        b.push().translate(0f, g + 1.4f, 0f).cylinder(0.4f, 0.4f, 0.04f, 12, rgb(0x2A2C32), rgb(0x2A2C32), smooth = false).pop()
    }

    private fun coin(b: MeshBuilder) {
        // disc facing the player (axis along Z); spin about Y shows the thin edge
        b.rotateX(90f)
        b.cylinder(0.85f, 0.85f, 0.16f, 20, rgb(0xFFD23C), rgb(0xFFB400), smooth = true)
        b.push().cylinder(0.62f, 0.62f, 0.2f, 16, rgb(0xFFE98A), rgb(0xFFC83A), smooth = false).pop()
    }

    private fun nitro(b: MeshBuilder) {
        b.push().cylinder(0.55f, 0.55f, 1.5f, 14, rgb(0x2E8BFF), rgb(0x1B5FD0), smooth = true).pop()
        b.push().translate(0f, 0.85f, 0f).cylinder(0.25f, 0.4f, 0.22f, 12, rgb(0xE5ECF5), rgb(0xAEB8C6), smooth = true).pop()
        b.push().cylinder(0.575f, 0.575f, 0.3f, 14, rgb(0x9DF2FF), rgb(0x9DF2FF), smooth = true, capTop = false, capBottom = false).pop()
        b.push().translate(0f, -0.82f, 0f).cylinder(0.4f, 0.5f, 0.16f, 12, rgb(0x384150), rgb(0x2A303B), smooth = true).pop()
        b.push().translate(0f, 0.3f, 0.5f).box(0.12f, 0.6f, 0.04f, rgb(0xFFF176)).pop()
    }

    private fun repair(b: MeshBuilder) {
        b.push().box(1.9f, 1.9f, 0.5f, rgb(0xF2F6F4)).pop()
        b.push().translate(0f, 0f, 0.28f).box(1.3f, 0.42f, 0.1f, rgb(0x1DBA55)).pop()
        b.push().translate(0f, 0f, 0.28f).box(0.42f, 1.3f, 0.1f, rgb(0x1DBA55)).pop()
        b.push().translate(0f, 0f, -0.28f).box(1.3f, 0.42f, 0.1f, rgb(0x1DBA55)).pop()
        b.push().translate(0f, 0f, -0.28f).box(0.42f, 1.3f, 0.1f, rgb(0x1DBA55)).pop()
    }

    private fun pine(b: MeshBuilder, tiers: Int, variant: Int) {
        val trunk = rgb(0x6B4A2E)
        val greens = intArrayOf(rgb(0x2F7D3A), rgb(0x286B33), rgb(0x3A8A3C))
        val g = greens[variant % 3]
        b.push().translate(0f, 0.9f, 0f).cylinder(0.22f, 0.3f, 1.8f, 7, trunk, rgb(0x4E3520), smooth = false).pop()
        var y = 1.6f
        var r = 2.0f
        for (t in 0 until tiers) {
            val c = Col.mul(g, 0.85f + 0.12f * t)
            b.push().translate(0f, y + 1.1f, 0f).cone(r, 2.6f, 8, Col.mul(c, 1.15f), Col.mul(c, 0.85f), smooth = false).pop()
            y += 1.45f
            r *= 0.78f
        }
    }

    private fun roundTree(b: MeshBuilder, variant: Int) {
        val trunk = rgb(0x73502F)
        val leaf = intArrayOf(rgb(0x5BAA3A), rgb(0x7BB83D), rgb(0x3F9B4B))[variant % 3]
        b.push().translate(0f, 1.1f, 0f).cylinder(0.25f, 0.34f, 2.2f, 7, trunk, rgb(0x4E3520), smooth = false).pop()
        b.push().translate(0f, 3.4f, 0f).icosphere(1.9f, 1, leaf, 0.12f, variant + 4, 0.1f).pop()
        b.push().translate(0.9f, 2.9f, 0.3f).icosphere(1.2f, 1, Col.mul(leaf, 0.92f), 0.12f, variant + 9, 0.1f).pop()
        b.push().translate(-0.8f, 3.0f, -0.4f).icosphere(1.3f, 1, Col.mul(leaf, 1.06f), 0.12f, variant + 13, 0.1f).pop()
    }

    private fun billboard(b: MeshBuilder, variant: Int) {
        val panels = intArrayOf(rgb(0xFFC21A), rgb(0x2D7BE0), rgb(0xE23B2E), rgb(0x2DB36B))
        val c = panels[variant % 4]
        b.push().translate(-2.4f, 3.0f, 0f).box(0.3f, 6.0f, 0.3f, STEEL_DARK).pop()
        b.push().translate(2.4f, 3.0f, 0f).box(0.3f, 6.0f, 0.3f, STEEL_DARK).pop()
        b.push().translate(0f, 6.6f, 0f).box(7.2f, 3.4f, 0.3f, rgb(0xF4F4F2)).pop()
        b.push().translate(0f, 6.6f, 0.18f).box(6.7f, 2.9f, 0.1f, c).pop()
        // simple pictograms: big chevrons / stripes so no text is needed
        when (variant % 4) {
            0 -> for (k in -1..1) b.push().translate(k * 1.6f, 6.6f, 0.26f).rotateZ(-45f).box(0.5f, 2.0f, 0.06f, rgb(0x20222A)).pop()
            1 -> { b.push().translate(0f, 6.6f, 0.26f).cylinder(1.0f, 1.0f, 0.06f, 20, WHITE, WHITE, smooth = false).pop() }
            2 -> for (k in -2..2) b.push().translate(k * 1.2f, 6.6f, 0.26f).box(0.45f, 2.3f, 0.06f, WHITE).pop()
            else -> { b.push().translate(0f, 6.6f, 0.26f).box(4.2f, 0.5f, 0.06f, WHITE).pop(); b.push().translate(0f, 6.6f, 0.26f).box(0.5f, 2.0f, 0.06f, WHITE).pop() }
        }
    }

    private fun startArch(b: MeshBuilder) {
        for (side in intArrayOf(-1, 1)) b.push().translate(side * 7.4f, 4.2f, 0f).box(0.8f, 8.4f, 0.8f, STEEL_DARK).pop()
        b.push().translate(0f, 8.2f, 0f).box(15.6f, 1.6f, 0.9f, rgb(0x20222A)).pop()
        // chequered strip
        val cols = 24
        val w = 15.0f / cols
        for (i in 0 until cols) for (j in 0 until 2) {
            val c = if ((i + j) % 2 == 0) WHITE else BLACK
            b.push().translate(-7.5f + w * (i + 0.5f), 8.45f - j * 0.62f, 0.5f).box(w, 0.62f, 0.06f, c).pop()
        }
        b.push().translate(0f, 7.3f, 0.0f).box(14.6f, 0.12f, 0.6f, rgb(0x4DE0FF)).pop()
    }

    private fun gateArch(b: MeshBuilder, big: Boolean) {
        val h = if (big) 10f else 6.5f
        val w = if (big) 15.2f else 14.6f
        val glow = rgb(0x4DE0FF)
        val glow2 = rgb(0xFF4DD2)
        val t = if (big) 0.9f else 0.45f
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * w / 2, h / 2, 0f).box(t, h, t, if (big) STEEL_DARK else glow).pop()
            if (big) b.push().translate(side * w / 2, h / 2, 0.0f).box(t * 0.4f, h * 0.98f, t * 1.15f, glow).pop()
        }
        b.push().translate(0f, h, 0f).box(w + t, if (big) 1.3f else 0.5f, t, if (big) STEEL_DARK else glow).pop()
        if (big) {
            b.push().translate(0f, h, 0.0f).box(w, 0.5f, t * 1.2f, glow).pop()
            // glowing chevrons across the top beam
            for (k in -3..3) {
                b.push().translate(k * 1.9f, h + 1.25f, 0f).rotateZ(if (k % 2 == 0) 0f else 0f).box(1.2f, 0.9f, 0.4f, if (k % 2 == 0) glow else glow2).pop()
            }
            // a hovering "swirl" ring in the gateway
            b.push().translate(0f, h * 0.5f, 0f).torus(h * 0.46f, 0.12f, 36, 6, glow2, glow).pop()
        }
    }

    private fun ramp(b: MeshBuilder) {
        // low end at z = 0, high end at z = -62 (11 m up). Concrete sides, asphalt top, glowing chevrons.
        val poly = floatArrayOf(-62f, 0f, 0f, 0f, -62f, 11f)
        b.prismZY(poly, 13f, rgb(0x6D717C), ASPHALT, rgb(0x2A2C32))
        val ang = Math.toDegrees(Math.atan2(11.0, 62.0)).toFloat()
        for (k in 0 until 9) {
            val dz = -4f - k * 6.6f
            b.push().translate(0f, 11f * (-dz / 62f) + 0.06f, dz).rotateX(ang).box(12f, 0.05f, 0.5f, if (k % 2 == 0) rgb(0x4DE0FF) else WHITE).pop()
        }
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 6.45f, 0f, 0f).prismZY(poly, 0.1f, rgb(0xFFB020), rgb(0xFFB020), rgb(0xFFB020)).pop()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // sky leg
    // ---------------------------------------------------------------------------------------------

    /** Rock spire standing on y = 0, unit radius and unit height (scaled by the entity). */
    private fun peak(b: MeshBuilder, segs: Int, variant: Int) {
        val rock = intArrayOf(rgb(0x7D766E), rgb(0x6F6A66), rgb(0x857A6E))[variant % 3]
        b.push().translate(0f, 0.5f, 0f).cylinder(0.32f, 1.0f, 1.0f, segs, Col.mul(rock, 1.15f), Col.mul(rock, 0.8f), smooth = false).pop()
        b.push().translate(0f, 0.92f, 0f).cone(0.34f, 0.2f, segs, rgb(0xF4F7FA), rgb(0xE2E8F0), smooth = false, cap = false).pop()
        b.push().translate(0.12f, 0.4f, 0.06f).rotateZ(6f).cylinder(0.22f, 0.5f, 0.8f, segs, Col.mul(rock, 1.05f), Col.mul(rock, 0.75f), smooth = false).pop()
    }

    private fun balloon(b: MeshBuilder, variant: Int) {
        val a = intArrayOf(rgb(0xE23B2E), rgb(0x2D7BE0), rgb(0xF2B21C), rgb(0x8A4FD8))[variant % 4]
        val c = rgb(0xF4F2EA)
        // striped envelope: gores coloured alternately by longitude
        val lon = 12
        val lat = 7
        b.push().translate(0f, 0.7f, 0f).scale(1f, 1.08f, 1f)
        for (j in 0 until lon) {
            val col = if (j % 2 == 0) a else c
            val g0 = (2.0 * PI * j / lon).toFloat()
            val g1 = (2.0 * PI * (j + 1) / lon).toFloat()
            for (i in 0 until lat) {
                val f0 = (-PI / 2 + PI * i / lat).toFloat()
                val f1 = (-PI / 2 + PI * (i + 1) / lat).toFloat()
                val r = 3.0f
                val ax = r * cos(f0) * cos(g0); val ay = r * sin(f0); val az = r * cos(f0) * sin(g0)
                val bx = r * cos(f0) * cos(g1); val by = r * sin(f0); val bz = r * cos(f0) * sin(g1)
                val cx = r * cos(f1) * cos(g1); val cy = r * sin(f1); val cz = r * cos(f1) * sin(g1)
                val dx = r * cos(f1) * cos(g0); val dy = r * sin(f1); val dz = r * cos(f1) * sin(g0)
                val shade = Col.mul(col, 0.8f + 0.2f * (i.toFloat() / lat))
                if (i < lat - 1) b.tri(ax, ay, az, dx, dy, dz, cx, cy, cz, shade)
                if (i > 0) b.tri(ax, ay, az, cx, cy, cz, bx, by, bz, shade)
            }
        }
        b.pop()
        // basket and cords
        b.push().translate(0f, -3.3f, 0f).box(1.0f, 0.7f, 1.0f, rgb(0x8A5A2B), rgb(0x7A4E26), rgb(0x5E3C1C), rgb(0x4E3016)).pop()
        for (sx in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) {
            b.push().translate(sx * 0.45f, -2.4f, sz * 0.45f).box(0.05f, 1.9f, 0.05f, rgb(0x3A2A1A)).pop()
        }
        b.push().translate(0f, -2.4f, 0f).cone(0.28f, 0.5f, 8, rgb(0xFFC040), rgb(0xFF7A20), smooth = false).pop()
    }

    private fun storm(b: MeshBuilder, variant: Int) {
        // a wide, flat-bottomed thundercloud: dark underside, lighter billowing top
        val under = rgb(0x30344A)
        val mid = rgb(0x46506A)
        val top = rgb(0x6A7592)
        val rnd = variant * 31
        b.scale(1.25f, 0.85f, 1.1f)
        val ring = if (variant == 0) 6 else 8
        for (k in 0 until ring) {
            val a = k * (2f * PI.toFloat() / ring) + variant * 0.4f
            b.push().translate(cos(a) * 0.62f, -0.12f, sin(a) * 0.5f).icosphere(0.5f, 1, under, 0.18f, rnd + k, 0.08f).pop()
        }
        for (k in 0 until 5) {
            val a = k * (2f * PI.toFloat() / 5) + 0.7f + variant
            b.push().translate(cos(a) * 0.38f, 0.18f, sin(a) * 0.34f).icosphere(0.5f, 1, mid, 0.18f, rnd + 20 + k, 0.08f).pop()
        }
        b.push().translate(0f, 0.34f, 0f).icosphere(0.72f, 1, top, 0.14f, rnd + 90, 0.07f).pop()
        b.push().translate(0.3f, 0.5f, -0.15f).icosphere(0.42f, 1, top, 0.14f, rnd + 91, 0.07f).pop()
        // faint inner flicker so the lightning has a source
        b.push().translate(0f, -0.15f, 0.1f).icosphere(0.22f, 1, rgb(0xB9D2FF), 0.08f, rnd, 0f).pop()
    }

    private fun bolt(b: MeshBuilder) {
        val c = rgb(0xCFE4FF)
        // a jagged zig-zag of thin boxes hanging downward
        var x = 0f
        var y = 0f
        for (k in 0 until 7) {
            val nx = x + if (k % 2 == 0) 0.45f else -0.45f
            val ny = y - 1.0f
            val dx = nx - x
            val dy = ny - y
            val len = sqrt(dx * dx + dy * dy)
            val ang = Math.toDegrees(Math.atan2(dx.toDouble(), -dy.toDouble())).toFloat()
            b.push().translate((x + nx) / 2, (y + ny) / 2, 0f).rotateZ(ang).box(0.16f, len, 0.16f, c).pop()
            x = nx; y = ny
        }
    }

    private fun jet(b: MeshBuilder, body: Int, accent: Int) {
        // heading -Z; fuselage
        b.push().rotateX(90f).cylinder(0.5f, 0.5f, 4.4f, 10, Col.mul(body, 1.1f), Col.mul(body, 0.9f), smooth = true, capTop = true, capBottom = true).pop()
        b.push().translate(0f, 0f, -2.7f).rotateX(-90f).cone(0.5f, 1.2f, 10, rgb(0xDADFE6), Col.mul(body, 1.1f), smooth = true).pop()
        b.push().translate(0f, 0.38f, -0.9f).scale(0.4f, 0.32f, 1.0f).sphere(1f, 5, 10, rgb(0x6FA3D6), rgb(0x1E2F45), smooth = true, latFromDeg = 0f, latToDeg = 90f).pop()
        val wing = floatArrayOf(-0.6f, 0f, 0.9f, 0f, 1.35f, 1f, 0.8f, 1f)
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 0.3f, 0f, 0.3f).rotateZ(side * -90f).scale(1f, 1.9f, 1f).prismZY(wing, 0.1f, body, accent, accent).pop()
            b.push().translate(side * 0.2f, 0.1f, 1.8f).rotateZ(side * -90f).scale(1f, 0.8f, 0.6f).prismZY(wing, 0.1f, body, accent, accent).pop()
        }
        b.push().translate(0f, 0.3f, 1.8f).scale(1f, 0.9f, 0.7f).prismZY(wing, 0.1f, body, accent, accent).pop()
        b.push().translate(0f, 0f, 2.35f).rotateX(-90f).cylinder(0.4f, 0.28f, 0.3f, 10, rgb(0x2A2C32), rgb(0x2A2C32), smooth = true).pop()
        b.push().translate(0f, 0f, 2.55f).box(0.4f, 0.4f, 0.1f, rgb(0xFFAA55)).pop()
    }

    private fun ring(b: MeshBuilder) {
        b.torus(5.2f, 0.34f, 40, 8, rgb(0x6FF3FF), rgb(0xFFFFFF))
        b.push().torus(5.2f, 0.12f, 40, 5, rgb(0xFFE08A), rgb(0xFFE08A)).pop()
        // four marker pylons make the ring easy to read from afar
        for (k in 0 until 4) {
            val a = (k * PI / 2).toFloat()
            b.push().translate(cos(a) * 5.2f, sin(a) * 5.2f, 0f).rotateZ(Math.toDegrees(a.toDouble()).toFloat()).box(0.9f, 0.5f, 0.5f, rgb(0xFF9A3C)).pop()
        }
    }

    private fun orb(b: MeshBuilder) {
        b.icosphere(1.1f, 2, rgb(0x6FEFFF), 0.02f, 3, 0f)
        b.push().rotateX(90f).torus(1.7f, 0.07f, 28, 5, rgb(0xFFFFFF)).pop()
        b.push().rotateY(90f).torus(1.7f, 0.07f, 28, 5, rgb(0x9DF2FF)).pop()
    }

    private fun cloud(b: MeshBuilder, variant: Int) {
        val white = rgb(0xFFFFFF)
        val shade = rgb(0xDCE6F4)
        b.scale(1f, 0.62f, 1f)
        val n = 5 + variant
        for (k in 0 until n) {
            val a = k * 2.4f + variant * 1.3f
            val r = 0.55f + 0.2f * sin(k * 2.1f + variant)
            val x = cos(a) * 0.7f * (0.5f + 0.5f * (k % 3) / 2f + 0.3f)
            val z = sin(a) * 0.55f
            val y = 0.18f * (k % 2) + (if (k == 0) 0.25f else 0f)
            b.push().translate(x, y, z).icosphere(r, 1, if (k % 2 == 0) white else shade, 0.12f, variant * 13 + k, 0.03f).pop()
        }
        b.push().translate(0f, 0.2f, 0f).icosphere(0.75f, 1, white, 0.1f, variant + 70, 0.02f).pop()
    }

    private fun portal(b: MeshBuilder, big: Boolean) {
        val cyan = rgb(0x4DE0FF)
        val magenta = rgb(0xFF4DD2)
        if (big) {
            b.torus(15f, 0.9f, 56, 8, cyan, rgb(0xFFFFFF))
            b.push().scale(1f, 1f, 0.4f).torus(13.2f, 0.4f, 56, 6, magenta, magenta).pop()
            for (k in 0 until 16) {
                val a = (k * 2.0 * PI / 16).toFloat()
                b.push().translate(cos(a) * 15f, sin(a) * 15f, 0f).rotateZ(Math.toDegrees(a.toDouble()).toFloat()).box(2.2f, 0.9f, 1.4f, if (k % 2 == 0) rgb(0xFFFFFF) else magenta).pop()
            }
        } else {
            b.torus(9f, 0.38f, 44, 6, cyan, rgb(0xFFFFFF))
            for (k in 0 until 8) {
                val a = (k * 2.0 * PI / 8).toFloat()
                b.push().translate(cos(a) * 9f, sin(a) * 9f, 0f).rotateZ(Math.toDegrees(a.toDouble()).toFloat()).box(1.0f, 0.5f, 0.6f, magenta).pop()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // space leg
    // ---------------------------------------------------------------------------------------------

    private fun asteroid(b: MeshBuilder, variant: Int) {
        val base = intArrayOf(rgb(0x8E8579), rgb(0x7A736C), rgb(0x9A8F80), rgb(0x6F6B73))[variant % 4]
        b.icosphere(1f, 2, base, 0.30f + 0.04f * variant, variant * 17 + 5, 0.16f)
    }

    private fun satellite(b: MeshBuilder) {
        b.push().box(1.4f, 1.4f, 1.8f, rgb(0xE8C25A), rgb(0xD2A93E), rgb(0xB8902C), rgb(0x8A6A1C)).pop()
        b.push().translate(0f, 0f, -1.05f).box(0.9f, 0.9f, 0.3f, rgb(0xC8CED8)).pop()
        for (side in intArrayOf(-1, 1)) {
            b.push().translate(side * 1.1f, 0f, 0f).box(1.0f, 0.1f, 0.3f, STEEL_DARK).pop()
            for (k in 0 until 3) {
                val c = if (k % 2 == 0) rgb(0x2A4FA8) else rgb(0x3563C4)
                b.push().translate(side * (2.1f + k * 1.0f), 0f, 0f).box(0.92f, 0.06f, 1.5f, c, c, c, rgb(0x1A2E66)).pop()
            }
        }
        b.push().translate(0f, 0.95f, 0.3f).cylinder(0.05f, 0.05f, 0.6f, 6, STEEL, STEEL, smooth = false).pop()
        b.push().translate(0f, 1.35f, 0.3f).rotateX(-30f).cylinder(0.5f, 0.1f, 0.2f, 12, rgb(0xE8EDF4), rgb(0xB0B8C6), smooth = true).pop()
    }

    private fun crystal(b: MeshBuilder) {
        // octahedron built from two 4-sided cones
        b.push().translate(0f, 0.55f, 0f).rotateY(45f).cone(0.75f, 1.1f, 4, rgb(0xE0FFFF), rgb(0x3FD6FF), smooth = false, cap = false).pop()
        b.push().translate(0f, -0.55f, 0f).rotateY(45f).rotateX(180f).cone(0.75f, 1.1f, 4, rgb(0x9AF0FF), rgb(0x1FA3E8), smooth = false, cap = false).pop()
    }

    private fun planet(b: MeshBuilder, variant: Int) {
        // colours from latitude bands plus value-noise blotches; flat-shaded low poly
        val data = MeshBuilder(8000)
        data.icosphere(1f, 3, rgb(0xFFFFFF), 0.02f, variant + 40, 0f)
        recolorSphere(data.data, b, when (variant) {
            0 -> PlanetStyle(rgb(0xC9633B), rgb(0x8E3F24), rgb(0xE8A06A), 0.35f, 5f)
            1 -> PlanetStyle(rgb(0xE8D3A8), rgb(0xB58A55), rgb(0xF6EAD0), 0.0f, 9f)
            2 -> PlanetStyle(rgb(0x7FB9E8), rgb(0x3C78B8), rgb(0xDDF0FF), 0.2f, 6f)
            else -> PlanetStyle(rgb(0x6FC59A), rgb(0x2E7F68), rgb(0xCFF5DC), 0.3f, 7f)
        }, variant)
    }

    private class PlanetStyle(val a: Int, val b: Int, val c: Int, val blotch: Float, val bands: Float)

    private fun recolorSphere(src: MeshData, out: MeshBuilder, st: PlanetStyle, seed: Int) {
        for (i in 0 until src.triangleCount) {
            val v0 = i * 3
            val cx = (src.pos[v0 * 3] + src.pos[(v0 + 1) * 3] + src.pos[(v0 + 2) * 3]) / 3f
            val cy = (src.pos[v0 * 3 + 1] + src.pos[(v0 + 1) * 3 + 1] + src.pos[(v0 + 2) * 3 + 1]) / 3f
            val cz = (src.pos[v0 * 3 + 2] + src.pos[(v0 + 1) * 3 + 2] + src.pos[(v0 + 2) * 3 + 2]) / 3f
            val band = 0.5f + 0.5f * sin(cy * st.bands + MeshBuilder.valueNoise3(cx * 2f + seed, cy * 2f, cz * 2f) * 3f)
            val blotch = MeshBuilder.valueNoise3(cx * 3f + seed * 5f, cy * 3f, cz * 3f)
            var col = Col.mix(st.a, st.b, band)
            col = Col.mix(col, st.c, if (blotch > 0.62f) (blotch - 0.62f) * 2.2f * (0.4f + st.blotch) else 0f)
            if (abs(cy) > 0.86f) col = Col.mix(col, st.c, 0.6f)
            out.tri(
                src.pos[v0 * 3], src.pos[v0 * 3 + 1], src.pos[v0 * 3 + 2],
                src.pos[(v0 + 1) * 3], src.pos[(v0 + 1) * 3 + 1], src.pos[(v0 + 1) * 3 + 2],
                src.pos[(v0 + 2) * 3], src.pos[(v0 + 2) * 3 + 1], src.pos[(v0 + 2) * 3 + 2], col,
            )
        }
    }

    private fun earth(b: MeshBuilder) {
        val data = MeshBuilder(20000)
        data.icosphere(1f, 4, rgb(0xFFFFFF), 0.0f, 0, 0f)
        val src = data.data
        for (i in 0 until src.triangleCount) {
            val v0 = i * 3
            val cx = (src.pos[v0 * 3] + src.pos[(v0 + 1) * 3] + src.pos[(v0 + 2) * 3]) / 3f
            val cy = (src.pos[v0 * 3 + 1] + src.pos[(v0 + 1) * 3 + 1] + src.pos[(v0 + 2) * 3 + 1]) / 3f
            val cz = (src.pos[v0 * 3 + 2] + src.pos[(v0 + 1) * 3 + 2] + src.pos[(v0 + 2) * 3 + 2]) / 3f
            val land = MeshBuilder.valueNoise3(cx * 2.3f + 4f, cy * 2.3f, cz * 2.3f) * 0.7f + MeshBuilder.valueNoise3(cx * 5f, cy * 5f + 3f, cz * 5f) * 0.3f
            val cloud = MeshBuilder.valueNoise3(cx * 3.4f + 9f, cy * 3.4f + 2f, cz * 3.4f)
            var col = if (land > 0.56f) Col.mix(rgb(0x4FA05A), rgb(0xB5A36A), Col.r(rgb(0x000000)) + (land - 0.56f) * 2.2f) else Col.mix(rgb(0x14479F), rgb(0x2370C8), land)
            if (abs(cy) > 0.88f) col = rgb(0xF2F6FA)
            if (cloud > 0.64f) col = Col.mix(col, rgb(0xFFFFFF), (cloud - 0.64f) * 3f)
            b.tri(
                src.pos[v0 * 3], src.pos[v0 * 3 + 1], src.pos[v0 * 3 + 2],
                src.pos[(v0 + 1) * 3], src.pos[(v0 + 1) * 3 + 1], src.pos[(v0 + 1) * 3 + 2],
                src.pos[(v0 + 2) * 3], src.pos[(v0 + 2) * 3 + 1], src.pos[(v0 + 2) * 3 + 2], col,
            )
        }
    }

    private fun moon(b: MeshBuilder) {
        val data = MeshBuilder(20000)
        data.icosphere(1f, 4, rgb(0xFFFFFF), 0.0f, 0, 0f)
        val src = data.data
        for (i in 0 until src.triangleCount) {
            val v0 = i * 3
            val cx = (src.pos[v0 * 3] + src.pos[(v0 + 1) * 3] + src.pos[(v0 + 2) * 3]) / 3f
            val cy = (src.pos[v0 * 3 + 1] + src.pos[(v0 + 1) * 3 + 1] + src.pos[(v0 + 2) * 3 + 1]) / 3f
            val cz = (src.pos[v0 * 3 + 2] + src.pos[(v0 + 1) * 3 + 2] + src.pos[(v0 + 2) * 3 + 2]) / 3f
            val n1 = MeshBuilder.valueNoise3(cx * 2.2f + 2f, cy * 2.2f, cz * 2.2f)
            val n2 = MeshBuilder.valueNoise3(cx * 9f, cy * 9f + 5f, cz * 9f)
            // large dark "maria" plus fine speckle
            val shade = 0.50f + 0.36f * Mathx_smoothstep(0.34f, 0.66f, n1) + (n2 - 0.5f) * 0.12f
            val col = Col.make(shade * 0.98f, shade * 0.97f, shade * 0.95f)
            b.tri(
                src.pos[v0 * 3], src.pos[v0 * 3 + 1], src.pos[v0 * 3 + 2],
                src.pos[(v0 + 1) * 3], src.pos[(v0 + 1) * 3 + 1], src.pos[(v0 + 1) * 3 + 2],
                src.pos[(v0 + 2) * 3], src.pos[(v0 + 2) * 3 + 1], src.pos[(v0 + 2) * 3 + 2], col,
            )
        }
    }
}
