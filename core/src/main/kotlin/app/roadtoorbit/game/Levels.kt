package app.roadtoorbit.game

import app.roadtoorbit.gfx.VehicleMode

/** The world a level is set in: it decides the palettes, the terrain, the scenery and the bodies in the sky. */
enum class World { EARTH, MARS }

/**
 * One level: three legs (car, plane, rocket), the world they happen in and where the rocket lands. Everything
 * the simulation, the renderer and the HUD need to know about "which level is this" lives here, so adding a
 * level means adding an entry to [Levels.ALL] (and, for a new [World], its look).
 */
class LevelSpec(
    val index: Int,
    val world: World,
    /** Shown on the menu and the results screen. */
    val name: String,
    val legs: Array<LegSpec>,
    /** The rocket's destination: the finale announces "APPROACHING $destination" and the altitude readout ends at [destinationKm]. */
    val destination: String,
    val destinationKm: Float,
    /** Obstacle complexity at the start of each leg (0 = only the easiest patterns, 1 = all of them). */
    val startDifficulty: FloatArray,
    /** A run that finishes in this many seconds earns no time bonus. */
    val parSeconds: Float,
    /** Score needed for one, two and three stars (before the difficulty's score multiplier). */
    val starScores: IntArray,
) {
    init {
        require(legs.size == 3 && startDifficulty.size == legs.size) { "a level has three legs" }
    }

    val lastLeg: Int get() = legs.size - 1
}

object Levels {
    private val MOON = LevelSpec(
        index = 0,
        world = World.EARTH,
        name = "THE MOON",
        legs = arrayOf(
            LegSpec(0, VehicleMode.CAR, "GRAND PRIX", "Race to the launch gate", 2800f, 320f, 40f, 56f, 0.45f, 400f, 150f, 3.6f),
            LegSpec(1, VehicleMode.PLANE, "SKY RALLY", "Climb to the edge of space", 4300f, 400f, 72f, 96f, 0.40f, 560f, 120f, 3.6f * 2.6f),
            LegSpec(2, VehicleMode.ROCKET, "ORBIT RUN", "Reach the Moon", 6300f, 0f, 112f, 150f, 0.50f, 680f, 120f, 3.6f * 38f),
        ),
        destination = "THE MOON",
        destinationKm = 384_400f,
        startDifficulty = floatArrayOf(0f, 0.15f, 0.25f),
        parSeconds = 330f,
        starScores = intArrayOf(9000, 15000, 21000),
    )

    private val MARS = LevelSpec(
        index = 1,
        world = World.MARS,
        name = "MARS",
        legs = arrayOf(
            LegSpec(0, VehicleMode.CAR, "RED DUST RALLY", "Cross the Martian desert", 3300f, 340f, 44f, 62f, 0.45f, 430f, 150f, 3.6f),
            LegSpec(1, VehicleMode.PLANE, "DUST STORM", "Climb above the storm", 4900f, 420f, 78f, 104f, 0.40f, 600f, 120f, 3.6f * 2.6f),
            LegSpec(2, VehicleMode.ROCKET, "PHOBOS RUN", "Reach Phobos", 6900f, 0f, 120f, 162f, 0.50f, 720f, 120f, 3.6f * 38f),
        ),
        destination = "PHOBOS",
        destinationKm = 5_980f,
        startDifficulty = floatArrayOf(0.12f, 0.28f, 0.38f),
        parSeconds = 345f,
        starScores = intArrayOf(10000, 16500, 23000),
    )

    val ALL: Array<LevelSpec> = arrayOf(MOON, MARS)

    val count: Int get() = ALL.size

    /** The level with this index; out-of-range values (a stale preference, a fuzzer) fall back to the nearest valid one. */
    operator fun get(index: Int): LevelSpec = ALL[index.coerceIn(0, ALL.size - 1)]
}
