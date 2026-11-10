package app.roadtoorbit.gfx

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The models of the sea and of the dive are authored against the sizes, origins and collision boxes of [OceanSpec]: what the
 * player sees has to be what it can hit, so the bounds of every model are checked against the contract.
 */
class OceanSeaMeshTest {
    private val lib = MeshLibrary()

    private fun id(first: MeshId, k: Int) = MeshId.values()[first.ordinal + k]

    private val seaAndDive = MeshId.values().filter { it.ordinal in MeshId.SEASTACK_0.ordinal..MeshId.CORAL_2.ordinal }

    /** minX, minY, minZ, maxX, maxY, maxZ */
    private fun bounds(id: MeshId) = lib.data(id).bounds()

    private fun radiusXZ(id: MeshId): Float {
        val m = lib.data(id)
        var r = 0f
        for (v in 0 until m.count) r = maxOf(r, sqrt(m.pos[v * 3] * m.pos[v * 3] + m.pos[v * 3 + 2] * m.pos[v * 3 + 2]))
        return r
    }

    private fun radius3(id: MeshId): Float {
        val m = lib.data(id)
        var r = 0f
        for (v in 0 until m.count) r = maxOf(r, sqrt(m.pos[v * 3] * m.pos[v * 3] + m.pos[v * 3 + 1] * m.pos[v * 3 + 1] + m.pos[v * 3 + 2] * m.pos[v * 3 + 2]))
        return r
    }

    @Test fun everyModelIsRealFiniteAndSmallEnough() {
        var total = 0L
        for (id in seaAndDive) {
            val m = lib.data(id)
            assertTrue(m.triangleCount > 100, "$id is still the placeholder box or nearly empty: ${m.triangleCount} triangles")
            assertTrue(m.triangleCount <= 8000, "$id has ${m.triangleCount} triangles")
            for (i in 0 until m.count * 3) assertTrue(m.pos[i].isFinite() && m.nrm[i].isFinite(), "$id has a vertex that is not finite")
            total += m.col.size * 28L
        }
        println("ocean sea models: ${seaAndDive.size} meshes retain ${total / 1024} KB")
        assertTrue(total < 6L * 1024 * 1024, "the sea and dive models retain ${total / 1024} KB, more than their share of the budget")
    }

    @Test fun seaStacksAreUnitPillarsOnTheWater() {
        for (k in 0..2) {
            val id = id(MeshId.SEASTACK_0, k)
            val b = bounds(id)
            assertTrue(radiusXZ(id) <= 1.12f, "$id is wider than its unit radius: ${radiusXZ(id)}")
            assertTrue(b[1] > -0.03f && b[1] < 0.05f, "$id should stand on y = 0, its lowest point is ${b[1]}")
            assertTrue(b[4] in 0.95f..1.08f, "$id should be about 1 high, it is ${b[4]}")
        }
    }

    @Test fun shipsFillTheirCollisionBoxesAndStandOnTheWaterline() {
        for (k in 0..2) {
            val id = id(MeshId.SHIP_0, k)
            val h = OceanSpec.SHIP_HALF[k]
            val b = bounds(id)
            val e = 0.07f
            assertTrue(b[0] >= -h[0] - e && b[3] <= h[0] + e, "$id is wider than its box: x ${b[0]}..${b[3]} (half ${h[0]})")
            assertTrue(b[2] >= -h[2] - e && b[5] <= h[2] + e, "$id is longer than its box: z ${b[2]}..${b[5]} (half ${h[2]})")
            assertTrue(b[4] <= 2 * h[1] + e && b[4] >= 2 * h[1] - 0.5f, "$id should be about ${2 * h[1]} m high, it is ${b[4]}")
            assertTrue(b[1] <= 0.1f && b[1] >= -3f, "$id should stand on the waterline, its lowest point is ${b[1]}")
            assertTrue(b[3] - b[0] >= 1.7f * h[0], "$id does not fill the width of its box: ${b[3] - b[0]} of ${2 * h[0]}")
            assertTrue(b[5] - b[2] >= 1.85f * h[2], "$id does not fill the length of its box: ${b[5] - b[2]} of ${2 * h[2]}")
        }
    }

