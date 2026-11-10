package app.roadtoorbit.trace

import app.roadtoorbit.game.Bot
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.HudState
import app.roadtoorbit.game.Kind
import app.roadtoorbit.game.Levels
import app.roadtoorbit.game.Phase
import app.roadtoorbit.gfx.SceneRenderer
import java.io.File
import org.junit.Test

/** Plays the whole game with the test bot and records rendered frames at key moments. */
class JourneyTraceTest {
    private class Capture(val name: String, val cond: (Game) -> Boolean) { var done = false }

    @Test
    fun journeyFrames() {
        val w = (System.getProperty("traceW") ?: "960").toInt()
        val h = (System.getProperty("traceH") ?: "540").toInt()
        val gl = RecordingGles(w, h)
        val scene = SceneRenderer(gl)
        scene.resize(w, h)
        val game = Game(7)
        game.level = Levels[(System.getProperty("traceLevel") ?: "0").toInt()]
        game.bestScore = 18_420
        game.bestMillis = 185_140 // (the title screen shows the best time next to the best score)
        val input = GameInput()
        val bot = Bot(game)
        val hudDir = File(System.getProperty("hudStatesDir") ?: "build/traces/hud").also { it.mkdirs() }
        val hudState = HudState()
        val dt = 1f / 60f
        val names = ArrayList<String>()

        fun snap(name: String) {
            scene.render(game)
            gl.endFrame()
            game.fillHud(hudState)
            dumpHud(hudState, File(hudDir, "hud_%04d.properties".format(names.size)))
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

        // the ocean level has its own journey: the sea, the dive, the landing on the seabed and the arrival
        fun near(g: Game, kind: Kind, zFar: Float, zNear: Float, dx: Float = 9f, dy: Float = 99f) =
            g.entities.any { e -> e.kind == kind && !e.hit && e.z > zFar && e.z < zNear && kotlin.math.abs(e.x - g.player.x) < dx && kotlin.math.abs(g.entityY(e) - g.player.y) < dy }
        val oceanCaptures = listOf(
            Capture("countdown") { it.phase == Phase.COUNTDOWN && it.phaseTime > 1.4f },
            Capture("sea_300") { it.phase == Phase.RUN && it.legIndex == 0 && it.legDist > 300f },
            Capture("sea_stack") { g -> g.phase == Phase.RUN && g.legIndex == 0 && g.legDist > 200f && near(g, Kind.PEAK, -80f, -40f, 12f) },
            Capture("sea_ship") { g -> g.phase == Phase.RUN && g.legIndex == 0 && g.legDist > 200f && near(g, Kind.SHIP, -90f, -40f, 14f) },
            Capture("sea_gulls") { g -> g.phase == Phase.RUN && g.legIndex == 0 && g.legDist > 200f && near(g, Kind.BALLOON, -60f, -30f, 8f, 8f) },
            Capture("sea_seaplane") { g -> g.phase == Phase.RUN && g.legIndex == 0 && g.legDist > 200f && near(g, Kind.JET, -70f, -30f, 30f, 12f) },
            Capture("sea_1800") { it.phase == Phase.RUN && it.legIndex == 0 && it.legDist > 1800f },
            Capture("sea_zone") { it.phase == Phase.RUN && it.legIndex == 0 && it.legDist > 3300f },
            Capture("dive_a") { it.xf.active && it.legIndex == 1 && it.xf.t > 0.3f },
            Capture("dive_b") { it.xf.active && it.legIndex == 1 && it.xf.t > 0.55f },
            Capture("dive_c") { it.xf.active && it.legIndex == 1 && it.xf.t > 0.85f },
            Capture("deep_300") { it.phase == Phase.RUN && it.legIndex == 1 && !it.xf.active && it.legDist > 300f },
            Capture("deep_mine") { g -> g.phase == Phase.RUN && g.legIndex == 1 && !g.xf.active && near(g, Kind.MINE, -70f, -35f, 8f, 8f) },
            Capture("deep_shark") { g -> g.phase == Phase.RUN && g.legIndex == 1 && !g.xf.active && near(g, Kind.SHARK, -70f, -30f, 30f, 12f) },
            Capture("deep_jelly") { g -> g.phase == Phase.RUN && g.legIndex == 1 && !g.xf.active && near(g, Kind.JELLY, -60f, -30f, 8f, 8f) },
            Capture("deep_1800") { it.phase == Phase.RUN && it.legIndex == 1 && it.legDist > 1800f },
            Capture("deep_3300") { it.phase == Phase.RUN && it.legIndex == 1 && it.legDist > 3300f },
            Capture("land_a") { it.xf.active && it.legIndex == 2 && it.xf.t > 0.3f },
            Capture("land_b") { it.xf.active && it.legIndex == 2 && it.xf.t > 0.65f },
            Capture("land_c") { it.xf.active && it.legIndex == 2 && it.xf.t > 0.95f },
            Capture("trench_400") { it.phase == Phase.RUN && it.legIndex == 2 && !it.xf.active && it.legDist > 400f },
            Capture("trench_crab") { g -> g.phase == Phase.RUN && g.legIndex == 2 && g.entities.any { e -> e.kind == Kind.CRAB && e.vx != 0f && e.z > -60f && e.z < -25f } },
            Capture("trench_traffic") { g -> g.phase == Phase.RUN && g.legIndex == 2 && near(g, Kind.TRAFFIC, -90f, -45f, 6f) },
            Capture("trench_1800") { it.phase == Phase.RUN && it.legIndex == 2 && it.legDist > 1800f },
            Capture("trench_gate") { it.phase == Phase.RUN && it.legIndex == 2 && it.legDist > it.leg.length - 120f },
            Capture("arrive_a") { it.phase == Phase.FINALE && it.finaleT > 1.5f },
            Capture("arrive_b") { it.phase == Phase.FINALE && it.finaleT > 3.6f },
            Capture("arrive_c") { it.phase == Phase.FINALE && it.finaleT > 5.6f },
            Capture("arrive_d") { it.phase == Phase.FINALE && it.finaleT > 6.9f },
            Capture("victory") { it.phase == Phase.VICTORY && it.phaseTime > 1.5f },
            Capture("victory_late") { it.phase == Phase.VICTORY && it.phaseTime > 7f },
        )
        // homecoming goes the other way: out of Atlantis, up through the water, through the surface, over the sea and down on the island's airstrip
        val ascentCaptures = listOf(
            Capture("countdown") { it.phase == Phase.COUNTDOWN && it.phaseTime > 1.4f },
            Capture("trench_250") { it.phase == Phase.RUN && it.legIndex == 0 && it.legDist > 250f },
            Capture("trench_traffic") { g -> g.phase == Phase.RUN && g.legIndex == 0 && near(g, Kind.TRAFFIC, -90f, -45f, 6f) },
            Capture("trench_crab") { g -> g.phase == Phase.RUN && g.legIndex == 0 && g.entities.any { e -> e.kind == Kind.CRAB && e.vx != 0f && e.z > -60f && e.z < -25f } },
            Capture("trench_1800") { it.phase == Phase.RUN && it.legIndex == 0 && it.legDist > 1800f },
            Capture("launch_zone") { it.phase == Phase.RUN && it.legIndex == 0 && it.legDist > it.leg.length - 100f },
            Capture("launch_a") { it.xf.active && it.legIndex == 1 && it.xf.t > 0.3f },
            Capture("launch_b") { it.xf.active && it.legIndex == 1 && it.xf.t > 0.6f },
            Capture("launch_c") { it.xf.active && it.legIndex == 1 && it.xf.t > 0.95f },
            Capture("ascent_300") { it.phase == Phase.RUN && it.legIndex == 1 && !it.xf.active && it.legDist > 300f },
            Capture("ascent_shark") { g -> g.phase == Phase.RUN && g.legIndex == 1 && !g.xf.active && near(g, Kind.SHARK, -70f, -30f, 30f, 12f) },
            Capture("ascent_1800") { it.phase == Phase.RUN && it.legIndex == 1 && it.legDist > 1800f },
            Capture("ascent_3300") { it.phase == Phase.RUN && it.legIndex == 1 && it.legDist > 3500f },
            Capture("breach_a") { it.xf.active && it.legIndex == 2 && it.xf.t > 0.3f },
            Capture("breach_b") { it.xf.active && it.legIndex == 2 && it.xf.t > 0.55f },
            Capture("breach_c") { it.xf.active && it.legIndex == 2 && it.xf.t > 0.85f },
            Capture("sea_300") { it.phase == Phase.RUN && it.legIndex == 2 && !it.xf.active && it.legDist > 300f },
            Capture("sea_1800") { it.phase == Phase.RUN && it.legIndex == 2 && it.legDist > 1800f },
            Capture("sea_3100") { it.phase == Phase.RUN && it.legIndex == 2 && it.legDist > it.leg.length - 220f },
            Capture("approach_a") { it.phase == Phase.FINALE && it.finaleT > 1.2f },
            Capture("approach_b") { it.phase == Phase.FINALE && it.finaleT > 3.0f },
            Capture("touchdown") { it.phase == Phase.FINALE && it.player.speed > 0f && it.finaleT > 4.4f && it.player.y < 3.4f },
            Capture("rollout") { it.phase == Phase.FINALE && it.finaleT > 6.4f },
            Capture("stopped") { it.phase == Phase.FINALE && it.celebrationTime > 4.6f },
            Capture("welcome") { it.phase == Phase.FINALE && it.celebrationTime > 6.0f },
            Capture("victory") { it.phase == Phase.VICTORY && it.phaseTime > 1.5f },
            Capture("victory_late") { it.phase == Phase.VICTORY && it.phaseTime > 7f },
        )
        val captures = if (game.level.ascends) ascentCaptures else if (game.level.world == app.roadtoorbit.game.World.OCEAN) oceanCaptures else listOf(
            Capture("countdown") { it.phase == Phase.COUNTDOWN && it.phaseTime > 1.4f },
            Capture("car_200") { it.phase == Phase.RUN && it.legIndex == 0 && it.legDist > 230f },
            Capture("car_900") { it.phase == Phase.RUN && it.legIndex == 0 && it.legDist > 900f },
            Capture("car_1800") { it.phase == Phase.RUN && it.legIndex == 0 && it.legDist > 1800f },
            Capture("car_zone") { it.phase == Phase.RUN && it.legIndex == 0 && it.legDist > 2560f },
            // Mars only: a boulder that has just started to roll across the road
            Capture("boulder") { g -> g.phase == Phase.RUN && g.legIndex == 0 && g.entities.any { e -> e.kind == Kind.BOULDER && e.vx != 0f && e.z > -60f && e.z < -30f } },
            Capture("xf1_a") { it.xf.active && it.legIndex == 1 && it.xf.t > 0.15f },
            // a balloon (the Moon) or a flying saucer (Mars) ahead of the plane
            Capture("air_hazard") { g -> g.phase == Phase.RUN && g.legIndex == 1 && !g.xf.active && g.legDist > 300f && g.entities.any { e -> e.kind == Kind.BALLOON && e.z > -48f && e.z < -28f && kotlin.math.abs(e.x - g.player.x) < 7f && kotlin.math.abs(e.y - g.player.y) < 7f } },
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
            Capture("victory_late") { it.phase == Phase.VICTORY && it.phaseTime > 7f },
        )

        var time = 0f
        while (time < 500f && !(game.phase == Phase.VICTORY && game.phaseTime > 7.5f) && game.phase != Phase.GAME_OVER) {
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

    private fun dumpHud(h: HudState, f: File) {
        val p = java.util.Properties()
        fun put(k: String, v: Any) { p[k] = v.toString() }
        put("phase", h.phase.name); put("legIndex", h.legIndex); put("legName", h.legName); put("legSubtitle", h.legSubtitle)
        put("levelIndex", h.levelIndex); put("levelName", h.levelName); put("nextLevelName", h.nextLevelName); put("tagline", h.tagline)
        put("legLength", h.legLength); put("legZoneLength", h.legZoneLength)
        put("world", h.world.name)
        for (i in h.legModes.indices) put("legMode$i", h.legModes[i].name)
        put("mode", h.mode.name); put("journey", h.journey); put("legProgress", h.legProgress); put("zoneProgress", h.zoneProgress)
        put("distanceToGate", h.distanceToGate); put("score", h.score); put("bestScore", h.bestScore); put("coins", h.coins)
        put("rings", h.rings); put("ringStreak", h.ringStreak); put("health", h.health); put("maxHealth", h.maxHealth)
        put("difficulty", h.difficulty.name); put("boost", h.boost); put("boosting", h.boosting); put("invulnerable", h.invulnerable)
        put("speedKmh", h.speedKmh); put("altitudeKm", h.altitudeKm); put("runTime", h.runTime)
        put("transformReady", h.transformReady); put("transforming", h.transforming); put("countdown", h.countdown)
        put("flash", h.flash); put("damageFlash", h.damageFlash); put("stars", h.stars); put("newBest", h.newBest)
        put("finishMillis", h.finishMillis); put("bestMillis", h.bestMillis); put("newBestTime", h.newBestTime)
        put("breakdownBase", h.breakdownBase); put("breakdownHealth", h.breakdownHealth); put("breakdownTime", h.breakdownTime)
        put("popupCount", h.popupCount)
        for (i in 0 until h.popupCount) {
            put("popup.$i.text", h.popupText[i]); put("popup.$i.color", h.popupColor[i]); put("popup.$i.age", h.popupAge[i])
            put("popup.$i.ttl", h.popupTtl[i]); put("popup.$i.big", h.popupBig[i])
        }
        f.outputStream().use { p.store(it, null) }
    }
}
