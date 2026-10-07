package app.roadtoorbit.gfx

import app.roadtoorbit.game.Bot
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.Levels
import app.roadtoorbit.game.Phase
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rocket lands and the car drives off along the route at heights the game fixes as if the ground were flat
 * there. The Moon's terrain used to keep 35 % of its craters and ridges right along the route, so they rose through
 * the vehicle ("the car passed through the moon mountains"). The strip around the route must be exactly flat.
 */
class MoonTerrainTest {
    @Test
    fun theShaderIsBuiltFromTheSharedStripWidths() {
        val expected = "smoothstep(${MoonTerrain.FLAT_HALF_WIDTH}, ${MoonTerrain.FULL_RELIEF_AT}, abs(xs.x))"
        assertTrue(Shaders.TERRAIN_VERT.contains(expected), "moonHeight must fade in the relief with $expected")
        assertTrue(!Shaders.TERRAIN_VERT.contains("mix(0.35, 1.0, flat_)"), "the old, never-quite-flat corridor is back")
    }

    @Test
    fun theVehicleAndTheCameraStayInsideTheFlatStripThroughTheFinaleAndTheVictoryDrive() {
        // the Moon and Phobos share the landing strip
        for (level in Levels.ALL) theVehicleAndTheCameraStayInsideTheFlatStrip(level)
    }

    private fun theVehicleAndTheCameraStayInsideTheFlatStrip(level: app.roadtoorbit.game.LevelSpec) {
        val game = Game(7)
        game.level = level
        val input = GameInput()
        val rig = CameraRig()
        val bot = Bot(game)
        val dt = 1f / 60f
        game.startRun(7)

        var maxVehicleX = 0f
        var maxVehicleXOnTheGround = 0f
        var maxCameraX = 0f
        var maxTargetX = 0f
        var minCameraY = Float.MAX_VALUE
        var finaleFrames = 0
        var victoryFrames = 0
        var t = 0f
        while (t < 400f && !(game.phase == Phase.VICTORY && game.phaseTime > 30f)) {
            bot.control(input, dt)
            game.update(dt, input)
            rig.update(game, dt)
            if (game.phase == Phase.FINALE || game.phase == Phase.VICTORY) {
                if (game.phase == Phase.FINALE) finaleFrames++ else victoryFrames++
                maxVehicleX = maxOf(maxVehicleX, abs(game.player.x))
                // after touchdown (4.2 s into the finale) it skids and drives along the route
                if (game.phase == Phase.VICTORY || game.finaleT > 4.5f) maxVehicleXOnTheGround = maxOf(maxVehicleXOnTheGround, abs(game.player.x))
                maxCameraX = maxOf(maxCameraX, abs(rig.camera.ex))
                maxTargetX = maxOf(maxTargetX, abs(rig.camera.tx))
                minCameraY = minOf(minCameraY, rig.camera.ey)
            }
            t += dt
        }
        assertEquals(Phase.VICTORY, game.phase, "${level.name}: the pilot should reach ${level.destination}")
        assertTrue(finaleFrames > 300 && victoryFrames > 1_000, "${level.name}: finale $finaleFrames frames, victory $victoryFrames frames")

        val strip = MoonTerrain.FLAT_HALF_WIDTH
        assertTrue(maxVehicleX < strip - 6f, "${level.name}: the vehicle glides in from |x| = $maxVehicleX; it must stay well inside the $strip m flat strip")
        assertTrue(maxVehicleXOnTheGround < 1.5f, "${level.name}: on the ground the vehicle follows the route (max |x| = $maxVehicleXOnTheGround)")
        assertTrue(maxCameraX < strip - 6f, "${level.name}: the camera swings out to |x| = $maxCameraX; it must stay well inside the ${strip} m flat strip")
        assertTrue(maxTargetX < strip - 6f, "${level.name}: the camera looks at |x| = $maxTargetX")
        assertTrue(minCameraY > 0.5f, "${level.name}: the camera dips to y = $minCameraY, too close to the ground plane")
    }
}
