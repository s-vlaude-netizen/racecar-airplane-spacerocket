package app.roadtoorbit.gfx

/**
 * The ocean level ("THE DEEP"): a waterplane over the open sea, a submarine-rocket that dives into the abyss, and a
 * car that drives along the seabed to the lost city of Atlantis.
 *
 * This file is the contract between the people who place things (the spawner, the renderer) and the people who model
 * them (OceanVehicleMeshes, OceanSeaMeshes, OceanFloorMeshes): how big every model is, where its origin is and what
 * its collision box is. Same conventions as [WorldMeshes] and [MarsMeshes]:
 *
 *  - y is up, the vehicle and everything that moves along the route points to -Z (its nose / bow is at -Z);
 *  - a model that can be hit (hazards, pickups) is authored around its collision centre: the entity's position is the
 *    model's origin, and the model stays inside its collision shape (give or take what the notes below allow), so what
 *    the player sees is what it can hit;
 *  - scenery and "ground bound" models stand on y = 0 (the waterline for things on the sea, the seabed for things on
 *    the floor), with their middle on x = 0, z = 0;
 *  - colours are baked into vertex colours (and multiplied by the material's tint when drawn); the lit shader has
 *    a sun, an ambient term and fog but no textures, so shape and colour have to carry the model.
 *
 * Sizes are in metres; a "half extent" is half the box's width / height / length.
 */
object OceanSpec {
    // ----------------------------------------------------------------------------------------------------------
    // The sea (leg 1, the waterplane): the water is at y = 0, the plane flies between y = 1.2 and y = 30.
    // ----------------------------------------------------------------------------------------------------------

    /** Sea stacks (SEASTACK_0..2) are unit models like the sky leg's peaks: radius <= 1.1 and height 1, standing on y = 0; the entity scales them to (r, h, r). */
    const val STACK_UNIT_HEIGHT = 1f

    /**
     * Ships (SHIP_0 sailboat, SHIP_1 fishing trawler, SHIP_2 container ship): collision half extents (x, y, z). The model
     * stands on the waterline (its hull is mostly above y = 0, anything below does not matter), bow toward -Z, and
     * fills its box: the box's bottom is y = 0 and its middle is [hy] above.
     */
    val SHIP_HALF: Array<FloatArray> = arrayOf(
        floatArrayOf(2.2f, 6.0f, 5.0f), // sailboat: 11 m long, mast 12 m high
        floatArrayOf(3.2f, 5.0f, 10.0f), // trawler: 21 m long, wheelhouse and gantry 10 m high
        floatArrayOf(5.0f, 7.0f, 28.0f), // container ship: 56 m long, stacks and bridge 14 m high
    )

    /** A flock of gulls (FLOCK_0..3) is a sphere of radius 3 around the model's origin; the birds stay within 3.4 m of it. */
    const val FLOCK_RADIUS = 3f

    /** A rival seaplane (SEAPLANE_0..2), authored around its collision centre like the sky leg's jets: half extents (x, y, z). */
    val SEAPLANE_HALF: FloatArray = floatArrayOf(2.0f, 0.7f, 2.6f)

    // ----------------------------------------------------------------------------------------------------------
    // The dive (leg 2, the submarine-rocket): the vehicle flies between y = 3 and y = 28 under the surface.
    // ----------------------------------------------------------------------------------------------------------

    /** A sea mine (MINE_0, MINE_1) is a unit model: its hull is a sphere of radius 1 around the origin; the spawner scales it by the hit radius / 0.92. */
    const val MINE_HULL_RADIUS = 1f

    /** A jellyfish (JELLY_0, JELLY_1): the bell is a hemisphere-ish dome of radius ~2.9 centred on the origin (collision sphere radius 3). */
    const val JELLY_RADIUS = 3f

    /** Sharks (SHARK_0 great white, SHARK_1 hammerhead, MANTA): collision half extents (x, y, z), nose toward -Z, authored around the centre. */
    val SHARK_HALF: Array<FloatArray> = arrayOf(
        floatArrayOf(1.0f, 0.9f, 2.8f),
        floatArrayOf(1.2f, 0.8f, 2.9f),
        floatArrayOf(3.2f, 0.55f, 2.2f),
    )

    /** A pearl (PEARL), a pickup: around 1.1 m across, centred on the origin (pickup radius 1.5). */
    const val PEARL_RADIUS = 1.1f

    // ----------------------------------------------------------------------------------------------------------
    // The seabed (leg 3, the car): the same box sizes as the sedan, truck and van that the traffic of the other levels uses.
    // ----------------------------------------------------------------------------------------------------------

    /** SUBCAR_0..2 (like the sedan), SUBHAULER_0..2 (the truck), SUBVAN_0..2 (the van): half extents; the model is centred on its box and stands on its bottom. */
    val SUBCAR_HALF: FloatArray = floatArrayOf(0.95f, 0.65f, 2.1f)
    val SUBHAULER_HALF: FloatArray = floatArrayOf(1.25f, 1.6f, 3.7f)
    val SUBVAN_HALF: FloatArray = floatArrayOf(1.05f, 1.1f, 2.6f)

