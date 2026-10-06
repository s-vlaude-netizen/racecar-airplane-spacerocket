package app.roadtoorbit.game

import app.roadtoorbit.gfx.VehicleMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameSimTest {
    class Result(
        val finished: Boolean, val crashedInLeg: Int, val seconds: Float, val score: Int, val health: Int,
        val coins: Int, val rings: Int, val nearMisses: Int, val legTimes: FloatArray, val hits: Int,
        val modes: List<VehicleMode>, val transforms: Int,
    )

    private fun play(seed: Long, boost: Boolean = false, maxSeconds: Float = 600f, skill: Float = 1f): Result {
        val game = Game(seed)
        val input = GameInput()
        val bot = Bot(game, boost, skill)
        game.startRun(seed)
        var t = 0f
        val dt = 1f / 60f
        var lastLeg = 0
        var legStart = 0f
        val legTimes = FloatArray(3)
        var hits = 0
        var lastHealth = Tuning.MAX_HEALTH
        val modes = ArrayList<VehicleMode>()
        var transforms = 0
        var wasXf = false
        var crashedLeg = -1
        while (t < maxSeconds && game.phase != Phase.VICTORY) {
            bot.control(input, dt)
            game.update(dt, input)
            t += dt
            val p = game.player
            assertFinite(p.x, "x"); assertFinite(p.y, "y"); assertFinite(p.speed, "speed")
            if (game.phase == Phase.RUN || game.phase == Phase.COUNTDOWN) {
                if (p.health < lastHealth) {
                    hits += lastHealth - p.health
                    val h = game.lastHit
                    if (System.getProperty("simVerbose") != null) println("  seed=$seed hit by ${h?.kind}/${h?.variant} at leg=${game.legIndex} dist=${"%.0f".format(game.lastHitLegDist)} p=(${"%.1f".format(p.x)},${"%.1f".format(p.y)}) e=(${"%.1f".format(h?.x ?: 0f)},${"%.1f".format(h?.y ?: 0f)},${"%.1f".format(h?.z ?: 0f)}) r=${h?.radius}")
                }
                lastHealth = p.health
            }
            if (game.legIndex != lastLeg) {
                legTimes[lastLeg] = t - legStart
                legStart = t
                lastLeg = game.legIndex
                lastHealth = p.health
            }
            if (game.xf.active && !wasXf) transforms++
            wasXf = game.xf.active
            if (modes.isEmpty() || modes.last() != p.mode) modes.add(p.mode)
            if (game.phase == Phase.GAME_OVER) { crashedLeg = game.legIndex; break }
            while (game.pollSfx() != null) { /* drain */ }
        }
        return Result(
            game.phase == Phase.VICTORY, crashedLeg, t, game.score, game.player.health, game.coins, game.rings,
            game.nearMisses, legTimes, hits, modes, transforms,
        )
    }

    private fun assertFinite(v: Float, name: String) = assertTrue(v.isFinite(), "$name is not finite: $v")

    @Test
    fun botCompletesTheJourneyOnSeveralSeeds() {
        val seeds = longArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        var finished = 0
        for (seed in seeds) {
            val r = play(seed)
            println(
                "seed=$seed finished=${r.finished} crashedLeg=${r.crashedInLeg} time=${"%.1f".format(r.seconds)}s score=${r.score} " +
                    "hp=${r.health} hits=${r.hits} coins=${r.coins} rings=${r.rings} near=${r.nearMisses} legs=${r.legTimes.joinToString { "%.1f".format(it) }}",
            )
            if (r.finished) {
                finished++
                assertEquals(listOf(VehicleMode.CAR, VehicleMode.PLANE, VehicleMode.ROCKET, VehicleMode.CAR), r.modes.take(4))
                assertEquals(2, r.transforms)
            }
        }
        assertTrue(finished >= seeds.size - 2, "bot should beat the game on most seeds, finished $finished of ${seeds.size}")
    }

    @Test
    fun anIdlePlayerCrashesIntoSomething() {
        val game = Game(11)
        val input = GameInput()
        game.startRun(11)
        var t = 0f
        while (t < 120f && game.phase != Phase.GAME_OVER) {
            game.update(1f / 60f, input)
            t += 1f / 60f
        }
        assertEquals(Phase.GAME_OVER, game.phase, "obstacles must be able to end a run of a player who never steers")
        assertEquals(0, game.legIndex)
    }

    @Test
    fun transformsTriggerAutomaticallyAtTheGate() {
        // a bot that never presses TRANSFORM still gets through the gate (no soft-lock)
        val game = Game(5)
        val input = GameInput()
        val bot = Bot(game)
        game.startRun(5)
        val dt = 1f / 60f
        var t = 0f
        var sawPlane = false
        while (t < 200f && game.phase != Phase.GAME_OVER) {
            bot.control(input, dt)
            input.takeTransform() // swallow the bot's press
            game.update(dt, input)
            if (game.legIndex >= 1) { sawPlane = true; break }
            t += dt
        }
        assertTrue(sawPlane || game.phase == Phase.GAME_OVER)
    }

    @Test
    fun retryRestoresTheCurrentLegWithFullHealth() {
        val game = Game(3)
        val input = GameInput()
        game.startRun(3)
        var t = 0f
        while (t < 120f && game.phase != Phase.GAME_OVER) { game.update(1f / 60f, input); t += 1f / 60f }
        assertEquals(Phase.GAME_OVER, game.phase)
        game.retryLeg()
        assertEquals(Phase.COUNTDOWN, game.phase)
        assertEquals(Tuning.MAX_HEALTH, game.player.health)
        assertTrue(game.player.alive)
        assertFalse(game.entities.isEmpty(), "world is repopulated for the retry")
    }

    @Test
    fun menuCyclesThroughAllForms() {
        val game = Game(1)
        val input = GameInput()
        val seen = HashSet<VehicleMode>()
        var t = 0f
        while (t < 14f) {
            game.update(1f / 60f, input)
            seen.add(game.player.visual.dominant)
            t += 1f / 60f
        }
        assertEquals(3, seen.size)
    }
}
