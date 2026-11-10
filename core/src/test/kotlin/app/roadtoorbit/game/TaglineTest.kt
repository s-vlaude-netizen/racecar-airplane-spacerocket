package app.roadtoorbit.game

import kotlin.test.Test
import kotlin.test.assertEquals

/** The menu names the three disciplines in the order the level comes in (and says nothing of what the rocket does in the deep). */
class TaglineTest {
    @Test fun theTaglineFollowsTheOrderOfTheLegs() {
        assertEquals("RACE   ·   FLY   ·   LAUNCH", Levels[0].tagline)
        assertEquals("RACE   ·   FLY   ·   LAUNCH", Levels[1].tagline)
        assertEquals("FLY   ·   LAUNCH   ·   RACE", Levels.ALL.first { it.world == World.OCEAN && it.legs[0].mode == app.roadtoorbit.gfx.VehicleMode.PLANE }.tagline)
    }

    @Test fun theHudCarriesTheTaglineOfTheSelectedLevel() {
        val game = Game(1)
        val hud = HudState()
        for (level in Levels.ALL) {
            game.level = level
            game.fillHud(hud)
            assertEquals(level.tagline, hud.tagline)
        }
    }
}
