package app.roadtoorbit.gfx

import app.roadtoorbit.math.Mat4
import app.roadtoorbit.trace.RecordingGles
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What can be checked cheaply about the ocean skin of the vehicle: the part table, the new models and what gets drawn. */
class OceanVehicleTest {
    private val modes = VehicleMode.values()

    private fun visible(p: Pose) = max(p.sx, max(p.sy, p.sz)) > 0.01f
    private fun hiddenIn(p: Pose) = max(p.sx, max(p.sy, p.sz)) <= 0.002f

    @Test fun everyPoseIsFinite() {
        for (part in VehicleModel.oceanParts) for (m in modes) {
            val p = part.pose(m)
            for (v in floatArrayOf(p.x, p.y, p.z, p.rx, p.ry, p.rz, p.sx, p.sy, p.sz)) assertTrue(v.isFinite(), "${part.mesh} $m has a non-finite pose value")
            assertTrue(p.sx > 0f && p.sy > 0f && p.sz > 0f, "${part.mesh} $m has a non-positive scale")
        }
        for (b in VehicleModel.oceanBeams) for (m in modes) {
            val p = b.pose(m)
            for (v in floatArrayOf(p.x, p.y, p.z, p.rx, p.ry, p.rz, p.sx, p.sy, p.sz)) assertTrue(v.isFinite())
        }
        for (p in VehicleModel.oceanFlamePose) assertTrue(p.z.isFinite() && p.sx > 0f && p.sz > 0f)
    }

    @Test fun everyPartReachesItsEndPoseAtFullMorph() {
        for (part in VehicleModel.oceanParts) {
            assertTrue(part.delay <= 0.35f, "${part.mesh} starts too late (${part.delay}) to arrive at morph 1")
            assertEquals(0f, VehicleModel.partProgress(part, 0f), 1e-5f)
            assertEquals(1f, VehicleModel.partProgress(part, 1f), 1e-3f)
        }
        for (b in VehicleModel.oceanBeams) assertEquals(1f, VehicleModel.partProgress(b.lamp, 1f), 1e-3f)
    }

    @Test fun hiddenPartsAreReallyHiddenAndTheRestIsNot() {
        for (part in VehicleModel.oceanParts) {
            var shown = 0
            for (m in modes) {
                val p = part.pose(m)
                if (visible(p)) {
                    shown++
                    assertTrue(minOf(p.sx, p.sy, p.sz) >= 0.02f, "${part.mesh} is half hidden in $m")
                } else {
                    assertTrue(hiddenIn(p), "${part.mesh} is neither shown nor hidden in $m")
                }
            }
            assertTrue(shown >= 1, "${part.mesh} is never shown")
        }
        fun shownIn(mesh: MeshId, m: VehicleMode) = VehicleModel.oceanParts.filter { it.mesh == mesh }.all { visible(it.pose(m)) }
        fun hiddenAlways(mesh: MeshId, vararg ms: VehicleMode) = ms.forEach { m -> assertTrue(VehicleModel.oceanParts.filter { it.mesh == mesh }.all { hiddenIn(it.pose(m)) }, "$mesh should be hidden in $m") }
        // the waterplane's pontoons and wings, its propeller: only in the plane
        hiddenAlways(MeshId.PONTOON, VehicleMode.CAR, VehicleMode.ROCKET)
        hiddenAlways(MeshId.PROP, VehicleMode.CAR, VehicleMode.ROCKET)
        assertTrue(shownIn(MeshId.PONTOON, VehicleMode.PLANE) && shownIn(MeshId.PROP, VehicleMode.PLANE))
        // the submarine's tower, portholes and nozzle: only in the rocket
        for (mesh in listOf(MeshId.TOWER, MeshId.PORTHOLE, MeshId.NOZZLE)) {
            hiddenAlways(mesh, VehicleMode.CAR, VehicleMode.PLANE)
            assertTrue(shownIn(mesh, VehicleMode.ROCKET), "$mesh should be shown on the submarine")
        }
        // the periscope is the car's snorkel too, but the waterplane has none
        hiddenAlways(MeshId.PERISCOPE, VehicleMode.PLANE)
        assertTrue(shownIn(MeshId.PERISCOPE, VehicleMode.ROCKET) && shownIn(MeshId.PERISCOPE, VehicleMode.CAR))
        // the searchlights: one at the submarine's nose, two on the car, none on the waterplane
        val lamps = VehicleModel.oceanParts.filter { it.mesh == MeshId.SEARCHLIGHT }
        assertEquals(2, lamps.size)
        assertTrue(lamps.all { hiddenIn(it.plane) && visible(it.car) })
        assertEquals(1, lamps.count { visible(it.rocket) })
        // one propeller, and it is the only part with that role
        assertEquals(1, VehicleModel.oceanParts.count { it.role == PartRole.PROPELLER })
        // the beams belong to the lamps and shine in the right forms
        assertEquals(2, VehicleModel.oceanBeams.size)
        for (b in VehicleModel.oceanBeams) assertEquals(0f, Col.a(b.plane.tint), 1e-3f)
        assertEquals(1f, Col.a(VehicleModel.oceanBeams[0].rocket.tint), 1e-3f)
        assertEquals(0f, Col.a(VehicleModel.oceanBeams[1].rocket.tint), 1e-3f)
        assertTrue(VehicleModel.oceanBeams.all { Col.a(it.car.tint) > 0.99f })
    }

