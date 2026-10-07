package app.roadtoorbit.game

import app.roadtoorbit.gfx.VehicleMode

/** One of the three stages of a level's journey. Distances are metres, speeds metres per second. */
class LegSpec(
    val index: Int,
    val mode: VehicleMode,
    val name: String,
    val subtitle: String,
    val length: Float,
    /** The last [zoneLength] metres form the TRANSFORM zone (0 = no transform at the end). */
    val zoneLength: Float,
    val speedStart: Float,
    val speedEnd: Float,
    val boostPower: Float,
    val spawnAhead: Float,
    val firstPatternAt: Float,
    /** Multiplier from m/s to the km/h shown on the speedometer (fiction scales with the leg). */
    val displayFactor: Float,
    val maxHealthPenaltyFree: Boolean = true,
)

object Tuning {
    // ---- player / health
    const val MAX_HEALTH = 3
    const val INVULN_AFTER_HIT = 1.7f
    const val HIT_SPEED_PENALTY = 0.55f
    const val PENALTY_RECOVERY = 0.6f // per second

    // ---- boost
    const val BOOST_DRAIN = 0.30f
    const val BOOST_REGEN = 0.035f
    const val NITRO_GAIN = 0.40f

    // ---- car handling
    const val LANE_WIDTH = 3.0f
    const val LANES = 4
    const val ROAD_HALF_WIDTH = 6.4f
    const val CAR_MAX_X = 5.3f
    const val CAR_LATERAL_SPEED = 15f
    const val CAR_LATERAL_ACCEL = 85f

    // ---- plane handling
    const val PLANE_MAX_X = 13f
    const val PLANE_MIN_Y = 3.5f
    const val PLANE_MAX_Y = 31f
    const val PLANE_LATERAL_SPEED = 21f
    const val PLANE_VERTICAL_SPEED = 15f
    const val PLANE_ACCEL = 70f
    const val PLANE_TAKEOFF_Y = 12f

    // ---- rocket handling
    const val ROCKET_MAX_X = 15f
    const val ROCKET_MIN_Y = 3f
    const val ROCKET_MAX_Y = 28f
    const val ROCKET_LATERAL_SPEED = 24f
    const val ROCKET_VERTICAL_SPEED = 18f
    const val ROCKET_ACCEL = 80f

    // ---- transformation
    const val TRANSFORM_SECONDS = 1.9f
    const val PERFECT_WINDOW = 70f // metres before the gate that count as a perfect transform

    // ---- timeline
    const val COUNTDOWN_SECONDS = 3.2f
    const val CRASH_SECONDS = 1.6f

    // ---- scoring
    const val SCORE_PER_METRE = 0.25f
    const val COIN_SCORE = 25
    const val RING_SCORE = 150
    const val NEAR_MISS_SCORE = 40
    const val TRANSFORM_SCORE = 300
    const val PERFECT_TRANSFORM_SCORE = 1000
    const val HEALTH_BONUS = 600
}
