package app.roadtoorbit.game

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The boulders of the Martian highway wait at the roadside and start to roll across the road shortly before they reach
 * the car, aimed at a lane. They must give a human time to see them coming and must really cross the road.
 */
class RockfallTest {
    private class Track(var armedAtZ: Float = Float.NaN, var armedSpeed: Float = 0f, var xAtPass: Float = Float.NaN, var wasAhead: Boolean = true)

    private fun boulders(level: LevelSpec, seconds: Float, seed: Long): Pair<Int, List<Track>> {
        val game = Game(seed)
        game.level = level
        game.startRun(seed)
        val input = GameInput() // nobody steers; the vehicle is unbreakable so that the run goes on
        val tracks = java.util.IdentityHashMap<Entity, Track>()
        var seen = 0
        var t = 0f
        while (t < seconds && game.legIndex == 0 && game.phase != Phase.GAME_OVER) {
            game.player.invuln = 99f
            game.update(1f / 60f, input)
            while (game.pollSfx() != null) { /* drain */ }
            for (e in game.entities) {
                if (e.kind != Kind.BOULDER) continue
                val tr = tracks.getOrPut(e) { seen++; Track() }
                if (tr.armedAtZ.isNaN() && e.vx != 0f) { tr.armedAtZ = e.z; tr.armedSpeed = game.player.speed }
                if (tr.wasAhead && e.z >= 0f) { tr.wasAhead = false; tr.xAtPass = e.x }
            }
            t += 1f / 60f
        }
        return seen to tracks.values.toList()
    }

    @Test fun theMoonHasNoBoulders() {
        val (seen, _) = boulders(Levels[0], 80f, 3)
        assertEquals(0, seen)
    }

    @Test fun marsBouldersStartLateAndCrossTheRoad() {
        var crossed = 0
        for (seed in 1L..4L) {
            val (seen, tracks) = boulders(Levels[1], 70f, seed)
            assertTrue(seen > 0, "seed $seed: the highway should have boulders")
            for (tr in tracks) {
                if (tr.xAtPass.isNaN()) continue // still on its way when the run ended
                assertTrue(!tr.armedAtZ.isNaN(), "a boulder reached the car without ever rolling")
                val seconds = -tr.armedAtZ / tr.armedSpeed
                assertTrue(seconds in 1.3f..2.2f, "the roll should start about 1.5 - 1.9 s ahead of the car, not $seconds s")
                assertTrue(abs(tr.xAtPass) < 6.0f, "a boulder passes the car at x = ${tr.xAtPass}, outside the road")
                crossed++
            }
        }
        assertTrue(crossed >= 4, "only $crossed boulders crossed the road in four runs")
    }
}
