package app.roadtoorbit.game

import app.roadtoorbit.gfx.Footprints
import app.roadtoorbit.gfx.OceanSpec
import app.roadtoorbit.gfx.VehicleMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Scenery is drawn but not solid, so none of it may stand where the vehicle can go: whatever the spawners place, the nearest part
 * of it stays clear of the lanes of the leg that will meet it (a flying vehicle's lanes are much wider than a car's). Many seeds, so
 * that the unlucky combinations of size, turn and distance come up too.
 */
class SceneryClearanceTest {
    private val dt = 1f / 60f

    @Test fun sceneryNeverStandsInTheLanes() {
        var checked = 0
        for (level in Levels.ALL) {
            for (seed in 1L..14L) {
                val game = Game(seed)
                game.level = level
                game.difficulty = if (seed % 3L == 0L) Difficulty.HARD else Difficulty.NORMAL
                val input = GameInput()
                val bot = Bot(game)
                game.startRun(seed)
                var t = 0f
                while (t < 520f && !(game.phase == Phase.VICTORY && game.phaseTime > 1f) && game.phase != Phase.GAME_OVER) {
                    bot.control(input, dt)
                    game.update(dt, input)
                    while (game.pollSfx() != null) { /* drain */ }
                    t += dt
                    if (game.phase != Phase.RUN) continue
                    val leg = game.leg
                    for (e in game.entities) {
                        if (e.age > 0.03f || e.kind.category != Category.DECOR) continue
                        val reach = Footprints.reach(e.kind, e.variant)
                        if (reach <= 0f) continue
                        if (e.kind == Kind.SEASCAPE && e.variant >= OceanSpec.WHALE) continue // the whale swims far off; the merfolk and the flags line the avenue on purpose
                        // what the leg this was placed for can meet: a car (or, near the end of a leg that ends in a transformation, a flying vehicle) or a flying one
                        val s = game.legDist - e.z
                        val flyers = leg.mode != VehicleMode.CAR || (leg.zoneLength > 0f && s > leg.length - leg.zoneLength - 20f)
                        val clear = if (flyers) Footprints.AIR_CLEAR else Footprints.CAR_CLEAR
                        val gap = abs(e.x) - reach * e.scale
                        checked++
                        if (e.kind == Kind.SEASCAPE && (e.variant == OceanSpec.SCHOOL || e.variant == OceanSpec.SCHOOL + 1) && !flyers) {
                            // a school over the seabed road: either beside it or high enough over a car
                            assertTrue(gap >= clear - 0.01f || e.y - 3.05f * e.scale >= 2.3f, "${level.name} seed $seed: a school at x=${e.x} y=${e.y} scale ${e.scale} hangs into the car's lane")
                            continue
                        }
                        assertTrue(gap >= clear - 0.01f, "${level.name} seed $seed leg ${leg.index}: ${e.kind}/${e.variant} at x=%.1f (scale %.2f, reach %.1f) comes within %.1f m of the route, the ${if (flyers) "flyers" else "car"} need %.1f m".format(e.x, e.scale, reach, gap, clear))
                    }
                }
            }
        }
        assertTrue(checked > 5_000, "pieces of scenery checked: $checked")
    }
}
