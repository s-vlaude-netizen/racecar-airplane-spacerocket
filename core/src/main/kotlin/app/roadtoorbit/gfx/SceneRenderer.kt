package app.roadtoorbit.gfx

import app.roadtoorbit.game.Entity
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.Kind
import app.roadtoorbit.game.Phase
import app.roadtoorbit.game.Tuning
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

    fun resize(w: Int, h: Int) {
        renderer.resize(w, h)
        rig.aspect = w.toFloat() / max(1, h)
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
        val inSpace = g.legIndex == 2 || g.phase == Phase.FINALE || g.phase == Phase.VICTORY
        if (!inSpace || g.phase == Phase.MENU) return
        val p = Mathx.clamp01(g.legDist / Tuning.LEGS[2].length)

        // Earth: sits just beneath the sinking cloud deck, then dominates the view below
        val earthR = 4300f
        if (g.phase == Phase.RUN || g.phase == Phase.COUNTDOWN) {
            val earthY = look.groundY - earthR - 60f
            mat.reset(); mat.fogScale = 0.15f
            mat.rim(0.18f, 0.38f, 0.8f, 3.2f)
            Mat4.setTrs(m, -500f, earthY, -1800f, 0f, 20f + g.clock * 0.4f, 0f, earthR, earthR, earthR)
            renderer.drawLit(renderer.meshes[MeshId.EARTH], m, mat)
        } else {
            // finale: a small Earth hangs in the sky ("earthrise")
            mat.reset(); mat.fogScale = 0f
            mat.rim(0.25f, 0.5f, 1.0f, 2.2f)
            Mat4.setTrs(m, -1700f, 1100f, -4200f, 0f, 40f, 0f, 520f, 520f, 520f)
            renderer.drawLit(renderer.meshes[MeshId.EARTH], m, mat)
        }

        // The Moon grows ahead and slips beneath us during the final approach
        if (g.legIndex == 2 && g.phase != Phase.CRASHING || g.phase == Phase.FINALE) {
            val mApp = if (g.phase == Phase.FINALE || g.phase == Phase.VICTORY) 1f else look.moonApproach
            if (mApp > 0f || g.phase == Phase.FINALE) {
                val r = 2400f
                val x = Mathx.lerp(1400f, 0f, mApp)
                val y = if (g.phase == Phase.FINALE) g.finaleGroundY - r - 25f
                else Mathx.lerp(500f, -330f - r - 25f, Mathx.smoothstep(0f, 1f, mApp))
                val z = Mathx.lerp(-21000f, -700f, mApp.pow(1.6f))
                mat.reset(); mat.fogScale = 0f
                Mat4.setTrs(m, x, y, z, 0f, 30f, 0f, r, r, r)
                renderer.drawLit(renderer.meshes[MeshId.MOON], m, mat)
            }
        }
    }

    // ---- terrain ---------------------------------------------------------------------------------

    private fun terrain(g: Game) {
        if (!env.terrainOn) return
        if (g.phase == Phase.MENU) return
        if (env.terrainStyle > 1.5f) {
            renderer.drawTerrain(g.travelled, 4f, 96, 110, 8, env.terrainTint)
        } else {
            renderer.drawTerrain(g.travelled, 10f, 64, 80, 8, env.terrainTint)
        }
    }

    // ---- road ------------------------------------------------------------------------------------

    private fun road(g: Game) {
        if (g.phase == Phase.MENU || g.legIndex > 1 || !g.look.roadVisible) return
        val leg0 = Tuning.LEGS[0]
        val routeS = if (g.legIndex == 0) g.legDist else leg0.length + g.legDist
        val endRoute = leg0.length + 6f + 62f
        val seg = renderer.meshes[MeshId.ROAD_SEGMENT]
        var s0 = floor((routeS - 40f) / SEG) * SEG
        mat.reset().gloss(0.04f, 8f)
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
        mat.reset()
        var id: MeshId
        var sx = e.scale
        var sy = e.scale
        var sz = e.scale
        var ty = y
        when (e.kind) {
            Kind.TRAFFIC -> {
                id = when (e.variant) {
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
            Kind.ROCK -> id = ROCKS[e.variant % 3]
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
                id = PEAKS[e.variant % 3]
                sx = e.hx; sz = e.hx; sy = e.hy * 2f
                ty = y - e.hy
            }
            Kind.BALLOON -> { id = BALLOONS[e.variant % 4]; sx = 1f; sy = 1f; sz = 1f }
            Kind.STORM -> {
                id = STORMS[e.variant % 2]
                val k = e.radius / 1.4f
                sx = k; sy = k; sz = k
                val flash = lightning(e)
                if (flash > 0.01f) {
                    mat.tint(1f + 1.4f * flash, 1f + 1.4f * flash, 1f + 1.6f * flash)
                    mat.emissive = 0.5f * flash
                    if (bolts < 8) {
                        boltX[bolts] = e.x; boltY[bolts] = y - e.radius * 0.6f; boltZ[bolts] = e.z + 0.5f; boltS[bolts] = e.radius * 0.45f * flash.coerceAtLeast(0.4f)
                        bolts++
                    }
                }
            }
            Kind.JET -> { id = JETS[e.variant % 3]; mat.gloss(0.3f, 20f); sx = 1f; sy = 1f; sz = 1f }
            Kind.RING -> {
                id = MeshId.RING; mat.emissive = 0.9f; mat.fogScale = 0.5f
                if (e.hit) mat.tint(1f, 1f, 1f, 1f)
            }
            Kind.ORB -> { id = MeshId.ORB; mat.emissive = 0.9f; mat.rim(0.4f, 1f, 1f, 2f); sx = 1f; sy = 1f; sz = 1f }
            Kind.CLOUD -> {
                id = CLOUDS[e.variant % 3]; mat.fogScale = 0.7f; mat.emissive = 0.32f
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
            Kind.ASTEROID -> { id = ASTEROIDS[e.variant % 4]; mat.gloss(0.08f, 10f) }
            Kind.SATELLITE -> { id = MeshId.SATELLITE; mat.gloss(0.6f, 30f); sx = 1f; sy = 1f; sz = 1f }
            Kind.CRYSTAL -> { id = MeshId.CRYSTAL; mat.gloss(0.8f, 50f); mat.emissive = 0.7f; mat.rim(0.3f, 0.9f, 1f, 2.2f); sx = 1f; sy = 1f; sz = 1f }
            Kind.PLANET -> {
                id = PLANETS[e.variant % 4]; mat.fogScale = 0f
                mat.rim(0.15f, 0.2f, 0.35f, 2.5f)
            }
            Kind.MOON -> return
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
    }
}
