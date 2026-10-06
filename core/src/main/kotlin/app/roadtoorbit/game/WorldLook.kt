package app.roadtoorbit.game

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

    fun update(g: Game) {
        val leg = Tuning.LEGS[g.legIndex]
        val p = Mathx.clamp01(g.legDist / leg.length)
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
        moonApproach = if (g.legIndex == 2) Mathx.smoothstep(0.55f, 1f, p) else 0f
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
}
