package app.roadtoorbit.game

import app.roadtoorbit.gfx.OceanSpec
import app.roadtoorbit.gfx.VehicleMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ocean level: a waterplane over the sea, a dive into a submarine-rocket, a landing on the seabed as a car and an
 * arrival at Atlantis. The vehicle changes in the order plane, rocket, car, which no other level does.
 */
class OceanLevelTest {
    private val deep = Levels.ALL.first { it.world == World.OCEAN }
    private val dt = 1f / 60f

    private fun start(seed: Long = 7, difficulty: Difficulty = Difficulty.NORMAL): Triple<Game, GameInput, Bot> {
        val game = Game(seed)
        game.level = deep
        game.difficulty = difficulty
        val input = GameInput()
        game.startRun(seed)
        return Triple(game, input, Bot(game))
    }

    @Test fun theLevelIsTheThirdAndItsFormsFollowEachOther() {
        assertEquals(2, deep.index)
        assertEquals(listOf(VehicleMode.PLANE, VehicleMode.ROCKET, VehicleMode.CAR), deep.legs.map { it.mode })
        assertEquals(Finale.ARRIVAL, deep.finale)
        assertTrue(deep.legs[0].minY < 2f, "the waterplane can skim the waves")
        assertEquals(0f, deep.legs[2].zoneLength, "the last leg has no transformation at its end")
    }

    /** The dive: the sea comes up over the vehicle and it is under the surface before the transformation is over. */
    @Test fun theDivePutsTheVehicleUnderTheSurfaceAndKeepsItThere() {
        val (game, input, bot) = start()
        var t = 0f
        var wasDiving = false
        var submergedAt = -1f
        var lastSurface = 0f
        while (t < 200f && game.phase != Phase.GAME_OVER && game.legIndex < 2) {
            bot.control(input, dt)
            game.update(dt, input)
            t += dt
            if (game.legIndex == 0) assertEquals(0f, game.look.surfaceY, "the sea is at y = 0 while the waterplane flies")
            if (game.legIndex == 1 && game.xf.active) {
                wasDiving = true
                assertTrue(game.look.surfaceY >= lastSurface - 1e-3f, "the surface only rises during the dive: ${game.look.surfaceY} after $lastSurface")
                if (submergedAt < 0f && game.look.surfaceY > game.player.y + 0.4f) submergedAt = game.xf.t
            }
            if (game.legIndex == 1) {
                lastSurface = game.look.surfaceY
                if (!game.xf.active) {
                    assertTrue(game.player.y <= game.flightCeiling(game.leg) + 1e-3f, "under water the vehicle stays below the surface: y ${game.player.y}, ceiling ${game.flightCeiling(game.leg)}")
                    assertTrue(game.look.surfaceY > game.player.y + 5f, "the surface is well above the submarine: ${game.look.surfaceY} vs ${game.player.y}")
                }
            }
            while (game.pollSfx() != null) { /* drain */ }
        }
        assertTrue(wasDiving, "the bot should dive")
        assertTrue(submergedAt in 0.05f..0.95f, "the vehicle goes under the surface during the transformation (at ${submergedAt})")
    }

    /** The landing: the vehicle comes down onto a seabed that rises to meet it, without a jump. */
    @Test fun theLandingIsSmoothAndEndsOnTheSeabedInsideTheLanes() {
        val (game, input, bot) = start(seed = 3)
        var t = 0f
        var landing = false
        var lastRel = Float.NaN
        var lastX = Float.NaN
        while (t < 200f && game.phase != Phase.GAME_OVER && game.legIndex < 2) { bot.control(input, dt); game.update(dt, input); t += dt; while (game.pollSfx() != null) { } }
        assertEquals(2, game.legIndex, "the bot reaches the seabed")
        // from the first frame of the landing: the height above the seabed shrinks steadily, the lateral position settles
        var frames = 0
        while (t < 260f && game.phase == Phase.RUN && game.xf.active) {
            landing = true
            val rel = game.player.y - game.look.groundY
            if (!lastRel.isNaN()) {
                assertTrue(rel <= lastRel + 0.02f, "the vehicle only descends: $rel after $lastRel")
                assertTrue(lastRel - rel < 3f, "no jump in a frame: $lastRel -> $rel")
                assertTrue(abs(game.player.x - lastX) < 1.2f, "no lateral jump: $lastX -> ${game.player.x}")
            }
            lastRel = rel; lastX = game.player.x
            frames++
            bot.control(input, dt); game.update(dt, input); t += dt
            while (game.pollSfx() != null) { }
        }
        assertTrue(landing && frames > 30, "the landing lasts $frames frames")
        assertTrue(abs(game.player.y) < 0.05f && abs(game.look.groundY) < 0.05f, "down on the seabed: y ${game.player.y}, ground ${game.look.groundY}")
        assertTrue(abs(game.player.x) <= Tuning.CAR_MAX_X, "inside the lanes: x ${game.player.x}")
        assertEquals(VehicleMode.CAR, game.player.mode)
    }

