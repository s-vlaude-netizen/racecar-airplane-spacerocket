package app.roadtoorbit.gfx

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The seabed's models stand in for models with collision boxes of their own (the traffic, the barrier, the soft obstacle),
 * so what the player sees has to match what it can hit, and the road has to repeat seamlessly.
 */
class OceanFloorMeshTest {
    private val lib = MeshLibrary()

    private fun id(first: MeshId, k: Int) = MeshId.values()[first.ordinal + k]

    private fun tris(id: MeshId) = lib.data(id).triangleCount

    /** The same check as MarsMeshTest.check: the model is centred on its hit box and stands on its bottom. */
    private fun check(id: MeshId, half: FloatArray) {
        val hx = half[0]; val hy = half[1]; val hz = half[2]
        val b = lib.data(id).bounds() // minX, minY, minZ, maxX, maxY, maxZ
        val halfW = (b[3] - b[0]) / 2f
        val halfL = (b[5] - b[2]) / 2f
        assertTrue(abs(halfW - hx) < 0.2f, "$id is ${halfW * 2} m wide, its hit box ${hx * 2} m")
        assertTrue(abs(halfL - hz) < 0.25f, "$id is ${halfL * 2} m long, its hit box ${hz * 2} m")
        assertTrue(abs(b[0] + b[3]) < 0.4f && abs(b[2] + b[5]) < 0.4f, "$id is not centred on its hit box: x ${b[0]}..${b[3]}, z ${b[2]}..${b[5]}")
        assertTrue(b[1] > -hy - 0.05f && b[1] < -hy + 0.12f, "$id should stand on the ground (the box bottom, ${-hy}), its lowest point is ${b[1]}")
        assertTrue(b[4] < hy + 0.95f, "$id rises to ${b[4]}, far above its hit box top $hy")
    }

    @Test fun trafficFitsTheCollisionBoxes() {
        for (k in 0..2) {
            check(id(MeshId.SUBCAR_0, k), OceanSpec.SUBCAR_HALF)
            check(id(MeshId.SUBHAULER_0, k), OceanSpec.SUBHAULER_HALF)
            check(id(MeshId.SUBVAN_0, k), OceanSpec.SUBVAN_HALF)
            for (family in listOf(MeshId.SUBCAR_0, MeshId.SUBHAULER_0, MeshId.SUBVAN_0)) {
                val t = tris(id(family, k))
                assertTrue(t in 200..3000, "${id(family, k)} has $t triangles")
            }
        }
    }

    @Test fun thePaintsOfOneFamilyDiffer() {
        for (family in listOf(MeshId.SUBCAR_0, MeshId.SUBHAULER_0, MeshId.SUBVAN_0)) {
            val a = lib.data(id(family, 0)); val b = lib.data(id(family, 1)); val c = lib.data(id(family, 2))
            assertTrue(a.count == b.count && b.count == c.count, "$family: the paints should only change colours")
            assertTrue(!a.col.copyOf(a.count).contentEquals(b.col.copyOf(b.count)), "$family: variants 0 and 1 are painted alike")
            assertTrue(!b.col.copyOf(b.count).contentEquals(c.col.copyOf(c.count)), "$family: variants 1 and 2 are painted alike")
        }
    }

    @Test fun containerAndTuftSitInTheirBoxes() {
        val c = lib.data(MeshId.CONTAINER).bounds()
        val h = OceanSpec.CONTAINER_HALF
        assertTrue(c[0] >= -h[0] - 0.03f && c[3] <= h[0] + 0.03f, "container x ${c[0]}..${c[3]}")
        assertTrue(c[1] >= -h[1] - 0.03f && c[4] <= h[1] + 0.03f, "container y ${c[1]}..${c[4]}")
        assertTrue(c[2] >= -h[2] - 0.03f && c[5] <= h[2] + 0.03f, "container z ${c[2]}..${c[5]}")
        assertTrue(c[3] - c[0] > 2.6f && c[4] - c[1] > 0.85f, "the container should fill its box: ${c.toList()}")
        val t = lib.data(MeshId.KELP_TUFT).bounds()
        assertTrue(abs(t[0]) <= 0.6f && abs(t[3]) <= 0.6f && abs(t[2]) <= 0.6f && abs(t[5]) <= 0.6f, "the tuft strays from its origin: ${t.toList()}")
        assertTrue(t[1] >= -OceanSpec.TUFT_RADIUS - 0.05f && t[1] <= -OceanSpec.TUFT_RADIUS + 0.12f, "the tuft's bottom is ${t[1]}")
        assertTrue(t[4] > 0.4f && t[4] < 0.62f, "the tuft's top is ${t[4]}")
        assertTrue(tris(MeshId.KELP_TUFT) >= 150 && tris(MeshId.CONTAINER) >= 200, "tuft ${tris(MeshId.KELP_TUFT)}, container ${tris(MeshId.CONTAINER)} triangles")
    }

