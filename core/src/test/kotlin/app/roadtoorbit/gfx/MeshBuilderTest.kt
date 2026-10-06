package app.roadtoorbit.gfx

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeshBuilderTest {
    private val white = Col.WHITE

    /** For convex solids every triangle must face away from the centroid (CCW-outward winding). */
    private fun assertOutwardFacing(m: MeshData, name: String) {
        var cx = 0f; var cy = 0f; var cz = 0f
        for (v in 0 until m.count) { cx += m.pos[v * 3]; cy += m.pos[v * 3 + 1]; cz += m.pos[v * 3 + 2] }
        cx /= m.count; cy /= m.count; cz /= m.count
        var bad = 0
        for (t in 0 until m.triangleCount) {
            val i = t * 9
            val ax = m.pos[i]; val ay = m.pos[i + 1]; val az = m.pos[i + 2]
            val bx = m.pos[i + 3]; val by = m.pos[i + 4]; val bz = m.pos[i + 5]
            val dx = m.pos[i + 6]; val dy = m.pos[i + 7]; val dz = m.pos[i + 8]
            val ux = bx - ax; val uy = by - ay; val uz = bz - az
            val vx = dx - ax; val vy = dy - ay; val vz = dz - az
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            val mx = (ax + bx + dx) / 3 - cx; val my = (ay + by + dy) / 3 - cy; val mz = (az + bz + dz) / 3 - cz
            if (nx * mx + ny * my + nz * mz <= 0f) bad++
        }
        assertEquals(0, bad, "$name: $bad of ${m.triangleCount} triangles wind inward")
    }

    /** Stored vertex normals must agree with the winding and be unit length. */
    private fun assertNormalsConsistent(m: MeshData, name: String) {
        for (v in 0 until m.count) {
            val l = sqrt(m.nrm[v * 3] * m.nrm[v * 3] + m.nrm[v * 3 + 1] * m.nrm[v * 3 + 1] + m.nrm[v * 3 + 2] * m.nrm[v * 3 + 2])
            assertTrue(abs(l - 1f) < 1e-3f, "$name: normal $v not unit length ($l)")
        }
        for (t in 0 until m.triangleCount) {
            val i = t * 9
            val ux = m.pos[i + 3] - m.pos[i]; val uy = m.pos[i + 4] - m.pos[i + 1]; val uz = m.pos[i + 5] - m.pos[i + 2]
            val vx = m.pos[i + 6] - m.pos[i]; val vy = m.pos[i + 7] - m.pos[i + 1]; val vz = m.pos[i + 8] - m.pos[i + 2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            val n = t * 3
            val dot = nx * m.nrm[n * 3] + ny * m.nrm[n * 3 + 1] + nz * m.nrm[n * 3 + 2]
            assertTrue(dot > -1e-6f, "$name: vertex normal opposes face winding on triangle $t")
        }
    }

    @Test fun boxFacesOutward() {
        val m = MeshBuilder().box(2f, 3f, 4f, white).build()
        assertEquals(12, m.triangleCount)
        assertOutwardFacing(m, "box"); assertNormalsConsistent(m, "box")
    }

    @Test fun cylinderConeAndSphereFaceOutward() {
        for (smooth in listOf(true, false)) {
            val cyl = MeshBuilder().cylinder(1f, 1f, 2f, 16, white, white, smooth).build()
            assertOutwardFacing(cyl, "cylinder smooth=$smooth"); assertNormalsConsistent(cyl, "cylinder smooth=$smooth")
            val tapered = MeshBuilder().cylinder(0.3f, 1f, 2f, 12, white, white, smooth).build()
            assertOutwardFacing(tapered, "tapered smooth=$smooth"); assertNormalsConsistent(tapered, "tapered smooth=$smooth")
            val cone = MeshBuilder().cone(1f, 2f, 12, white, white, smooth).build()
            assertOutwardFacing(cone, "cone smooth=$smooth"); assertNormalsConsistent(cone, "cone smooth=$smooth")
            val sphere = MeshBuilder().sphere(1f, 8, 14, white, white, smooth).build()
            assertOutwardFacing(sphere, "sphere smooth=$smooth"); assertNormalsConsistent(sphere, "sphere smooth=$smooth")
        }
    }

    @Test fun icosphereIsClosedAndFacesOutward() {
        for (sub in 0..2) {
            val m = MeshBuilder().icosphere(1f, sub, white).build()
            assertEquals(20 * Math.pow(4.0, sub.toDouble()).toInt(), m.triangleCount)
            assertOutwardFacing(m, "icosphere $sub")
        }
        val lumpy = MeshBuilder().icosphere(1f, 2, white, jitter = 0.3f, seed = 4).build()
        assertOutwardFacing(lumpy, "lumpy icosphere")
    }

    @Test fun prismFacesOutward() {
        val poly = floatArrayOf(-0.7f, 0f, 0.9f, 0f, 1.45f, 1f, 0.85f, 1f)
        val m = MeshBuilder().prismZY(poly, 0.1f, white).build()
        assertOutwardFacing(m, "fin prism")
        val ramp = MeshBuilder().prismZY(floatArrayOf(-62f, 0f, 0f, 0f, -62f, 11f), 13f, white).build()
        assertOutwardFacing(ramp, "ramp prism")
    }

    @Test fun torusNormalsPointAwayFromTheTube() {
        val m = MeshBuilder().torus(5f, 0.5f, 24, 8, white).build()
        var bad = 0
        for (t in 0 until m.triangleCount) {
            val i = t * 9
            val cx = (m.pos[i] + m.pos[i + 3] + m.pos[i + 6]) / 3
            val cy = (m.pos[i + 1] + m.pos[i + 4] + m.pos[i + 7]) / 3
            val cz = (m.pos[i + 2] + m.pos[i + 5] + m.pos[i + 8]) / 3
            val len = sqrt(cx * cx + cy * cy)
            val tx = cx / len * 5f; val ty = cy / len * 5f
            val ux = m.pos[i + 3] - m.pos[i]; val uy = m.pos[i + 4] - m.pos[i + 1]; val uz = m.pos[i + 5] - m.pos[i + 2]
            val vx = m.pos[i + 6] - m.pos[i]; val vy = m.pos[i + 7] - m.pos[i + 1]; val vz = m.pos[i + 8] - m.pos[i + 2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            if (nx * (cx - tx) + ny * (cy - ty) + nz * cz <= 0f) bad++
        }
        assertEquals(0, bad)
    }

    @Test fun mirroredTransformsKeepFacesOutward() {
        val m = MeshBuilder().scale(-1f, 1f, 1f).box(2f, 2f, 2f, white).build()
        assertOutwardFacing(m, "mirrored box")
    }

    @Test fun transformStackAppliesPositionsAndNormals() {
        val b = MeshBuilder()
        b.push().translate(10f, 0f, 0f).rotateZ(90f).box(2f, 2f, 2f, white).pop()
        val m = b.build()
        val bounds = m.bounds()
        assertEquals(9f, bounds[0], 1e-4f); assertEquals(11f, bounds[3], 1e-4f)
        assertNormalsConsistent(m, "transformed box")
    }

    @Test fun everyLibraryMeshIsWellFormed() {
        val lib = MeshLibrary()
        for (id in MeshId.values()) {
            val m = lib.data(id)
            assertTrue(m.count > 0 && m.count % 3 == 0, "$id has ${m.count} vertices")
            assertTrue(m.count < 65_000 * 3, "$id too big: ${m.count}")
            for (v in 0 until m.count * 3) assertTrue(m.pos[v].isFinite() && m.nrm[v].isFinite(), "$id has a non-finite value")
            val b = m.bounds()
            assertTrue(b[3] - b[0] < 20000f && b[4] - b[1] < 20000f && b[5] - b[2] < 20000f, "$id has absurd bounds")
        }
    }
}