    @Test fun sceneryStandsOnTheSurfaceOrOnTheSeabed() {
        val (game, input, bot) = start(seed = 11)
        var t = 0f
        var onSurface = 0
        var onSeabed = 0
        while (t < 250f && game.phase != Phase.GAME_OVER && game.phase != Phase.FINALE) {
            bot.control(input, dt); game.update(dt, input); t += dt
            while (game.pollSfx() != null) { }
            for (e in game.entities) {
                if (!e.groundBound) continue
                if (e.onSurface) {
                    onSurface++
                    assertTrue(e.kind == Kind.PEAK || e.kind == Kind.SHIP || (e.kind == Kind.SEASCAPE && e.variant in OceanSpec.ISLAND..OceanSpec.ISLAND + 2), "${e.kind}/${e.variant} stands on the sea")
                } else onSeabed++
            }
        }
        assertTrue(onSurface > 0 && onSeabed > 0, "the level has both: on the sea $onSurface, on the seabed $onSeabed")
    }

    @Test fun theArrivalStopsTheCarAndKeepsItParked() {
        val (game, input, bot) = start(seed = 5)
        var t = 0f
        var maxLegDistBefore = 0f
        while (t < 300f && game.phase != Phase.FINALE) { bot.control(input, dt); game.update(dt, input); t += dt; while (game.pollSfx() != null) { } }
        assertEquals(Phase.FINALE, game.phase, "the bot arrives")
        assertEquals(VehicleMode.CAR, game.player.mode, "it is still the car: there is no landing")
        maxLegDistBefore = game.legDist
        val startSpeed = game.player.speed
        assertTrue(startSpeed > 30f, "it arrives at speed: $startSpeed")
        while (game.phase == Phase.FINALE) { game.update(dt, input); while (game.pollSfx() != null) { } }
        assertEquals(Phase.VICTORY, game.phase)
        assertTrue(game.player.speed < 3f, "stopped or nearly: ${game.player.speed}")
        assertTrue(game.legDist > maxLegDistBefore + 30f, "it rolled on through the gate before stopping")
        for (i in 0 until 600) { game.update(dt, input); while (game.pollSfx() != null) { } }
        assertTrue(game.player.speed < 0.2f, "in the victory the car stays parked: ${game.player.speed}")
        assertEquals(0f, game.player.y, "on the ground")
    }

    @Test fun theDepthReadoutGoesFromTheSurfaceToTheTrench() {
        val (game, input, bot) = start(seed = 2)
        var t = 0f
        var lastKm = 0f
        val hud = HudState()
        while (t < 300f && game.phase != Phase.VICTORY && game.phase != Phase.GAME_OVER) {
            bot.control(input, dt); game.update(dt, input); t += dt
            while (game.pollSfx() != null) { }
            game.fillHud(hud)
            assertTrue(hud.altitudeKm >= lastKm - 1e-3f, "the depth never decreases: ${hud.altitudeKm} after $lastKm")
            lastKm = hud.altitudeKm
            assertEquals(World.OCEAN, hud.world)
            assertEquals(deep.legs.map { it.mode }, hud.legModes.toList())
        }
        assertEquals(deep.destinationKm, lastKm, 1e-3f, "the readout ends at the depth of Atlantis")
    }

    /** The ocean's obstacles use the collision boxes of the models' contract (OceanSpec). */
    @Test fun hazardsUseTheContractBoxes() {
        val seen = HashSet<Kind>()
        for (seed in 1L..4L) {
            val (game, input, bot) = start(seed)
            var t = 0f
            while (t < 220f && game.phase != Phase.GAME_OVER && game.phase != Phase.FINALE) {
                bot.control(input, dt); game.update(dt, input); t += dt
                while (game.pollSfx() != null) { }
                for (e in game.entities) {
                    seen.add(e.kind)
                    when (e.kind) {
                        Kind.SHIP -> {
                            val h = OceanSpec.SHIP_HALF[e.variant]
                            assertEquals(h[0], e.hx); assertEquals(h[1], e.hy); assertEquals(h[2], e.hz)
                            assertEquals(h[1], e.y, "a ship's box rests on the waterline")
                        }
                        Kind.SHARK -> {
                            val h = OceanSpec.SHARK_HALF[e.variant]
                            assertEquals(h[0], e.hx); assertEquals(h[1], e.hy); assertEquals(h[2], e.hz)
                        }
                        Kind.JELLY -> assertEquals(OceanSpec.JELLY_RADIUS, e.radius)
                        Kind.BALLOON -> assertEquals(OceanSpec.FLOCK_RADIUS, e.radius, "a flock of gulls is a sphere of the contract's size")
                        Kind.MINE -> assertTrue(e.radius > 1.1f && e.radius < 2.3f, "mine radius ${e.radius}")
                        else -> Unit
                    }
                }
            }
        }
        for (k in listOf(Kind.SHIP, Kind.PEAK, Kind.BALLOON, Kind.JET, Kind.MINE, Kind.SHARK, Kind.JELLY, Kind.TRAFFIC, Kind.BARRIER, Kind.CRAB, Kind.SEASCAPE)) {
            assertTrue(k in seen, "$k never appeared in four journeys")
        }
    }
}
