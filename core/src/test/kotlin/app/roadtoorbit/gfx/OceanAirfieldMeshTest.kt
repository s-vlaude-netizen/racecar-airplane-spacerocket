package app.roadtoorbit.gfx

import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/** The models of the island airstrip are what [AirfieldSpec] says: finite, the right size, standing where they should. */
class OceanAirfieldMeshTest {
    private val lib = MeshLibrary()
    private val ids = listOf(
        MeshId.STRIP_SEG, MeshId.STRIP_SEG_GLOW, MeshId.STRIP_START, MeshId.STRIP_END, MeshId.TERMINAL, MeshId.CONTROL_TOWER, MeshId.HANGAR,
        MeshId.PALM, MeshId.WINDSOCK, MeshId.PERSON_0, MeshId.PERSON_1, MeshId.PERSON_2, MeshId.BALLOON, MeshId.CONFETTI_PAPER,
    )

    private fun bounds(id: MeshId): FloatArray {
        val d = lib.data(id)
        val b = floatArrayOf(1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f)
        for (i in 0 until d.count) for (k in 0 until 3) { b[k] = minOf(b[k], d.pos[i * 3 + k]); b[3 + k] = max(b[3 + k], d.pos[i * 3 + k]) }
        return b
    }

    @Test fun everyModelIsRealAndFinite() {
        for (id in ids) {
            val d = lib.data(id)
            assertTrue(d.count >= 6 && d.count % 3 == 0, "$id has ${d.count} vertices")
            for (i in 0 until d.count * 3) assertTrue(d.pos[i].isFinite() && d.nrm[i].isFinite(), "$id has a non-finite number at $i")
            for (i in 0 until d.count) {
                val n = sqrt(d.nrm[i * 3] * d.nrm[i * 3] + d.nrm[i * 3 + 1] * d.nrm[i * 3 + 1] + d.nrm[i * 3 + 2] * d.nrm[i * 3 + 2])
                assertTrue(n in 0.9f..1.1f || n == 0f, "$id vertex $i has a normal of length $n")
            }
            assertTrue(d.count < 6_000, "$id is too heavy: ${d.count} vertices")
        }
    }

    @Test fun theStripSegmentsTileAlongTheRoute() {
        for (id in listOf(MeshId.STRIP_SEG, MeshId.STRIP_START, MeshId.STRIP_END, MeshId.STRIP_SEG_GLOW)) {
            val b = bounds(id)
            assertTrue(b[2] >= -AirfieldSpec.SEG - 0.01f && b[5] <= 0.01f, "$id must lie between z = -24 and z = 0: ${b.toList()}")
            assertTrue(b[0] >= -AirfieldSpec.BANK_HALF - 0.01f && b[3] <= AirfieldSpec.BANK_HALF + 0.01f, "$id is wider than the strip: ${b.toList()}")
        }
        for (id in listOf(MeshId.STRIP_SEG, MeshId.STRIP_START)) {
            val b = bounds(id)
            assertTrue(b[2] <= -AirfieldSpec.SEG + 0.01f && b[5] >= -0.01f, "$id must fill its 24 m: ${b.toList()}")
            assertTrue(b[4] in 0f..0.5f && b[1] <= -AirfieldSpec.BANK_DROP + 0.01f, "$id: the top is the runway (y = 0) and the bank goes down to -4: ${b.toList()}")
        }
        // the runway itself: up (every face of the top that is flat points up)
        val d = lib.data(MeshId.STRIP_SEG)
        var flatUp = 0
        for (i in 0 until d.count) if (d.pos[i * 3 + 1] == 0f && d.nrm[i * 3 + 1] > 0.99f) flatUp++
        assertTrue(flatUp > 60, "the runway's faces point up: $flatUp")
    }

    @Test fun peopleAreHumanSized() {
        for (id in listOf(MeshId.PERSON_0, MeshId.PERSON_1, MeshId.PERSON_2)) {
            val b = bounds(id)
            assertTrue(b[1] >= -0.01f && b[1] < 0.05f, "$id stands on y = 0: ${b.toList()}")
            assertTrue(b[4] in 2.1f..2.5f, "$id with an arm up is about 2.3 m: ${b.toList()}")
            assertTrue(b[0] > -0.8f && b[3] < 0.8f && b[2] > -0.8f && b[5] < 0.8f, "$id is within 0.8 m of its origin: ${b.toList()}")
        }
    }

    @Test fun buildingsAndTreesStandOnTheGround() {
        val terminal = bounds(MeshId.TERMINAL)
        assertTrue(terminal[1] >= -0.01f && terminal[4] in 10f..13f && terminal[3] - terminal[0] in 44f..50f, "the terminal: ${terminal.toList()}")
        assertTrue(terminal[2] < -8f && terminal[5] < 9f, "the canopy comes out toward -Z: ${terminal.toList()}")
        val tower = bounds(MeshId.CONTROL_TOWER)
        assertTrue(tower[1] >= -0.01f && tower[4] in 22f..26f && tower[3] < 4.1f && tower[0] > -4.1f, "the tower: ${tower.toList()}")
        val hangar = bounds(MeshId.HANGAR)
        assertTrue(hangar[1] >= -0.01f && hangar[4] in 9f..11.5f && hangar[3] - hangar[0] in 21f..23f && hangar[5] - hangar[2] in 23f..25f, "the hangar: ${hangar.toList()}")
        val palm = bounds(MeshId.PALM)
        assertTrue(palm[1] >= -0.01f && palm[4] in 7.5f..9.5f && palm[3] < 4.4f && palm[0] > -4.4f, "the palm: ${palm.toList()}")
        val sock = bounds(MeshId.WINDSOCK)
        assertTrue(sock[1] >= -0.01f && sock[4] in 6.5f..7.3f && sock[3] > 2f && sock[0] > -0.7f, "the windsock points to +X: ${sock.toList()}")
    }

    @Test fun balloonsAndConfettiAreSmall() {
        val balloon = bounds(MeshId.BALLOON)
        assertTrue(balloon[3] - balloon[0] in 1f..1.2f && balloon[4] in 0.6f..0.75f && balloon[1] in -2.6f..-2.2f, "the balloon: ${balloon.toList()}")
        val paper = bounds(MeshId.CONFETTI_PAPER)
        assertTrue(paper[3] - paper[0] in 0.3f..0.4f && paper[5] - paper[2] in 0.15f..0.2f && paper[4] - paper[1] < 0.01f, "the paper: ${paper.toList()}")
    }
}
