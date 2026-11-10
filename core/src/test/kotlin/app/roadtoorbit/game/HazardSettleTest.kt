package app.roadtoorbit.game

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The spawner drops a hazard that would run through another (a car through the barrier ahead of it in its lane, a mine through a mine):
 * it must be a small share of what it places, or the levels would be emptier than they were meant to be, and what it keeps must
 * not be inside each other (the audit of what is drawn, PassThroughAuditTest, checks that on screen).
 */
class HazardSettleTest {
    private val dt = 1f / 60f

    @Test fun theSpawnerDropsOnlyAFewHazards() {
        for (level in Levels.ALL) {
            var placed = 0
            var dropped = 0
            for (seed in 1L..8L) {
                val game = Game(seed)
                game.level = level
                val input = GameInput()
                val bot = Bot(game)
                game.startRun(seed)
                var t = 0f
                while (t < 520f && !(game.phase == Phase.VICTORY && game.phaseTime > 1f) && game.phase != Phase.GAME_OVER) {
                    bot.control(input, dt); game.update(dt, input); t += dt
                    while (game.pollSfx() != null) { /* drain */ }
                }
                placed += game.hazardsPlaced
                dropped += game.hazardsDropped
            }
            println("${level.name}: the spawner placed $placed hazards and dropped $dropped (${100 * dropped / maxOf(1, placed)} %)")
            assertTrue(placed > 500, "${level.name}: hazards placed: $placed")
            assertTrue(dropped * 100 < placed * 22, "${level.name}: dropped $dropped of $placed hazards: too many")
        }
    }

    @Test fun carsNeverCatchUpWithCarsAheadOfThem() {
        // within a lane, a car is never faster than the one ahead of it (nearest to the vehicle first): the faster one behind would drive through it
        for (level in Levels.ALL) {
            for (seed in 1L..6L) {
                val game = Game(seed)
                game.level = level
                val input = GameInput()
                val bot = Bot(game)
                game.startRun(seed)
                var t = 0f
                while (t < 520f && !(game.phase == Phase.VICTORY && game.phaseTime > 1f) && game.phase != Phase.GAME_OVER) {
                    bot.control(input, dt); game.update(dt, input); t += dt
                    while (game.pollSfx() != null) { /* drain */ }
                    val cars = game.entities.filter { it.kind == Kind.TRAFFIC && !it.hit && it.z < 0f }
                    for (a in cars) for (b in cars) {
                        if (a === b || kotlin.math.abs(a.x - b.x) > 0.5f || a.z <= b.z) continue // a is nearer to the vehicle than b, in the same lane
                        assertTrue(a.speed <= b.speed + 1e-3f, "${level.name} seed $seed: a car doing ${a.speed} m/s is ${b.z - a.z} m behind one doing ${b.speed}")
                    }
                }
            }
        }
    }
}
