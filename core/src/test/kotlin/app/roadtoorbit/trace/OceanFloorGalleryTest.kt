package app.roadtoorbit.trace

import app.roadtoorbit.gfx.Camera
import app.roadtoorbit.gfx.Material
import app.roadtoorbit.gfx.MeshId
import app.roadtoorbit.gfx.MeshLibrary
import app.roadtoorbit.gfx.Renderer
import app.roadtoorbit.math.Mat4
import app.roadtoorbit.trace.GalleryKit.Look
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test

/**
 * Not assertions: lays the seabed's models out in the SEABED look (dark teal water, a dim sun, a sandy floor, the sunken highway)
 * and records GL traces for the headless render check, in the views a player would see them from: the chase camera at a few
 * distances, close up and from the side. Run one at a time:
 *
 *     ./gradlew :core:test --tests '*OceanFloorGalleryTest.trafficGallery' -DtraceDir=<dir>
 *     node tools/render-check/render.mjs <dir>/floor_traffic.bin <dir>/traffic all
 *
 * It uses [GalleryKit]'s looks but its own small writer, because the gallery's items have no fog scale and the game draws
 * far things with a lower one.
 */
class OceanFloorGalleryTest {
    /** One draw: a model with its transform and material, the way the game would draw it. */
    private class Part(
        val id: MeshId, val x: Float, val y: Float, val z: Float,
        val ry: Float = 0f, val sx: Float = 1f, val sy: Float = sx, val sz: Float = sx,
        val rx: Float = 0f, val rz: Float = 0f,
        val tint: FloatArray? = null, val emissive: Float = 0f, val spec: Float = 0.3f, val shine: Float = 30f,
        val fogScale: Float = 1f,
    )

    private class Shot(
        val name: String, val eye: FloatArray, val target: FloatArray, val fov: Float, val parts: List<Part>,
        val look: Look = Look.SEABED, val floorRadius: Float = 900f, val floorY: Float = -0.03f, val fog: Float? = null,
    )

    private fun write(name: String, shots: List<Shot>) {
        val w = (System.getProperty("traceW") ?: "1200").toInt()
        val h = (System.getProperty("traceH") ?: "675").toInt()
        val gl = RecordingGles(w, h)
        val renderer = Renderer(gl, MeshLibrary())
        renderer.resize(w, h)
        val cam = Camera().apply { aspect = w.toFloat() / h }
        val mat = Material()
        val m = FloatArray(16)
        for (s in shots) {
            val env = GalleryKit.environment(s.look)
            if (s.fog != null) env.fogDensity = s.fog
            cam.ex = s.eye[0]; cam.ey = s.eye[1]; cam.ez = s.eye[2]
            cam.tx = s.target[0]; cam.ty = s.target[1]; cam.tz = s.target[2]
            cam.fovY = s.fov
            cam.far = 4000f
            cam.update()
            renderer.beginFrame(env, cam)
            if (s.floorRadius > 0f) {
                // the gallery's floor is a squashed platform disc; its top sits 0.01 * (k - 1) above the y it is given
                val k = s.floorRadius / 4.6f
                mat.reset().tint(SAND[0], SAND[1], SAND[2]).gloss(0.1f, 12f)
                Mat4.setTrs(m, 0f, s.floorY + 0.01f * (k - 1f) - 0.2f * k * 0.05f, 0f, 0f, 0f, 0f, k, 0.05f, k)
                renderer.drawLit(renderer.meshes[MeshId.PLATFORM], m, mat)
            }
            for (p in s.parts) {
                mat.reset().gloss(p.spec, p.shine)
                if (p.tint != null) mat.tint(p.tint[0], p.tint[1], p.tint[2])
                mat.emissive = p.emissive
                mat.fogScale = p.fogScale
                Mat4.setTrs(m, p.x, p.y, p.z, p.rx, p.ry, p.rz, p.sx, p.sy, p.sz)
                renderer.drawLit(renderer.meshes[p.id], m, mat)
            }
            renderer.endFrame()
            gl.endFrame()
        }
        val dir = File(System.getProperty("traceDir") ?: "build/traces")
        gl.writeTo(File(dir, "$name.bin"))
        File(dir, "$name.txt").writeText(shots.joinToString("\n") { it.name })
    }