    @Test fun flocksStayInsideTheirSphere() {
        for (k in 0..3) {
            val id = id(MeshId.FLOCK_0, k)
            assertTrue(radius3(id) <= 3.4f, "$id reaches ${radius3(id)} m from its centre, the limit is 3.4 m")
            assertTrue(radius3(id) >= 2f, "$id is a clump, not a flock: ${radius3(id)} m")
        }
    }

    @Test fun seaplanesAndSharksFitTheirBoxes() {
        val e = 0.07f
        for (k in 0..2) {
            val id = id(MeshId.SEAPLANE_0, k)
            val h = OceanSpec.SEAPLANE_HALF
            val b = bounds(id)
            assertTrue(b[0] >= -h[0] - e && b[3] <= h[0] + e && b[1] >= -h[1] - e && b[4] <= h[1] + e && b[2] >= -h[2] - e && b[5] <= h[2] + e, "$id leaves its box: ${b.toList()}")
            assertTrue(b[3] - b[0] > 3.4f && b[5] - b[2] > 4.4f, "$id is smaller than its box: ${b.toList()}")
        }
        for (k in 0..2) {
            val id = id(MeshId.SHARK_0, k) // SHARK_0, SHARK_1, MANTA
            val h = OceanSpec.SHARK_HALF[k]
            val b = bounds(id)
            assertTrue(b[0] >= -h[0] - e && b[3] <= h[0] + e && b[1] >= -h[1] - e && b[4] <= h[1] + e && b[2] >= -h[2] - e && b[5] <= h[2] + e, "$id leaves its box: ${b.toList()}")
            assertTrue(b[5] - b[2] > 1.7f * h[2], "$id is too short for its box: ${b[5] - b[2]}")
            assertTrue(abs(b[2] + h[2]) < 0.5f, "$id should have its nose at the front of its box (z ${b[2]})")
        }
    }

    @Test fun mineHullsAreUnitSpheresWithHorns() {
        for (k in 0..1) {
            val id = id(MeshId.MINE_0, k)
            val m = lib.data(id)
            var onHull = 0
            for (v in 0 until m.count) {
                val x = m.pos[v * 3]; val y = m.pos[v * 3 + 1]; val z = m.pos[v * 3 + 2]
                if (abs(sqrt(x * x + y * y + z * z) - OceanSpec.MINE_HULL_RADIUS) < 0.02f) onHull++
            }
            assertTrue(onHull >= 50, "$id has a hull of radius 1 made of only $onHull vertices")
            val b = bounds(id)
            assertTrue(b[3] <= 1.6f && b[0] >= -1.6f && b[5] <= 1.6f && b[2] >= -1.6f && b[4] <= 1.6f, "$id is more than a hull with horns: ${b.toList()}")
        }
    }

    @Test fun jellyfishAreDomesWithLongTentacles() {
        for (k in 0..1) {
            val id = id(MeshId.JELLY_0, k)
            val b = bounds(id)
            assertTrue(radiusXZ(id) in 2.7f..3.15f, "$id has a bell of radius ${radiusXZ(id)}, the collision sphere is ${OceanSpec.JELLY_RADIUS}")
            assertTrue(b[4] <= 3.0f, "$id rises to ${b[4]}")
            assertTrue(b[1] in -4.8f..-3.5f, "$id's tentacles should hang to about -4.5, they reach ${b[1]}")
        }
    }

    @Test fun pearlStaysWithinItsPickupRadius() {
        val b = bounds(MeshId.PEARL)
        for (v in b) assertTrue(abs(v) <= OceanSpec.PEARL_RADIUS + 0.03f, "the pearl leaves its +-1.1 m cube: ${b.toList()}")
    }

