package app.roadtoorbit.gfx

/**
 * The island airstrip where HOMECOMING ends: the plane glides in over the sea, lands on a runway that lies on a long thin island,
 * rolls out and stops in front of a cheering crowd. This is the contract between the people who lay it out (the level, the game, the
 * renderer) and the models (OceanAirfieldMeshes): where things are measured from and how big they are.
 *
 * The runway is drawn like the sunken highway: segments of [SEG] metres (near edge at z = 0, far edge at z = -SEG, x = 0 the middle)
 * placed one after the other along the route, standing on [STRIP_Y] above the sea. Everything is in metres; y is up, models face -Z.
 */
object AirfieldSpec {
    /** The top of the runway above the sea level (the waves are up to 1.4 m high, so the island stands clear of them). */
    const val STRIP_Y = 1.7f

    /** One segment of the strip (STRIP_START, STRIP_SEG, STRIP_END), and how many there are: the start, [SEGMENTS] - 2 of the others, the end. */
    const val SEG = 24f
    const val SEGMENTS = 26
    const val LENGTH = SEG * SEGMENTS

    /** Across the strip: the paved runway is [RUNWAY_HALF] to each side of the middle, sand and grass to [SHOULDER_HALF], then the bank down into the sea. */
    const val RUNWAY_HALF = 12f
    const val SHOULDER_HALF = 50f
    const val BANK_HALF = 58f

    /** How far the bank goes down (m below the top of the strip) at its foot. */
    const val BANK_DROP = 4f

    // ---- where things are along the route, in metres beyond the end of the last leg (where the player's control ends) -----------

    /** The strip (its slope out of the sea, then the runway) begins this far beyond the end of the leg: the plane glides in over the sea until then. */
    const val THRESHOLD_AHEAD = 300f

    /** The wheels touch this far into the runway, and the plane rolls on for [ROLLOUT] metres before it stops. */
    const val TOUCHDOWN_AFTER = 60f
    const val ROLLOUT = 120f

    /** Where the plane stops, measured from the end of the leg. */
    const val STOP_AFTER_LEG = THRESHOLD_AHEAD + TOUCHDOWN_AFTER + ROLLOUT

    /** The crowd lines the runway this far to each side of the middle (m): outside the paved runway, clear of the wings. */
    const val CROWD_NEAR = 15.5f
    const val CROWD_FAR = 19.5f

    // ---- the models: what each looks like and how big (all stand on y = 0, front toward -Z) -----------------------------------
    //   PERSON_0..2   a person waving, 1.75 m tall (arm up: 2.35 m), facing -Z, standing on y = 0, within 0.8 m of the origin
    //   BALLOON       a party balloon in white (tinted by the material), 1.1 m wide, 1.35 m tall, centred; a string hangs 2.4 m below its middle
    //   CONFETTI_PAPER  a strip of paper 0.34 x 0.17 m in the XZ plane, centred, white (tinted by the material), seen from both sides
    //   PALM          a palm tree 8 m tall, standing on y = 0, its fronds reach 3.2 m from the trunk
    //   WINDSOCK      a pole 6.5 m high with an orange and white sock that points toward +X, standing on y = 0
    //   TERMINAL      the terminal: 46 m long along X, 15 m deep, 10 m high, its glass front toward -Z (turned to face the runway), on y = 0
    //   CONTROL_TOWER  a tower 22 m high with a glass cab, within 4 m of the origin, on y = 0
    //   HANGAR        an arched hangar 24 m long along Z, 22 m wide, 10 m high, its open end toward -Z, on y = 0
    //   BUNTING       (see OceanSpec) strung across the runway, 28 m between its poles
}