    companion object {
        /** The floor mesh's top is dark blue-grey, so its tint has to lift it a lot to look like sand. */
        private val SAND = floatArrayOf(2.3f, 1.8f, 1.0f)

        /** What I recommend the game draws each family with (tint, emissive, gloss, fog scale): the views use the same. */
        private val WARM = floatArrayOf(1.3f, 1.05f, 0.9f)
        private const val TRAFFIC_EMISSIVE = 0.22f
        private const val TRAFFIC_FOG = 0.6f
        private const val PROP_EMISSIVE = 0.25f
        private const val PROP_FOG = 0.65f
    }

    /** Road segments from z0 (the near edge of the first) down to z1, as the game lays them: 24 m apart, the ordinary road material. */
    private fun roadParts(z0: Float = 24f, z1: Float = -480f, fog: Float = 0.7f): List<Part> {
        val items = ArrayList<Part>()
        var z = z0
        while (z > z1) {
            items += Part(MeshId.ROAD_SEA, 0f, 0f, z, spec = 0.04f, shine = 8f, fogScale = fog)
            items += Part(MeshId.ROAD_SEA_GLOW, 0f, 0f, z, emissive = 1f, spec = 0f, fogScale = fog)
            z -= 24f
        }
        return items
    }

    private fun car(v: Int, x: Float, z: Float, ry: Float = 0f) =
        Part(MeshId.values()[MeshId.SUBCAR_0.ordinal + v], x, 0.03f + 0.65f, z, ry, tint = WARM, spec = 0.45f, shine = 36f, emissive = TRAFFIC_EMISSIVE, fogScale = TRAFFIC_FOG)

    private fun hauler(v: Int, x: Float, z: Float, ry: Float = 0f) =
        Part(MeshId.values()[MeshId.SUBHAULER_0.ordinal + v], x, 0.03f + 1.6f, z, ry, tint = WARM, spec = 0.45f, shine = 36f, emissive = TRAFFIC_EMISSIVE, fogScale = TRAFFIC_FOG)

    private fun van(v: Int, x: Float, z: Float, ry: Float = 0f) =
        Part(MeshId.values()[MeshId.SUBVAN_0.ordinal + v], x, 0.03f + 1.1f, z, ry, tint = WARM, spec = 0.45f, shine = 36f, emissive = TRAFFIC_EMISSIVE, fogScale = TRAFFIC_FOG)

    private fun container(x: Float, z: Float, ry: Float = 0f) =
        Part(MeshId.CONTAINER, x, 0.03f + 0.5f, z, ry, tint = WARM, spec = 0.1f, shine = 12f, emissive = PROP_EMISSIVE, fogScale = PROP_FOG)

    private fun tuft(x: Float, z: Float) = Part(MeshId.KELP_TUFT, x, 0.03f + 0.55f, z, 0f, spec = 0.1f, shine = 10f, emissive = PROP_EMISSIVE, fogScale = PROP_FOG)

    private fun crab(v: Int, x: Float, z: Float, s: Float = 1.2f, ry: Float = 0f) =
        Part(MeshId.values()[MeshId.CRAB_0.ordinal + v], x, 0.03f + s, z, ry, s, tint = WARM, spec = 0.3f, shine = 24f, emissive = PROP_EMISSIVE, fogScale = PROP_FOG)

    private val lanes = floatArrayOf(-4.4f, -1.5f, 1.5f, 4.4f)

    /** The game's chase camera: a few metres behind the player (at z = 0), above and looking ahead. */
    private fun chase(name: String, items: List<Part>, behind: Float = 10f, up: Float = 3.6f, ahead: Float = 28f, fov: Float = 62f, x: Float = 0f) =
        Shot(name, floatArrayOf(x, up, behind), floatArrayOf(x, 0.4f, -ahead), fov, roadParts() + items)

    private fun orbit(name: String, items: List<Part>, cx: Float, cy: Float, cz: Float, dist: Float, yawDeg: Float, pitchDeg: Float, fov: Float = 40f, withRoad: Boolean = false): Shot {
        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pit = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val eye = floatArrayOf(cx + dist * sin(yaw) * cos(pit), cy + dist * sin(pit), cz + dist * cos(yaw) * cos(pit))
        return Shot(name, eye, floatArrayOf(cx, cy, cz), fov, (if (withRoad) roadParts(z0 = cz + 60f, z1 = cz - 80f) else emptyList()) + items)
    }

