package app.roadtoorbit.game

import app.roadtoorbit.gfx.VehicleMode
import kotlin.test.assertTrue

/** Plays whole runs with the test [Bot]; shared by the simulation tests and the balance report. */
object SimRunner {
    class Result(
        val finished: Boolean, val crashedInLeg: Int, val seconds: Float, val score: Int, val health: Int,
        val coins: Int, val rings: Int, val nearMisses: Int, val legTimes: FloatArray, val hits: Int,
        val modes: List<VehicleMode>, val transforms: Int,
    )

    fun play(
        seed: Long, boost: Boolean = false, maxSeconds: Float = 600f, skill: Float = 1f,
        latency: Float = 0f, interval: Float = 0.1f, noise: Float = 0f,
        difficulty: Difficulty = Difficulty.NORMAL, level: LevelSpec = Levels[0],
    ): Result {
        val game = Game(seed)
        game.level = level
        game.difficulty = difficulty
        val input = GameInput()
        val bot = Bot(game, boost, skill, latency, interval, noise)
        game.startRun(seed)
        var t = 0f
        val dt = 1f / 60f
        var lastLeg = 0
        var legStart = 0f
        val legTimes = FloatArray(3)
        var hits = 0
        var lastHealth = difficulty.maxHealth
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
}