    @Test fun crabsAreUnitModelsAboutOneMetreAcross() {
        for (k in 0..1) {
            val id = id(MeshId.CRAB_0, k)
            val b = lib.data(id).bounds()
            assertTrue(b[1] > -1.05f && b[1] < -0.85f, "$id: the feet should reach y = -0.95, they reach ${b[1]}")
            assertTrue(b[4] > 0.4f && b[4] < 0.9f, "$id rises to ${b[4]}")
            assertTrue(b[2] < -1.2f && b[2] > -2.8f, "$id: the claws should point toward -z, they reach ${b[2]}")
            assertTrue(b[3] - b[0] > b[5] - b[2], "$id: wider than long: x ${b[0]}..${b[3]}, z ${b[2]}..${b[5]}")
            assertTrue(b[3] < 3.0f && b[0] > -3.0f, "$id: the legs spread too far, x ${b[0]}..${b[3]}")
            // the shell itself: the vertices near the middle of the model are about a metre from the centre
            val m = lib.data(id)
            var widest = 0f
            for (v in 0 until m.count) {
                val y = m.pos[v * 3 + 1]
                if (y > -0.15f && y < 0.3f && abs(m.pos[v * 3 + 2]) < 0.25f) widest = maxOf(widest, abs(m.pos[v * 3]))
            }
            assertTrue(widest > 0.7f, "$id: the shell is narrower than about 1 m ($widest)")
        }
    }

    @Test fun theRoadKeepsItsFootprintAndRepeatsSeamlessly() {
        for (id in listOf(MeshId.ROAD_SEA, MeshId.ROAD_SEA_GLOW)) {
            val b = lib.data(id).bounds()
            assertTrue(b[2] >= -24.001f && b[5] <= 0.001f, "$id leaves its 24 m: z ${b[2]}..${b[5]}")
            assertTrue(b[0] >= -10.2f && b[3] <= 10.2f, "$id is wider than the road: x ${b[0]}..${b[3]}")
            assertTrue(b[4] < 8f && b[1] > -0.1f, "$id: heights ${b[1]}..${b[4]}")
            assertTrue(tris(id) in 200..3000, "$id has ${tris(id)} triangles")
        }
        val road = lib.data(MeshId.ROAD_SEA).bounds()
        assertTrue(road[5] > -0.01f && road[2] < -23.99f && road[0] < -6.4f && road[3] > 6.4f, "the road should fill its 13 m x 24 m: ${road.toList()}")
        // the glowing parts lie above the road's surface (0.03) and its drifts (0.052)
        val glow = lib.data(MeshId.ROAD_SEA_GLOW)
        var lowest = Float.MAX_VALUE
        for (v in 0 until glow.count) lowest = minOf(lowest, glow.pos[v * 3 + 1])
        assertTrue(lowest >= 0.048f, "a glowing part lies in the road: y = $lowest")
    }

    @Test fun sceneryStandsOnTheSeabed() {
        val limits = mapOf(
            MeshId.VENT to (5f to 8.2f),
            MeshId.WRECK_0 to (8f to 14f), MeshId.WRECK_1 to (8f to 14f),
            MeshId.RUINS_0 to (6f to 14f), MeshId.RUINS_1 to (6f to 14f), MeshId.RUINS_2 to (6f to 14f),
            MeshId.ATLANTIS to (90f to 105f), MeshId.ATLANTIS_GATE to (24f to 25.01f),
        )
        for ((id, range) in limits) {
            val b = lib.data(id).bounds()
            assertTrue(b[1] > -0.1f && b[1] < 0.3f, "$id should stand on y = 0, its lowest point is ${b[1]}")
            assertTrue(b[4] >= range.first && b[4] <= range.second, "$id is ${b[4]} m high, expected ${range.first}..${range.second}")
            assertTrue(tris(id) in 200..6000 || id == MeshId.ATLANTIS, "$id has ${tris(id)} triangles")
        }
    }

    @Test fun atlantisIsAWholeCityAndItsGateFitsItsOpening() {
        val c = lib.data(MeshId.ATLANTIS).bounds()
        assertTrue(c[3] - c[0] in 300f..380f, "Atlantis is ${c[3] - c[0]} m wide")
        assertTrue(c[5] - c[2] in 300f..380f, "Atlantis is ${c[5] - c[2]} m deep")
        assertTrue(abs(c[0] + c[3]) < 20f && abs(c[2] + c[5]) < 20f, "Atlantis is off its middle: ${c.toList()}")
        val l = lib.data(MeshId.ATLANTIS_LIGHTS).bounds()
        assertTrue(l[0] >= c[0] - 1f && l[3] <= c[3] + 1f && l[2] >= c[2] - 1f && l[5] <= c[5] + 1f && l[4] <= c[4] + 6f, "the lights stray from the city: ${l.toList()} vs ${c.toList()}")
        val total = tris(MeshId.ATLANTIS) + tris(MeshId.ATLANTIS_LIGHTS)
        assertTrue(total in 3000..12000, "ATLANTIS and its lights have $total triangles")
        val g = lib.data(MeshId.ATLANTIS_GATE).bounds()
        assertTrue(g[0] >= -14.55f && g[3] <= 14.55f && g[2] >= -2.55f && g[5] <= 2.55f, "the gate is outside its bounds: ${g.toList()}")
        assertTrue(g[3] - g[0] > 27f, "the gate is ${g[3] - g[0]} m wide")
    }

    @Test fun everyModelIsFiniteDeterministicAndNoStub() {
        val other = MeshLibrary()
        for (k in MeshId.SUBCAR_0.ordinal..MeshId.ATLANTIS_GATE.ordinal) {
            val id = MeshId.values()[k]
            val m = lib.data(id)
            assertTrue(m.triangleCount > 100, "$id is still a placeholder (${m.triangleCount} triangles)")
            for (v in 0 until m.count * 3) {
                assertTrue(m.pos[v].isFinite() && m.nrm[v].isFinite(), "$id has a non-finite position or normal")
            }
            val again = other.data(id)
            assertTrue(again.count == m.count && again.pos.copyOf(m.count * 3).contentEquals(m.pos.copyOf(m.count * 3)), "$id is not deterministic")
        }
    }
}
