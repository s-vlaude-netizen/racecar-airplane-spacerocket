package app.roadtoorbit.game

import app.roadtoorbit.math.Mathx
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates the obstacle / pickup patterns and scenery for each leg as the player advances.
 * Everything is driven by the game's seeded RNG, so a seed fully determines a run's level.
 *
 * Patterns are placed at a *route distance* `S` (metres from the start of the leg); an entity at
 * `S + dz` sits at world z = -(S + dz - legDist).
 */
internal class Spawner(private val g: Game) {
    private val rng get() = g.rng

    /**
     * Pattern geometry is authored for the car's ~48 m/s. Faster legs stretch distances along z by
     * this factor so obstacle spacing stays constant in *seconds* (the play area itself does not scale).
     */
    private val zs: Float
        get() {
            val l = g.leg
            return (l.speedStart + l.speedEnd) * 0.5f * g.difficulty.speedScale / 48f
        }

    fun fill() = update()

    /** The starting-grid arch just ahead of the car. */
    fun startLine() {
        val e = g.obtain(Kind.START_ARCH)
        e.z = -16f
    }

    fun update() {
        val leg = g.leg
        val hazardEnd = leg.length - hazardFreeTail(leg)

        while (g.spawnCursor - g.legDist < leg.spawnAhead && g.spawnCursor < leg.length - 15f) {
            val s = g.spawnCursor
            val used = when (leg.index) {
                0 -> carPattern(s, diff(0, s, hazardEnd), s < hazardEnd)
                1 -> planePattern(s, diff(1, s, hazardEnd), s < hazardEnd)
                else -> rocketPattern(s, diff(2, s, hazardEnd), s < hazardEnd)
            }
            g.spawnCursor += used * zs
        }

        if (!g.gateSpawned && leg.length - g.legDist < leg.spawnAhead + 40f) {
            g.gateSpawned = true
            spawnGate(leg)
        }

        while (g.decorCursor - g.legDist < leg.spawnAhead + 80f) {
            val s = g.decorCursor
            g.decorCursor += when (leg.index) {
                0 -> carDecor(s)
                1 -> skyDecor(s)
                else -> spaceDecor(s)
            }
        }
    }

    private fun hazardFreeTail(leg: LegSpec): Float = if (leg.zoneLength > 0f) leg.zoneLength * 0.8f else 520f

    private fun diff(leg: Int, s: Float, hazardEnd: Float): Float {
        val prog = Mathx.clamp01(s / hazardEnd)
        val base = when (leg) { 0 -> 0f; 1 -> 0.15f; else -> 0.25f }
        return base + (1f - base) * prog
    }

    // ---- helpers ------------------------------------------------------------------------------

    private fun laneX(i: Int): Float = (i - (Tuning.LANES - 1) / 2f) * Tuning.LANE_WIDTH

    private fun place(kind: Kind, s: Float, dz: Float, x: Float, y: Float = 0f, variant: Int = 0): Entity {
        val e = g.obtain(kind)
        e.x = x
        e.y = y
        e.z = -(s + dz * zs - g.legDist)
        setup(e, variant)
        return e
    }

    private fun setup(e: Entity, variant: Int) {
        e.variant = variant
        when (e.kind) {
            Kind.TRAFFIC -> when (variant) {
                4 -> { e.hx = 1.25f; e.hy = 1.6f; e.hz = 3.7f; e.y = 1.6f; e.speed = rng.range(15f, 20f) }
                5 -> { e.hx = 1.05f; e.hy = 1.1f; e.hz = 2.6f; e.y = 1.1f; e.speed = rng.range(18f, 24f) }
                else -> { e.hx = 0.95f; e.hy = 0.65f; e.hz = 2.1f; e.y = 0.65f; e.speed = rng.range(20f, 29f) }
            }
            Kind.BARRIER -> { e.hx = 1.45f; e.hy = 0.5f; e.hz = 0.5f; e.y = 0.5f }
            Kind.CONE -> { e.radius = 0.55f; e.y = 0.5f; e.damage = 0; e.slowdown = 0.12f }
            Kind.BARREL -> { e.radius = 0.7f; e.y = 0.7f }
            Kind.COIN -> { e.radius = 1.2f; if (e.y == 0f) e.y = 1.0f; e.spinY = 200f }
            Kind.NITRO -> { e.radius = 1.5f; e.y = 1.1f; e.spinY = 110f }
            Kind.REPAIR -> { e.radius = 1.5f; e.y = 1.1f; e.spinY = 90f }
            Kind.PEAK -> { e.groundBound = true }
            Kind.BALLOON -> { e.radius = 3.0f; e.spinY = 12f }
            Kind.STORM -> { e.radius = 5.6f; e.spinY = 8f }
            Kind.JET -> { e.hx = 2.0f; e.hy = 0.7f; e.hz = 2.6f; e.speed = 30f }
            Kind.RING -> { e.param = 5.2f; e.spinZ = 40f }
            Kind.ORB -> { e.radius = 1.7f; e.spinY = 90f }
            Kind.ASTEROID -> {
                e.radius = 2f; e.spinX = rng.range(-40f, 40f); e.spinY = rng.range(-40f, 40f); e.spinZ = rng.range(-30f, 30f)
            }
            Kind.SATELLITE -> { e.hx = 3.0f; e.hy = 0.7f; e.hz = 1.1f; e.spinZ = 25f }
            Kind.CRYSTAL -> { e.radius = 1.5f; e.spinY = 140f }
            else -> Unit
        }
    }

