package app.roadtoorbit.game

import app.roadtoorbit.gfx.Skin
import app.roadtoorbit.gfx.VehicleMode

/** The world a level is set in: it decides the palettes, the terrain, the scenery and the bodies in the sky. */
enum class World { EARTH, MARS, OCEAN }

/** How a level ends once its last leg is done. */
enum class Finale {
    /** The rocket glides in, lands on the destination, unfolds into the car and drives off (the Moon, Phobos). */
    LANDING,

    /** The last leg is a drive already: the car arrives at its destination, pulls up and the camera swings round it. */
    ARRIVAL,
}

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
    val finale: Finale = Finale.LANDING,
) {
    init {
        require(legs.size == 3 && startDifficulty.size == legs.size) { "a level has three legs" }
    }

    val lastLeg: Int get() = legs.size - 1

    /** How the player's vehicle is dressed on this level. */
    val skin: Skin get() = if (world == World.OCEAN) Skin.OCEAN else Skin.STANDARD
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

    /**
     * The ocean: a waterplane skims the sea (it can fly almost down to the waves), dives and becomes a submarine-rocket, which
     * lands on the seabed as the car that drives along the sunken highway to Atlantis. The vehicle changes in the order
     * plane, rocket, car, and the last leg is a drive, so the finale is an arrival and not a landing.
     */
    private val DEEP = LevelSpec(
        index = 2,
        world = World.OCEAN,
        name = "THE DEEP",
        legs = arrayOf(
            LegSpec(0, VehicleMode.PLANE, "WAVE RUNNER", "Skim the open sea", 3600f, 360f, 56f, 80f, 0.40f, 520f, 120f, 3.6f * 2f, minY = 1.2f, startY = 1.8f),
            LegSpec(1, VehicleMode.ROCKET, "DEEP DIVE", "Plunge into the abyss", 4000f, 440f, 48f, 76f, 0.45f, 440f, 110f, 3.6f * 4f),
            LegSpec(2, VehicleMode.CAR, "TRENCH RUN", "Drive to Atlantis", 3400f, 0f, 40f, 58f, 0.45f, 400f, 150f, 3.6f),
        ),
        destination = "ATLANTIS",
        destinationKm = 10.9f,
        startDifficulty = floatArrayOf(0.20f, 0.34f, 0.46f),
        parSeconds = 350f,
        starScores = intArrayOf(11500, 18500, 25500),
        finale = Finale.ARRIVAL,
    )

    val ALL: Array<LevelSpec> = arrayOf(MOON, MARS, DEEP)

    val count: Int get() = ALL.size

    /** The level with this index; out-of-range values (a stale preference, a fuzzer) fall back to the nearest valid one. */
    operator fun get(index: Int): LevelSpec = ALL[index.coerceIn(0, ALL.size - 1)]
}
