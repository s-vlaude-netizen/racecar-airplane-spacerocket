package app.roadtoorbit.trace

import app.roadtoorbit.game.Bot
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.Phase
import app.roadtoorbit.gfx.SceneRenderer
import java.io.File
import org.junit.Test

/** Plays the whole game with the test bot and records rendered frames at key moments. */
class JourneyTraceTest {
    private class Capture(val name: String, val cond: (Game) -> Boolean) { var done = false }

    @Test
    fun journeyFrames() {
        val w = 960
        val h = 540
        val gl = RecordingGles(w, h)
        val scene = SceneRenderer(gl)
        scene.resize(w, h)
        val game = Game(7)
        val input = GameInput()
        val bot = Bot(game)
        val dt = 1f / 60f
        val names = ArrayList<String>()

        fun snap(name: String) {
            scene.render(game)
            gl.endFrame()
            names.add(name)
        }

        // --- menu showroom
        var t = 0f
        val menuShots = floatArrayOf(0.4f, 3.2f, 5.0f, 8.2f)
        var mi = 0
        while (mi < menuShots.size) {
            game.update(dt, input)
            scene.rig.update(game, dt)
            t += dt
            if (t >= menuShots[mi]) { snap("menu_$mi"); mi++ }
        }
        game.startRun(7)

        val captures = listOf(
            Capture("countdown") { it.phase == Phase.COUNTDOWN && it.phaseTime > 1.4f },
            Capture("car_200") { it.phase == Phase.RUN && it.legIndex == 0 && it.legDist > 230f },
            Capture("car_900") { it.phase == Phase.RUN && it.legIndex == 0 && it.legDist > 900f },
            Capture("car_1800") { it.phase == Phase.RUN && it.legIndex == 0 && it.legDist > 1800f },
            Capture("car_zone") { it.phase == Phase.RUN && it.legIndex == 0 && it.legDist > 2560f },
            Capture("xf1_a") { it.xf.active && it.legIndex == 1 && it.xf.t > 0.15f },
            Capture("xf1_b") { it.xf.active && it.legIndex == 1 && it.xf.t > 0.45f },
            Capture("xf1_c") { it.xf.active && it.legIndex == 1 && it.xf.t > 0.8f },
            Capture("plane_150") { it.phase == Phase.RUN && it.legIndex == 1 && !it.xf.active && it.legDist > 200f },
            Capture("plane_1200") { it.phase == Phase.RUN && it.legIndex == 1 && it.legDist > 1200f },
            Capture("plane_2400") { it.phase == Phase.RUN && it.legIndex == 1 && it.legDist > 2400f },
            Capture("plane_3700") { it.phase == Phase.RUN && it.legIndex == 1 && it.legDist > 3800f },
            Capture("xf2_a") { it.xf.active && it.legIndex == 2 && it.xf.t > 0.2f },
            Capture("xf2_b") { it.xf.active && it.legIndex == 2 && it.xf.t > 0.55f },
            Capture("xf2_c") { it.xf.active && it.legIndex == 2 && it.xf.t > 0.9f },
            Capture("rocket_300") { it.phase == Phase.RUN && it.legIndex == 2 && !it.xf.active && it.legDist > 500f },
            Capture("rocket_2000") { it.phase == Phase.RUN && it.legIndex == 2 && it.legDist > 2000f },
            Capture("rocket_4000") { it.phase == Phase.RUN && it.legIndex == 2 && it.legDist > 4000f },
            Capture("rocket_5800") { it.phase == Phase.RUN && it.legIndex == 2 && it.legDist > 5900f },
            Capture("finale_a") { it.phase == Phase.FINALE && it.finaleT > 1.5f },
            Capture("finale_b") { it.phase == Phase.FINALE && it.finaleT > 3.6f },
            Capture("finale_c") { it.phase == Phase.FINALE && it.finaleT > 5.6f },
            Capture("finale_d") { it.phase == Phase.FINALE && it.finaleT > 6.9f },
            Capture("victory") { it.phase == Phase.VICTORY && it.phaseTime > 1.5f },
        )

        var time = 0f
        while (time < 400f && !(game.phase == Phase.VICTORY && game.phaseTime > 2f) && game.phase != Phase.GAME_OVER) {
            bot.control(input, dt)
            game.update(dt, input)
            scene.rig.update(game, dt)
            while (game.pollSfx() != null) { /* drain */ }
            time += dt
            for (c in captures) {
                if (!c.done && c.cond(game)) { c.done = true; snap(c.name) }
            }
        }
        val dir = File(System.getProperty("traceDir") ?: "build/traces")
        gl.writeTo(File(dir, "journey.bin"))
        File(dir, "journey.txt").writeText(names.joinToString("\n"))
        println("journey: ${names.size} frames, max draw calls/frame = ${gl.maxDrawCallsPerFrame}, end phase=${game.phase} score=${game.score}")
    }
}