    @Test fun pontoonsStayUnderTheWingspan() {
        for (part in VehicleModel.oceanParts.filter { it.mesh == MeshId.PONTOON }) {
            val p = part.plane
            assertTrue(abs(p.x) + p.sx / 2f <= 3.1f, "span of ${part.mesh}")
            if (p.sz < 3.5f) assertTrue(abs(p.x) + p.sx / 2f <= 1.8f, "the pontoons stay within 1.8 m of the centre line")
            assertTrue(p.z - p.sz / 2f > -2.9f && p.z + p.sz / 2f < 2.9f, "body length")
        }
    }

    @Test fun standardTableIsUnchanged() {
        val parts = VehicleModel.parts
        assertEquals(20, parts.size)
        assertEquals(-0.98f, parts[0].car.x)
        assertEquals(5.2f, parts[0].rocket.sx)
        assertEquals(MeshId.FIN, parts[8].mesh)
        assertEquals(86f, parts[8].plane.rz)
        assertEquals(2.7f, parts[8].plane.sy)
        assertEquals(MeshId.CANOPY, parts[14].mesh)
        assertEquals(0.28f, parts[14].car.z)
        assertEquals(3.55f, parts[15].rocket.z)
        assertEquals(MeshId.NOZZLE, parts[15].mesh)
        assertEquals(4.25f, VehicleModel.flamePose[2].z)
        assertTrue(parts.all { it.role != PartRole.PROPELLER })
        assertTrue(VehicleModel.partsFor(Skin.STANDARD) === parts)
        assertTrue(VehicleModel.partsFor(Skin.OCEAN) === VehicleModel.oceanParts)
        assertTrue(VehicleModel.oceanParts.size != parts.size)
    }

    @Test fun oceanBodyStaysWellFormedThroughEveryMorph() {
        val out = MeshData(VehicleModel.maxBodyVertices + 64)
        for (from in modes) for (to in modes) for (step in 0..10) {
            VehicleModel.buildBody(out, from, to, step / 10f, Skin.OCEAN)
            assertTrue(out.count > 0 && out.count <= VehicleModel.maxBodyVertices)
            for (v in 0 until out.count * 3) assertTrue(out.pos[v].isFinite() && out.nrm[v].isFinite())
            for (v in 0 until out.count) {
                val l = sqrt(out.nrm[v * 3] * out.nrm[v * 3] + out.nrm[v * 3 + 1] * out.nrm[v * 3 + 1] + out.nrm[v * 3 + 2] * out.nrm[v * 3 + 2])
                assertTrue(abs(l - 1f) < 1e-3f, "normal length $l for $from->$to")
            }
        }
    }

