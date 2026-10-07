package app.roadtoorbit.gfx

import app.roadtoorbit.game.Bot
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.Levels
import app.roadtoorbit.game.Phase
import app.roadtoorbit.gl.NullGles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "The Moon landscape is cut off too near": a single fine grid reached 400 m ahead, 190 m to the sides and only 32 m behind
 * the vehicle - and the victory camera looks back over the car - so the plain ended in a visible edge instead of at the
 * horizon, and there were no mountains to see. The landscape is now a fine grid plus a coarse one reaching 2 km.
 */
class MoonHorizonTest {
    private val nearSide = MoonTerrain.NEAR_COLS * MoonTerrain.NEAR_CELL / 2f
    private val nearAhead = (MoonTerrain.NEAR_ROWS - MoonTerrain.NEAR_BEHIND) * MoonTerrain.NEAR_CELL
    private val nearBehind = MoonTerrain.NEAR_BEHIND * MoonTerrain.NEAR_CELL
    private val farSide = MoonTerrain.FAR_COLS * MoonTerrain.FAR_CELL / 2f
    private val farAhead = (MoonTerrain.FAR_ROWS - MoonTerrain.FAR_BEHIND) * MoonTerrain.FAR_CELL
    private val farBehind = MoonTerrain.FAR_BEHIND * MoonTerrain.FAR_CELL

    @Test fun theLandscapeReachesTheHorizonInEveryDirection() {
        // seen from a camera a few metres up, an edge 1.5 km away is within a few pixels of the true horizon
        for ((name, metres) in listOf("ahead" to farAhead, "behind" to farBehind, "to the side" to farSide)) {
            assertTrue(metres >= 1500f, "the landscape reaches only $metres m $name")
        }
        // the fine grid lies well inside the coarse one (each slides by up to one cell as the vehicle moves)
        val margin = 2 * MoonTerrain.FAR_CELL
        assertTrue(nearSide + margin <= farSide && nearAhead + margin <= farAhead && nearBehind + margin <= farBehind, "the fine grid must sit inside the coarse one")
        // and it covers what the finale camera sees close up, in front of and behind the car
        assertTrue(nearBehind >= 200f && nearAhead >= 300f && nearSide >= 150f, "fine grid: $nearAhead ahead, $nearBehind behind, $nearSide to the side")
    }

    @Test fun bothGridsFitInSixteenBitIndices() {
        for ((cols, rows) in listOf(MoonTerrain.NEAR_COLS to MoonTerrain.NEAR_ROWS, MoonTerrain.FAR_COLS to MoonTerrain.FAR_ROWS)) {
            assertTrue((cols + 1) * (rows + 1) < 65_536, "a $cols x $rows grid has too many vertices for 16-bit indices")
        }
    }

    @Test fun theMountainsStandBeyondTheFineGridAndInsideTheCoarseOne() {
        assertTrue(MoonTerrain.RANGE_FROM >= nearSide + 100f, "mountains must start well outside the fine grid")
        assertTrue(MoonTerrain.RANGE_FROM > MoonTerrain.FULL_RELIEF_AT && MoonTerrain.RANGE_FULL > MoonTerrain.RANGE_FROM)
        assertTrue(MoonTerrain.RANGE_FULL <= farSide - 300f, "the mountains reach full height before the landscape ends")
        val src = Shaders.TERRAIN_VERT
        assertTrue(src.contains("smoothstep(${MoonTerrain.RANGE_FROM}, ${MoonTerrain.RANGE_FULL}, abs(xs.x)) * uRange"), "the shader raises the ranges from the shared distances")
        assertTrue(src.contains("* ${MoonTerrain.RANGE_HEIGHT}"), "the shader uses the shared mountain height")
    }

    private class CountingGles : NullGles() {
        val indexCounts = ArrayList<Int>()
        override fun drawElements(mode: Int, count: Int, type: Int, offsetBytes: Int) { indexCounts.add(count) }
    }

    @Test fun theCoarseGridIsOnlyDrawnLowDownAndTheFineOneAlways() {
        val lib = MeshLibrary()
        val near = MoonTerrain.NEAR_COLS * MoonTerrain.NEAR_ROWS * 6
        val far = MoonTerrain.FAR_COLS * MoonTerrain.FAR_ROWS * 6
        for (level in Levels.ALL) {
            val gl = CountingGles()
            val scene = SceneRenderer(gl, lib)
            scene.resize(1600, 740)
            val game = Game(7)
            game.level = level
            game.startRun(7)
            val input = GameInput()
            val bot = Bot(game)
            val dt = 1f / 60f
            var farFrames = 0
            var firstFarAltitude = Float.MAX_VALUE
            var t = 0f
            while (t < 400f && !(game.phase == Phase.VICTORY && game.phaseTime > 3f)) {
                bot.control(input, dt)
                game.update(dt, input)
                scene.rig.update(game, dt)
                while (game.pollSfx() != null) { /* drain */ }
                gl.indexCounts.clear()
                scene.render(game)
                val nears = gl.indexCounts.count { it == near }
                val fars = gl.indexCounts.count { it == far }
                val landing = game.phase == Phase.FINALE || game.phase == Phase.VICTORY
                val altitude = game.player.y - game.look.groundY
                if (!landing) {
                    assertEquals(0, nears + fars, "${level.name}: the Moon's landscape in ${game.phase}")
                } else {
                    assertEquals(1, nears, "${level.name}: the fine grid is drawn in every frame of the landing")
                    assertEquals(if (altitude < MoonTerrain.FAR_FROM_ALTITUDE) 1 else 0, fars, "${level.name}: coarse grid at $altitude m")
                    if (fars == 1) { farFrames++; firstFarAltitude = minOf(firstFarAltitude, altitude); assertTrue(altitude < MoonTerrain.FAR_FROM_ALTITUDE) }
                }
                t += dt
            }
            assertEquals(Phase.VICTORY, game.phase, "${level.name}: the pilot should land")
            assertTrue(farFrames > 400, "${level.name}: only $farFrames frames with the whole landscape (about 4 s of landing and 3 s of victory drive are played)")
            scene.release()
        }
    }
}
