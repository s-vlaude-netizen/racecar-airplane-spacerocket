package app.roadtoorbit.gfx

import app.roadtoorbit.game.Entity
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.Kind
import app.roadtoorbit.game.Phase
import app.roadtoorbit.game.World
import app.roadtoorbit.gl.Gles
import app.roadtoorbit.math.Mat4
import app.roadtoorbit.math.Mathx
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Draws a [Game] to the screen: sky, celestial bodies, procedural terrain, road, every entity,
 * the transforming vehicle, shadows and particles. Holds no game state of its own.
 */
class SceneRenderer(private val gl: Gles, library: MeshLibrary = MeshLibrary()) {
    val renderer = Renderer(gl, library)
    val env = Environment()
    val rig = CameraRig()

    private val vehicle = VehicleRenderer(gl, renderer)
    private val particleGpu = ParticleRenderer(gl, 2400)
    private val envModel = EnvironmentModel()
    private val mat = Material()
    private val m = FloatArray(16)
    private val vm = FloatArray(16)

    // lightning bolts queued while drawing storms (drawn additively afterwards)
    private val boltX = FloatArray(8)
    private val boltY = FloatArray(8)
    private val boltZ = FloatArray(8)
    private val boltS = FloatArray(8)
    private var bolts = 0

    /** A surface can briefly report 0 x N while windows are being resized; keep the last usable size then. */
    fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        renderer.resize(w, h)
        rig.aspect = w.toFloat() / h
    }

    fun release() {
        vehicle.release()
        particleGpu.release()
        renderer.release()
    }

    fun render(g: Game) {
        envModel.update(g, env)
        bolts = 0
        renderer.beginFrame(env, rig.camera)
        celestials(g)
        terrain(g)
        road(g)
        entities(g)
        showroomPlatform(g)
        shadow(g)
        vehicleDraw(g)
        translucent(g)
        renderer.endFrame()
    }

    // ---- celestial bodies --------------------------------------------------------------------------

    private fun celestials(g: Game) {
        val look = g.look
        val inSpace = g.legIndex == g.level.lastLeg || g.phase == Phase.FINALE || g.phase == Phase.VICTORY
        if (!inSpace || g.phase == Phase.MENU) return
        val mars = g.level.world == World.MARS

        // The planet we left: sits just beneath the sinking cloud (or dust) deck, then dominates the view below
        val homeR = 4300f
        if (g.phase == Phase.RUN || g.phase == Phase.COUNTDOWN) {
            val homeY = look.groundY - homeR - 60f
            mat.reset(); mat.fogScale = if (mars) 0.05f else 0.15f
            if (mars) mat.rim(0.30f, 0.14f, 0.06f, 4.2f) else mat.rim(0.18f, 0.38f, 0.8f, 3.2f)
            Mat4.setTrs(m, -500f, homeY, -1800f, 0f, 20f + g.clock * 0.4f, 0f, homeR, homeR, homeR)
            renderer.drawLit(renderer.meshes[if (mars) MeshId.MARS else MeshId.EARTH], m, mat)
        } else if (mars) {
            // finale on Phobos: Mars fills the sky behind the landing site, where the camera swings round to see it
            mat.reset(); mat.fogScale = 0f
            mat.rim(0.55f, 0.30f, 0.16f, 2.6f)
            Mat4.setTrs(m, -2800f, 900f, 3300f, 0f, 200f, 0f, 1150f, 1150f, 1150f)
            renderer.drawLit(renderer.meshes[MeshId.MARS], m, mat)
        } else {
            // finale: a small Earth hangs in the sky ("earthrise")
            mat.reset(); mat.fogScale = 0f
            mat.rim(0.25f, 0.5f, 1.0f, 2.2f)
            Mat4.setTrs(m, -1700f, 1100f, -4200f, 0f, 40f, 0f, 520f, 520f, 520f)
            renderer.drawLit(renderer.meshes[MeshId.EARTH], m, mat)
        }

        // The destination (the Moon, or Phobos) grows ahead and slips beneath us during the final approach
        if (g.legIndex == g.level.lastLeg && g.phase != Phase.CRASHING || g.phase == Phase.FINALE) {
            val mApp = if (g.phase == Phase.FINALE || g.phase == Phase.VICTORY) 1f else look.moonApproach
            if (mApp > 0f || g.phase == Phase.FINALE) {
                val r = 2400f
                val x = Mathx.lerp(1400f, 0f, mApp)
                val y = if (g.phase == Phase.FINALE) g.finaleGroundY - r - 25f
                else Mathx.lerp(500f, -330f - r - 25f, Mathx.smoothstep(0f, 1f, mApp))
                val z = Mathx.lerp(-21000f, -700f, mApp.pow(1.6f))
                mat.reset(); mat.fogScale = 0f
                Mat4.setTrs(m, x, y, z, 0f, 30f, 0f, r, r, r)
                renderer.drawLit(renderer.meshes[if (mars) MeshId.PHOBOS else MeshId.MOON], m, mat)
            }
        }
    }

    // ---- terrain ---------------------------------------------------------------------------------

    private val nearWindow = TerrainWindow()

    private fun terrain(g: Game) {
        if (!env.terrainOn) return
        if (g.phase == Phase.MENU) return
        if (env.terrainStyle > 1.5f) {
            moonTerrain(g)
        } else {
            renderer.drawTerrain(g.travelled, 10f, 64, 80, 8, env.terrainTint)
        }
    }

    /**
     * The Moon (and Phobos): a fine grid around the vehicle and, once the vehicle is low enough, a coarse one under it that
     * reaches to the horizon with the mountain ranges standing on it, so that the plain has no visible edge.
     */
    private fun moonTerrain(g: Game) {
        val altitude = g.player.y - env.groundY
        val fade = Mathx.smoothstep(MoonTerrain.RANGE_RISES_BY, MoonTerrain.FAR_FROM_ALTITUDE, altitude) // 1 high up, 0 down on the ground
        val range = 1f - fade
        if (altitude < MoonTerrain.FAR_FROM_ALTITUDE) {
            renderer.terrainWindow(g.travelled, MoonTerrain.NEAR_CELL, MoonTerrain.NEAR_COLS, MoonTerrain.NEAR_ROWS, MoonTerrain.NEAR_BEHIND, nearWindow)
            renderer.drawTerrain(
                g.travelled, MoonTerrain.FAR_CELL, MoonTerrain.FAR_COLS, MoonTerrain.FAR_ROWS, MoonTerrain.FAR_BEHIND,
                env.terrainTint, under = nearWindow, fogScale = 1f + MoonTerrain.FAR_FOG_BOOST * fade, range = range,
            )
        }
        renderer.drawTerrain(
            g.travelled, MoonTerrain.NEAR_CELL, MoonTerrain.NEAR_COLS, MoonTerrain.NEAR_ROWS, MoonTerrain.NEAR_BEHIND,
            env.terrainTint, range = range,
        )
    }

    // ---- road ------------------------------------------------------------------------------------

    private fun road(g: Game) {
        if (g.phase == Phase.MENU || g.legIndex > 1 || !g.look.roadVisible) return
        val leg0 = g.level.legs[0]
        val routeS = if (g.legIndex == 0) g.legDist else leg0.length + g.legDist
        val endRoute = leg0.length + 6f + 62f
        val seg = renderer.meshes[MeshId.ROAD_SEGMENT]
        var s0 = floor((routeS - 40f) / SEG) * SEG
        mat.reset().gloss(0.04f, 8f)
        if (g.level.world == World.MARS) mat.tint(1.10f, 0.84f, 0.72f) // dust on the asphalt
        while (s0 < routeS + 800f && s0 < endRoute) {
            if (s0 + SEG > -30f) {
                Mat4.setTrs(m, 0f, env.groundY, -(s0 - routeS), 0f, 0f, 0f, 1f, 1f, 1f)
                renderer.drawLit(seg, m, mat)
            }
            s0 += SEG
        }
    }

    // ---- entities --------------------------------------------------------------------------------

    private fun entities(g: Game) {
        for (e in g.entities) {
            if (e.z > 28f) continue
            if (e.hit && e.kind.category == app.roadtoorbit.game.Category.PICKUP) continue
            drawEntity(g, e)
        }
    }

    private fun drawEntity(g: Game, e: Entity) {
        val y = g.entityY(e)
        val mars = g.level.world == World.MARS
        mat.reset()
        var id: MeshId
        var sx = e.scale
        var sy = e.scale
        var sz = e.scale
        var ty = y
        when (e.kind) {
            Kind.TRAFFIC -> {
                id = if (mars) when (e.variant) {
                    4 -> HAULERS[e.tint % 3]
                    5 -> CRAWLERS[e.tint % 3]
                    else -> ROVERS[e.tint % 3]
                } else when (e.variant) {
                    4 -> TRUCKS[e.tint % 3]
                    5 -> VANS[e.tint % 3]
                    else -> SEDANS[e.tint % 6]
                }
                mat.gloss(0.45f, 36f)
                sx = 1f; sy = 1f; sz = 1f
            }
            Kind.BARRIER -> { id = MeshId.BARRIER; sx = 1f; sy = 1f; sz = 1f }
            Kind.CONE -> { id = MeshId.CONE; sx = 1f; sy = 1f; sz = 1f }
            Kind.BARREL -> { id = MeshId.BARREL; sx = 1f; sy = 1f; sz = 1f }
            Kind.COIN -> { id = MeshId.COIN; mat.gloss(0.8f, 40f); mat.emissive = 0.35f; sx = 1f; sy = 1f; sz = 1f }
            Kind.NITRO -> { id = MeshId.NITRO; mat.gloss(0.7f, 40f); mat.emissive = 0.3f; mat.rim(0.2f, 0.7f, 1f, 2.5f); sx = 1f; sy = 1f; sz = 1f }
            Kind.REPAIR -> { id = MeshId.REPAIR; mat.emissive = 0.3f; mat.rim(0.2f, 1f, 0.5f, 2.5f); sx = 1f; sy = 1f; sz = 1f }
            Kind.TREE_PINE -> id = PINES[e.variant % 3]
            Kind.TREE_ROUND -> id = ROUND_TREES[e.variant % 3]
            Kind.ROCK -> { id = ROCKS[e.variant % 3]; if (mars) mat.tint(1.28f, 0.66f, 0.48f) }
            Kind.BILLBOARD -> { id = BILLBOARDS[e.variant % 4]; sx = 1f; sy = 1f; sz = 1f }
            Kind.START_ARCH -> { id = MeshId.START_ARCH; sx = 1f; sy = 1f; sz = 1f; ty = g.look.groundY }
            Kind.GATE_ARCH -> {
                id = if (e.variant == 0) MeshId.GATE_ARCH_BIG else MeshId.GATE_ARCH_SMALL
                mat.emissive = if (e.variant == 0) 0.4f else 0.8f
                mat.fogScale = 0.4f
                sx = 1f; sy = 1f; sz = 1f; ty = g.look.groundY
            }
            Kind.RAMP -> { id = MeshId.RAMP; sx = 1f; sy = 1f; sz = 1f; ty = g.look.groundY }
            Kind.PEAK -> {
                id = if (mars) SPIRES[e.variant % 3] else PEAKS[e.variant % 3]
                sx = e.hx; sz = e.hx; sy = e.hy * 2f
                ty = y - e.hy
            }
            Kind.BALLOON -> {
                if (mars) { id = SAUCERS[e.variant % 4]; mat.gloss(0.5f, 40f); mat.emissive = 0.18f } else id = BALLOONS[e.variant % 4]
                sx = 1f; sy = 1f; sz = 1f
            }
            Kind.STORM -> {
                id = STORMS[e.variant % 2]
                val k = e.radius / 1.4f
                sx = k; sy = k; sz = k
                if (mars) mat.tint(1.5f, 1.0f, 0.62f) // dust storms: no lightning, a brown-orange haze
                val flash = if (mars) 0f else lightning(e)
                if (flash > 0.01f) {
                    mat.tint(1f + 1.4f * flash, 1f + 1.4f * flash, 1f + 1.6f * flash)
                    mat.emissive = 0.5f * flash
                    if (bolts < 8) {
                        boltX[bolts] = e.x; boltY[bolts] = y - e.radius * 0.6f; boltZ[bolts] = e.z + 0.5f; boltS[bolts] = e.radius * 0.45f * flash.coerceAtLeast(0.4f)
                        bolts++
                    }
                }
            }
            Kind.JET -> { id = if (mars) MARS_JETS[e.variant % 3] else JETS[e.variant % 3]; mat.gloss(0.3f, 20f); sx = 1f; sy = 1f; sz = 1f }
            Kind.RING -> {
                id = MeshId.RING; mat.emissive = 0.9f; mat.fogScale = 0.5f
                if (e.hit) mat.tint(1f, 1f, 1f, 1f)
            }
            Kind.ORB -> { id = MeshId.ORB; mat.emissive = 0.9f; mat.rim(0.4f, 1f, 1f, 2f); sx = 1f; sy = 1f; sz = 1f }
            Kind.CLOUD -> {
                id = CLOUDS[e.variant % 3]; mat.fogScale = 0.7f; mat.emissive = 0.32f
                if (mars) mat.tint(0.98f, 0.74f, 0.55f) // puffs of dust
                // clouds melt away as we leave the atmosphere
                val fade = 1f - Mathx.smoothstep(1.0f, 1.14f, g.look.altitude)
                if (fade <= 0.01f) return
                sx *= fade; sy *= fade; sz *= fade
            }
            Kind.PORTAL -> {
                id = if (e.variant == 0) MeshId.PORTAL_BIG else MeshId.PORTAL_SMALL
                mat.emissive = 0.9f; mat.fogScale = 0.4f
                sx = 1f; sy = 1f; sz = 1f
            }
            Kind.ASTEROID -> { id = ASTEROIDS[e.variant % 4]; mat.gloss(0.08f, 10f); if (mars) mat.tint(1.12f, 0.82f, 0.68f) }
            Kind.SATELLITE -> { id = MeshId.SATELLITE; mat.gloss(0.6f, 30f); sx = 1f; sy = 1f; sz = 1f }
            Kind.CRYSTAL -> { id = MeshId.CRYSTAL; mat.gloss(0.8f, 50f); mat.emissive = 0.7f; mat.rim(0.3f, 0.9f, 1f, 2.2f); sx = 1f; sy = 1f; sz = 1f }
            Kind.PLANET -> {
                id = PLANETS[e.variant % 4]; mat.fogScale = 0f
                mat.rim(0.15f, 0.2f, 0.35f, 2.5f)
            }
            Kind.MOON -> return
            Kind.MESA -> id = MESAS[e.variant % 3]
            Kind.DEVIL -> id = MeshId.DEVIL
            Kind.DOME -> id = MeshId.DOME
            Kind.BOULDER -> { id = ASTEROIDS[e.variant % 4]; mat.gloss(0.08f, 10f); mat.tint(0.66f, 0.52f, 0.46f); mat.rim(0.95f, 0.38f, 0.10f, 2.6f) } // dark basalt with a hot rim: not scenery
        }
        // tint multipliers chosen by the spawner (balloons/billboards keep their own colours)
        Mat4.setTrs(m, e.x, ty, e.z, e.rx, e.ry, e.rz, sx, sy, sz)
        renderer.drawLit(renderer.meshes[id], m, mat)
        if (e.kind == Kind.PLANET && e.variant == 1) {
            // ringed gas giant
            Mat4.setTrs(m, e.x, ty, e.z, e.rx + 18f, e.ry, e.rz, sx, sy, sz)
            mat.reset(); mat.fogScale = 0f
            renderer.drawLit(renderer.meshes[MeshId.PLANET_RING], m, mat)
        }
    }

    /** 0..1 flash intensity for a storm cloud, deterministic per cloud. */
    private fun lightning(e: Entity): Float {
        val t = e.age * 2.6f + e.x * 0.37f
        val w = sin(t * 3.1f) * sin(t * 7.3f + 1.2f)
        return if (w > 0.82f) ((w - 0.82f) / 0.18f).coerceIn(0f, 1f) else 0f
    }

    // ---- vehicle ---------------------------------------------------------------------------------

    private fun showroomPlatform(g: Game) {
        if (g.phase != Phase.MENU) return
        mat.reset().gloss(0.3f, 30f)
        Mat4.setTrs(m, 0f, -0.2f, 0f, 0f, g.menuTime * 6f, 0f, 1f, 1f, 1f)
        renderer.drawLit(renderer.meshes[MeshId.PLATFORM], m, mat)
        mat.reset().tint(0.6f, 0.95f, 1f); mat.emissive = 1f; mat.fogScale = 0f
        renderer.drawLit(renderer.meshes[MeshId.PLATFORM_RING], m, mat)
    }

    private fun shadow(g: Game) {
        val p = g.player
        if (!p.alive || g.phase == Phase.MENU) return
        val ground = if (g.phase == Phase.FINALE || g.phase == Phase.VICTORY) g.finaleGroundY else g.look.groundY
        if (!g.look.terrainOn && g.phase != Phase.FINALE && g.phase != Phase.VICTORY) return
        val h = max(0f, p.y - ground)
        if (h > 90f) return
        val alpha = (0.6f - h * 0.012f).coerceIn(0f, 0.6f)
        if (alpha < 0.03f) return
        val dom = p.visual.dominant
        val len = when (dom) { VehicleMode.CAR -> 2.5f; VehicleMode.PLANE -> 3f; VehicleMode.ROCKET -> 3.4f }
        val wid = when (dom) { VehicleMode.CAR -> 1.3f; VehicleMode.PLANE -> 3.2f; VehicleMode.ROCKET -> 1.5f }
        renderer.setBlend(Blend.ALPHA)
        mat.reset().tint(1f, 1f, 1f, alpha); mat.fogScale = 0.3f
        Mat4.setTrs(m, p.x, ground + 0.06f, 0f, 0f, p.yaw, 0f, wid + h * 0.03f, 1f, len + h * 0.03f)
        renderer.drawLit(renderer.meshes[MeshId.SHADOW], m, mat)
        renderer.setBlend(Blend.OPAQUE)
    }

    private fun vehicleDraw(g: Game) {
        val p = g.player
        val v = p.visual
        val baseY = if (g.phase == Phase.MENU) 0f else p.y + p.bounce
        val spin = if (g.phase == Phase.MENU) g.menuTime * 6f else 0f
        Mat4.setTrs(vm, p.x, baseY, 0f, p.pitch, p.yaw + spin, p.roll, 1f, 1f, 1f)
        vehicle.draw(v, vm)
    }

    // ---- translucent pass ------------------------------------------------------------------------

    private fun translucent(g: Game) {
        // lightning bolts
        if (bolts > 0) {
            renderer.setBlend(Blend.ADDITIVE)
            for (i in 0 until bolts) {
                mat.reset().tint(0.8f, 0.9f, 1f, 1f); mat.emissive = 1f; mat.fogScale = 0.3f
                Mat4.setTrs(m, boltX[i], boltY[i], boltZ[i], 0f, 0f, 0f, boltS[i], boltS[i], boltS[i])
                renderer.drawLit(renderer.meshes[MeshId.BOLT], m, mat)
            }
            renderer.setBlend(Blend.OPAQUE)
        }
        particleGpu.draw(renderer, g.particles, additive = false)
        particleGpu.draw(renderer, g.particles, additive = true)
    }

    private companion object {
        const val SEG = 24f
        val SEDANS = arrayOf(MeshId.SEDAN_0, MeshId.SEDAN_1, MeshId.SEDAN_2, MeshId.SEDAN_3, MeshId.SEDAN_4, MeshId.SEDAN_5)
        val TRUCKS = arrayOf(MeshId.TRUCK_0, MeshId.TRUCK_1, MeshId.TRUCK_2)
        val VANS = arrayOf(MeshId.VAN_0, MeshId.VAN_1, MeshId.VAN_2)
        val PINES = arrayOf(MeshId.PINE_0, MeshId.PINE_1, MeshId.PINE_2)
        val ROUND_TREES = arrayOf(MeshId.ROUND_TREE_0, MeshId.ROUND_TREE_1, MeshId.ROUND_TREE_2)
        val ROCKS = arrayOf(MeshId.ROCK_0, MeshId.ROCK_1, MeshId.ROCK_2)
        val BILLBOARDS = arrayOf(MeshId.BILLBOARD_0, MeshId.BILLBOARD_1, MeshId.BILLBOARD_2, MeshId.BILLBOARD_3)
        val PEAKS = arrayOf(MeshId.PEAK_0, MeshId.PEAK_1, MeshId.PEAK_2)
        val BALLOONS = arrayOf(MeshId.BALLOON_0, MeshId.BALLOON_1, MeshId.BALLOON_2, MeshId.BALLOON_3)
        val STORMS = arrayOf(MeshId.STORM_0, MeshId.STORM_1)
        val JETS = arrayOf(MeshId.JET_0, MeshId.JET_1, MeshId.JET_2)
        val CLOUDS = arrayOf(MeshId.CLOUD_0, MeshId.CLOUD_1, MeshId.CLOUD_2)
        val ASTEROIDS = arrayOf(MeshId.ASTEROID_0, MeshId.ASTEROID_1, MeshId.ASTEROID_2, MeshId.ASTEROID_3)
        val PLANETS = arrayOf(MeshId.PLANET_0, MeshId.PLANET_1, MeshId.PLANET_2, MeshId.PLANET_3)
        val ROVERS = arrayOf(MeshId.ROVER_0, MeshId.ROVER_1, MeshId.ROVER_2)
        val HAULERS = arrayOf(MeshId.HAULER_0, MeshId.HAULER_1, MeshId.HAULER_2)
        val CRAWLERS = arrayOf(MeshId.CRAWLER_0, MeshId.CRAWLER_1, MeshId.CRAWLER_2)
        val MESAS = arrayOf(MeshId.MESA_0, MeshId.MESA_1, MeshId.MESA_2)
        val SPIRES = arrayOf(MeshId.SPIRE_0, MeshId.SPIRE_1, MeshId.SPIRE_2)
        val SAUCERS = arrayOf(MeshId.SAUCER_0, MeshId.SAUCER_1, MeshId.SAUCER_2, MeshId.SAUCER_3)
        val MARS_JETS = arrayOf(MeshId.MARS_JET_0, MeshId.MARS_JET_1, MeshId.MARS_JET_2)
    }
}
