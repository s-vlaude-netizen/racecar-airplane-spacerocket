package app.roadtoorbit.game

import app.roadtoorbit.gfx.OceanSpec
import app.roadtoorbit.gfx.VehicleMode
import app.roadtoorbit.math.Mathx
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The obstacles, pickups and scenery of the ocean level: a waterplane over the sea (leg 0), the submarine-rocket's dive
 * (leg 1) and the car on the seabed (leg 2, which shares the road patterns of the other levels). Same rules as the other
 * levels: everything is driven by the game's seeded random numbers, at route distance `s` from the start of the leg.
 */
internal class OceanSpawner(private val sp: Spawner, private val g: Game) {
    private val rng get() = g.rng

    fun pattern(leg: LegSpec, s: Float, d: Float, hazards: Boolean): Float = when (leg.mode) {
        VehicleMode.PLANE -> seaPattern(s, d, hazards)
        VehicleMode.ROCKET -> divePattern(s, d, hazards)
        VehicleMode.CAR -> sp.carPattern(s, d, hazards)
    }

    fun decor(leg: LegSpec, s: Float): Float = when (leg.mode) {
        VehicleMode.PLANE -> seaDecor(s)
        VehicleMode.ROCKET -> diveDecor(s)
        VehicleMode.CAR -> seabedDecor(s)
    }

    // ---- the sea ------------------------------------------------------------------------------------------

    private fun seaPattern(s: Float, d: Float, hazards: Boolean): Float {
        if (!hazards) return if (rng.chance(0.6f)) seaRings(s, 4, 0.6f) else seaCoins(s)
        val w = floatArrayOf(
            3.0f, // ring chain skimming the waves
            1.5f, // coin arc
            2.2f, // flocks of gulls
            1.9f + 0.6f * d, // sea stacks
            0.5f + 2.2f * Mathx.smoothstep(0.1f, 0.5f, d), // squalls
            0.5f + 2.0f * d, // rival seaplanes
            1.5f + 1.4f * d, // ships
            0.9f, // orbs
            if (g.player.health < g.maxHealth) 0.7f else 0.05f, // repair
        )
        return when (sp.pickWeighted(w)) {
            0 -> seaRings(s, rng.intRange(5, 7), 1f) + 30f
            1 -> seaCoins(s) + 18f
            2 -> gulls(s, d) + sp.gap(d)
            3 -> stacks(s, d) + sp.gap(d)
            4 -> sp.stormCells(s, d) + sp.gap(d)
            5 -> seaplanes(s, d) + sp.gap(d)
            6 -> ships(s, d) + sp.gap(d)
            7 -> sp.orbTrail(s) + 18f
            else -> sp.airRepair(s) + 18f
        }
    }

    private fun seaRings(s: Float, n: Int, ringScale: Float): Float {
        var x = rng.range(-6f, 6f)
        var y = rng.range(5f, 12f)
        val spacing = 46f
        for (k in 0 until n) {
            val e = sp.place(Kind.RING, s, k * spacing, x, y)
            e.param = 5.2f * ringScale
            e.scale = e.param / 5.2f
            x = Mathx.clamp(x + rng.range(-6.5f, 6.5f), -10.5f, 10.5f)
            y = Mathx.clamp(y + rng.range(-4f, 4f), 3.4f, 16f)
        }
        return n * spacing
    }

    private fun seaCoins(s: Float): Float {
        val n = 12
        val x0 = rng.range(-6f, 6f)
        val y0 = rng.range(4.5f, 12f)
        val ax = rng.range(2f, 5f)
        val ay = rng.range(1.5f, 3.5f)
        val ph = rng.range(0f, 6.28f)
        for (k in 0 until n) {
            val a = k * 0.4f + ph
            sp.place(Kind.COIN, s, k * 7f, Mathx.clamp(x0 + sin(a) * ax, -11f, 11f), Mathx.clamp(y0 + cos(a) * ay, 2.2f, 17f))
        }
        return n * 7f
    }

