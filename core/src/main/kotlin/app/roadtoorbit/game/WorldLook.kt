package app.roadtoorbit.game

import app.roadtoorbit.gfx.VehicleMode
import app.roadtoorbit.math.Mathx

/**
 * Presentation-relevant facts derived from the journey progress: how far below the vehicle the ground
 * is, what the terrain is made of (grass → cloud deck → moon dust) and how high up we are. The game
 * uses [groundY] for ground-bound objects; the renderer's EnvironmentModel turns the rest into colours.
 */
class WorldLook {
    var groundY = 0f
    /** 0 = grass, 1 = cloud deck, 2 = moon dust (blended continuously in the shader). */
    var terrainStyle = 0f
    var terrainOn = true
    /** 0 at sea level, 1 at the edge of space, up to ~3 deep in space. */
    var altitude = 0f
    /** Metres of road remaining ahead of the player's start line, for drawing (car leg only). */
    var roadEndRoute = 0f
    var roadVisible = true
    /** 0..1 how much of the "approaching the Moon" look is active. */
    var moonApproach = 0f

    /**
     * The ocean level: the height of the sea surface in the vehicle's frame (0 while the waterplane skims it, rising over the
     * submarine as it dives, out of sight on the seabed; [groundY] is the seabed there), and how deep the journey is,
     * 0 at the surface to 1 in the trench.
     */
    var surfaceY = 0f
    var depth = 0f

    fun update(g: Game) {
        if (g.level.world == World.OCEAN) {
            updateOcean(g)
            return
        }
        val p = Mathx.clamp01(g.legDist / g.leg.length)
        when (g.phase) {
            Phase.FINALE, Phase.VICTORY -> {
                terrainOn = true
                terrainStyle = 2f
                altitude = 3f
                roadVisible = false
                moonApproach = 1f
                groundY = g.finaleGroundY
                return
            }
            else -> Unit
        }
        moonApproach = if (g.legIndex == g.level.lastLeg) Mathx.smoothstep(0.55f, 1f, p) else 0f
        when (g.legIndex) {
            0 -> {
                groundY = 0f
                terrainStyle = 0f
                terrainOn = true
                altitude = 0f
                roadVisible = true
            }
            1 -> {
                val sink = Mathx.smoother(p / 0.32f)
                groundY = -64f * sink
                terrainStyle = Mathx.smoothstep(0.10f, 0.34f, p)
                terrainOn = true
                altitude = Mathx.smoothstep(0f, 1f, p)
                roadVisible = g.legDist < 260f // the road and its launch ramp slip behind quickly
            }
            else -> {
                val fall = Mathx.smoother(p / 0.14f)
                groundY = -64f - 1500f * fall
                terrainStyle = 1f
                terrainOn = fall < 0.8f
                altitude = 1f + 2f * Mathx.smoothstep(0f, 0.6f, p)
                roadVisible = false
            }
        }
    }

    /**
     * The sea, the dive under it (or the ascent out of it) and the seabed: see [surfaceY]. The terrain stays "on" throughout; the renderer
     * decides what to draw. What the vehicle sees depends on its form (the plane flies over the sea, the rocket is in the water, the car is on
     * the seabed) and on the way the level goes (down in the deep, up in homecoming).
     */
    private fun updateOcean(g: Game) {
        val p = Mathx.clamp01(g.legDist / g.leg.length)
        val up = g.level.ascends
        terrainOn = true
        terrainStyle = 0f
        altitude = 0f
        moonApproach = 0f
        if (g.phase == Phase.FINALE || g.phase == Phase.VICTORY) {
            if (g.level.finale == Finale.AIRSTRIP) {
                // the homecoming: in daylight over the sea, the island's airstrip below
                surfaceY = 0f; depth = 0f; groundY = -4000f; roadVisible = false
            } else {
                // the arrival: on the seabed, in the light of the city
                surfaceY = 900f; depth = 1f; groundY = 0f; roadVisible = true
            }
            return
        }
        when (g.leg.mode) {
            VehicleMode.PLANE -> {
                // over the sea; after the ascent the surface falls away from the vehicle during the transformation: it breaks through it
                val breach = if (up && g.xf.active) Mathx.smoothstep(0f, 0.9f, g.xf.t) else 1f
                surfaceY = if (up) BREACH_FROM * (1f - breach) else 0f
                depth = 0f
                groundY = -4000f // the sea is far deeper than anything the waterplane can see
                roadVisible = false
            }
            VehicleMode.ROCKET -> {
                // the plunge happens during the transformation: the surface comes up over the vehicle; then it slowly recedes.
                // Going up it is all the other way round: the seabed falls away as the car lifts off, and the surface comes down to the vehicle.
                val q = if (up) 1f - p else p
                val plunge = if (!up && g.xf.active) Mathx.smoothstep(0f, 0.9f, g.xf.t) else 1f
                surfaceY = BREACH_FROM * plunge + 48f * Mathx.smoothstep(0.02f, 0.4f, q) + 330f * Mathx.smoothstep(0.4f, 1f, q)
                depth = 0.9f * Mathx.smoothstep(0f, 0.8f, q)
                val rise = Mathx.smoothstep(0.25f, 1f, q)
                var floor = Mathx.lerp(-420f, -6f, rise * rise)
                if (up && g.xf.active) floor = Mathx.lerp(0f, floor, Mathx.smoothstep(0.05f, 0.92f, g.xf.t))
                groundY = floor
                roadVisible = false
            }
            VehicleMode.CAR -> {
                // the seabed comes up to meet the vehicle as it lands on it (going down); going up the car starts on it
                val q = if (up) 1f - p else p
                val land = if (!up && g.xf.active) Mathx.smoothstep(0.05f, 0.92f, g.xf.t) else 1f
                surfaceY = 900f
                depth = 0.9f + 0.1f * Mathx.smoothstep(0f, 1f, q)
                groundY = Mathx.lerp(-6f, 0f, land)
                roadVisible = true
            }
        }
    }

    companion object {
        /** The height of the sea surface above the submarine at the end of the ascent (and at the start of the dive): 22 m, where the submarine's ceiling is 15 m. */
        const val BREACH_FROM = 22f
    }
}
