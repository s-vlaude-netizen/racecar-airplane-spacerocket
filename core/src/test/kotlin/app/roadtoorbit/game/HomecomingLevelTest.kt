package app.roadtoorbit.game

import app.roadtoorbit.gfx.AirfieldSpec
import app.roadtoorbit.gfx.MeshData
import app.roadtoorbit.gfx.MeshId
import app.roadtoorbit.gfx.MeshLibrary
import app.roadtoorbit.gfx.OceanSpec
import app.roadtoorbit.gfx.Skin
import app.roadtoorbit.gfx.VehicleMode
import app.roadtoorbit.gfx.VehicleModel
import app.roadtoorbit.gfx.OceanVehicle
import app.roadtoorbit.math.Mat4
import app.roadtoorbit.math.Mathx
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Homecoming is the deep the other way round: the car drives out of Atlantis, becomes the submarine-rocket, launches from the seabed up through
 * the water, breaks through the surface as the waterplane, flies home over the sea and lands on the island's airstrip.
 */
class HomecomingLevelTest {
    private val home = Levels.ALL.first { it.finale == Finale.AIRSTRIP }
    private val dt = 1f / 60f

    private fun start(seed: Long = 7, difficulty: Difficulty = Difficulty.NORMAL, boost: Boolean = false): Triple<Game, GameInput, Bot> {
        val game = Game(seed)
        game.level = home
        game.difficulty = difficulty
        val input = GameInput()
        game.startRun(seed)
        return Triple(game, input, Bot(game, useBoost = boost))
    }

    @Test fun theLevelGoesUpInTheOrderCarRocketPlane() {
        assertEquals(3, home.index)
        assertEquals(listOf(VehicleMode.CAR, VehicleMode.ROCKET, VehicleMode.PLANE), home.legs.map { it.mode })
        assertEquals(World.OCEAN, home.world)
        assertTrue(home.ascends)
        assertEquals("RACE   ·   LAUNCH   ·   FLY", home.tagline)
        assertEquals(0f, home.legs[2].zoneLength, "the last leg ends in a landing, not in a transformation")
        assertEquals(Skin.OCEAN, home.skin)
    }

    /** The launch lifts the car off the seabed, the water gets lighter and the surface comes down until it passes through the vehicle. */
    @Test fun theAscentLeavesTheSeabedAndTheSurfaceComesDownThroughTheVehicle() {
        val (game, input, bot) = start()
        var t = 0f
        var splashes = 0
        var lastSurface = Float.MAX_VALUE
        var lastDepth = 2f
        var lastKm = Float.MAX_VALUE
        var lastGround = 0f
        var breachSeen = false
        val hud = HudState()
        while (t < 300f && game.phase != Phase.GAME_OVER && game.legIndex < 3 && !(game.legIndex == 2 && !game.xf.active && game.legDist > 100f)) {
            bot.control(input, dt)
            game.update(dt, input)
            t += dt
            while (true) { val s = game.pollSfx() ?: break; if (s == Sfx.SPLASH) splashes++ }
            game.fillHud(hud)
            assertTrue(hud.altitudeKm <= lastKm + 1e-3f, "the depth only gets smaller going up: ${hud.altitudeKm} after $lastKm")
            lastKm = hud.altitudeKm
            val look = game.look
            if (game.legIndex == 0) {
                assertEquals(0f, look.groundY, "the car is on the seabed")
                assertTrue(look.depth <= lastDepth + 1e-3f, "the water gets lighter: ${look.depth} after $lastDepth")
            }
            if (game.legIndex >= 1 && !(game.legIndex == 1 && game.xf.active)) {
                // the seabed only ever falls away, the surface only ever comes down
                assertTrue(look.surfaceY <= lastSurface + 1e-3f, "the surface only comes down: ${look.surfaceY} after $lastSurface")
                assertTrue(look.groundY <= lastGround + 1e-3f, "the seabed only falls away: ${look.groundY} after $lastGround")
            }
            if (game.legIndex == 1) {
                // (the surface comes down faster than the vehicle can: it is pushed down with it, a frame late)
                assertTrue(game.player.y <= game.flightCeiling(game.leg) + 0.25f, "under water the rocket stays below the surface: y ${game.player.y}, ceiling ${game.flightCeiling(game.leg)}")
            }
            if (game.legIndex == 2 && game.xf.active) {
                breachSeen = true
                if (look.surfaceY > game.player.y + 0.5f) assertTrue(game.player.y < look.surfaceY, "still under water")
            }
            if (game.legIndex >= 1) lastSurface = look.surfaceY
            lastGround = look.groundY
            lastDepth = look.depth
        }
        assertTrue(breachSeen, "the rocket breaks through the surface")
        assertEquals(1, splashes, "one splash, at the breach")
        // out of the water: the sea is below the plane, in daylight
        assertEquals(0f, game.look.surfaceY, "the sea is at y = 0 under the waterplane")
        assertTrue(game.player.y >= Game.BREACH_Y - 0.01f, "the plane is above the sea: ${game.player.y}")
        assertEquals(VehicleMode.PLANE, game.player.mode)
        assertEquals(0f, hud.altitudeKm, 1e-3f)
    }

