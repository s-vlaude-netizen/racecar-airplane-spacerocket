package app.roadtoorbit.trace

import app.roadtoorbit.gfx.Blend
import app.roadtoorbit.gfx.Camera
import app.roadtoorbit.gfx.Environment
import app.roadtoorbit.gfx.Material
import app.roadtoorbit.gfx.MeshId
import app.roadtoorbit.gfx.MeshLibrary
import app.roadtoorbit.gfx.Renderer
import app.roadtoorbit.gfx.VehicleMode
import app.roadtoorbit.gfx.VehicleRenderer
import app.roadtoorbit.gfx.VehicleVisual
import app.roadtoorbit.math.Mat4
import java.io.File
import org.junit.Test

/**
 * Not assertions: GL command traces of the player's vehicle for the headless render check (tools/render-check). The
 * "standard" trace is the regression guard of the standard skin (the Moon and Mars use it): render it before and after a
 * change to the vehicle and compare the PNGs (or the .bin files) byte for byte.
 */
class OceanVehicleGalleryTest {
    private fun outFile(name: String) = File(System.getProperty("traceDir") ?: "build/traces", name)

    /** One vehicle pose in one view. */
    private class Shot(
        val name: String, val from: VehicleMode, val to: VehicleMode, val t: Float,
        val yaw: Float, val eye: FloatArray,
        val steer: Float = 0f, val wheelSpin: Float = 0f, val flame: Float = 1f,
    )