    private fun pickWeighted(w: FloatArray): Int {
        var total = 0f
        for (v in w) total += v
        var r = rng.float() * total
        for (i in w.indices) {
            r -= w[i]
            if (r <= 0f) return i
        }
        return w.size - 1
    }

    private fun gap(d: Float): Float = Mathx.lerp(70f, 40f, d) * rng.range(0.9f, 1.25f) * g.difficulty.gapScale

    // ---- car leg ------------------------------------------------------------------------------

    private fun carPattern(s: Float, d: Float, hazards: Boolean): Float {
        if (!hazards) return if (rng.chance(0.3f)) nitro(s) else coinLine(s) + 10f

        val needsRepair = g.player.health < g.maxHealth
        val w = floatArrayOf(
            2.4f, // coin line
            1.5f, // coin wave
            3.4f, // single traffic
            1.0f + 2.8f * d, // traffic pair
            2.6f, // single barrier
            0.5f + 2.6f * d, // barrier wall
            1.6f, // cone slalom
            1.4f, // barrels
            1.0f, // nitro
            if (needsRepair) 0.9f else 0.05f, // repair
            2.6f * max(0f, d - 0.35f), // chicane
        )
        return when (pickWeighted(w)) {
            0 -> coinLine(s) + 14f
            1 -> coinWave(s) + 14f
            2 -> trafficSingle(s, d) + gap(d)
            3 -> trafficPair(s, d) + gap(d)
            4 -> barrierSingle(s) + gap(d)
            5 -> barrierWall(s, d) + gap(d)
            6 -> coneSlalom(s) + gap(d) * 0.6f
            7 -> barrels(s) + gap(d)
            8 -> nitro(s) + 12f
            9 -> repair(s) + 12f
            else -> chicane(s, d) + gap(d)
        }
    }

    private fun coinLine(s: Float): Float {
        val lane = rng.int(Tuning.LANES)
        val n = rng.intRange(6, 10)
        for (k in 0 until n) place(Kind.COIN, s, k * 5f, laneX(lane))
        return n * 5f
    }

    private fun coinWave(s: Float): Float {
        val n = 11
        val amp = rng.range(2.5f, 4.2f)
        val phase = rng.range(0f, 6.28f)
        for (k in 0 until n) place(Kind.COIN, s, k * 5f, sin(k * 0.55f + phase) * amp)
        return n * 5f
    }

    private fun randomTrafficVariant(d: Float): Int {
        val r = rng.float()
        return when {
            r < 0.14f && d > 0.15f -> 4
            r < 0.32f -> 5
            else -> rng.int(4)
        }
    }

    private fun trafficSingle(s: Float, d: Float): Float {
        val lane = rng.int(Tuning.LANES)
        val e = place(Kind.TRAFFIC, s, 0f, laneX(lane), variant = randomTrafficVariant(d))
        e.tint = rng.int(6)
        if (rng.chance(0.5f)) {
            val other = (lane + 1 + rng.int(Tuning.LANES - 1)) % Tuning.LANES
            for (k in 0 until 4) place(Kind.COIN, s, 8f + k * 5f, laneX(other))
        }
        return 12f
    }

