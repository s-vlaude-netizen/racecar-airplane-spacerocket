package app.roadtoorbit.trace

import app.roadtoorbit.gfx.Camera
import app.roadtoorbit.gfx.Environment
import app.roadtoorbit.gfx.Material
import app.roadtoorbit.gfx.MeshId
import app.roadtoorbit.gfx.MeshLibrary
import app.roadtoorbit.gfx.Renderer
import app.roadtoorbit.math.Mat4
import java.io.File
import org.junit.Test

/**
 * Not assertions: emits a GL command trace of the world models laid out on the Martian ground, a few views of a few
 * dozen models each, for the headless render check to turn into PNGs (build/traces/gallery.bin).
 */
class MeshGalleryTest {
    private class Item(val id: MeshId, val x: Float, val y: Float, val z: Float, val ry: Float = 0f, val sx: Float = 1f, val sy: Float = sx, val sz: Float = sx, val tint: FloatArray? = null, val emissive: Float = 0f)

    private class View(val name: String, val eye: FloatArray, val target: FloatArray, val fov: Float, val items: List<Item>, val terrain: Boolean = true, val fog: Float = 0.0015f)

    @Test
    fun marsGallery() {
        val w = (System.getProperty("traceW") ?: "1200").toInt()
        val h = (System.getProperty("traceH") ?: "675").toInt()
        val gl = RecordingGles(w, h)
        val renderer = Renderer(gl, MeshLibrary())
        renderer.resize(w, h)
        val env = Environment().apply {
            world = 1f
            zenith[0] = 0.50f; zenith[1] = 0.36f; zenith[2] = 0.36f
            horizon[0] = 0.90f; horizon[1] = 0.66f; horizon[2] = 0.46f
            groundCol[0] = 0.62f; groundCol[1] = 0.38f; groundCol[2] = 0.26f
            fogColor[0] = 0.90f; fogColor[1] = 0.66f; fogColor[2] = 0.46f
            sunColor[0] = 1.0f; sunColor[1] = 0.90f; sunColor[2] = 0.78f
            ambSky[0] = 0.60f; ambSky[1] = 0.46f; ambSky[2] = 0.42f
            ambGround[0] = 0.38f; ambGround[1] = 0.24f; ambGround[2] = 0.18f
            setSun(-0.45f, 0.62f, 0.62f)
            terrainOn = true; groundY = 0f; terrainStyle = 0f; terrainAmp = 100f; corridor = 60f
        }
        val cam = Camera().apply { aspect = w.toFloat() / h }
        val mat = Material()
        val m = FloatArray(16)

        val vehicles = ArrayList<Item>()
        val cols = floatArrayOf(-7f, -2.5f, 2f)
        for (k in 0..2) {
            vehicles.add(Item(MeshId.values()[MeshId.ROVER_0.ordinal + k], cols[k], 0.65f, 0f))
            vehicles.add(Item(MeshId.values()[MeshId.HAULER_0.ordinal + k], cols[k], 1.6f, -10f))
            vehicles.add(Item(MeshId.values()[MeshId.CRAWLER_0.ordinal + k], cols[k], 1.1f, -20f))
            // for comparison, the Earth models they replace
            vehicles.add(Item(MeshId.values()[MeshId.SEDAN_0.ordinal + k], cols[k] + 15f, 0.65f, 0f))
            vehicles.add(Item(MeshId.values()[MeshId.TRUCK_0.ordinal + k], cols[k] + 15f, 1.6f, -10f))
            vehicles.add(Item(MeshId.values()[MeshId.VAN_0.ordinal + k], cols[k] + 15f, 1.1f, -20f))
        }

        val air = ArrayList<Item>()
        for (k in 0..3) air.add(Item(MeshId.values()[MeshId.SAUCER_0.ordinal + k], -10f + k * 6.5f, 3f, -16f, ry = 20f * k))
        for (k in 0..2) air.add(Item(MeshId.values()[MeshId.MARS_JET_0.ordinal + k], -8f + k * 8f, 3f, -34f))
        for (k in 0..2) air.add(Item(MeshId.values()[MeshId.JET_0.ordinal + k], -8f + k * 8f, 3f, -52f))
        // the same saucers seen from the side and from below
        for (k in 0..3) air.add(Item(MeshId.values()[MeshId.SAUCER_0.ordinal + k], -10f + k * 6.5f, 9f, -16f, ry = 20f * k))

        val scenery = ArrayList<Item>()
        for (k in 0..2) scenery.add(Item(MeshId.values()[MeshId.MESA_0.ordinal + k], -40f + k * 36f, 0f, -80f, ry = 40f * k, sx = 1f))
        for (k in 0..2) scenery.add(Item(MeshId.values()[MeshId.SPIRE_0.ordinal + k], -14f + k * 12f, 0f, -34f, sx = 3.5f, sy = 22f, sz = 3.5f))
        scenery.add(Item(MeshId.DEVIL, 24f, 0f, -50f))
        scenery.add(Item(MeshId.DOME, -28f, 0f, -36f, ry = 25f))
        for (k in 0..2) scenery.add(Item(MeshId.values()[MeshId.ROCK_0.ordinal + k], 8f + k * 5f, 0f, -14f, sx = 2f, tint = floatArrayOf(1.25f, 0.62f, 0.45f)))

        val bodies = listOf(
            Item(MeshId.MARS, -150f, 20f, -420f, sx = 140f),
            Item(MeshId.PHOBOS, 90f, 20f, -420f, sx = 120f),
            Item(MeshId.EARTH, -150f, -150f, -420f, sx = 90f),
            Item(MeshId.MOON, 90f, -150f, -420f, sx = 90f),
        )

        val views = listOf(
            View("vehicles", floatArrayOf(5f, 4.6f, 14f), floatArrayOf(5f, 1.2f, -12f), 56f, vehicles),
            View("vehicles_side", floatArrayOf(30f, 3.2f, -10f), floatArrayOf(-2f, 1.2f, -10f), 40f, vehicles.filter { it.x < 8f }),
            View("air", floatArrayOf(2f, 5f, 8f), floatArrayOf(0f, 3f, -34f), 56f, air, terrain = false),
            View("scenery", floatArrayOf(0f, 7f, 8f), floatArrayOf(0f, 7f, -45f), 62f, scenery),
            View("scenery_far", floatArrayOf(-4f, 5f, 18f), floatArrayOf(-4f, 8f, -80f), 58f, scenery),
            View("bodies", floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, -40f, -420f), 62f, bodies, terrain = false, fog = 0f),
        )

        for (v in views) {
            cam.ex = v.eye[0]; cam.ey = v.eye[1]; cam.ez = v.eye[2]
            cam.tx = v.target[0]; cam.ty = v.target[1]; cam.tz = v.target[2]
            cam.fovY = v.fov
            cam.far = 3000f
            cam.update()
            env.fogDensity = v.fog
            env.terrainOn = v.terrain
            renderer.beginFrame(env, cam)
            if (v.terrain) renderer.drawTerrain(0.0, 10f, 64, 80, 8, env.terrainTint)
            for (it in v.items) {
                mat.reset().gloss(0.3f, 30f)
                if (it.tint != null) mat.tint(it.tint[0], it.tint[1], it.tint[2])
                mat.emissive = it.emissive
                Mat4.setTrs(m, it.x, it.y, it.z, 0f, it.ry, 0f, it.sx, it.sy, it.sz)
                renderer.drawLit(renderer.meshes[it.id], m, mat)
            }
            renderer.endFrame()
            gl.endFrame()
        }
        val dir = File(System.getProperty("traceDir") ?: "build/traces")
        gl.writeTo(File(dir, "gallery.bin"))
        File(dir, "gallery.txt").writeText(views.joinToString("\n") { it.name })
    }
}
