package app.roadtoorbit.gfx

import app.roadtoorbit.game.Kind
import app.roadtoorbit.game.Levels
import app.roadtoorbit.game.Tuning
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The table of how far scenery reaches sideways ([Footprints]) is what keeps it out of the lanes (scenery is drawn but not solid: a
 * ruin that reached into the lanes was a thing the car drove through). It has to be true to the meshes and big enough for the lanes.
 */
class FootprintTest {
    private val lib = MeshLibrary()

    private fun reachOf(id: MeshId): Float {
        val d = lib.data(id)
        var r = 0f
        for (i in 0 until d.count) {
            val x = d.pos[i * 3]; val z = d.pos[i * 3 + 2]
            r = max(r, sqrt(x * x + z * z))
        }
        return r
    }

    @Test fun theTableIsTheReachOfTheMeshes() {
        for (kind in listOf(Kind.TREE_PINE, Kind.TREE_ROUND, Kind.ROCK, Kind.BILLBOARD, Kind.MESA, Kind.DEVIL, Kind.DOME)) {
            val measured = Footprints.meshes(kind).maxOf { reachOf(it) }
            val table = Footprints.reach(kind, 0)
            assertTrue(table >= measured - 0.01f, "$kind reaches ${"%.2f".format(measured)} m but the table says ${"%.2f".format(table)}")
            assertTrue(table <= measured + 0.5f, "$kind: the table says ${"%.2f".format(table)} m but the models reach only ${"%.2f".format(measured)}")
        }
        for ((variant, id) in OceanSpec.SEASCAPE.withIndex()) {
            val measured = reachOf(id)
            val table = Footprints.reach(Kind.SEASCAPE, variant)
            assertTrue(table >= measured - 0.01f, "$id reaches ${"%.2f".format(measured)} m but the table says ${"%.2f".format(table)}")
            assertTrue(table <= measured + 0.5f, "$id: the table says ${"%.2f".format(table)} m but the model reaches only ${"%.2f".format(measured)}")
        }
    }

    @Test fun theClearancesAreWiderThanTheLanesAndTheVehiclesThatUseThem() {
        // every leg of every level: its lanes plus half the width of the vehicle in every skin it can have
        for (level in Levels.ALL) for (leg in level.legs) {
            var widest = 0f
            for (skin in Skin.values()) {
                val b = VehicleExtents.of(lib, leg.mode, skin)
                widest = max(widest, max(-b[0], b[3]))
            }
            val needed = leg.maxX + widest
            val clear = if (leg.mode == VehicleMode.CAR) Footprints.CAR_CLEAR else Footprints.AIR_CLEAR
            assertTrue(clear >= needed + 0.5f, "${level.name} / ${leg.name}: scenery may stand ${"%.1f".format(clear)} m from the route but the ${leg.mode} reaches ${"%.1f".format(needed)} m")
        }
        assertTrue(Footprints.CAR_CLEAR >= Tuning.CAR_MAX_X + 1.4f)
        assertTrue(Footprints.AIR_CLEAR >= Tuning.PLANE_MAX_X + 3.2f && Footprints.AIR_CLEAR >= Tuning.ROCKET_MAX_X + 1.4f)
    }
}
