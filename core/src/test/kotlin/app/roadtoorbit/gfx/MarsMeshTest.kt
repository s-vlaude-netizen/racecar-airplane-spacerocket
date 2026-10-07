package app.roadtoorbit.gfx

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Martian models stand in for models with collision boxes of their own (the traffic, the balloons, the spires), so
 * what the player sees has to match what it can hit: a rover that looks 3 m long but hits like a 4 m one is unfair.
 */
class MarsMeshTest {
    private val lib = MeshLibrary()

    private class Box(val hx: Float, val hy: Float, val hz: Float)

    /** The collision boxes the spawner gives the traffic variants (sedan, truck, van); the model's origin is the box centre. */
    private val sedan = Box(0.95f, 0.65f, 2.1f)
    private val truck = Box(1.25f, 1.6f, 3.7f)
    private val van = Box(1.05f, 1.1f, 2.6f)

    private fun check(id: MeshId, box: Box) {
        val b = lib.data(id).bounds() // minX, minY, minZ, maxX, maxY, maxZ
        val halfW = (b[3] - b[0]) / 2f
        val halfL = (b[5] - b[2]) / 2f
        assertTrue(abs(halfW - box.hx) < 0.2f, "$id is ${halfW * 2} m wide, its hit box ${box.hx * 2} m")
        assertTrue(abs(halfL - box.hz) < 0.25f, "$id is ${halfL * 2} m long, its hit box ${box.hz * 2} m")
        assertTrue(abs(b[0] + b[3]) < 0.4f && abs(b[2] + b[5]) < 0.4f, "$id is not centred on its hit box: x ${b[0]}..${b[3]}, z ${b[2]}..${b[5]}")
        assertTrue(b[1] > -box.hy - 0.05f && b[1] < -box.hy + 0.12f, "$id should stand on the ground (the box bottom, ${-box.hy}), its lowest point is ${b[1]}")
        assertTrue(b[4] < box.hy + 0.95f, "$id rises to ${b[4]}, far above its hit box top ${box.hy}")
    }

    @Test fun colonyVehiclesFitTheCollisionBoxesOfTheVehiclesTheyReplace() {
        for (k in 0..2) {
            check(MeshId.values()[MeshId.ROVER_0.ordinal + k], sedan)
            check(MeshId.values()[MeshId.HAULER_0.ordinal + k], truck)
            check(MeshId.values()[MeshId.CRAWLER_0.ordinal + k], van)
        }
        // and the Earth models they replace obey the same rule, so the rule itself is sound
        for (k in 0..5) check(MeshId.values()[MeshId.SEDAN_0.ordinal + k], sedan)
        for (k in 0..2) check(MeshId.values()[MeshId.TRUCK_0.ordinal + k], truck)
        for (k in 0..2) check(MeshId.values()[MeshId.VAN_0.ordinal + k], van)
    }

    @Test fun saucersAreAsWideAsTheirDiscAndAsThinAsTheirHitBox() {
        // the spawner gives a saucer a 2.7 x 0.85 x 2.7 box (half extents)
        for (k in 0..3) {
            val id = MeshId.values()[MeshId.SAUCER_0.ordinal + k]
            val b = lib.data(id).bounds()
            assertTrue(b[3] <= 3.1f && b[0] >= -3.1f && b[5] <= 3.1f && b[2] >= -3.1f, "$id is wider than 6 m: ${b.toList()}")
            assertTrue(b[3] >= 2.8f && b[5] >= 2.8f, "$id is smaller than its disc: ${b.toList()}")
            assertTrue(b[1] >= -0.85f - 0.1f && b[4] <= 0.85f + 0.2f, "$id is taller than its hit box: y ${b[1]}..${b[4]}")
        }
    }

    @Test fun sceneryStandsOnTheGround() {
        for (id in listOf(MeshId.MESA_0, MeshId.MESA_1, MeshId.MESA_2, MeshId.DEVIL, MeshId.DOME, MeshId.SPIRE_0, MeshId.SPIRE_1, MeshId.SPIRE_2)) {
            val b = lib.data(id).bounds()
            assertTrue(b[1] > -0.1f && b[1] < 0.3f, "$id should stand on y = 0, its lowest point is ${b[1]}")
            assertTrue(b[4] > 1f, "$id is tiny: ${b[4]}")
        }
    }

    @Test fun spiresAreUnitModelsTheEntityStretchesLikeThePeaks() {
        // a spire hazard is scaled to (radius, height, radius); its hit box is that radius and half that height
        for (id in listOf(MeshId.SPIRE_0, MeshId.SPIRE_1, MeshId.SPIRE_2, MeshId.PEAK_0, MeshId.PEAK_1, MeshId.PEAK_2)) {
            val b = lib.data(id).bounds()
            assertTrue(b[4] in 0.95f..1.08f, "$id should be about 1 high, it is ${b[4]}")
            assertTrue(b[3] <= 1.12f && b[0] >= -1.12f && b[5] <= 1.12f && b[2] >= -1.12f, "$id is wider than its unit radius: ${b.toList()}")
        }
    }

    /**
     * The finale puts the ground 25 m above the top of the destination's sphere (radius 2400 m), so a lump anywhere on the
     * sphere that rises more than 25 m above its nominal surface and sits near the top would come up through the ground
     * and the landing vehicle. Phobos is lumpy only far from the top; the cap that can ever be seen from the landing
     * site is a perfect sphere, and nothing reaches above the nominal top.
     */
    @Test fun phobosHasAPerfectCapAndLumpsElsewhere() {
        val m = lib.data(MeshId.PHOBOS)
        var lumpiest = 0f
        var highest = 0f
        for (v in 0 until m.count) {
            val x = m.pos[v * 3]; val y = m.pos[v * 3 + 1]; val z = m.pos[v * 3 + 2]
            val r = sqrt(x * x + y * y + z * z)
            // within 10 degrees of the top nothing may deviate at all: that is more than the terrain around the landing covers
            if (y / r > 0.9f) assertTrue(abs(r - 1f) < 0.002f, "the cap of Phobos is not round: radius $r at height ${y / r}")
            lumpiest = maxOf(lumpiest, abs(r - 1f))
            highest = maxOf(highest, y)
        }
        assertTrue(highest <= 1.0f + 25f / 2400f, "a lump rises to ${highest * 2400f - 2400f} m above the top of the sphere")
        assertTrue(lumpiest > 0.05f, "Phobos should look like a lumpy rock, not a ball (largest deviation $lumpiest)")
    }
}