    @Test
    fun trafficGallery() {
        val v = ArrayList<Shot>()
        // close up: each family in its three paints, from the front, the rear and the side
        for ((fam, ys) in listOf("car" to 0.65f, "hauler" to 1.6f, "van" to 1.1f)) {
            val items = (0..2).map { k ->
                val x = (k - 1) * 4.6f
                when (fam) { "car" -> car(k, x, 0f); "hauler" -> hauler(k, x * 1.3f, 0f); else -> van(k, x * 1.15f, 0f) }
            }
            val d = when (fam) { "hauler" -> 19f; "van" -> 15.5f; else -> 12.5f }
            v += orbit("${fam}_front", items, 0f, ys, 0f, d, 152f, 16f, 46f)
            v += orbit("${fam}_rear", items, 0f, ys, 0f, d, -28f, 22f, 46f)
            v += orbit("${fam}_side", items.take(1), 0f, ys, 0f, 11f, 90f, 6f, 40f)
        }
        // from the chase camera: 20, 60 and 150 m ahead of the player (the camera sits 10 m behind him)
        for (dist in intArrayOf(20, 60, 150)) {
            val z = -dist.toFloat()
            v += chase("chase_${dist}_a", listOf(car(0, lanes[0], z), hauler(1, lanes[1], z), van(2, lanes[2], z), container(lanes[3], z)))
            v += chase("chase_${dist}_b", listOf(car(1, lanes[0], z), hauler(2, lanes[1], z), van(0, lanes[2], z), crab(0, lanes[3], z)))
            v += chase("chase_${dist}_c", listOf(car(2, lanes[0], z), hauler(0, lanes[1], z), van(1, lanes[2], z), crab(1, lanes[3], z)))
        }
        write("floor_traffic", v)
    }

    @Test
    fun hazardsGallery() {
        val v = ArrayList<Shot>()
        val all = listOf(container(-5f, 0f), tuft(-2.2f, 0f), crab(0, 2f, 0f, 1.0f), crab(1, 6.2f, 0f, 1.0f))
        v += orbit("hazards_rear", all, 0.5f, 0.6f, 0f, 20f, -20f, 20f, 44f)
        v += orbit("hazards_front", all, 0.5f, 0.6f, 0f, 20f, 165f, 24f, 44f)
        v += orbit("container_front", listOf(container(0f, 0f)), 0f, 0.55f, 0f, 6.5f, 150f, 22f, 40f)
        v += orbit("container_rear", listOf(container(0f, 0f)), 0f, 0.55f, 0f, 6.5f, -35f, 18f, 40f)
        v += orbit("tuft_front", listOf(tuft(0f, 0f)), 0f, 0.55f, 0f, 3.8f, 150f, 14f, 40f)
        v += orbit("tuft_side", listOf(tuft(0f, 0f)), 0f, 0.55f, 0f, 3.8f, 90f, 4f, 40f)
        v += orbit("crab0_top", listOf(crab(0, 0f, 0f, 1f)), 0f, 0.6f, 0f, 7f, 5f, 52f, 40f)
        v += orbit("crab0_front", listOf(crab(0, 0f, 0f, 1f)), 0f, 0.6f, 0f, 7f, 155f, 24f, 40f)
        v += orbit("crab1_top", listOf(crab(1, 0f, 0f, 1f)), 0f, 0.6f, 0f, 7f, 5f, 52f, 40f)
        v += orbit("crab1_front", listOf(crab(1, 0f, 0f, 1f)), 0f, 0.6f, 0f, 7f, 155f, 24f, 40f)
        for (dist in intArrayOf(20, 60, 150)) {
            val z = -dist.toFloat()
            v += chase("hazard_chase_$dist", listOf(container(lanes[0], z), tuft(lanes[1], z), crab(0, lanes[2], z), crab(1, lanes[3], z)))
        }
        write("floor_hazards", v)
    }