    private fun trafficPair(s: Float, d: Float): Float {
        val a = rng.int(Tuning.LANES)
        val b = (a + 1 + rng.int(Tuning.LANES - 1)) % Tuning.LANES
        place(Kind.TRAFFIC, s, 0f, laneX(a), variant = randomTrafficVariant(d)).tint = rng.int(6)
        place(Kind.TRAFFIC, s, rng.range(0f, 26f), laneX(b), variant = randomTrafficVariant(d)).tint = rng.int(6)
        return 40f
    }

    private fun barrierSingle(s: Float): Float {
        val lane = rng.int(Tuning.LANES)
        place(Kind.BARRIER, s, 0f, laneX(lane))
        val other = (lane + 2) % Tuning.LANES
        for (k in 0 until 3) place(Kind.COIN, s, 6f + k * 5f, laneX(other))
        return 12f
    }

    private fun barrierWall(s: Float, d: Float): Float {
        val blocked = if (d > 0.7f && rng.chance(0.4f)) 3 else 2
        val free = BooleanArray(Tuning.LANES) { true }
        var n = 0
        while (n < blocked) {
            val lane = rng.int(Tuning.LANES)
            if (free[lane]) { free[lane] = false; n++ }
        }
        var guide = -1
        for (i in 0 until Tuning.LANES) {
            if (!free[i]) {
                place(Kind.BARRIER, s, 0f, laneX(i))
            } else if (guide < 0) guide = i
        }
        if (guide >= 0) for (k in -3 until 3) place(Kind.COIN, s, k * 5f + 8f, laneX(guide))
        return 16f
    }

    private fun coneSlalom(s: Float): Float {
        val a = rng.int(2)
        val b = a + 2
        val n = 6
        for (k in 0 until n) {
            val lane = if (k % 2 == 0) a else b
            place(Kind.CONE, s, k * 15f, laneX(lane))
            place(Kind.CONE, s, k * 15f, laneX(lane) + 1.2f)
        }
        return n * 15f
    }

    private fun barrels(s: Float): Float {
        val l0 = rng.int(Tuning.LANES - 1)
        for (k in 0 until 4) {
            val lane = if (k % 2 == 0) l0 else l0 + 1
            place(Kind.BARREL, s, k * 5f, laneX(lane))
        }
        return 18f
    }

    private fun nitro(s: Float): Float {
        val lane = rng.int(Tuning.LANES)
        for (k in 0 until 3) place(Kind.COIN, s, k * 5f, laneX(lane))
        place(Kind.NITRO, s, 17f, laneX(lane))
        return 22f
    }

    private fun repair(s: Float): Float {
        val lane = rng.int(Tuning.LANES)
        place(Kind.REPAIR, s, 0f, laneX(lane))
        for (k in 1..3) place(Kind.COIN, s, k * 5f, laneX(lane))
        return 18f
    }

    private fun chicane(s: Float, d: Float): Float {
        val a = rng.int(Tuning.LANES - 1)
        val b = a + 1
        for (k in 0 until 3) {
            val lane = if (k % 2 == 0) a else b
            place(Kind.TRAFFIC, s, k * 24f, laneX(lane), variant = randomTrafficVariant(d)).tint = rng.int(6)
        }
        return 52f
    }

    // ---- sky leg ------------------------------------------------------------------------------

    private fun planePattern(s: Float, d: Float, hazards: Boolean): Float {
        if (!hazards) return if (rng.chance(0.6f)) ringChain(s, 4, 0.6f) else coinArc(s)
        val early = g.legDist < Tuning.LEGS[1].length * 0.26f
        val w = floatArrayOf(
            3.2f, // ring chain
            1.6f, // coin arc
            2.2f, // balloons
            if (early) 2.4f else 0.2f, // rock spires (only while still low)
            0.6f + 2.4f * Mathx.smoothstep(0.1f, 0.5f, d), // storm cells (once above the clouds)
            0.4f + 2.0f * d, // jets
            0.9f, // orbs
            if (g.player.health < g.maxHealth) 0.7f else 0.05f, // repair
        )
        return when (pickWeighted(w)) {
            0 -> ringChain(s, rng.intRange(5, 7), 1f) + 30f
            1 -> coinArc(s) + 18f
            2 -> balloons(s, d) + gap(d)
            3 -> spires(s, d) + gap(d)
            4 -> stormCells(s, d) + gap(d)
            5 -> jets(s, d) + gap(d)
            6 -> orbTrail(s) + 18f
            else -> airRepair(s) + 18f
        }
    }