    // ---- the new models -------------------------------------------------------------------------

    private fun mesh(id: MeshId): MeshData = MeshLibrary().data(id)

    private fun checkBudget(id: MeshId, d: MeshData) {
        assertTrue(d.triangleCount in 60..3000, "$id has ${d.triangleCount} triangles")
        for (i in 0 until d.count * 3) assertTrue(d.pos[i].isFinite() && d.nrm[i].isFinite(), "$id has a non-finite vertex")
        assertTrue((0 until d.count).none { d.col[it] == 0xFFFF00FF.toInt() }, "$id is still the magenta placeholder")
    }

    @Test fun modelsAreWithinTheirBudgets() {
        for (id in listOf(MeshId.PONTOON, MeshId.PROP, MeshId.TOWER, MeshId.PERISCOPE, MeshId.PORTHOLE, MeshId.SEARCHLIGHT, MeshId.LIGHT_CONE)) checkBudget(id, mesh(id))
    }

    @Test fun pontoonIsAUnitFloatWithItsBowAtMinusZ() {
        val d = mesh(MeshId.PONTOON)
        val b = d.bounds()
        for (k in 0 until 3) {
            assertEquals(-0.5f, b[k], 0.02f); assertEquals(0.5f, b[3 + k], 0.02f)
        }
        // the bow tip (the lowest z) is higher than the middle of the float: a little upturned
        var bowY = 0f; var n = 0
        for (v in 0 until d.count) if (d.pos[v * 3 + 2] < -0.49f) { bowY += d.pos[v * 3 + 1]; n++ }
        assertTrue(n > 0 && bowY / n > 0.05f, "the bow tip should curve up")
    }

    @Test fun propIsAUnitDiscAlongZ() {
        val d = mesh(MeshId.PROP)
        val b = d.bounds()
        var radius = 0f
        for (v in 0 until d.count) radius = max(radius, sqrt(d.pos[v * 3] * d.pos[v * 3] + d.pos[v * 3 + 1] * d.pos[v * 3 + 1]))
        assertEquals(1f, radius, 0.02f) // the blade tips are on the unit circle in XY
        assertTrue(b[2] > -0.45f && b[5] < 0.25f, "hub and spinner stay close to the plane of the blades")
    }

    @Test fun towerStandsOnTheFloor() {
        val b = mesh(MeshId.TOWER).bounds()
        assertEquals(0f, b[1], 0.01f); assertEquals(1f, b[4], 0.01f)
        assertTrue(b[3] <= 0.26f && b[0] >= -0.26f && b[5] <= 0.81f && b[2] >= -0.81f)
        assertTrue(b[5] > 0.75f && b[2] < -0.75f)
        assertEquals(0f, (b[0] + b[3]) / 2f, 0.02f); assertEquals(0f, (b[2] + b[5]) / 2f, 0.05f)
    }

    @Test fun periscopeIsAOneMetreTubeWithAHeadLookingForward() {
        val b = mesh(MeshId.PERISCOPE).bounds()
        assertEquals(0f, b[1], 0.01f); assertEquals(1f, b[4], 0.03f)
        assertTrue(b[3] < 0.13f && b[0] > -0.13f, "thin")
        assertTrue(-b[2] > b[5], "the head sticks out toward -Z")
    }

    @Test fun portholeAndSearchlightAreRoundAndFaceMinusZ() {
        for ((id, rMin, rMax) in listOf(Triple(MeshId.PORTHOLE, 0.5f, 0.64f), Triple(MeshId.SEARCHLIGHT, 0.48f, 0.54f))) {
            val d = mesh(id)
            val b = d.bounds()
            assertTrue(b[3] in rMin..rMax && b[4] in rMin..rMax && b[0] in -rMax..-rMin && b[1] in -rMax..-rMin, "$id radius ${b[3]}")
            assertTrue(b[2] < 0f && b[5] > 0.1f && b[5] - b[2] < 0.7f, "$id is a short drum")
            // the vertices at the very front are on the face (inside the rim), the rim itself is on the outside
            var frontR = 0f
            for (v in 0 until d.count) if (d.pos[v * 3 + 2] < b[2] + 0.01f) frontR = max(frontR, sqrt(d.pos[v * 3] * d.pos[v * 3] + d.pos[v * 3 + 1] * d.pos[v * 3 + 1]))
            assertTrue(frontR > 0.3f, "$id faces -Z")
        }
    }