    /** The lowest point of the plane's body and parts in the world, with the wheels as far down as the game has them. */
    private fun lowestPointOfThePlane(game: Game, lib: MeshLibrary, body: MeshData): Float {
        val p = game.player
        val v = p.visual
        VehicleModel.buildBody(body, v.from, v.to, if (v.from == v.to) 0f else v.morph, v.skin)
        val vm = FloatArray(16); val pm = FloatArray(16); val wm = FloatArray(16)
        Mat4.setTrs(vm, p.x, p.y + p.bounce, 0f, p.pitch, p.yaw, p.roll, 1f, 1f, 1f)
        var low = Float.MAX_VALUE
        fun scan(d: MeshData, m: FloatArray) {
            for (i in 0 until d.count) {
                val x = d.pos[i * 3]; val y = d.pos[i * 3 + 1]; val z = d.pos[i * 3 + 2]
                low = minOf(low, m[1] * x + m[5] * y + m[9] * z + m[13])
            }
        }
        scan(body, vm)
        for (part in VehicleModel.partsFor(v.skin)) {
            val a = part.pose(v.from)
            val b = if (v.from == v.to) a else part.pose(v.to)
            val pr = VehicleModel.partProgress(part, if (v.from == v.to) 0f else v.morph)
            val sx = Mathx.lerp(a.sx, b.sx, pr); val sy = Mathx.lerp(a.sy, b.sy, pr); val sz = Mathx.lerp(a.sz, b.sz, pr)
            if (maxOf(sx, sy, sz) < 0.05f) continue
            var y = Mathx.lerp(a.y, b.y, pr)
            if (part.role == app.roadtoorbit.gfx.PartRole.GEAR) y -= OceanVehicle.GEAR_DROP * v.gear * v.weight(VehicleMode.PLANE)
            Mat4.setTrs(pm, Mathx.lerp(a.x, b.x, pr), y, Mathx.lerp(a.z, b.z, pr), Mathx.lerp(a.rx, b.rx, pr), Mathx.lerp(a.ry, b.ry, pr), Mathx.lerp(a.rz, b.rz, pr), sx, sy, sz)
            Mat4.multiply(wm, vm, pm)
            scan(lib.data(part.mesh), wm)
        }
        return low
    }

    /**
     * The plane glides in, lands and rolls out: its wheels are on the runway from the moment it touches down, no part of it is ever lower
     * than the runway over the island, and it stops at the same place however fast it came (with the boost it comes in much faster).
     */
    @Test fun thePlaneLandsOnTheRunwayAndStopsAtTheSamePlaceHoweverFastItCame() {
        val lib = MeshLibrary()
        val body = MeshData(VehicleModel.maxBodyVertices + 64)
        val stops = ArrayList<Float>()
        for ((seed, d, boost) in listOf(Triple(5L, Difficulty.NORMAL, false), Triple(6L, Difficulty.NORMAL, true), Triple(7L, Difficulty.HARD, true), Triple(8L, Difficulty.EASY, false))) {
            val (game, input, bot) = start(seed, d, boost)
            var t = 0f
            while (t < 400f && game.phase != Phase.FINALE && game.phase != Phase.GAME_OVER) { bot.control(input, dt); game.update(dt, input); t += dt; while (game.pollSfx() != null) { } }
            if (game.phase != Phase.FINALE) continue
            val strip0 = game.leg.length + AirfieldSpec.THRESHOLD_AHEAD
            val arrivedAt = game.player.speed
            var worstClearance = Float.MAX_VALUE
            var touched = false
            var welcomed = false
            var frames = 0
            while (game.phase != Phase.VICTORY || game.phaseTime < 3f) {
                game.update(dt, input)
                while (game.pollSfx() != null) { }
                frames++
                if (game.popups.any { it.text.startsWith("WELCOME HOME") }) welcomed = true
                val low = lowestPointOfThePlane(game, lib, body)
                if (game.legDist > strip0 + 12f) worstClearance = minOf(worstClearance, low - AirfieldSpec.STRIP_Y) // (over the island: the front of it slopes up out of the sea for 12 m)
                if (game.player.speed < 10f && game.legDist > strip0) touched = true
                if (frames > 60 * 60) break
            }
            assertEquals(Phase.VICTORY, game.phase, "$d boost $boost: the finale ends in the victory")
            assertTrue(touched && welcomed, "$d boost $boost: it touched down and was welcomed")
            val stop = game.legDist - game.leg.length
            stops.add(stop)
            assertTrue(abs(stop - AirfieldSpec.STOP_AFTER_LEG) < 1.5f, "$d boost $boost: arrived at ${arrivedAt.toInt()} m/s and stopped ${stop} m beyond the end of the leg (should be ${AirfieldSpec.STOP_AFTER_LEG})")
            assertTrue(game.player.speed < 0.05f, "parked")
            assertTrue(worstClearance > -0.03f, "$d boost $boost: some part of the plane is ${-worstClearance} m below the runway")
            assertTrue(abs(game.player.y - (AirfieldSpec.STRIP_Y + OceanVehicle.GEAR_BOTTOM)) < 0.03f, "on its wheels: y ${game.player.y}")
            assertTrue(game.player.visual.gear > 0.99f, "the gear is down")
        }
        assertTrue(stops.size >= 3, "the pilots arrived: ${stops.size}")
    }