    private fun flyX(): Float = rng.range(-9.5f, 9.5f)
    private fun flyY(): Float = rng.range(8f, 26f)

    private fun ringChain(s: Float, n: Int, ringScale: Float): Float {
        var x = rng.range(-6f, 6f)
        var y = rng.range(11f, 22f)
        val spacing = 46f
        for (k in 0 until n) {
            val e = place(Kind.RING, s, k * spacing, x, y)
            e.param = 5.2f * ringScale
            e.scale = e.param / 5.2f
            x = Mathx.clamp(x + rng.range(-6.5f, 6.5f), -10.5f, 10.5f)
            y = Mathx.clamp(y + rng.range(-4.5f, 4.5f), 8f, 26f)
        }
        return n * spacing
    }

    private fun coinArc(s: Float): Float {
        val n = 12
        val x0 = rng.range(-6f, 6f)
        val y0 = rng.range(10f, 22f)
        val ax = rng.range(2f, 5f)
        val ay = rng.range(2f, 5f)
        val ph = rng.range(0f, 6.28f)
        for (k in 0 until n) {
            val a = k * 0.4f + ph
            place(Kind.COIN, s, k * 7f, Mathx.clamp(x0 + sin(a) * ax, -11f, 11f), Mathx.clamp(y0 + cos(a) * ay, 7f, 27f))
        }
        return n * 7f
    }

    private fun balloons(s: Float, d: Float): Float {
        val n = 3 + (d * 2f).toInt()
        for (k in 0 until n) {
            val e = place(Kind.BALLOON, s, k * rng.range(16f, 24f), flyX(), flyY(), variant = rng.int(4))
            e.tint = rng.int(6)
        }
        return n * 22f
    }

    private fun spires(s: Float, d: Float): Float {
        val n = 2 + rng.int(3)
        for (k in 0 until n) {
            val h = rng.range(14f, 30f)
            val r = rng.range(2.6f, 4.2f)
            val e = place(Kind.PEAK, s, k * rng.range(18f, 30f), rng.range(-11f, 11f), h / 2f, variant = rng.int(3))
            e.hx = r; e.hz = r; e.hy = h / 2f
            e.scale = 1f
            e.param = h
            e.groundBound = true
        }
        return n * 26f
    }

    private fun stormCells(s: Float, d: Float): Float {
        val n = 2 + (d * 2.5f).toInt()
        for (k in 0 until n) {
            val e = place(Kind.STORM, s, k * rng.range(24f, 40f), rng.range(-10f, 10f), rng.range(9f, 25f), variant = rng.int(2))
            e.radius = rng.range(5.0f, 6.6f)
            e.scale = e.radius / 5.6f
        }
        return n * 36f
    }

    private fun jets(s: Float, d: Float): Float {
        val n = 1 + (d * 2f).toInt()
        for (k in 0 until n) {
            val side = rng.sign()
            val e = place(Kind.JET, s, k * rng.range(20f, 34f), side * rng.range(20f, 26f), rng.range(8f, 26f), variant = rng.int(3))
            val tx = rng.range(-8f, 8f)
            val zDist = abs(e.z)
            val est = Mathx.lerp(Tuning.LEGS[1].speedStart, Tuning.LEGS[1].speedEnd, Mathx.clamp01(g.legDist / Tuning.LEGS[1].length))
            val time = zDist / max(10f, est - e.speed)
            e.vx = (tx - e.x) / time
            e.ry = if (e.vx > 0f) -90f else 90f
        }
        return n * 30f
    }

    private fun orbTrail(s: Float): Float {
        val x = flyX(); val y = flyY()
        for (k in 0 until 3) place(Kind.COIN, s, k * 6f, x, y)
        place(Kind.ORB, s, 20f, x, y)
        return 24f
    }

    private fun airRepair(s: Float): Float {
        place(Kind.REPAIR, s, 0f, flyX(), flyY())
        return 6f
    }

    // ---- space leg ----------------------------------------------------------------------------

    private fun rocketPattern(s: Float, d: Float, hazards: Boolean): Float {
        if (!hazards) return crystalSpiral(s) + 30f
        val w = floatArrayOf(
            3.0f, // asteroid field
            1.2f + 2.0f * d, // asteroid ring
            1.6f, // satellites
            2.0f, // crystal spiral
            0.8f + 2.2f * d, // meteor wall
            if (g.player.health < g.maxHealth) 0.7f else 0.05f, // repair
        )
        return when (pickWeighted(w)) {
            0 -> asteroidField(s, d) + gap(d)
            1 -> asteroidRing(s, d) + gap(d)
            2 -> satellites(s, d) + gap(d)
            3 -> crystalSpiral(s) + 26f
            4 -> meteorWall(s, d) + gap(d)
            else -> { place(Kind.REPAIR, s, 0f, flyX(), flyY()); 30f }
        }
    }