    private fun gulls(s: Float, d: Float): Float {
        val n = 2 + (d * 2f).toInt()
        for (k in 0 until n) {
            val e = sp.place(Kind.BALLOON, s, k * rng.range(16f, 24f), sp.flyX(), rng.range(4f, 24f), variant = rng.int(4))
            e.radius = OceanSpec.FLOCK_RADIUS
            e.spinY = 12f
        }
        return n * 22f
    }

    /** Pillars of rock standing in the sea, scaled like the sky leg's spires. */
    private fun stacks(s: Float, d: Float): Float {
        val n = 2 + rng.int(3)
        for (k in 0 until n) {
            val h = rng.range(10f, 26f)
            val r = rng.range(2.6f, 4.2f)
            val e = sp.place(Kind.PEAK, s, k * rng.range(18f, 30f), rng.range(-11f, 11f), h / 2f, variant = rng.int(3))
            e.hx = r; e.hz = r; e.hy = h / 2f
            e.scale = 1f
            e.param = h
            e.groundBound = true
            e.onSurface = true
        }
        return n * 26f
    }

    private fun ships(s: Float, d: Float): Float {
        val n = 1 + (d * 1.6f).toInt()
        for (k in 0 until n) {
            val variant = if (rng.chance(0.4f)) 0 else rng.int(3)
            val half = OceanSpec.SHIP_HALF[variant]
            val side = rng.sign()
            val e = sp.place(Kind.SHIP, s, k * rng.range(30f, 48f), side * rng.range(2f, 11f), half[1], variant = variant)
            e.hx = half[0]; e.hy = half[1]; e.hz = half[2]
            e.groundBound = true
            e.onSurface = true
            e.speed = rng.range(5f, 12f)
            e.ry = rng.range(-8f, 8f)
            // a coin trail over the deck marks the way over it
            for (c in 0 until 3) sp.place(Kind.COIN, s, k * 40f + 6f + c * 6f, e.x, half[1] * 2f + 4.5f)
        }
        return n * 42f
    }

    private fun seaplanes(s: Float, d: Float): Float {
        val n = 1 + (d * 2f).toInt()
        val l = g.leg
        for (k in 0 until n) {
            val side = rng.sign()
            val e = sp.place(Kind.JET, s, k * rng.range(20f, 34f), side * rng.range(20f, 26f), rng.range(5f, 24f), variant = rng.int(3))
            e.hx = OceanSpec.SEAPLANE_HALF[0]; e.hy = OceanSpec.SEAPLANE_HALF[1]; e.hz = OceanSpec.SEAPLANE_HALF[2]
            val tx = rng.range(-8f, 8f)
            val zDist = abs(e.z)
            val est = Mathx.lerp(l.speedStart, l.speedEnd, Mathx.clamp01(g.legDist / l.length))
            val time = zDist / max(10f, est - e.speed)
            e.vx = (tx - e.x) / time
            e.ry = if (e.vx > 0f) -90f else 90f
        }
        return n * 30f
    }

    private fun seaDecor(s: Float): Float {
        val side = rng.sign()
        val r = rng.float()
        if (r < 0.42f) {
            // a low tropical island off to the side
            val e = sp.place(Kind.SEASCAPE, s, 0f, side * rng.range(34f, 170f), 0f, variant = OceanSpec.ISLAND + rng.int(3))
            e.scale = rng.range(0.8f, 1.7f)
            e.groundBound = true
            e.onSurface = true
            e.ry = rng.range(0f, 360f)
        } else {
            // puffy clouds high above the sea
            val size = rng.range(5f, 14f)
            val c = sp.place(Kind.CLOUD, s, rng.range(0f, 30f), side * (15f + 1.4f * size + rng.range(0f, 70f)), rng.range(20f, 62f), variant = rng.int(3))
            c.scale = size
            c.ry = rng.range(0f, 360f)
        }
        return rng.range(14f, 30f)
    }

    // ---- the dive -----------------------------------------------------------------------------------------

