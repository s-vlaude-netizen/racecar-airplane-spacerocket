package app.roadtoorbit.game

enum class Category { HAZARD, PICKUP, DECOR, GATE }

/** Everything that can exist in the world besides the player. */
enum class Kind(val category: Category) {
    // --- car leg
    TRAFFIC(Category.HAZARD), BARRIER(Category.HAZARD), CONE(Category.HAZARD), BARREL(Category.HAZARD),
    COIN(Category.PICKUP), NITRO(Category.PICKUP), REPAIR(Category.PICKUP),
    TREE_PINE(Category.DECOR), TREE_ROUND(Category.DECOR), ROCK(Category.DECOR), BILLBOARD(Category.DECOR),
    START_ARCH(Category.DECOR), GATE_ARCH(Category.GATE), RAMP(Category.DECOR),

    // --- sky leg
    PEAK(Category.HAZARD), BALLOON(Category.HAZARD), STORM(Category.HAZARD), JET(Category.HAZARD),
    RING(Category.PICKUP), ORB(Category.PICKUP), CLOUD(Category.DECOR), PORTAL(Category.GATE),

    // --- space leg
    ASTEROID(Category.HAZARD), SATELLITE(Category.HAZARD), CRYSTAL(Category.PICKUP),
    PLANET(Category.DECOR), MOON(Category.GATE),
}

/**
 * A pooled world object. The world scrolls toward the player ("treadmill"): the player always sits
 * at z = 0 and entities move toward +z. [speed] is the entity's own speed in the direction of travel
 * (traffic drives the same way as the player, so it approaches more slowly).
 */
class Entity {
    var kind = Kind.COIN
    var variant = 0

    var x = 0f
    var y = 0f
    var z = 0f

    /** Own motion: [speed] along the direction of travel, [vx]/[vy] sideways. */
    var speed = 0f
    var vx = 0f
    var vy = 0f

    /** Euler angles (degrees) and angular velocities. */
    var rx = 0f; var ry = 0f; var rz = 0f
    var spinX = 0f; var spinY = 0f; var spinZ = 0f

    var scale = 1f

    /** Collision: a sphere when [radius] > 0, otherwise a box with half extents. */
    var radius = 0f
    var hx = 0f; var hy = 0f; var hz = 0f

    /** Fraction of the world speed this entity moves with (distant scenery < 1 for parallax). */
    var parallax = 1f

    /** Position y is relative to the terrain, which sinks as the vehicle climbs. */
    var groundBound = false

    var damage = 1
    var slowdown = 0f

    var alive = false
    var hit = false
    var passed = false
    var age = 0f
    var tint = 0 // 0 = default colours, otherwise 0xRRGGBB multiplier chosen by the spawner
    var param = 0f // spare per-kind value (e.g. ring radius, gate width)
    /** Smallest clearance to the player seen while alongside (near-miss detection). */
    var minGap = 1e9f

    fun reset(k: Kind) {
        kind = k
        variant = 0
        x = 0f; y = 0f; z = 0f
        speed = 0f; vx = 0f; vy = 0f
        rx = 0f; ry = 0f; rz = 0f
        spinX = 0f; spinY = 0f; spinZ = 0f
        scale = 1f
        radius = 0f; hx = 0f; hy = 0f; hz = 0f
        parallax = 1f
        groundBound = false
        damage = 1
        slowdown = 0f
        alive = true
        hit = false
        passed = false
        age = 0f
        tint = 0
        param = 0f
        minGap = 1e9f
    }
}
