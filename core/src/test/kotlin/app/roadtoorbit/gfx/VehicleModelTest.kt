package app.roadtoorbit.gfx

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VehicleModelTest {
    @Test fun bodyStaysWellFormedThroughEveryMorph() {
        val out = MeshData(VehicleModel.maxBodyVertices + 64)
        val modes = VehicleMode.values()
        for (from in modes) for (to in modes) {
            for (step in 0..10) {
                val t = step / 10f
                VehicleModel.buildBody(out, from, to, t)
                assertTrue(out.count > 0 && out.count <= VehicleModel.maxBodyVertices, "vertex count ${out.count}")
                assertEquals(0, out.count % 3)
                for (v in 0 until out.count) {
                    for (k in 0 until 3) {
                        assertTrue(out.pos[v * 3 + k].isFinite(), "non-finite position $from->$to t=$t")
                        assertTrue(out.nrm[v * 3 + k].isFinite(), "non-finite normal $from->$to t=$t")
                    }
                    val l = sqrt(out.nrm[v * 3] * out.nrm[v * 3] + out.nrm[v * 3 + 1] * out.nrm[v * 3 + 1] + out.nrm[v * 3 + 2] * out.nrm[v * 3 + 2])
                    assertTrue(abs(l - 1f) < 1e-3f, "normal length $l for $from->$to t=$t")
                }
            }
        }
    }

    @Test fun formsHaveTheIntendedSilhouettes() {
        val out = MeshData(VehicleModel.maxBodyVertices + 64)
        fun bounds(m: VehicleMode): FloatArray { VehicleModel.buildBody(out, m, m, 0f); return out.bounds() }
        val car = bounds(VehicleMode.CAR)
        val plane = bounds(VehicleMode.PLANE)
        val rocket = bounds(VehicleMode.ROCKET)
        val carLen = car[5] - car[2]; val planeLen = plane[5] - plane[2]; val rocketLen = rocket[5] - rocket[2]
        assertTrue(carLen < planeLen && planeLen < rocketLen, "lengths $carLen < $planeLen < $rocketLen")
        val carWidth = car[3] - car[0]; val rocketWidth = rocket[3] - rocket[0]
        assertTrue(carWidth > rocketWidth, "the car is wider than the rocket tube")
        // the car sits on its wheels: the body floats above the ground
        assertTrue(car[1] > 0.1f)
    }

    @Test fun partProgressIsMonotonicWithStagger() {
        for (part in VehicleModel.parts) {
            assertEquals(0f, VehicleModel.partProgress(part, 0f), 1e-5f)
            assertEquals(1f, VehicleModel.partProgress(part, 1f), 1e-3f)
        }
        // a late part has not started when an early part is well under way
        val early = VehicleModel.parts.first { it.delay < 0.1f }
        val late = VehicleModel.parts.first { it.delay > 0.3f }
        assertTrue(VehicleModel.partProgress(early, 0.3f) > VehicleModel.partProgress(late, 0.3f))
    }
}