    private fun divePattern(s: Float, d: Float, hazards: Boolean): Float {
        if (!hazards) return pearlSpiral(s) + 30f
        val w = floatArrayOf(
            3.0f, // mine field
            1.2f + 2.0f * d, // mine ring around a pearl
            1.5f, // shark patrol
            2.0f, // pearl spiral
            0.8f + 2.2f * d, // net: a wall of mines with a hole in it
            1.3f + 1.0f * d, // jellyfish bloom
            if (g.player.health < g.maxHealth) 0.7f else 0.05f, // repair
        )
        return when (sp.pickWeighted(w)) {
            0 -> mineField(s, d) + sp.gap(d)
            1 -> mineRing(s, d) + sp.gap(d)
            2 -> sharks(s, d) + sp.gap(d)
            3 -> pearlSpiral(s) + 26f
            4 -> net(s, d) + sp.gap(d)
            5 -> jellyBloom(s, d) + sp.gap(d)
            else -> { sp.place(Kind.REPAIR, s, 0f, sp.flyX(), sp.flyY()); 30f }
        }
    }

    private fun mineAt(s: Float, dz: Float, x: Float, y: Float, radius: Float): Entity {
        val e = sp.place(Kind.MINE, s, dz, x, y, variant = rng.int(2))
        e.radius = radius * 0.92f
        e.scale = radius
        e.spinY = rng.range(-14f, 14f)
        return e
    }

    private fun mineField(s: Float, d: Float): Float {
        val n = 8 + (d * 8f).toInt()
        val length = 110f
        val ampX = rng.range(4f, 7f)
        val ampY = rng.range(2.5f, 5f)
        val phase = rng.range(0f, 6.28f)
        val cy = 15.5f
        fun pathX(z: Float) = ampX * sin(z * 0.03f + phase)
        fun pathY(z: Float) = cy + ampY * sin(z * 0.036f + phase * 1.7f)
        var placed = 0
        var tries = 0
        while (placed < n && tries < 80) {
            tries++
            val dz = rng.range(0f, length)
            val r = rng.range(1.3f, 2.4f)
            val x = rng.range(-14f, 14f)
            val y = rng.range(4.5f, 26.5f)
            val clearance = r + 5.2f
            var ok = true
            var zz = dz - 22f
            while (zz <= dz + 22f) {
                val dx = x - pathX(zz); val dy = y - pathY(zz)
                if (sqrt(dx * dx + dy * dy) < clearance) { ok = false; break }
                zz += 11f
            }
            if (ok) { mineAt(s, dz, x, y, r); placed++ }
        }
        var z = 8f
        while (z < length) {
            sp.place(Kind.CRYSTAL, s, z, pathX(z), pathY(z))
            z += 18f
        }
        return length
    }

    private fun mineRing(s: Float, d: Float): Float {
        val m = 11
        val radius = 11.5f
        val cx = rng.range(-5f, 5f)
        val cy = rng.range(11f, 20f)
        for (k in 0 until m) {
            val a = k * (2f * PI.toFloat() / m) + rng.range(-0.05f, 0.05f)
            mineAt(s, rng.range(-3f, 3f), cx + cos(a) * radius, cy + sin(a) * radius, rng.range(1.6f, 2.2f))
        }
        sp.place(Kind.CRYSTAL, s, 0f, cx, cy)
        return 36f
    }

    private fun sharks(s: Float, d: Float): Float {
        val n = 2 + (d * 1.5f).toInt()
        val l = g.leg
        for (k in 0 until n) {
            val side = rng.sign()
            val variant = rng.int(3)
            val half = OceanSpec.SHARK_HALF[variant]
            val e = sp.place(Kind.SHARK, s, k * rng.range(26f, 40f), side * rng.range(22f, 28f), rng.range(7f, 25f), variant = variant)
            e.hx = half[0]; e.hy = half[1]; e.hz = half[2]
            val tx = rng.range(-9f, 9f)
            val est = Mathx.lerp(l.speedStart, l.speedEnd, Mathx.clamp01(g.legDist / l.length))
            val time = abs(e.z) / max(20f, est)
            e.vx = (tx - e.x) / time
            e.ry = if (e.vx > 0f) -90f else 90f
        }
        return n * 36f
    }

    private fun pearlSpiral(s: Float): Float {
        val cx = rng.range(-7f, 7f)
        val cy = rng.range(11f, 20f)
        val n = 14
        val r = 4.5f
        val dir = rng.sign()
        for (k in 0 until n) {
            val a = k * 0.45f * dir
            sp.place(Kind.CRYSTAL, s, k * 6.5f, cx + cos(a) * r, cy + sin(a) * r)
        }
        return n * 6.5f
    }

