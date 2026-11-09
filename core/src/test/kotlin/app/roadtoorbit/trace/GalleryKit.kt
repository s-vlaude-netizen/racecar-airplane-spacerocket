package app.roadtoorbit.trace

import app.roadtoorbit.gfx.Camera
import app.roadtoorbit.gfx.Environment
import app.roadtoorbit.gfx.Material
import app.roadtoorbit.gfx.MeshId
import app.roadtoorbit.gfx.MeshLibrary
import app.roadtoorbit.gfx.Renderer
import app.roadtoorbit.math.Mat4
import java.io.File

/**
 * A small harness for looking at models: lays [Item]s out in [View]s and records the renderer's GL commands for the
 * headless render check (tools/render-check/render.mjs turns them into PNGs).
 *
 *     GalleryKit.write("sea", views)                                         // test: build/traces/sea.bin and sea.txt (view names)
 *     node tools/render-check/render.mjs core/build/traces/sea.bin out/sea all   // one PNG per view, in the order of sea.txt
 *
 * The size comes from -DtraceW / -DtraceH (default 1200 x 675) and the folder from -DtraceDir (default build/traces), as for
 * the other trace tests. This is a dev tool: it asserts nothing.
 */
object GalleryKit {
    class Item(
        val id: MeshId, val x: Float, val y: Float, val z: Float,
        val ry: Float = 0f, val sx: Float = 1f, val sy: Float = sx, val sz: Float = sx,
        val rx: Float = 0f, val rz: Float = 0f,
        val tint: FloatArray? = null, val emissive: Float = 0f, val spec: Float = 0.3f, val shine: Float = 30f,
        val rim: FloatArray? = null,
    )

    /** What the background and the light look like, roughly as in the ocean level's three situations (and a plain day). */
    enum class Look { DAY, SURFACE, DEEP, SEABED }

    /** A flat floor under the models: a disc of [radius] m at height [y], tinted [color]. */
    class Floor(val y: Float, val radius: Float, val color: FloatArray)

    class View(
        val name: String,
        val eye: FloatArray, val target: FloatArray, val fov: Float,
        val items: List<Item>,
        val look: Look = Look.DAY,
        val floor: Floor? = null,
        val fog: Float? = null,
    )

    fun environment(look: Look): Environment = Environment().apply {
        fun set(a: FloatArray, r: Float, g: Float, b: Float) { a[0] = r; a[1] = g; a[2] = b }
        terrainOn = false
        when (look) {
            Look.DAY -> {
                set(zenith, 0.20f, 0.46f, 0.92f); set(horizon, 0.84f, 0.91f, 0.98f); set(groundCol, 0.60f, 0.68f, 0.74f)
                set(fogColor, 0.84f, 0.91f, 0.98f); fogDensity = 0.0015f
                set(sunColor, 1.0f, 0.95f, 0.86f); set(ambSky, 0.50f, 0.58f, 0.70f); set(ambGround, 0.34f, 0.32f, 0.28f)
                setSun(-0.45f, 0.62f, 0.62f)
            }
            Look.SURFACE -> {
                set(zenith, 0.16f, 0.50f, 0.95f); set(horizon, 0.80f, 0.92f, 0.98f); set(groundCol, 0.10f, 0.42f, 0.62f)
                set(fogColor, 0.74f, 0.88f, 0.96f); fogDensity = 0.0030f
                set(sunColor, 1.0f, 0.95f, 0.84f); set(ambSky, 0.52f, 0.62f, 0.74f); set(ambGround, 0.20f, 0.36f, 0.46f)
                setSun(-0.45f, 0.62f, 0.62f)
            }
            Look.DEEP -> {
                set(zenith, 0.10f, 0.50f, 0.70f); set(horizon, 0.04f, 0.30f, 0.46f); set(groundCol, 0.01f, 0.08f, 0.18f)
                set(fogColor, 0.04f, 0.30f, 0.46f); fogDensity = 0.0055f
                set(sunColor, 0.55f, 0.85f, 0.95f); set(ambSky, 0.20f, 0.46f, 0.60f); set(ambGround, 0.05f, 0.14f, 0.22f)
                setSun(-0.3f, 0.8f, 0.5f)
            }
            Look.SEABED -> {
                set(zenith, 0.02f, 0.10f, 0.22f); set(horizon, 0.02f, 0.14f, 0.26f); set(groundCol, 0.0f, 0.03f, 0.07f)
                set(fogColor, 0.02f, 0.13f, 0.25f); fogDensity = 0.0060f
                set(sunColor, 0.22f, 0.45f, 0.58f); set(ambSky, 0.14f, 0.30f, 0.42f); set(ambGround, 0.04f, 0.09f, 0.14f)
                setSun(-0.3f, 0.8f, 0.5f)
            }
        }
    }

    fun write(name: String, views: List<View>) {
        val w = (System.getProperty("traceW") ?: "1200").toInt()
        val h = (System.getProperty("traceH") ?: "675").toInt()
        val gl = RecordingGles(w, h)
        val renderer = Renderer(gl, MeshLibrary())
        renderer.resize(w, h)
        val cam = Camera().apply { aspect = w.toFloat() / h }
        val mat = Material()
        val m = FloatArray(16)
        for (v in views) {
            val env = environment(v.look)
            if (v.fog != null) env.fogDensity = v.fog
            cam.ex = v.eye[0]; cam.ey = v.eye[1]; cam.ez = v.eye[2]
            cam.tx = v.target[0]; cam.ty = v.target[1]; cam.tz = v.target[2]
            cam.fovY = v.fov
            cam.far = 4000f
            cam.update()
            renderer.beginFrame(env, cam)
            v.floor?.let { f ->
                val k = f.radius / 4.6f
                mat.reset().tint(f.color[0], f.color[1], f.color[2]).gloss(0.1f, 12f)
                Mat4.setTrs(m, 0f, f.y - 0.2f * k * 0.05f, 0f, 0f, 0f, 0f, k, 0.05f, k)
                renderer.drawLit(renderer.meshes[MeshId.PLATFORM], m, mat)
            }
            for (it in v.items) {
                mat.reset().gloss(it.spec, it.shine)
                if (it.tint != null) mat.tint(it.tint[0], it.tint[1], it.tint[2])
                if (it.rim != null) mat.rim(it.rim[0], it.rim[1], it.rim[2], if (it.rim.size > 3) it.rim[3] else 3f)
                mat.emissive = it.emissive
                Mat4.setTrs(m, it.x, it.y, it.z, it.rx, it.ry, it.rz, it.sx, it.sy, it.sz)
                renderer.drawLit(renderer.meshes[it.id], m, mat)
            }
            renderer.endFrame()
            gl.endFrame()
        }
        val dir = File(System.getProperty("traceDir") ?: "build/traces")
        gl.writeTo(File(dir, "$name.bin"))
        File(dir, "$name.txt").writeText(views.joinToString("\n") { it.name })
    }
}
