package app.roadtoorbit.gfx

import app.roadtoorbit.game.Bot
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.Kind
import app.roadtoorbit.game.Levels
import app.roadtoorbit.game.Phase
import app.roadtoorbit.game.World
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The road, its shoulders and the roadside scenery are placed at heights the game fixes as if the ground were flat there,
 * so the Martian terrain must be exactly flat in the corridor around the route (a mesa or a crater rim rising through the
 * road would be a bug you only see by driving into it).
 */
class MarsTerrainTest {
    private fun marsHeightSource(): String {
        val src = Shaders.TERRAIN_VERT
        val start = src.indexOf("float marsHeight(")
        val end = src.indexOf("float cloudHeight(")
        assertTrue(start in 0 until end, "marsHeight should be defined before cloudHeight")
        return src.substring(start, end)
    }

    @Test fun everyTermOfTheMartianHeightIsFadedInOutsideTheCorridor() {
        val body = marsHeightSource()
        assertTrue(body.contains("float valley = smoothstep(uTer.z, uTer.z + 130.0, ax);"), "the mesas fade in from the corridor edge")
        assertTrue(body.contains("* uTer.y * valley;"), "the mesa field is scaled by the fade")
        assertTrue(body.contains("* smoothstep(uTer.z - 2.0, uTer.z + 6.0, ax);"), "the gravel ripples fade in at the corridor edge")
        assertTrue(body.contains("craters(xs + 77.0, 260.0) * 4.0 * valley;"), "the craters are scaled by the fade")
        // nothing else may add to the height
        val additions = Regex("""\bh \+= ([^;]+);""").findAll(body).map { it.groupValues[1] }.toList()
        assertEquals(2, additions.size, "marsHeight adds exactly the ripples and the craters to the mesas: $additions")
    }

    @Test fun theCorridorIsAsWideAsTheRoadsideSceneryNeedsAndTheSceneryStaysInsideIt() {
        val game = Game(5)
        game.level = Levels.ALL.first { it.world == World.MARS }
        game.startRun(5)
        val env = Environment()
        val model = EnvironmentModel()
        val input = GameInput()
        val bot = Bot(game)
        var worstDecor = 0f
        val decor = setOf(Kind.ROCK, Kind.MESA, Kind.DEVIL, Kind.DOME, Kind.BILLBOARD)
        var t = 0f
        while (t < 90f && game.phase != Phase.GAME_OVER && game.legIndex == 0) {
            bot.control(input, 1f / 60f)
            game.update(1f / 60f, input)
            model.update(game, env)
            assertEquals(1f, env.world, "Mars")
            assertEquals(0f, env.terrainStyle, "the car leg drives on land")
            assertTrue(env.corridor >= 60f, "corridor ${env.corridor}")
            for (e in game.entities) if (e.kind in decor) worstDecor = maxOf(worstDecor, abs(e.x))
            while (game.pollSfx() != null) { /* drain */ }
            t += 1f / 60f
        }
        assertTrue(worstDecor > 20f, "the roadside should have scenery at all (widest $worstDecor)")
        assertTrue(worstDecor <= 60f, "scenery stands at |x| = $worstDecor, outside the flat 60 m corridor")
    }
}
