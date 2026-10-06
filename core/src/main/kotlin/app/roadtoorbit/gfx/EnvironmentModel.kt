package app.roadtoorbit.gfx

import app.roadtoorbit.game.Game
import app.roadtoorbit.game.Phase
import app.roadtoorbit.math.Mathx
import kotlin.math.sin

/**
 * Turns the game state into the look of the world: the sky/fog/light palette slides continuously
 * from a bright day, through a deepening blue stratosphere, into the black of space as the journey's
 * altitude rises. The "curved world" bend winds the road and finally curves the horizon away.
 */
class EnvironmentModel {
    private val dayZenith = floatArrayOf(0.20f, 0.46f, 0.92f)
    private val dayHorizon = floatArrayOf(0.84f, 0.91f, 0.98f)
    private val dayGround = floatArrayOf(0.60f, 0.68f, 0.74f)
    private val highZenith = floatArrayOf(0.03f, 0.07f, 0.30f)
    private val highHorizon = floatArrayOf(0.42f, 0.60f, 0.88f)
    private val spaceZenith = floatArrayOf(0.0f, 0.0f, 0.012f)
    private val spaceHorizon = floatArrayOf(0.012f, 0.016f, 0.045f)

    private val menuZenith = floatArrayOf(0.04f, 0.06f, 0.20f)
    private val menuHorizon = floatArrayOf(0.52f, 0.30f, 0.46f)

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

        mix3(dayZenith, highZenith, k1, e.zenith); mix3(e.zenith, spaceZenith, k2, e.zenith)
        mix3(dayHorizon, highHorizon, k1, e.horizon); mix3(e.horizon, spaceHorizon, k2, e.horizon)
        mix3(dayGround, highHorizon, k1, e.groundCol); mix3(e.groundCol, spaceHorizon, k2, e.groundCol)
        e.fogColor[0] = e.horizon[0]; e.fogColor[1] = e.horizon[1]; e.fogColor[2] = e.horizon[2]

        // light comes from behind and above so the vehicle and obstacles are always well lit
        val sx = Mathx.lerp(-0.45f, 0.5f, k2)
        val sy = Mathx.lerp(0.62f, 0.42f, k2)
        val sz = 0.62f
        e.setSun(sx, sy, sz)
        mix3(floatArrayOf(1.0f, 0.95f, 0.86f), floatArrayOf(1.0f, 0.97f, 0.92f), k2, e.sunColor)
        mix3(floatArrayOf(0.50f, 0.58f, 0.70f), floatArrayOf(0.16f, 0.19f, 0.30f), k2, e.ambSky)
        mix3(floatArrayOf(0.34f, 0.32f, 0.28f), floatArrayOf(0.09f, 0.09f, 0.15f), k2, e.ambGround)

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
                e.fogDensity = 0.0040f
            }
            1 -> {
                val mild = Mathx.lerp(0.55f, 0.25f, k1)
                e.bendX = 0.00032f * curve * mild * calm
                e.bendY = (0.00014f * hills * mild - 0.00004f - 0.00006f * (0.3f + 0.7f * k1)) * calm
                e.fogDensity = Mathx.lerp(0.0036f, 0.0016f, k1)
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
        e.corridor = if (look.terrainStyle > 1.5f) 14f else 60f
        e.terrainTint[0] = 1f; e.terrainTint[1] = 1f; e.terrainTint[2] = 1f

        if (g.phase == Phase.FINALE || g.phase == Phase.VICTORY) {
            // moon plain: ink-black sky, hard sun, a little ground fog so the flat terrain edge dissolves
            e.fogDensity = 0.0021f
            e.fogColor[0] = 0.03f; e.fogColor[1] = 0.035f; e.fogColor[2] = 0.06f
            e.bendX = 0f; e.bendY = 0f
            e.sunColor[0] = 1.05f; e.sunColor[1] = 1.02f; e.sunColor[2] = 0.97f
            e.nebula = 0.7f
            e.starAmount = 1f
        }
    }

    private fun menu(e: Environment, g: Game) {
        copy(menuZenith, e.zenith); copy(menuHorizon, e.horizon); copy(menuHorizon, e.fogColor)
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