    private fun asteroidAt(s: Float, dz: Float, x: Float, y: Float, radius: Float): Entity {
        val e = place(Kind.ASTEROID, s, dz, x, y, variant = rng.int(4))
        e.radius = radius * 0.92f
        e.scale = radius
        return e
    }

    private fun asteroidField(s: Float, d: Float): Float {
        val n = 8 + (d * 8f).toInt()
        val length = 110f
        val ampX = rng.range(4f, 7f)
        val ampY = rng.range(2.5f, 5f)
        val phase = rng.range(0f, 6.28f)
        val cx = 0f
        val cy = 15.5f
        fun pathX(z: Float) = cx + ampX * sin(z * 0.03f + phase)
        fun pathY(z: Float) = cy + ampY * sin(z * 0.036f + phase * 1.7f)
        var placed = 0
        var tries = 0
        while (placed < n && tries < 80) {
            tries++
            val dz = rng.range(0f, length)
            val r = rng.range(1.3f, 3.8f)
            val x = rng.range(-14f, 14f)
            val y = rng.range(4.5f, 26.5f)
            val clearance = r + 5.2f
            // keep a corridor around the guaranteed path (sampled around dz since the ship moves along z)
            var ok = true
            var zz = dz - 22f
            while (zz <= dz + 22f) {
                val px = pathX(zz); val py = pathY(zz)
                val dx = x - px; val dy = y - py
                if (sqrt(dx * dx + dy * dy) < clearance) { ok = false; break }
                zz += 11f
            }
            if (ok) { asteroidAt(s, dz, x, y, r); placed++ }
        }
        // crystals along the path reward flying it
        var z = 8f
        while (z < length) {
            place(Kind.CRYSTAL, s, z, pathX(z), pathY(z))
            z += 18f
        }
        return length
    }

    private fun asteroidRing(s: Float, d: Float): Float {
        val m = 11
        val radius = 11.5f
        val cx = rng.range(-5f, 5f)
        val cy = rng.range(11f, 20f)
        for (k in 0 until m) {
            val a = k * (2f * PI.toFloat() / m) + rng.range(-0.05f, 0.05f)
            asteroidAt(s, rng.range(-3f, 3f), cx + cos(a) * radius, cy + sin(a) * radius, rng.range(2.4f, 3.3f))
        }
        place(Kind.CRYSTAL, s, 0f, cx, cy)
        return 36f
    }

    private fun satellites(s: Float, d: Float): Float {
        val n = 2 + (d * 1.5f).toInt()
        for (k in 0 until n) {
            val side = rng.sign()
            val e = place(Kind.SATELLITE, s, k * rng.range(26f, 40f), side * rng.range(22f, 28f), rng.range(7f, 25f))
            val tx = rng.range(-9f, 9f)
            val est = Mathx.lerp(Tuning.LEGS[2].speedStart, Tuning.LEGS[2].speedEnd, Mathx.clamp01(g.legDist / Tuning.LEGS[2].length))
            val time = abs(e.z) / max(20f, est)
            e.vx = (tx - e.x) / time
        }
        return n * 36f
    }

    private fun crystalSpiral(s: Float): Float {
        val cx = rng.range(-7f, 7f)
        val cy = rng.range(11f, 20f)
        val n = 14
        val r = 4.5f
        val dir = rng.sign()
        for (k in 0 until n) {
            val a = k * 0.45f * dir
            place(Kind.CRYSTAL, s, k * 6.5f, cx + cos(a) * r, cy + sin(a) * r)
        }
        return n * 6.5f
    }

    private fun meteorWall(s: Float, d: Float): Float {
        val cols = 7
        val rows = 4
        val gx = rng.int(cols - 1)
        val gy = rng.int(rows - 1)
        for (cx in 0 until cols) {
            for (cy in 0 until rows) {
                // leave a 2x2 hole
                if ((cx == gx || cx == gx + 1) && (cy == gy || cy == gy + 1)) continue
                val x = -13.5f + cx * 4.5f + rng.range(-0.6f, 0.6f)
                val y = 5.5f + cy * 6.4f + rng.range(-0.6f, 0.6f)
                asteroidAt(s, rng.range(-3f, 3f), x, y, rng.range(1.6f, 2.5f))
            }
        }
        return 30f
    }

