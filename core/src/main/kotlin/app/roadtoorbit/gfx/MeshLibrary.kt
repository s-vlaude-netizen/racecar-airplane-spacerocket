package app.roadtoorbit.gfx

import app.roadtoorbit.gfx.Col.rgb

/** Identifies every static mesh in the game. Dynamic meshes (vehicle body) are owned elsewhere. */
enum class MeshId {
    // vehicle parts
    WHEEL_TIRE, WHEEL_RIM, FIN, CANOPY, NOZZLE, LIGHT, FLAME_OUTER, FLAME_INNER, SHADOW,

    // showroom
    PLATFORM, PLATFORM_RING,

    // car leg
    ROAD_SEGMENT,
    SEDAN_0, SEDAN_1, SEDAN_2, SEDAN_3, SEDAN_4, SEDAN_5,
    TRUCK_0, TRUCK_1, TRUCK_2, VAN_0, VAN_1, VAN_2,
    BARRIER, CONE, BARREL, COIN, NITRO, REPAIR,
    PINE_0, PINE_1, PINE_2, ROUND_TREE_0, ROUND_TREE_1, ROUND_TREE_2, ROCK_0, ROCK_1, ROCK_2,
    BILLBOARD_0, BILLBOARD_1, BILLBOARD_2, BILLBOARD_3,
    START_ARCH, GATE_ARCH_BIG, GATE_ARCH_SMALL, RAMP,

    // sky leg
    PEAK_0, PEAK_1, PEAK_2, BALLOON_0, BALLOON_1, BALLOON_2, BALLOON_3, STORM_0, STORM_1, BOLT,
    JET_0, JET_1, JET_2, RING, ORB, CLOUD_0, CLOUD_1, CLOUD_2, PORTAL_BIG, PORTAL_SMALL,

    // space leg
    ASTEROID_0, ASTEROID_1, ASTEROID_2, ASTEROID_3, SATELLITE, CRYSTAL,
    PLANET_0, PLANET_1, PLANET_2, PLANET_3, PLANET_RING, EARTH, MOON,

    // Mars
    ROVER_0, ROVER_1, ROVER_2, HAULER_0, HAULER_1, HAULER_2, CRAWLER_0, CRAWLER_1, CRAWLER_2,
    MESA_0, MESA_1, MESA_2, DEVIL, DOME, SPIRE_0, SPIRE_1, SPIRE_2,
    SAUCER_0, SAUCER_1, SAUCER_2, SAUCER_3, MARS_JET_0, MARS_JET_1, MARS_JET_2, MARS, PHOBOS,
}

/**
 * Builds the CPU-side data for every static mesh on first request and keeps it, so that after an
 * Android GL context loss the GPU copies can be recreated without regenerating geometry.
 */
class MeshLibrary {
    private val cache = java.util.concurrent.ConcurrentHashMap<MeshId, MeshData>()

    /** Thread-safe: a background thread may prewarm while the GL thread asks for meshes. */
    fun data(id: MeshId): MeshData {
        cache[id]?.let { return it }
        val built = build(id) // building is pure, so a rare duplicate build is harmless
        return cache.putIfAbsent(id, built) ?: built
    }

    /**
     * Builds every mesh (and the baked nebula) on the calling thread. Call it from a background
     * thread at start-up so the first time something appears in the game never costs a frame.
     */
    fun prewarm() {
        NebulaCubeCache.faces
        for (id in MeshId.values()) data(id)
    }

    private fun build(id: MeshId): MeshData {
        val b = MeshBuilder()
        when (id) {
            MeshId.WHEEL_TIRE -> {
                // tyre: cylinder around the X axis, white so the part tint decides the colour
                b.rotateZ(90f).cylinder(0.36f, 0.36f, 0.30f, 18, rgb(0xFFFFFF), rgb(0xE8E8E8), smooth = true)
            }
            MeshId.WHEEL_RIM -> {
                b.rotateZ(90f)
                b.cylinder(0.22f, 0.22f, 0.325f, 12, rgb(0xFFFFFF), rgb(0xE0E0E0), smooth = true)
                b.push().cylinder(0.08f, 0.08f, 0.36f, 8, rgb(0xB0B0B0), rgb(0x909090), smooth = true).pop()
            }
            MeshId.FIN -> {
                // swept trapezoid in the (z, y) plane, height 1 along +Y, root chord 1.6
                val poly = floatArrayOf(-0.7f, 0f, 0.9f, 0f, 1.45f, 1f, 0.85f, 1f)
                b.prismZY(poly, 0.09f, rgb(0xFFFFFF), rgb(0xD6DAE0), rgb(0xBFC4CC))
            }
            MeshId.CANOPY -> {
                b.sphere(1f, 7, 16, rgb(0x5C86B5), rgb(0x1B2B3E), smooth = true, latFromDeg = 0f, latToDeg = 90f)
            }
            MeshId.NOZZLE -> {
                b.rotateX(90f).cylinder(0.38f, 0.22f, 0.7f, 14, rgb(0x8A93A3), rgb(0x4B5260), smooth = true, capTop = false, capBottom = true)
                b.push().translate(0f, 0.02f, 0f).cylinder(0.31f, 0.31f, 0.66f, 14, rgb(0x2A1608), rgb(0x2A1608), smooth = false, capTop = true, capBottom = false).pop()
            }
            MeshId.LIGHT -> b.box(1f, 1f, 1f, rgb(0xFFFFFF))
            MeshId.FLAME_OUTER -> {
                b.translate(0f, 0f, 0.5f).rotateX(90f)
                b.cylinder(0f, 1f, 1f, 14, rgb(0x330600), rgb(0xFF7A1A), smooth = true, capTop = false, capBottom = false)
            }
            MeshId.FLAME_INNER -> {
                b.translate(0f, 0f, 0.5f).rotateX(90f)
                b.cylinder(0f, 1f, 1f, 12, rgb(0x061233), rgb(0xCFE6FF), smooth = true, capTop = false, capBottom = false)
            }
            MeshId.SHADOW -> b.disc(1f, 22, Col.rgba(0x000000, 0.55f), Col.rgba(0x000000, 0f))
            MeshId.PLATFORM -> {
                b.cylinder(4.6f, 4.8f, 0.4f, 48, rgb(0x2B3345), rgb(0x1A2030), smooth = false)
                b.push().translate(0f, 0.21f, 0f).cylinder(4.1f, 4.1f, 0.04f, 48, rgb(0x3A4660), rgb(0x3A4660), smooth = false).pop()
            }
            MeshId.PLATFORM_RING -> {
                b.rotateX(90f).torus(4.35f, 0.07f, 64, 6, rgb(0x6FE8FF))
            }
            else -> WorldMeshes.build(id, b)
        }
        return b.build()
    }
}