    private fun net(s: Float, d: Float): Float {
        val cols = 7
        val rows = 4
        val gx = rng.int(cols - 1)
        val gy = rng.int(rows - 1)
        for (cx in 0 until cols) {
            for (cy in 0 until rows) {
                if ((cx == gx || cx == gx + 1) && (cy == gy || cy == gy + 1)) continue
                val x = -13.5f + cx * 4.5f + rng.range(-0.6f, 0.6f)
                val y = 5.5f + cy * 6.4f + rng.range(-0.6f, 0.6f)
                mineAt(s, rng.range(-3f, 3f), x, y, rng.range(1.5f, 2.1f))
            }
        }
        return 30f
    }

    private fun jellyBloom(s: Float, d: Float): Float {
        val n = 3 + (d * 2f).toInt()
        for (k in 0 until n) {
            val e = sp.place(Kind.JELLY, s, k * rng.range(14f, 22f), sp.flyX(), rng.range(6f, 25f), variant = rng.int(2))
            e.radius = OceanSpec.JELLY_RADIUS
            e.vy = rng.range(-0.8f, 0.8f)
        }
        return n * 22f
    }

    private fun diveDecor(s: Float): Float {
        val look = g.look
        val side = rng.sign()
        val r = rng.float()
        // the seabed only matters once it is near enough to be seen
        if (look.groundY > -150f && r < 0.5f) {
            val e = seabedThing(s, side * rng.range(12f, 90f), 0.5f)
            e.groundBound = true
        } else if (r < 0.78f) {
            // a school of fish swims alongside
            val e = sp.place(Kind.SEASCAPE, s, rng.range(0f, 30f), side * rng.range(16f, 46f), rng.range(5f, 26f), variant = OceanSpec.SCHOOL + rng.int(2))
            e.scale = rng.range(0.9f, 1.6f)
            e.speed = rng.range(8f, 18f)
            e.vx = rng.range(-1.5f, 1.5f)
        } else if (r < 0.86f) {
            // a whale far off to the side
            val e = sp.place(Kind.SEASCAPE, s, rng.range(0f, 30f), side * rng.range(50f, 110f), rng.range(6f, 26f), variant = OceanSpec.WHALE)
            e.scale = rng.range(0.9f, 1.5f)
            e.speed = rng.range(10f, 16f)
        }
        return rng.range(18f, 36f)
    }

    // ---- the seabed ---------------------------------------------------------------------------------------

    private fun seabedDecor(s: Float): Float {
        val side = rng.sign()
        val r = rng.float()
        val e = seabedThing(s, side * rng.range(10.5f, 46f), if (r < 0.9f) 1f else 0f)
        e.groundBound = true
        // a school of fish above the road now and then
        if (rng.chance(0.07f)) {
            val f = sp.place(Kind.SEASCAPE, s, rng.range(0f, 20f), side * rng.range(5f, 26f), rng.range(6f, 18f), variant = OceanSpec.SCHOOL + rng.int(2))
            f.scale = rng.range(0.9f, 1.5f)
            f.speed = rng.range(10f, 20f)
            f.vx = rng.range(-1.2f, 1.2f)
        }
        // signs of the lost civilisation hug the road now and then
        if (rng.chance(0.045f)) {
            val ru = sp.place(Kind.SEASCAPE, s, 4f, -side * rng.range(11f, 14f), 0f, variant = OceanSpec.RUINS + rng.int(3))
            ru.groundBound = true
            ru.scale = rng.range(0.7f, 1.0f)
            ru.ry = rng.range(0f, 360f)
        }
        return rng.range(8f, 18f)
    }