    @Test fun lightConeFadesFromTheTip() {
        val d = mesh(MeshId.LIGHT_CONE)
        val b = d.bounds()
        assertEquals(-1f, b[2], 1e-3f); assertEquals(0f, b[5], 1e-3f)
        assertEquals(0.35f, b[3], 0.005f); assertEquals(-0.35f, b[0], 0.005f)
        var tipAlpha = 0f; var farMax = 0f
        for (v in 0 until d.count) {
            val z = d.pos[v * 3 + 2]
            val a = Col.a(d.col[v])
            val r = sqrt(d.pos[v * 3] * d.pos[v * 3] + d.pos[v * 3 + 1] * d.pos[v * 3 + 1])
            assertEquals(0.35f * -z, r, 0.01f) // a cone
            assertTrue(a <= 0.56f)
            if (z > -1e-4f) tipAlpha = max(tipAlpha, a)
            if (z < -0.9999f) farMax = max(farMax, a)
        }
        assertEquals(0.55f, tipAlpha, 0.02f)
        assertEquals(0f, farMax, 1e-3f)
    }

    // ---- what is drawn --------------------------------------------------------------------------

    /** Draws one frame of the vehicle and returns (draw calls, the recorded command stream). */
    private fun frame(skin: Skin, from: VehicleMode, to: VehicleMode, t: Float, beam: Float): Pair<Int, ByteArray> {
        val gl = RecordingGles(320, 200)
        val renderer = Renderer(gl, MeshLibrary())
        renderer.resize(320, 200)
        val vehicle = VehicleRenderer(gl, renderer)
        val v = VehicleVisual().also { it.skin = skin; it.from = from; it.to = to; it.morph = t; it.beam = beam; it.flame = 0f; it.time = 1.3f }
        val cam = Camera().apply { ex = 0f; ey = 3.5f; ez = 10f; tx = 0f; ty = 0.5f; tz = -25f; update() }
        val m = FloatArray(16)
        Mat4.identity(m)
        renderer.beginFrame(Environment().apply { terrainOn = false }, cam)
        vehicle.draw(v, m)
        renderer.endFrame()
        gl.endFrame()
        val file = File.createTempFile("vehicle", ".bin")
        try {
            gl.writeTo(file)
            return gl.drawCalls to file.readBytes()
        } finally {
            file.delete()
        }
    }

    @Test fun theStandardSkinIgnoresTheBeam() {
        for (m in modes) {
            val dark = frame(Skin.STANDARD, m, m, 0f, 0f)
            val lit = frame(Skin.STANDARD, m, m, 0f, 1f)
            assertEquals(dark.first, lit.first)
            assertTrue(dark.second.contentEquals(lit.second), "the standard $m draws differently when the beam is on")
        }
    }

    @Test fun theOceanSkinThrowsBeamsOnlyWhenItIsDark() {
        val cones = mapOf(VehicleMode.CAR to 2, VehicleMode.PLANE to 0, VehicleMode.ROCKET to 1)
        for ((m, n) in cones) {
            val off = frame(Skin.OCEAN, m, m, 0f, 0f)
            val faint = frame(Skin.OCEAN, m, m, 0f, 0.01f)
            val on = frame(Skin.OCEAN, m, m, 0f, 1f)
            assertEquals(off.first, faint.first, "no beams below the threshold")
            assertEquals(off.first + n, on.first, "$m draws $n beam cones")
        }
    }

    @Test fun theOceanSkinLooksDifferentFromTheStandardOne() {
        for (m in modes) assertTrue(!frame(Skin.STANDARD, m, m, 0f, 0f).second.contentEquals(frame(Skin.OCEAN, m, m, 0f, 0f).second))
    }
}
