package app.roadtoorbit.gfx

import app.roadtoorbit.game.Bot
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.Kind
import app.roadtoorbit.game.Levels
import app.roadtoorbit.game.Phase
import app.roadtoorbit.game.World
import app.roadtoorbit.gl.NullGles
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The arrival in Atlantis must not be an anticlimax: people, fish, flags and fireworks are there when the car stops. */
class ArrivalCelebrationTest {
    private val deep = Levels.ALL.first { it.world == World.OCEAN }

    @Test fun everyFishIsSomewhereSensibleAtEveryMoment() {
        val field = CelebrationFish()
        val now = FloatArray(3)
        val soon = FloatArray(3)
        var seen = 0
        var t = 0f
        while (t < 60f) {
            for (i in 0 until CelebrationFish.COUNT) {
                val size = field.pose(i, t, now, soon)
                if (size <= 0.01f) continue
                seen++
                for (k in 0 until 3) assertTrue(now[k].isFinite() && soon[k].isFinite(), "fish $i at $t")
                assertTrue(abs(now[0]) < 75f && now[1] > -1.5f && now[1] < 22f && now[2] > -75f && now[2] < 8f, "fish $i at $t is at (${now[0]}, ${now[1]}, ${now[2]})")
                assertTrue(size < 1.5f)
            }
            t += 0.25f
        }
        assertTrue(seen > 20_000, "the show goes on: $seen fish-moments")
        // nothing of the confetti before it is thrown, all of it a few seconds later
        var early = 0
        for (i in CelebrationFish.ESCORT until CelebrationFish.COUNT) if (field.pose(i, CelebrationFish.LAUNCH_AT - 0.1f, now, soon) > 0f) early++
        var late = 0
        for (i in 0 until CelebrationFish.COUNT) if (field.pose(i, CelebrationFish.LAUNCH_AT + 6f, now, soon) > 0f) late++
        assertEquals(0, early)
        assertEquals(CelebrationFish.COUNT, late)
    }

    /**
     * The fish are drawn, so none may swim through the car (they rise over it), nor through the merfolk who line the avenue (the low
     * ones stay between its two sides). The show is a function of time alone, so a quarter of an hour of it settles the question.
     */
    @Test fun noFishEverSwimsThroughTheCarOrTheCrowd() {
        val field = CelebrationFish()
        val now = FloatArray(3)
        val soon = FloatArray(3)
        var t = 0f
        var checked = 0
        var closestToTheCar = Float.MAX_VALUE
        while (t < 900f) {
            for (i in 0 until CelebrationFish.COUNT) {
                val size = field.pose(i, t, now, soon)
                if (size <= 0.01f) continue
                checked++
                val body = 0.8f * size // the model is 1.2 m long; it never reaches farther than this from its middle
                // the car: x +-1.32, y 0..1.53, z -2.2..2.41 (the ocean skin)
                val dx = maxOf(0f, abs(now[0]) - 1.32f)
                val dy = maxOf(0f, now[1] - 1.53f, 0f - now[1])
                val dz = maxOf(0f, now[2] - 2.41f, -2.2f - now[2])
                val away = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
                closestToTheCar = minOf(closestToTheCar, away - body)
                assertTrue(away >= body, "fish $i at time $t is ${"%.2f".format(away)} m from the car (it is ${"%.2f".format(body)} m long) at (${now[0]}, ${now[1]}, ${now[2]})")
                // the crowd: the merfolk stand at |x| >= 8.7 and are 3.7 m tall, 1.45 m wide, up to 15 % bigger
                if (now[1] - body < 4.3f) assertTrue(abs(now[0]) + body <= 8.7f - 1.45f * 1.15f, "fish $i at time $t is at x=${now[0]} y=${now[1]}, among the merfolk")
            }
            t += 0.05f
        }
        assertTrue(checked > 100_000, "fish-moments checked: $checked")
        assertTrue(closestToTheCar < 1.5f, "the fish do come close to the car, they are not just kept away: $closestToTheCar")
    }

    @Test fun theCrowdWaitsAtTheGateAndTheFishComeWhenTheCarStops() {
        val lib = MeshLibrary()
        val scene = SceneRenderer(NullGles(), lib)
        scene.resize(1600, 740)
        val game = Game(5)
        game.level = deep
        val input = GameInput()
        val bot = Bot(game)
        game.startRun(5)
        val dt = 1f / 60f
        var t = 0f
        var folk = 0
        var bunting = 0
        var mostFish = 0
        var fishBeforeTheStop = 0
        var popups = 0
        while (t < 400f && !(game.phase == Phase.VICTORY && game.phaseTime > 4f) && game.phase != Phase.GAME_OVER) {
            bot.control(input, dt)
            game.update(dt, input)
            scene.rig.update(game, dt)
            while (game.pollSfx() != null) { /* drain */ }
            scene.render(game)
            t += dt
            if (game.legIndex == 2 && game.phase == Phase.RUN) {
                val f = game.entities.count { it.kind == Kind.SEASCAPE && it.variant in 17..19 }
                if (f > folk) folk = f
                val bu = game.entities.count { it.kind == Kind.SEASCAPE && it.variant == 20 }
                if (bu > bunting) bunting = bu
                assertEquals(0, scene.celebrationFish, "no fish of the welcome while the race is on")
            }
            if (game.phase == Phase.FINALE && game.celebrationTime < CelebrationFish.LAUNCH_AT) fishBeforeTheStop = maxOf(fishBeforeTheStop, scene.celebrationFish)
            if (game.phase == Phase.FINALE || game.phase == Phase.VICTORY) {
                mostFish = maxOf(mostFish, scene.celebrationFish)
                if (game.popups.any { it.text.startsWith("WELCOME") }) popups++
            }
        }
        assertEquals(Phase.VICTORY, game.phase)
        assertTrue(folk >= 12, "merfolk line the avenue: $folk at the most")
        assertTrue(bunting >= 3, "flags across the avenue: $bunting")
        assertTrue(fishBeforeTheStop in 10..CelebrationFish.ESCORT, "an escort of fish swims along before the car stops: $fishBeforeTheStop")
        assertEquals(CelebrationFish.COUNT, mostFish, "and then the confetti")
        assertTrue(popups > 30, "the welcome is announced on the screen ($popups frames)")
        scene.release()
    }
}
