package app.roadtoorbit.gfx

import app.roadtoorbit.game.Game
import app.roadtoorbit.game.Phase
import app.roadtoorbit.game.World
import app.roadtoorbit.math.Mathx
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Moon's landing and driving strip. The rocket lands and the car drives off along the route at heights the game
 * fixes as if the ground were flat there, so the terrain must be exactly flat in this strip - otherwise craters and
 * ridges rise through the vehicle - and the finale camera has to stay inside it too. The shader is built from these
 * numbers, and a test checks the vehicle and the camera against them.
 */
object MoonTerrain {
    /** Half width in metres of the strip around the route that has no relief at all. */
    const val FLAT_HALF_WIDTH = 26f

    /** Distance in metres from the route at which the hills have reached their full height. */
    const val FULL_RELIEF_AT = 90f
}

/** The colours of one world at ground level, at the edge of space and in space, plus its light. */
private class Palette(
    val dayZenith: FloatArray, val dayHorizon: FloatArray, val dayGround: FloatArray,
    val highZenith: FloatArray, val highHorizon: FloatArray,
    val spaceZenith: FloatArray, val spaceHorizon: FloatArray,
    val sunDay: FloatArray, val sunSpace: FloatArray,
    val ambSkyDay: FloatArray, val ambSkySpace: FloatArray,
    val ambGroundDay: FloatArray, val ambGroundSpace: FloatArray,
    /** Fog density on the ground, at the start of the climb and above the clouds. */
    val fogGround: Float, val fogClimbStart: Float, val fogClimbEnd: Float,
)

/**
 * Turns the game state into the look of the world: the sky/fog/light palette slides continuously
 * from a bright day, through a deepening blue stratosphere, into the black of space as the journey's
 * altitude rises. The "curved world" bend winds the road and finally curves the horizon away.
 */
class EnvironmentModel {
    private val earth = Palette(
        dayZenith = floatArrayOf(0.20f, 0.46f, 0.92f), dayHorizon = floatArrayOf(0.84f, 0.91f, 0.98f), dayGround = floatArrayOf(0.60f, 0.68f, 0.74f),
        highZenith = floatArrayOf(0.03f, 0.07f, 0.30f), highHorizon = floatArrayOf(0.42f, 0.60f, 0.88f),
        spaceZenith = floatArrayOf(0.0f, 0.0f, 0.012f), spaceHorizon = floatArrayOf(0.012f, 0.016f, 0.045f),
        sunDay = floatArrayOf(1.0f, 0.95f, 0.86f), sunSpace = floatArrayOf(1.0f, 0.97f, 0.92f),
        ambSkyDay = floatArrayOf(0.50f, 0.58f, 0.70f), ambSkySpace = floatArrayOf(0.16f, 0.19f, 0.30f),
        ambGroundDay = floatArrayOf(0.34f, 0.32f, 0.28f), ambGroundSpace = floatArrayOf(0.09f, 0.09f, 0.15f),
        fogGround = 0.0040f, fogClimbStart = 0.0036f, fogClimbEnd = 0.0016f,
    )

    /** Butterscotch sky and rust-coloured dust: a thin, dusty atmosphere that is gone a few kilometres up. */
    private val mars = Palette(
        dayZenith = floatArrayOf(0.50f, 0.36f, 0.36f), dayHorizon = floatArrayOf(0.90f, 0.66f, 0.46f), dayGround = floatArrayOf(0.62f, 0.38f, 0.26f),
        highZenith = floatArrayOf(0.07f, 0.04f, 0.14f), highHorizon = floatArrayOf(0.62f, 0.36f, 0.36f),
        spaceZenith = floatArrayOf(0.0f, 0.0f, 0.012f), spaceHorizon = floatArrayOf(0.022f, 0.012f, 0.035f),
        sunDay = floatArrayOf(1.0f, 0.90f, 0.78f), sunSpace = floatArrayOf(1.0f, 0.96f, 0.90f),
        ambSkyDay = floatArrayOf(0.60f, 0.46f, 0.42f), ambSkySpace = floatArrayOf(0.17f, 0.17f, 0.28f),
        ambGroundDay = floatArrayOf(0.38f, 0.24f, 0.18f), ambGroundSpace = floatArrayOf(0.10f, 0.08f, 0.13f),
        fogGround = 0.0046f, fogClimbStart = 0.0040f, fogClimbEnd = 0.0016f,
    )

    private val menuZenith = floatArrayOf(0.04f, 0.06f, 0.20f)
    private val menuHorizon = floatArrayOf(0.52f, 0.30f, 0.46f)

    // the menu's backdrop takes on the colour of the chosen level, so picking Mars is visible at once
    private val menuMarsZenith = floatArrayOf(0.13f, 0.05f, 0.10f)
    private val menuMarsHorizon = floatArrayOf(0.62f, 0.31f, 0.22f)