    /**
     * The sunken highway (ROAD_SEA): 24 m long, near edge at z = 0, far edge at z = -24, the same footprint, heights and seamless repeat as
     * the road of the other levels (WorldMeshes.roadSegment). ROAD_SEA_GLOW holds its glowing parts (lane dashes, edge lines, lamp globes) at the
     * same place and is drawn on top, emissive. The finale's city: ATLANTIS stands on y = 0 with its gate side toward +Z, and ATLANTIS_LIGHTS has
     * its windows, lamps and beacon rings (same origin, drawn emissive); ATLANTIS_GATE is the arch at the end of the road, standing on y = 0.
     */

    /** The barrier (CONTAINER, half buried in the sand): half extents (1.45, 0.5, 0.5), centred on the box, so it is 2.9 m wide, 1 m high and 1 m deep. */
    val CONTAINER_HALF: FloatArray = floatArrayOf(1.45f, 0.5f, 0.5f)

    /** The soft obstacle (KELP_TUFT): a clump of seaweed, collision sphere radius 0.55, centred on the origin (so 0.55 m above the seabed it stands on). */
    const val TUFT_RADIUS = 0.55f

    /** A crab (CRAB_0, CRAB_1) is a unit model: a body of about 1 m radius around the origin, the shell wider (x) than long (z); the spawner scales it by its hit radius. */
    const val CRAB_BODY_RADIUS = 1f

    // ----------------------------------------------------------------------------------------------------------
    // Scenery: a [app.roadtoorbit.game.Kind.SEASCAPE]'s variant picks its model from this table. All stand on y = 0
    // (islands on the waterline, the rest on the seabed) unless noted.
    // ----------------------------------------------------------------------------------------------------------

    const val ISLAND = 0 // 0..2: low tropical islands, 30 - 60 m across, 4 - 12 m high (palms, sand, a few rocks)
    const val CORAL = 3 // 3..5: reef heads, 3 - 9 m across, 2 - 7 m high
    const val KELP = 6 // 6..7: kelp stalks 10 - 18 m high
    const val WRECK = 8 // 8..9: a sunken ship lying on the seabed, 25 - 40 m long, 8 - 14 m high
    const val VENT = 10 // a hydrothermal chimney 5 - 8 m high with a glowing mouth
    const val RUINS = 11 // 11..13: Atlantean columns and arches, 6 - 14 m high
    const val SCHOOL = 14 // 14..15: a school of small fish, authored around its centre, within 6 m of it
    const val WHALE = 16 // a whale, authored around its centre: 28 m long, 7 m wide, 6 m high, swimming toward -Z
    const val MERFOLK = 17 // 17..19: a merperson standing on the tip of the tail, 3.3 m tall, arms raised, facing -Z (the welcome of Atlantis)
    const val BUNTING = 20 // a garland of flags between two poles 28 m apart across the avenue, in the XY plane, standing on y = 0, 11 m high

    val SEASCAPE: Array<MeshId> = arrayOf(
        MeshId.ISLAND_0, MeshId.ISLAND_1, MeshId.ISLAND_2,
        MeshId.CORAL_0, MeshId.CORAL_1, MeshId.CORAL_2,
        MeshId.KELP_0, MeshId.KELP_1,
        MeshId.WRECK_0, MeshId.WRECK_1,
        MeshId.VENT,
        MeshId.RUINS_0, MeshId.RUINS_1, MeshId.RUINS_2,
        MeshId.SCHOOL_0, MeshId.SCHOOL_1,
        MeshId.WHALE,
        MeshId.MERFOLK_0, MeshId.MERFOLK_1, MeshId.MERFOLK_2,
        MeshId.BUNTING,
    )

    // ----------------------------------------------------------------------------------------------------------
    // The vehicle's extra parts (the ocean skin), see VehicleModel. Each is authored in its own unit space and posed,
    // scaled and tinted by the part table.
    //   PONTOON    a float: a capsule / long ellipsoid, 1 m wide, 1 m high, 1 m long, centred, the bow (pointed, a little upturned) at -Z
    //   PROP       a propeller: three or four blades in the XY plane, radius 1, axis along Z, centred (it is spun about Z)
    //   TOWER      a submarine's conning tower (sail): streamlined, 0.5 wide (x), 1 high (y), 1.6 long (z), standing on y = 0, centred in x and z
    //   PERISCOPE  a thin tube with a small head facing -Z: 1 m high, standing on y = 0
    //   PORTHOLE   a round window: a disc of radius 0.5 and a thin frame, facing -Z (posed with ry = +-90 to face the sides)
    //   SEARCHLIGHT a round lamp: a short housing with a flat glowing face, radius 0.5, facing -Z
    //   LIGHT_CONE the beam of a lamp: a cone whose tip is at the origin and which opens toward -Z to radius 0.35 at z = -1;
    //              vertex alpha runs from 0.55 at the tip to 0 at the far end (it is drawn additively, translucent)
    // ----------------------------------------------------------------------------------------------------------
}

/** Builds the ocean's models: the work is split over three files so that they can be written independently. */
internal object OceanMeshes {
    fun build(id: MeshId, b: MeshBuilder) {
        val o = id.ordinal
        when {
            o in MeshId.PONTOON.ordinal..MeshId.LIGHT_CONE.ordinal -> OceanVehicleMeshes.build(id, b)
            o in MeshId.SEASTACK_0.ordinal..MeshId.CORAL_2.ordinal -> OceanSeaMeshes.build(id, b)
            o >= MeshId.MERFOLK_0.ordinal -> OceanCelebrationMeshes.build(id, b)
            else -> OceanFloorMeshes.build(id, b)
        }
    }
}