    // ---- gates --------------------------------------------------------------------------------

    private fun spawnGate(leg: LegSpec) {
        when (leg.index) {
            0 -> {
                val gate = place(Kind.GATE_ARCH, leg.length, 0f, 0f, 0f, variant = 0)
                gate.scale = 1f
                // smaller guide arches through the zone, then the launch ramp after the gate
                var k = 1
                while (k * 80f < leg.zoneLength) {
                    place(Kind.GATE_ARCH, leg.length - k * 80f, 0f, 0f, 0f, variant = 1)
                    k++
                }
                place(Kind.RAMP, leg.length + 6f, 0f, 0f, 0f)
            }
            1 -> {
                val portal = place(Kind.PORTAL, leg.length, 0f, 0f, 17f, variant = 0)
                portal.scale = 1f
                var k = 1
                while (k * 95f < leg.zoneLength) {
                    place(Kind.PORTAL, leg.length - k * 95f, 0f, 0f, 17f, variant = 1)
                    k++
                }
            }
            else -> Unit
        }
    }

    // ---- scenery ------------------------------------------------------------------------------

    private fun carDecor(s: Float): Float {
        val side = rng.sign()
        val r = rng.float()
        val x = side * rng.range(10.5f, 36f)
        when {
            r < 0.50f -> place(Kind.TREE_PINE, s, 0f, x, 0f, variant = rng.int(3)).apply { scale = rng.range(0.85f, 1.7f); groundBound = true; ry = rng.range(0f, 360f) }
            r < 0.85f -> place(Kind.TREE_ROUND, s, 0f, x, 0f, variant = rng.int(3)).apply { scale = rng.range(0.85f, 1.6f); groundBound = true; ry = rng.range(0f, 360f) }
            else -> place(Kind.ROCK, s, 0f, x, 0f, variant = rng.int(3)).apply { scale = rng.range(0.8f, 2.2f); groundBound = true; ry = rng.range(0f, 360f) }
        }
        // occasional billboard hugging the road
        if (rng.chance(0.045f)) {
            place(Kind.BILLBOARD, s, 4f, -side * 12.5f, 0f, variant = rng.int(4)).apply { groundBound = true; ry = if (-side > 0) -12f else 12f }
        }
        return rng.range(9f, 20f)
    }

    private fun skyDecor(s: Float): Float {
        val look = g.look
        if (g.legDist < 700f && look.groundY > -50f) {
            val side = rng.sign()
            val e = place(Kind.TREE_PINE, s, 0f, side * rng.range(12f, 36f), 0f, variant = rng.int(3))
            e.scale = rng.range(0.9f, 1.8f); e.groundBound = true
        }
        // puffy clouds off to the sides, above and below the flight corridor (never inside it)
        val side = rng.sign()
        val size = rng.range(5f, 14f)
        val x = side * (15f + 1.4f * size + rng.range(0f, 55f))
        val y = rng.range(-14f, 46f)
        val c = place(Kind.CLOUD, s, rng.range(0f, 30f), x, y, variant = rng.int(3))
        c.scale = size
        c.ry = rng.range(0f, 360f)
        if (rng.chance(0.25f)) {
            // a few clouds far below make the altitude feel real
            val big = rng.range(9f, 20f)
            val low = place(Kind.CLOUD, s, rng.range(0f, 40f), rng.range(-70f, 70f), rng.range(-40f, -16f), variant = rng.int(3))
            low.scale = big
        }
        return rng.range(26f, 46f)
    }

    private fun spaceDecor(s: Float): Float {
        // big distant planets drift slowly (heavy parallax); roughly one per kilometre of flight
        val side = rng.sign()
        val dist = rng.range(2600f, 4600f)
        val e = place(Kind.PLANET, s, 0f, side * dist * rng.range(0.18f, 0.62f), dist * rng.range(-0.16f, 0.34f), variant = rng.int(4))
        e.z = -dist
        e.scale = rng.range(130f, 320f)
        e.parallax = rng.range(0.25f, 0.42f)
        e.spinY = rng.range(-2f, 2f)
        return rng.range(700f, 1300f)
    }
}