    /** The standard-skin vehicle on the showroom platform of RenderTraceTest, in every form and every morph. */
    @Test fun standardSkinRegression() {
        val w = 960
        val h = 540
        val gl = RecordingGles(w, h)
        val renderer = Renderer(gl, MeshLibrary())
        renderer.resize(w, h)
        val vehicle = VehicleRenderer(gl, renderer)
        val env = Environment().apply {
            terrainOn = false
            zenith[0] = 0.18f; zenith[1] = 0.32f; zenith[2] = 0.62f
            horizon[0] = 0.78f; horizon[1] = 0.74f; horizon[2] = 0.78f
            fogColor[0] = 0.78f; fogColor[1] = 0.74f; fogColor[2] = 0.78f
            fogDensity = 0.002f
            setSun(0.55f, 0.75f, 0.45f)
        }
        val cam = Camera().apply { fovY = 38f; aspect = w.toFloat() / h; tx = 0f; ty = 0.9f; tz = 0f }
        val visual = VehicleVisual()
        val vm = FloatArray(16)
        val pm = FloatArray(16)
        val mat = Material()

        val front = floatArrayOf(7.5f, 3.4f, 9.5f)
        val side = floatArrayOf(0.5f, 2.0f, 12.5f)
        val shots = ArrayList<Shot>()
        for (m in VehicleMode.values()) {
            shots.add(Shot("${m}_front", m, m, 0f, 155f, front, steer = 12f, wheelSpin = 33f))
            shots.add(Shot("${m}_side", m, m, 0f, 90f, side, wheelSpin = 71f, flame = 0.4f))
            shots.add(Shot("${m}_rear", m, m, 0f, 35f, front, steer = -9f, flame = 0.1f))
        }
        for (a in VehicleMode.values()) for (b in VehicleMode.values()) {
            if (a == b) continue
            for (step in 0..8) shots.add(Shot("${a}_${b}_$step", a, b, step / 8f, 155f, front, wheelSpin = step * 20f))
        }
        for ((n, s) in shots.withIndex()) {
            visual.from = s.from; visual.to = s.to; visual.morph = s.t
            visual.flame = s.flame; visual.time = n * 0.1f
            visual.steerDeg = s.steer; visual.wheelSpinDeg = s.wheelSpin
            cam.ex = s.eye[0]; cam.ey = s.eye[1]; cam.ez = s.eye[2]
            cam.update()
            renderer.beginFrame(env, cam)
            Mat4.setTrs(pm, 0f, -0.2f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
            mat.reset().gloss(0.3f, 30f)
            renderer.drawLit(renderer.meshes[MeshId.PLATFORM], pm, mat)
            mat.reset().tint(0.6f, 0.95f, 1f).also { it.emissive = 1f }
            renderer.drawLit(renderer.meshes[MeshId.PLATFORM_RING], pm, mat)
            Mat4.setTrs(vm, 0f, 0f, 0f, 0f, s.yaw, 0f, 1f, 1f, 1f)
            vehicle.draw(visual, vm)
            renderer.endFrame()
            gl.endFrame()
        }
        gl.writeTo(outFile("standard.bin"))
        outFile("standard.txt").writeText(shots.joinToString("\n") { it.name })
    }

    // ---------------------------------------------------------------------------------------------
    // the ocean skin: a little stage that records one frame per call
    // ---------------------------------------------------------------------------------------------

    private class Stage {
        val w = (System.getProperty("traceW") ?: "1200").toInt()
        val h = (System.getProperty("traceH") ?: "675").toInt()
        val gl = RecordingGles(w, h)
        val renderer = Renderer(gl, MeshLibrary())
        val vehicle = VehicleRenderer(gl, renderer)
        val cam = Camera().apply { aspect = w.toFloat() / h }
        val mat = Material()
        val m = FloatArray(16)
        val names = ArrayList<String>()

        init { renderer.resize(w, h) }

        fun frame(name: String, look: GalleryKit.Look, eye: FloatArray, target: FloatArray, fov: Float, fog: Float? = null, draw: () -> Unit) {
            val env = GalleryKit.environment(look)
            if (fog != null) env.fogDensity = fog
            cam.ex = eye[0]; cam.ey = eye[1]; cam.ez = eye[2]
            cam.tx = target[0]; cam.ty = target[1]; cam.tz = target[2]
            cam.fovY = fov
            cam.far = 4000f
            cam.update()
            renderer.beginFrame(env, cam)
            draw()
            renderer.endFrame()
            gl.endFrame()
            names.add(name)
        }

        /** A mesh at (x, y, z) turned by [ry] and scaled, drawn the way GalleryKit does. */
        fun item(id: MeshId, x: Float, y: Float, z: Float, ry: Float = 0f, sx: Float = 1f, sy: Float = sx, sz: Float = sx, emissive: Float = 0f, tint: FloatArray? = null, rx: Float = 0f) {
            mat.reset().gloss(0.4f, 40f)
            if (tint != null) mat.tint(tint[0], tint[1], tint[2])
            mat.emissive = emissive
            Mat4.setTrs(m, x, y, z, rx, ry, 0f, sx, sy, sz)
            renderer.drawLit(renderer.meshes[id], m, mat)
        }

        /** A flat disc of [radius] m under the vehicle at height [y], tinted [c]. */
        fun floor(y: Float, radius: Float, c: FloatArray) {
            val k = radius / 4.6f
            mat.reset().tint(c[0], c[1], c[2]).gloss(0.1f, 12f)
            Mat4.setTrs(m, 0f, y - 0.2f * k * 0.05f, 0f, 0f, 0f, 0f, k, 0.05f, k)
            renderer.drawLit(renderer.meshes[MeshId.PLATFORM], m, mat)
        }

        fun write(file: String) {
            val dir = File(System.getProperty("traceDir") ?: "build/traces")
            gl.writeTo(File(dir, "$file.bin"))
            File(dir, "$file.txt").writeText(names.joinToString("\n"))
        }
    }

    /** The seven new models on their own, big, each from the front (-Z), a front 3/4 and the side. */
    @Test fun oceanPartsGallery() {
        val s = Stage()
        val sea = floatArrayOf(0.10f, 0.20f, 0.32f)
        class P(val name: String, val id: MeshId, val sx: Float, val sy: Float, val sz: Float, val cy: Float, val dist: Float, val emissive: Float = 0f, val tint: FloatArray? = null)
        val parts = listOf(
            P("pontoon", MeshId.PONTOON, 3f, 3f, 3f, 1.5f, 8f),
            P("float", MeshId.PONTOON, 1.0f, 0.9f, 7.5f, 1.0f, 12f),
            P("prop", MeshId.PROP, 3f, 3f, 3f, 1.5f, 8f),
            P("tower", MeshId.TOWER, 3.2f, 3.2f, 3.2f, 1.6f, 8f, tint = floatArrayOf(0.2f, 0.75f, 0.72f)),
            P("periscope", MeshId.PERISCOPE, 3.5f, 3.5f, 3.5f, 1.7f, 7f),
            P("porthole", MeshId.PORTHOLE, 3f, 3f, 3f, 1.5f, 7f, emissive = 0.9f),
            P("searchlight", MeshId.SEARCHLIGHT, 3f, 3f, 3f, 1.5f, 7f, emissive = 0.85f),
        )
        for (p in parts) {
            val ang = floatArrayOf(0f, 35f, 90f)
            val nm = arrayOf("front", "34", "side")
            for (k in 0..2) {
                val a = Math.toRadians(ang[k].toDouble())
                // the camera stands in front of the part (-Z) and swings round toward +X
                val eye = floatArrayOf((Math.sin(a) * p.dist).toFloat(), p.cy + 1.2f, (-Math.cos(a) * p.dist).toFloat())
                s.frame("${p.name}_${nm[k]}", GalleryKit.Look.DEEP, eye, floatArrayOf(0f, p.cy, 0f), 50f) {
                    s.floor(-0.05f, 12f, sea)
                    s.item(p.id, 0f, p.cy, 0f, sx = p.sx, sy = p.sy, sz = p.sz, emissive = p.emissive, tint = p.tint)
                }
            }
        }
        // the light cone, additive, seen from behind the lamp and from the side
        for (v in 0..1) {
            val eye = if (v == 0) floatArrayOf(0f, 3f, 9f) else floatArrayOf(14f, 5f, -4f)
            s.frame(if (v == 0) "cone_behind" else "cone_side", GalleryKit.Look.DEEP, eye, floatArrayOf(0f, 0.5f, -10f), 56f) {
                s.floor(-0.05f, 30f, sea)
                s.item(MeshId.SEARCHLIGHT, 0f, 0.6f, 0f, sx = 1f, emissive = 0.85f)
                s.renderer.setBlend(Blend.ADDITIVE)
                s.mat.reset().tint(1f, 0.95f, 0.8f, 1f); s.mat.emissive = 1f; s.mat.fogScale = 0.3f
                Mat4.setTrs(s.m, 0f, 0.6f, -0.2f, 0f, 0f, 0f, 5f, 5f, 18f)
                s.renderer.drawLit(s.renderer.meshes[MeshId.LIGHT_CONE], s.m, s.mat)
                s.renderer.setBlend(Blend.OPAQUE)
                s.item(MeshId.ORB, 0f, 0.6f, -12f, sx = 0.6f)
            }
        }
        s.write("parts")
    }

    // ---------------------------------------------------------------------------------------------
    // the vehicle in the ocean skin
    // ---------------------------------------------------------------------------------------------

    private fun look(m: VehicleMode) = if (m == VehicleMode.PLANE) GalleryKit.Look.SURFACE else GalleryKit.Look.DEEP

    private fun floorFor(s: Stage, m: VehicleMode) {
        when (m) {
            VehicleMode.PLANE -> s.floor(-5f, 70f, floatArrayOf(0.08f, 0.42f, 0.62f))
            VehicleMode.ROCKET -> s.floor(-6f, 70f, floatArrayOf(0.10f, 0.20f, 0.30f))
            VehicleMode.CAR -> s.floor(-0.02f, 70f, floatArrayOf(0.20f, 0.24f, 0.32f))
        }
    }

    private fun oceanVisual(from: VehicleMode, to: VehicleMode, t: Float, n: Int): VehicleVisual = VehicleVisual().also {
        it.skin = app.roadtoorbit.gfx.Skin.OCEAN
        it.from = from; it.to = to; it.morph = t
        it.flame = 1f; it.time = n * 0.07f; it.beam = 1f
        it.wheelSpinDeg = n * 25f
    }

    /** The three forms from the front 3/4 and from the chase camera (10 m behind, 3.5 m above, looking 25 m ahead, 62 degrees). */
    @Test fun oceanVehicleForms() {
        val s = Stage()
        var n = 0
        for (m in VehicleMode.values()) {
            val v = oceanVisual(m, m, 0f, n++)
            s.frame("${m}_front34", look(m), floatArrayOf(6.5f, 3.2f, -8.5f), floatArrayOf(0f, 0.8f, 0f), 40f) {
                floorFor(s, m)
                Mat4.setTrs(s.m, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
                s.vehicle.draw(v, s.m)
            }
        }
        for (m in VehicleMode.values()) {
            val v = oceanVisual(m, m, 0f, n++)
            s.frame("${m}_chase", look(m), floatArrayOf(0f, 3.5f, 10f), floatArrayOf(0f, 0.5f, -25f), 62f) {
                floorFor(s, m)
                Mat4.setTrs(s.m, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
                s.vehicle.draw(v, s.m)
            }
        }
        s.write("forms")
    }

    /** The two morphs of the ocean level (the dive and the landing), t = 0, .2, .4, .6, .8, 1. */
    @Test fun oceanVehicleMorphs() {
        val s = Stage()
        var n = 0
        for ((a, b) in listOf(VehicleMode.PLANE to VehicleMode.ROCKET, VehicleMode.ROCKET to VehicleMode.CAR, VehicleMode.CAR to VehicleMode.PLANE)) {
            for (step in 0..5) {
                val t = step / 5f
                val v = oceanVisual(a, b, t, n++)
                s.frame("${a}_${b}_$step", GalleryKit.Look.DEEP, floatArrayOf(6.5f, 3.2f, -8.5f), floatArrayOf(0f, 0.8f, 0f), 40f) {
                    floorFor(s, VehicleMode.ROCKET)
                    Mat4.setTrs(s.m, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
                    s.vehicle.draw(v, s.m)
                }
            }
        }
        s.write("morphs")
    }
}