    fun update(g: Game, e: Environment) {
        e.time = g.clock
        val look = g.look
        val a = look.altitude
        val k1 = Mathx.smoothstep(0f, 1f, a)
        val k2 = Mathx.smoothstep(0.95f, 1.7f, a)

        if (g.phase == Phase.MENU) {
            menu(e, g)
            return
        }

        val isMars = g.level.world == World.MARS
        val pal = if (isMars) mars else earth
        e.world = if (isMars) 1f else 0f

        mix3(pal.dayZenith, pal.highZenith, k1, e.zenith); mix3(e.zenith, pal.spaceZenith, k2, e.zenith)
        mix3(pal.dayHorizon, pal.highHorizon, k1, e.horizon); mix3(e.horizon, pal.spaceHorizon, k2, e.horizon)
        mix3(pal.dayGround, pal.highHorizon, k1, e.groundCol); mix3(e.groundCol, pal.spaceHorizon, k2, e.groundCol)
        e.fogColor[0] = e.horizon[0]; e.fogColor[1] = e.horizon[1]; e.fogColor[2] = e.horizon[2]

        // light comes from behind and above so the vehicle and obstacles are always well lit
        val sx = Mathx.lerp(-0.45f, 0.5f, k2)
        val sy = Mathx.lerp(0.62f, 0.42f, k2)
        val sz = 0.62f
        e.setSun(sx, sy, sz)
        mix3(pal.sunDay, pal.sunSpace, k2, e.sunColor)
        mix3(pal.ambSkyDay, pal.ambSkySpace, k2, e.ambSky)
        mix3(pal.ambGroundDay, pal.ambGroundSpace, k2, e.ambGround)

        e.starAmount = Mathx.smoothstep(0.55f, 1.05f, a)
        e.nebula = Mathx.smoothstep(1.1f, 2.0f, a) * 0.8f
        e.sunDisc = 0f

        val s = g.travelled.toFloat()
        val curve = 0.6f * sin(s / 310f) + 0.4f * sin(s / 173f + 1.7f)
        val hills = 0.5f * sin(s / 260f + 0.4f) + 0.5f * sin(s / 131f)
        val calm = 1f - k2
        when (g.legIndex) {
            0 -> {
                e.bendX = 0.00032f * curve
                e.bendY = 0.00014f * hills - 0.00004f
                e.fogDensity = pal.fogGround
            }
            1 -> {
                val mild = Mathx.lerp(0.55f, 0.25f, k1)
                e.bendX = 0.00032f * curve * mild * calm
                e.bendY = (0.00014f * hills * mild - 0.00004f - 0.00006f * (0.3f + 0.7f * k1)) * calm
                e.fogDensity = Mathx.lerp(pal.fogClimbStart, pal.fogClimbEnd, k1)
            }
            else -> {
                e.bendX = 0f; e.bendY = 0f
                e.fogDensity = Mathx.lerp(0.0016f, 0.00012f, k2)
            }
        }
        e.bendStart = 14f

        e.terrainOn = look.terrainOn
        e.groundY = look.groundY
        e.terrainStyle = look.terrainStyle
        e.terrainAmp = if (look.terrainStyle > 1.5f) 8f else 100f
        e.corridor = if (look.terrainStyle > 1.5f) MoonTerrain.FLAT_HALF_WIDTH else 60f
        e.terrainTint[0] = 1f; e.terrainTint[1] = 1f; e.terrainTint[2] = 1f

        if (g.phase == Phase.FINALE || g.phase == Phase.VICTORY) {
            // moon plain: ink-black sky, hard sun, a little ground fog so the flat terrain edge dissolves
            e.fogDensity = 0.0021f
            e.fogColor[0] = 0.03f; e.fogColor[1] = 0.035f; e.fogColor[2] = 0.06f
            e.bendX = 0f; e.bendY = 0f
            e.sunColor[0] = 1.05f; e.sunColor[1] = 1.02f; e.sunColor[2] = 0.97f
            e.nebula = 0.7f
            e.starAmount = 1f
            if (isMars) {
                // the sun stays behind the camera as it swings round the landing, so the car and Mars (which hangs
                // behind the landing site) are lit when the victory drive is in view
                val swing = Mathx.smoother((g.finaleT - 1f) / 6f)
                val sunAz = swing * 2.55f - 0.15f * (1f - swing) + 0.55f
                e.setSun(sin(sunAz) * 0.78f, 0.42f, cos(sunAz) * 0.78f)
            }
        }
    }

    private fun menu(e: Environment, g: Game) {
        val mars = g.level.world == World.MARS
        copy(if (mars) menuMarsZenith else menuZenith, e.zenith)
        copy(if (mars) menuMarsHorizon else menuHorizon, e.horizon)
        copy(if (mars) menuMarsHorizon else menuHorizon, e.fogColor)
        e.groundCol[0] = 0.20f; e.groundCol[1] = 0.12f; e.groundCol[2] = 0.28f
        e.setSun(-0.5f, 0.55f, 0.65f)
        e.sunColor[0] = 1.0f; e.sunColor[1] = 0.93f; e.sunColor[2] = 0.88f
        e.ambSky[0] = 0.42f; e.ambSky[1] = 0.40f; e.ambSky[2] = 0.58f
        e.ambGround[0] = 0.20f; e.ambGround[1] = 0.16f; e.ambGround[2] = 0.28f
        e.fogDensity = 0.0009f
        e.starAmount = 0.75f
        e.nebula = 0.55f
        e.sunDisc = 0f
        e.bendX = 0f; e.bendY = 0f; e.bendStart = 14f
        e.terrainOn = false
        e.time = g.clock
    }

    private fun mix3(a: FloatArray, b: FloatArray, t: Float, out: FloatArray) {
        out[0] = a[0] + (b[0] - a[0]) * t
        out[1] = a[1] + (b[1] - a[1]) * t
        out[2] = a[2] + (b[2] - a[2]) * t
    }

    private fun copy(a: FloatArray, out: FloatArray) {
        out[0] = a[0]; out[1] = a[1]; out[2] = a[2]
    }
}
