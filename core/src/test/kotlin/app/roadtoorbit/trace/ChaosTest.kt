package app.roadtoorbit.trace

import app.roadtoorbit.game.Bot
import app.roadtoorbit.game.Difficulty
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.HudBuffer
import app.roadtoorbit.game.HudState
import app.roadtoorbit.game.LevelSpec
import app.roadtoorbit.game.Levels
import app.roadtoorbit.game.Phase
import app.roadtoorbit.gfx.MeshLibrary
import app.roadtoorbit.gfx.SceneRenderer
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plays the game the way real devices and real thumbs do and renders every single frame through a strict GL
 * validator: random frame times (including hitches), random steering, boosting and transform presses, UI
 * actions at any moment (restart in the middle of a run, retry during a crash, difficulty changes), pauses,
 * degenerate surface sizes and GL context loss. Nothing may throw, go non-finite, leak or grow without bound.
 *
 * Light by default; `-Dchaos=heavy` (optionally `-DchaosRuns=` `-DchaosFrames=` `-DchaosSeed=`) runs many more.
 */
class ChaosTest {
    private val library = MeshLibrary().also { it.prewarm() }

    /** Phases a random player reaches; the finale and the victory need a pilot who survives (below). */
    private val reachable = setOf(Phase.MENU, Phase.COUNTDOWN, Phase.RUN, Phase.CRASHING, Phase.GAME_OVER)

    @Test
    fun chaosLight() = chaos(runs = 10, frames = 7_000, firstSeed = 1)

    /** A good pilot flies whole journeys; every frame of the finale and the victory is rendered and validated too. */
    @Test
    fun everyFrameOfWholeJourneys() {
        for (level in Levels.ALL) for (seed in 1L..4L) {
            val (_, seen) = oneRun(seed, 20_000, calm = true, level = level)
            assertTrue(Phase.VICTORY in seen && Phase.FINALE in seen, "${level.name}, seed $seed: the pilot should reach ${level.destination}, saw $seen")
        }
    }

    @Test
    fun chaosHeavy() {
        if (System.getProperty("chaos") != "heavy") return
        chaos(
            runs = (System.getProperty("chaosRuns") ?: "120").toInt(),
            frames = (System.getProperty("chaosFrames") ?: "15000").toInt(),
            firstSeed = (System.getProperty("chaosSeed") ?: "1000").toLong(),
        )
    }

    private fun chaos(runs: Int, frames: Int, firstSeed: Long) {
        var worstDraws = 0
        var phases = HashSet<Phase>()
        for (i in 0 until runs) {
            val seed = firstSeed + i
            try {
                val stats = oneRun(seed, frames)
                worstDraws = maxOf(worstDraws, stats.first)
                phases.addAll(stats.second)
            } catch (t: Throwable) {
                throw AssertionError("chaos run with seed $seed failed: $t", t)
            }
        }
        println("chaos: $runs runs x $frames frames, phases seen $phases, most draw calls in a frame $worstDraws")
        assertTrue(phases.containsAll(reachable), "the chaos should visit $reachable, saw $phases")
    }

    private val sizes = arrayOf(
        intArrayOf(1600, 740), intArrayOf(2400, 1080), intArrayOf(800, 480), intArrayOf(1080, 2400),
        intArrayOf(1, 1), intArrayOf(0, 0), intArrayOf(0, 500), intArrayOf(500, 0), intArrayOf(4096, 64), intArrayOf(64, 4096),
    )

