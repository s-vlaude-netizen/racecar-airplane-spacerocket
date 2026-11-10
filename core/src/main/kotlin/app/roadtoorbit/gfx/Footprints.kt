package app.roadtoorbit.gfx

import app.roadtoorbit.game.Kind

/**
 * How far scenery reaches sideways, so that nothing that stands beside the route gets into the lanes the vehicle can use (the
 * car drove through ruins and wrecks, the plane through trees and islands: all of it was drawn, none of it was solid).
 *
 * A model's [reach] is the largest distance of any of its vertices from its origin in the horizontal plane (at scale 1). It does
 * not depend on how the model is turned, so a piece of scenery whose middle is at |x| = [standoff] has no part of it closer to the
 * route than `standoff - reach * scale`. The numbers are measured from the meshes (FootprintTest checks them, so a model that
 * grows cannot silently break the lanes).
 */
object Footprints {
    /**
     * Scenery stays this far from the route's centre line, measured to its nearest part: the car's lanes (|x| up to 5.3 m) and half its
     * width (up to 1.35 m, with the ocean skin's wheel arches) and a margin.
     */
    const val CAR_CLEAR = 7.4f

    /**
     * The same for what the plane (lanes up to 13 m, wings 3.2 m) and the submarine (15 m, body 1.5 m) can fly past, and a margin.
     */
    const val AIR_CLEAR = 17.4f

    private val pine = 2.0f
    private val roundTree = 2.25f
    private val rock = 1.4f
    private val billboard = 3.6f
    private val mesa = 14.1f
    private val devil = 4.4f
    private val dome = 11.95f

    // Kind.SEASCAPE by variant (OceanSpec: ISLAND 0..2, CORAL 3..5, KELP 6..7, WRECK 8..9, VENT 10, RUINS 11..13, SCHOOL 14..15, WHALE 16, MERFOLK 17..19, BUNTING 20)
    private val seascape = floatArrayOf(
        23.5f, 29.6f, 32.2f,
        4.15f, 3.1f, 2.95f,
        4.2f, 3.95f,
        20.6f, 15.45f,
        5.5f,
        14.2f, 7.85f, 6.4f,
        5.05f, 4.35f,
        14.05f,
        0.95f, 1.45f, 0.95f,
        14.35f,
    )

    /** The horizontal reach (m, at scale 1) of the scenery [kind] / [variant], or 0 for what is not scenery. */
    fun reach(kind: Kind, variant: Int): Float = when (kind) {
        Kind.TREE_PINE -> pine
        Kind.TREE_ROUND -> roundTree
        Kind.ROCK -> rock
        Kind.BILLBOARD -> billboard
        Kind.MESA -> mesa
        Kind.DEVIL -> devil
        Kind.DOME -> dome
        Kind.SEASCAPE -> seascape[variant.coerceIn(0, seascape.size - 1)]
        else -> 0f
    }

    /** The mesh a piece of scenery is drawn with (the same ones SceneRenderer picks), for checking the table. */
    internal fun meshes(kind: Kind): List<MeshId> = when (kind) {
        Kind.TREE_PINE -> listOf(MeshId.PINE_0, MeshId.PINE_1, MeshId.PINE_2)
        Kind.TREE_ROUND -> listOf(MeshId.ROUND_TREE_0, MeshId.ROUND_TREE_1, MeshId.ROUND_TREE_2)
        Kind.ROCK -> listOf(MeshId.ROCK_0, MeshId.ROCK_1, MeshId.ROCK_2)
        Kind.BILLBOARD -> listOf(MeshId.BILLBOARD_0, MeshId.BILLBOARD_1, MeshId.BILLBOARD_2, MeshId.BILLBOARD_3)
        Kind.MESA -> listOf(MeshId.MESA_0, MeshId.MESA_1, MeshId.MESA_2)
        Kind.DEVIL -> listOf(MeshId.DEVIL)
        Kind.DOME -> listOf(MeshId.DOME)
        else -> emptyList()
    }
}
