package app.roadtoorbit.trace

import app.roadtoorbit.gfx.MeshId
import app.roadtoorbit.trace.GalleryKit.Floor
import app.roadtoorbit.trace.GalleryKit.Item
import app.roadtoorbit.trace.GalleryKit.Look
import app.roadtoorbit.trace.GalleryKit.View
import org.junit.Test

/** Not assertions: the welcome of Atlantis (merfolk, confetti fish, bunting) for the headless render check (see GalleryKit). */
class OceanCelebrationGalleryTest {
    @Test
    fun celebration() {
        val floor = Floor(0f, 60f, floatArrayOf(1.3f, 1.5f, 1.5f))
        val colours = listOf(floatArrayOf(1f, 0.3f, 0.3f), floatArrayOf(1f, 0.8f, 0.2f), floatArrayOf(0.3f, 1f, 0.5f), floatArrayOf(0.3f, 0.8f, 1f), floatArrayOf(0.8f, 0.4f, 1f))
        val folk = (0 until 3).map { Item(MeshId.values()[MeshId.MERFOLK_0.ordinal + it], -4f + it * 4f, 0.15f, -10f, ry = -20f + it * 20f, emissive = 0.2f) }
        val fish = colours.mapIndexed { i, c -> Item(MeshId.CONFETTI_FISH, -5f + i * 2.5f, 6f + (i % 2) * 1.5f, -6f, ry = 20f * i, sx = 1.2f, tint = c, emissive = 0.35f) }
        GalleryKit.write("celebration", listOf(
            View("folk_close", floatArrayOf(0f, 2.5f, 3f), floatArrayOf(0f, 1.8f, -10f), 50f, folk, Look.SEABED, floor),
            View("folk_far", floatArrayOf(0f, 3f, 14f), floatArrayOf(0f, 1.8f, -10f), 55f, folk + (0 until 6).map { Item(MeshId.values()[MeshId.MERFOLK_0.ordinal + it % 3], -12f + it * 4.8f, 0.15f, -25f - it * 3f, ry = 90f) }, Look.SEABED, floor),
            View("fish", floatArrayOf(0f, 6f, 4f), floatArrayOf(0f, 6f, -6f), 45f, fish, Look.DEEP),
            View("bunting", floatArrayOf(0f, 6f, 22f), floatArrayOf(0f, 7f, -10f), 60f, listOf(Item(MeshId.BUNTING, 0f, 0f, -10f, emissive = 0.2f)) + folk, Look.SEABED, floor),
        ))
    }
}