    /** One piece of seabed scenery at lateral position [x]; [reef] is how likely coral and kelp are over rock and the rare vent or wreck. */
    private fun seabedThing(s: Float, x: Float, reef: Float): Entity {
        val r = rng.float()
        val variant = when {
            r < 0.40f * reef -> OceanSpec.CORAL + rng.int(3)
            r < 0.66f * reef -> OceanSpec.KELP + rng.int(2)
            r < 0.72f -> OceanSpec.VENT
            r < 0.75f -> OceanSpec.WRECK + rng.int(2)
            r < 0.82f -> OceanSpec.RUINS + rng.int(3)
            else -> -1
        }
        val e = if (variant < 0) sp.place(Kind.ROCK, s, 0f, x, 0f, variant = rng.int(3)) else sp.place(Kind.SEASCAPE, s, 0f, x, 0f, variant = variant)
        e.scale = when (variant) {
            -1 -> rng.range(0.8f, 2.4f)
            in OceanSpec.WRECK..OceanSpec.WRECK + 1 -> rng.range(0.9f, 1.2f)
            else -> rng.range(0.7f, 1.5f)
        }
        e.ry = rng.range(0f, 360f)
        return e
    }

    // ---- gates --------------------------------------------------------------------------------------------

    fun gate(leg: LegSpec) {
        when (leg.index) {
            0 -> {
                // the dive gate: a ring standing in the sea, with smaller ones along the zone
                val big = sp.place(Kind.PORTAL, leg.length, 0f, 0f, 15f, variant = 0)
                big.scale = 1f
                var k = 1
                while (k * 95f < leg.zoneLength) {
                    sp.place(Kind.PORTAL, leg.length - k * 95f, 0f, 0f, 10f, variant = 1)
                    k++
                }
            }
            1 -> {
                val portal = sp.place(Kind.PORTAL, leg.length, 0f, 0f, 17f, variant = 0)
                portal.scale = 1f
                var k = 1
                while (k * 95f < leg.zoneLength) {
                    sp.place(Kind.PORTAL, leg.length - k * 95f, 0f, 0f, 17f, variant = 1)
                    k++
                }
            }
            else -> {
                // Atlantis: its gate across the road a little beyond the end of the leg, and the city behind it
                sp.place(Kind.GATE_ARCH, leg.length + GATE_AHEAD, 0f, 0f, 0f, variant = 2).groundBound = true
                val city = sp.place(Kind.MOON, leg.length + GATE_AHEAD + CITY_BEYOND_GATE, 0f, 0f, 0f)
                city.groundBound = true
                welcome(leg.length + GATE_AHEAD)
            }
        }
    }

    /**
     * The people of Atlantis have come to welcome the traveller: merfolk line the avenue on both sides from just before the gate
     * (their [Entity.param] is the phase of their cheering), flags are strung across it, and schools of fish swim above it.
     */
    private fun welcome(gateS: Float) {
        var s = gateS - 26f
        while (s < gateS + 125f) {
            for (side in intArrayOf(-1, 1)) {
                val e = sp.place(Kind.SEASCAPE, s + rng.range(-3f, 3f), 0f, side * rng.range(8.7f, 11.5f), 0.15f, variant = OceanSpec.MERFOLK + rng.int(3))
                e.groundBound = true
                e.ry = -side * (90f + rng.range(-35f, 35f)) // turned toward the road
                e.scale = rng.range(0.92f, 1.15f)
                e.param = rng.range(0f, 6.28f)
            }
            s += rng.range(10f, 15f)
        }
        for (d in floatArrayOf(16f, 48f, 82f)) sp.place(Kind.SEASCAPE, gateS + d, 0f, 0f, 0f, variant = OceanSpec.BUNTING).groundBound = true
        for (k in 0 until 6) {
            val f = sp.place(Kind.SEASCAPE, gateS - 70f + k * 38f, rng.range(-6f, 6f), rng.range(-15f, 15f), rng.range(5f, 14f), variant = OceanSpec.SCHOOL + rng.int(2))
            f.scale = rng.range(1.0f, 1.5f)
            f.speed = rng.range(8f, 16f)
            f.vx = rng.range(-1.2f, 1.2f)
        }
    }

    companion object {
        /** The road runs on past the end of the last leg: the gate of Atlantis is this far beyond it, and the city's middle this much further. */
        const val GATE_AHEAD = 90f
        const val CITY_BEYOND_GATE = 165f // the wall's opening, where the gate stands, is 165 m from the middle of the city
    }
}