    private fun oneRun(seed: Long, maxFrames: Int, calm: Boolean = false, level: LevelSpec? = null): Pair<Int, Set<Phase>> {
        val rnd = Random(seed * 7919 + 13)
        var gl = StrictGles()
        var scene = SceneRenderer(gl, library)
        scene.resize(1600, 740)
        val hud = HudBuffer()
        val game = Game(seed)
        game.difficulty = Difficulty.values()[rnd.nextInt(3)]
        game.level = level ?: Levels[rnd.nextInt(Levels.count)]
        val input = GameInput()
        val style = if (calm) 1 else rnd.nextInt(3) // 0 thumbs, 1 bot with human flaws, 2 nobody steers
        val bot = if (calm) Bot(game) else Bot(
            game, useBoost = rnd.nextBoolean(), skill = 0.5f + rnd.nextFloat() * 0.5f,
            latency = rnd.nextFloat() * 0.25f, interval = 0.05f + rnd.nextFloat() * 0.2f, noise = rnd.nextFloat() * 0.5f,
        )
        var victoryFrames = 0
        val seen = HashSet<Phase>()
        var steerTx = 0f
        var steerTy = 0f
        var pausedFrames = 0
        var worstDraws = 0

        for (frame in 0 until maxFrames) {
            // ---- the player and the UI
            when (game.phase) {
                Phase.MENU -> {
                    if (!calm && rnd.nextInt(60) == 0) game.level = Levels[rnd.nextInt(Levels.count)] // the level pill
                    if (rnd.nextInt(90) == 0) game.startRun(rnd.nextLong())
                }
                Phase.GAME_OVER -> if (rnd.nextInt(120) == 0) {
                    when (if (calm) 2 else rnd.nextInt(3)) {
                        0 -> game.toMenu()
                        1 -> game.startRun(rnd.nextLong())
                        else -> game.retryLeg()
                    }
                }
                Phase.VICTORY -> {
                    if (calm) { if (++victoryFrames > 240) break } // watch the victory drive for a while, then stop
                    else if (rnd.nextInt(120) == 0) {
                        if (rnd.nextBoolean()) game.toMenu() else game.startRun(rnd.nextLong())
                    }
                }
                else -> if (!calm) {
                    // taps that arrive at the wrong time: double taps on PLAY/RETRY, MENU in the middle of a run
                    val before = game.level
                    when (rnd.nextInt(4000)) {
                        0 -> game.toMenu()
                        1 -> game.startRun(rnd.nextLong())
                        2 -> game.retryLeg()
                        3 -> game.difficulty = game.difficulty.next()
                        4 -> game.level = Levels[rnd.nextInt(Levels.count)] // a late tap on the level pill: ignored mid-run
                    }
                    if (game.phase != Phase.MENU) assertEquals(before, game.level, "the level changed in the middle of a run (frame $frame, seed $seed)")
                }
            }
            if (!calm && pausedFrames == 0 && rnd.nextInt(700) == 0) pausedFrames = 20 + rnd.nextInt(200)

            when (style) {
                0 -> {
                    if (rnd.nextInt(18) == 0) {
                        steerTx = when (rnd.nextInt(4)) { 0 -> 0f; 1 -> if (rnd.nextBoolean()) 1f else -1f; else -> rnd.nextFloat() * 2f - 1f }
                        steerTy = when (rnd.nextInt(4)) { 0 -> 0f; 1 -> if (rnd.nextBoolean()) 1f else -1f; else -> rnd.nextFloat() * 2f - 1f }
                    }
                    input.steerX += (steerTx - input.steerX) * 0.2f
                    input.steerY += (steerTy - input.steerY) * 0.2f
                    if (rnd.nextInt(100) == 0) input.boost = !input.boost
                    if (rnd.nextInt(90) == 0) input.requestTransform()
                    if (rnd.nextInt(2500) == 0) input.neutral()
                }
                1 -> {
                    val dtForBot = 1f / 60f
                    bot.control(input, dtForBot)
                }
                else -> input.neutral()
            }

            // ---- frame time: mostly steady, with jitter, hitches and the app's 50 ms clamp
            val dt = when (rnd.nextInt(100)) {
                in 0..59 -> 1f / 60f
                in 60..79 -> (1f / 60f) * (0.7f + 0.6f * rnd.nextFloat())
                in 80..89 -> 1f / 30f
                in 90..94 -> 0.05f
                in 95..97 -> 0.0005f + rnd.nextFloat() * 0.004f
                else -> 1f / 120f
            }

            if (pausedFrames > 0) pausedFrames-- else {
                game.update(dt, input)
                scene.rig.update(game, dt)
                while (game.pollSfx() != null) { /* drain */ }
            }

            // ---- the surface and the GL context
            if (rnd.nextInt(1500) == 0) {
                val s = sizes[rnd.nextInt(sizes.size)]
                scene.resize(s[0], s[1])
            }
            if (rnd.nextInt(3000) == 0) {
                // context loss: everything is released and rebuilt on a fresh context
                scene.release()
                assertEquals(0, gl.liveObjects(), "release() left GL objects behind")
                gl = StrictGles()
                scene = SceneRenderer(gl, library)
                scene.resize(1600, 740)
            }

            scene.render(game)
            hud.publish(game)
            gl.endFrame()
            worstDraws = maxOf(worstDraws, gl.maxDrawCalls)
            seen.add(game.phase)
            check(game, scene, hud.latest, frame, seed)
        }
        scene.release()
        assertEquals(0, gl.liveObjects(), "release() left GL objects behind")
        return worstDraws to seen
    }

    private fun finite(what: String, vararg v: Float) {
        for (x in v) assertTrue(x.isFinite(), "$what is not finite: ${v.toList()}")
    }

    private fun check(game: Game, scene: SceneRenderer, h: HudState, frame: Int, seed: Long) {
        val where = "seed $seed frame $frame phase ${game.phase}"
        val p = game.player
        finite("$where player", p.x, p.y, p.vx, p.vy, p.yaw, p.pitch, p.roll, p.speed, p.bounce, p.boostMeter, p.boostFactor)
        assertTrue(game.score >= 0 && game.coins >= 0 && game.rings >= 0, "$where counters: ${game.score} ${game.coins} ${game.rings}")
        assertTrue(game.legIndex in 0..game.level.lastLeg, "$where leg ${game.legIndex}")
        assertTrue(game.level.index in 0 until Levels.count && game.level === Levels[game.level.index], "$where level ${game.level.index}")
        assertTrue(game.entities.size < 1_200, "$where entities ${game.entities.size}")
        assertTrue(game.particles.count <= game.particles.capacity, "$where particles")
        assertTrue(game.popups.size <= 5, "$where popups ${game.popups.size}")
        val cam = scene.rig.camera
        finite("$where camera", *cam.view, *cam.proj, cam.ex, cam.ey, cam.ez)
        assertTrue(p.boostMeter in 0f..1f, "$where boost ${p.boostMeter}")
        assertTrue(p.health in -3..game.maxHealth, "$where health ${p.health}")
        finite("$where hud", h.journey, h.legProgress, h.zoneProgress, h.distanceToGate, h.boost, h.altitudeKm, h.runTime, h.flash, h.damageFlash)
        assertTrue(h.popupCount in 0..HudState.MAX_POPUPS, "$where hud popups ${h.popupCount}")
        assertTrue(h.journey in 0f..1.0001f && h.legProgress in 0f..1f, "$where hud progress ${h.journey} ${h.legProgress}")
    }
}