    @Test
    fun roadGallery() {
        val v = ArrayList<Shot>()
        v += chase("road_chase", emptyList())
        v += chase("road_chase_high", emptyList(), behind = 13f, up = 4f)
        v += orbit("road_top", emptyList(), 0f, 0f, -12f, 20f, 0f, 70f, 55f, withRoad = true)
        v += orbit("road_side", emptyList(), 0f, 1f, -12f, 14f, 70f, 12f, 55f, withRoad = true)
        v += orbit("road_rail", emptyList(), 7f, 0.5f, -12f, 7f, 55f, 20f, 50f, withRoad = true)
        v += orbit("road_lamp", emptyList(), -7f, 5f, -12f, 12f, 40f, 12f, 50f, withRoad = true)
        // the seams: look along the road at ground level
        v += Shot("road_seam", floatArrayOf(2f, 1.2f, 6f), floatArrayOf(0f, 0.2f, -40f), 55f, roadParts())
        v += chase("road_far", emptyList(), ahead = 60f)
        write("floor_road", v)
    }

    private fun decor(id: MeshId, x: Float, z: Float, ry: Float = 0f) =
        Part(id, x, 0f, z, ry, tint = WARM, spec = 0.15f, shine = 14f, emissive = 0.2f, fogScale = 0.7f)

    private fun city() = listOf(
        Part(MeshId.ATLANTIS, 0f, 0f, 0f, tint = floatArrayOf(1.1f, 1.05f, 1f), spec = 0.35f, shine = 30f, emissive = 0.3f, fogScale = 0.3f),
        Part(MeshId.ATLANTIS_LIGHTS, 0f, 0f, 0f, emissive = 1f, spec = 0f, fogScale = 0.3f),
    )

    private fun gate(z: Float) = Part(MeshId.ATLANTIS_GATE, 0f, 0f, z, tint = floatArrayOf(1.1f, 1.05f, 1f), spec = 0.3f, shine = 30f, emissive = 0.35f, fogScale = 0.4f)

    /** Atlantis from 400 m, 150 m and from the road 40 m before its gate (the wall's opening is at z = +165), and the gate close up. */
    @Test
    fun cityGallery() {
        val v = ArrayList<Shot>()
        v += Shot("city_400", floatArrayOf(0f, 14f, 565f), floatArrayOf(0f, 38f, 0f), 60f, city() + gate(165f) + roadParts(z0 = 700f, z1 = 165f))
        v += Shot("city_150", floatArrayOf(0f, 10f, 315f), floatArrayOf(0f, 36f, 0f), 62f, city() + gate(165f) + roadParts(z0 = 500f, z1 = 165f))
        v += Shot("city_road_40", floatArrayOf(0f, 3.6f, 215f), floatArrayOf(0f, 14f, 100f), 64f, city() + gate(165f) + roadParts(z0 = 357f, z1 = 165f))
        v += Shot("gate_close", floatArrayOf(14f, 5f, 200f), floatArrayOf(0f, 15f, 165f), 56f, listOf(gate(165f)) + roadParts(z0 = 261f, z1 = 165f))
        write("floor_city", v)
    }

    /** The vent, the wrecks and the ruins, and the traffic and crabs on the road once more. */
    @Test
    fun decorGallery() {
        val v = ArrayList<Shot>()
        v += Shot("decor_wrecks", floatArrayOf(0f, 16f, 80f), floatArrayOf(0f, 6f, 0f), 58f,
            listOf(decor(MeshId.WRECK_0, -22f, -6f), decor(MeshId.WRECK_1, 22f, 0f, 20f), decor(MeshId.VENT, 0f, 32f)))
        v += Shot("decor_ruins", floatArrayOf(0f, 12f, 62f), floatArrayOf(0f, 6f, 0f), 58f,
            listOf(decor(MeshId.RUINS_0, -26f, 0f), decor(MeshId.RUINS_1, 0f, -4f), decor(MeshId.RUINS_2, 24f, 0f)))
        val z = -20f
        v += chase("chase_20_all", listOf(car(0, lanes[0], z), hauler(1, lanes[1], z), van(2, lanes[2], z), container(lanes[3], z), crab(0, lanes[3], z - 14f), crab(1, lanes[0], z - 16f), tuft(lanes[1], z - 12f)))
        v += orbit("crabs_close", listOf(crab(0, -2.4f, 0f, 1.0f), crab(1, 2.4f, 0f, 1.0f)), 0f, 0.6f, 0f, 9f, 8f, 40f, 40f)
        write("floor_decor", v)
    }
}