    @Test fun whaleIsAsBigAsItsContract() {
        val b = bounds(MeshId.WHALE)
        assertTrue(b[5] - b[2] in 26f..28.2f, "the whale should be 28 m long, it is ${b[5] - b[2]}")
        assertTrue(b[3] - b[0] in 6f..7.2f, "the whale should be 7 m wide, it is ${b[3] - b[0]}")
        assertTrue(b[4] - b[1] in 4.5f..6.3f, "the whale should be about 6 m high, it is ${b[4] - b[1]}")
        assertTrue(b[2] < -13f, "the whale swims toward -Z, its nose must be at -Z (z ${b[2]})")
    }

    @Test fun gatesAndBuoysHaveTheirSizes() {
        val big = bounds(MeshId.DIVE_GATE_BIG)
        val small = bounds(MeshId.DIVE_GATE_SMALL)
        assertTrue(big[3] in 15f..19f && big[0] <= -15f && big[4] in 15f..19f && big[1] <= -15f, "the big gate should have a radius of 15 m: ${big.toList()}")
        assertTrue(small[3] in 9f..12f && small[0] <= -9f && small[4] in 9f..12f && small[1] <= -9f, "the small gate should have a radius of 9 m: ${small.toList()}")
        assertTrue(big[5] - big[2] < 4f && small[5] - small[2] < 3f, "a gate is a ring in the XY plane")
        val s = bounds(MeshId.START_BUOYS)
        // the buoys stand outside the waterplane's lanes (13 m and its wings), the start line lies flat on the water between them
        assertTrue(s[0] >= -18.05f && s[3] <= 18.05f && s[2] >= -1.5f && s[5] <= 1.5f && s[1] >= -0.05f && s[4] in 9f..9.55f, "the start buoys leave their bounds: ${s.toList()}")
        assertTrue(s[3] - 0.45f >= 16.4f, "the buoys must stand outside the waterplane's lanes: their inner edge is at ${s[3] - 0.45f}")
    }

    @Test fun sceneryStandsOnTheGround() {
        for (id in listOf(MeshId.ISLAND_0, MeshId.ISLAND_1, MeshId.ISLAND_2)) {
            val b = bounds(id)
            assertTrue(b[1] > -0.4f && b[1] < 0.35f, "$id should stand on the waterline (rocks may sit a little below it), its lowest point is ${b[1]}")
            assertTrue(b[4] in 4f..12f, "$id should be 4 - 12 m high, it is ${b[4]}")
            val across = maxOf(b[3] - b[0], b[5] - b[2])
            assertTrue(across in 30f..85f, "$id is $across m across")
        }
        for (id in listOf(MeshId.KELP_0, MeshId.KELP_1, MeshId.CORAL_0, MeshId.CORAL_1, MeshId.CORAL_2)) {
            assertTrue(bounds(id)[1] > -0.2f && bounds(id)[1] < 0.2f, "$id should stand on y = 0, its lowest point is ${bounds(id)[1]}")
        }
        for (id in listOf(MeshId.KELP_0, MeshId.KELP_1)) assertTrue(bounds(id)[4] in 9f..19f, "$id should be 10 - 18 m high, it is ${bounds(id)[4]}")
        for (id in listOf(MeshId.CORAL_0, MeshId.CORAL_1, MeshId.CORAL_2)) {
            val b = bounds(id)
            assertTrue(b[4] in 2f..7.5f, "$id should be 2 - 7 m high, it is ${b[4]}")
            assertTrue(maxOf(b[3] - b[0], b[5] - b[2]) in 3f..10f, "$id should be 3 - 9 m across, it is ${maxOf(b[3] - b[0], b[5] - b[2])}")
        }
    }

    @Test fun schoolsStayWithinSixMetres() {
        for (k in 0..1) assertTrue(radius3(id(MeshId.SCHOOL_0, k)) <= 6f, "SCHOOL_$k reaches ${radius3(id(MeshId.SCHOOL_0, k))} m from its centre")
    }
}