    @Test fun theIslandAwaitsWithItsCrowdBuildingsAndFlags() {
        val (game, input, bot) = start(3, Difficulty.EASY)
        var t = 0f
        while (t < 400f && game.phase != Phase.FINALE && game.phase != Phase.GAME_OVER) { bot.control(input, dt); game.update(dt, input); t += dt; while (game.pollSfx() != null) { } }
        assertEquals(Phase.FINALE, game.phase)
        fun count(vararg variants: Int) = game.entities.count { it.kind == Kind.SEASCAPE && it.variant in variants }
        assertTrue(count(OceanSpec.PERSON, OceanSpec.PERSON + 1, OceanSpec.PERSON + 2) >= 30, "the crowd: ${count(OceanSpec.PERSON, OceanSpec.PERSON + 1, OceanSpec.PERSON + 2)} people")
        assertTrue(count(OceanSpec.BALLOON) >= 4, "balloons in the crowd's hands")
        assertEquals(1, count(OceanSpec.TERMINAL), "one terminal")
        assertEquals(1, count(OceanSpec.TOWER), "one tower")
        assertEquals(2, count(OceanSpec.HANGAR), "two hangars")
        assertEquals(1, count(OceanSpec.WINDSOCK), "a windsock")
        assertTrue(count(OceanSpec.PALM) >= 12, "palms: ${count(OceanSpec.PALM)}")
        assertEquals(3, count(OceanSpec.BUNTING), "three garlands of flags")
        // everyone on the island stands on it: on the runway's level, and none of the people on the runway
        for (e in game.entities) {
            if (e.kind != Kind.SEASCAPE || e.variant < OceanSpec.PERSON || e.variant > OceanSpec.PALM) continue
            if (e.variant == OceanSpec.BALLOON) continue
            assertTrue(abs(e.x) >= AirfieldSpec.CROWD_NEAR - 0.01f || e.variant == OceanSpec.PALM, "nobody stands on the runway: x = ${e.x}")
            assertEquals(AirfieldSpec.STRIP_Y, e.y, "stands on the island")
            assertTrue(e.onSurface && e.groundBound)
        }
    }

    @Test fun theHomecomingIsWinnableOnEveryDifficulty() {
        for ((seed, d) in listOf(1L to Difficulty.EASY, 2L to Difficulty.NORMAL, 5L to Difficulty.NORMAL, 6L to Difficulty.NORMAL, 4L to Difficulty.EASY)) {
            val (game, input, bot) = start(seed, d)
            var t = 0f
            while (t < 500f && game.phase != Phase.VICTORY && game.phase != Phase.GAME_OVER) { bot.control(input, dt); game.update(dt, input); t += dt; while (game.pollSfx() != null) { } }
            assertEquals(Phase.VICTORY, game.phase, "seed $seed on $d: the bot should get home (it ${game.phase} after ${t.toInt()} s)")
            assertTrue(game.stars >= 1, "seed $seed on $d: stars ${game.stars}")
        }
    }
}
