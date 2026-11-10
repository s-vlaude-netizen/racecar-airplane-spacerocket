package app.roadtoorbit.trace

import app.roadtoorbit.gfx.MeshId
import app.roadtoorbit.trace.GalleryKit.Floor
import app.roadtoorbit.trace.GalleryKit.Item
import app.roadtoorbit.trace.GalleryKit.Look
import app.roadtoorbit.trace.GalleryKit.View
import org.junit.Test

/**
 * Not assertions: lays out the models of the sea and of the dive (OceanSeaMeshes) in a few views each, for the headless
 * render check to turn into PNGs. One trace per group: build/traces/sea_<group>.bin (or the folder of -DtraceDir), the view
 * names in the .txt next to it. Every group is shown close up, from the side, and from the game's chase camera at 30, 80
 * and 150 m, because that is how the player meets them.
 */
class OceanSeaGalleryTest {
    private fun id(first: MeshId, k: Int) = MeshId.values()[first.ordinal + k]

    /** The sea seen from above: blue water under a bright sky. */
    private val sea = Floor(0f, 300f, floatArrayOf(0.37f, 1.45f, 1.75f))

    /** The seabed far under the dive (a dark teal disc). */
    private val deepFloor = Floor(-45f, 300f, floatArrayOf(0.3f, 1.1f, 1.2f))

    /** What a model is drawn with. */
    private class Spec(
        val id: MeshId, val sx: Float, val sy: Float = sx, val sz: Float = sx, val y: Float = 0f,
        val ry: Float = 0f, val emissive: Float = 0f, val spec: Float = 0.3f, val shine: Float = 30f,
        val tint: FloatArray? = null, val rim: FloatArray? = null, val rx: Float = 0f, val rz: Float = 0f,
    )

    private fun item(s: Spec, x: Float, z: Float, y: Float = s.y, ry: Float = s.ry) =
        Item(s.id, x, y, z, ry, s.sx, s.sy, s.sz, s.rx, s.rz, s.tint, s.emissive, s.spec, s.shine, s.rim)

    private fun lineup(specs: List<Spec>, spacing: Float, z: Float): List<Item> {
        val n = specs.size
        return specs.mapIndexed { i, s -> item(s, (i - (n - 1) / 2f) * spacing, z) }
    }

    private fun alongZ(specs: List<Spec>, spacing: Float, z0: Float): List<Item> =
        specs.mapIndexed { i, s -> item(s, 0f, z0 - i * spacing) }

    private fun look(name: String, eye: FloatArray, target: FloatArray, fov: Float, items: List<Item>, look: Look, floor: Floor?, fog: Float? = null) =
        View(name, eye, target, fov, items, look, floor, fog)

    /** The game's chase camera: a few metres behind and above the vehicle, looking ahead along -Z; the models stand [dist] m ahead. */
    private fun chase(name: String, dist: Float, specs: List<Spec>, spacing: Float, look: Look, floor: Floor?, camY: Float = 5f, fov: Float = 62f, lift: Float = 0f): View {
        val n = specs.size
        val items = specs.mapIndexed { i, s -> item(s, (i - (n - 1) / 2f) * spacing, -dist, s.y + lift) }
        return View("$name ${dist.toInt()}m", floatArrayOf(0f, camY, 0f), floatArrayOf(0f, camY - 1f, -25f), fov, items, look, floor)
    }

    private fun group(
        name: String, specs: List<Spec>, look: Look, floor: Floor?, spacing: Float,
        closeCam: Pair<FloatArray, FloatArray>, closeFov: Float, sideCam: Pair<FloatArray, FloatArray>, sideSpacing: Float,
        extra: List<View> = emptyList(), camY: Float = 5f, chaseSpacing: Float = spacing, lift: Float = 0f,
    ): List<View> {
        val views = ArrayList<View>()
        views.add(look("$name close", closeCam.first, closeCam.second, closeFov, lineup(specs, spacing, -40f), look, floor))
        views.add(look("$name side", sideCam.first, sideCam.second, 50f, alongZ(specs, sideSpacing, -20f), look, floor))
        views.addAll(extra)
        for (d in floatArrayOf(30f, 80f, 150f)) views.add(chase(name, d, specs, chaseSpacing, look, floor, camY, lift = lift))
        return views
    }

    private fun f(vararg v: Float) = v

    @Test
    fun stacks() {
        val specs = (0..2).map { Spec(id(MeshId.SEASTACK_0, it), 3.5f, 22f, 3.5f) }
        val views = group(
            "stacks", specs, Look.SURFACE, sea, 12f,
            f(0f, 6f, 6f) to f(0f, 10f, -40f), 60f, f(40f, 10f, -45f) to f(0f, 10f, -45f), 28f,
            extra = listOf(
                look("stack0 top", f(10f, 24f, -22f), f(0f, 17f, -40f), 40f, listOf(item(specs[0], 0f, -40f)), Look.SURFACE, sea),
                look("stack1 top", f(10f, 24f, -22f), f(0f, 17f, -40f), 40f, listOf(item(specs[1], 0f, -40f)), Look.SURFACE, sea),
                look("stack2 top", f(10f, 24f, -22f), f(0f, 17f, -40f), 40f, listOf(item(specs[2], 0f, -40f)), Look.SURFACE, sea),
                look("stack base", f(4f, 4f, -28f), f(0f, 2f, -40f), 55f, listOf(item(specs[1], 0f, -40f)), Look.SURFACE, sea),
            ),
        )
        GalleryKit.write("sea_stacks", views)
    }

    @Test
    fun ships() {
        val specs = (0..2).map { Spec(id(MeshId.SHIP_0, it), 1f) }
        val views = ArrayList<View>()
        views.add(look("sailboat 3/4", f(7f, 5.5f, 13f), f(0f, 3.5f, 0f), 55f, listOf(item(specs[0], 0f, 0f)), Look.SURFACE, sea))
        views.add(look("sailboat side", f(24f, 5f, 0f), f(0f, 5f, 0f), 45f, listOf(item(specs[0], 0f, 0f)), Look.SURFACE, sea))
        views.add(look("trawler 3/4", f(11f, 7f, 20f), f(0f, 3f, 0f), 55f, listOf(item(specs[1], 0f, 0f)), Look.SURFACE, sea))
        views.add(look("trawler side", f(34f, 5f, 0f), f(0f, 4f, 0f), 45f, listOf(item(specs[1], 0f, 0f)), Look.SURFACE, sea))
        views.add(look("container 3/4", f(18f, 12f, 46f), f(0f, 5f, 0f), 55f, listOf(item(specs[2], 0f, 0f)), Look.SURFACE, sea))
        views.add(look("container side", f(60f, 8f, 0f), f(0f, 6f, 0f), 50f, listOf(item(specs[2], 0f, 0f)), Look.SURFACE, sea))
        views.add(look("container bow", f(-16f, 9f, -50f), f(0f, 5f, -4f), 55f, listOf(item(specs[2], 0f, 0f)), Look.SURFACE, sea))
        views.add(look("container stern close", f(6f, 9f, 40f), f(0f, 8f, 20f), 60f, listOf(item(specs[2], 0f, 0f)), Look.SURFACE, sea))
        for (d in floatArrayOf(30f, 80f, 150f)) views.add(chase("ships", d, specs, 20f, Look.SURFACE, sea))
        GalleryKit.write("sea_ships", views)
    }

    @Test
    fun flocksAndPlanes() {
        val flocks = (0..3).map { Spec(id(MeshId.FLOCK_0, it), 1f, y = 8f) }
        val planes = (0..2).map { Spec(id(MeshId.SEAPLANE_0, it), 1f, y = 8f, ry = 0f) }
        val views = ArrayList<View>()
        views.add(look("flocks close", f(0f, 14f, 3f), f(0f, 8f, -26f), 50f, lineup(flocks, 9f, -26f), Look.SURFACE, sea))
        views.add(look("flocks above", f(0f, 30f, -20f), f(0f, 8f, -26f), 45f, lineup(flocks, 9f, -26f), Look.SURFACE, sea))
        views.add(look("flock 0 side", f(-5f, 8f, -16f), f(0f, 8f, -26f), 30f, listOf(item(flocks[0], 0f, -26f)), Look.SURFACE, sea))
        views.add(look("flock 2 side", f(-5f, 8f, -16f), f(0f, 8f, -26f), 30f, listOf(item(flocks[2], 0f, -26f)), Look.SURFACE, sea))
        views.add(look("planes close", f(0f, 11f, -14f), f(0f, 8f, -30f), 50f, lineup(planes, 7f, -30f), Look.SURFACE, sea))
        views.add(look("planes side", f(9f, 8f, -15f), f(0f, 8f, -26f), 45f, listOf(item(planes[0], 0f, -26f, ry = 90f), item(planes[1], 0f, -33f, ry = 90f), item(planes[2], 0f, -40f, ry = 90f)), Look.SURFACE, sea))
        views.add(look("planes top", f(0f, 22f, -22f), f(0f, 8f, -30f), 40f, lineup(planes, 7f, -30f), Look.SURFACE, sea))
        views.add(look("planes rear", f(5f, 10f, -12f), f(0f, 8f, -30f), 35f, lineup(planes, 7f, -30f), Look.SURFACE, sea))
        for (d in floatArrayOf(30f, 80f, 150f)) {
            views.add(chase("flocks", d, flocks, 12f, Look.SURFACE, sea, lift = 4f))
            views.add(chase("planes", d, planes, 12f, Look.SURFACE, sea, lift = 4f))
        }
        GalleryKit.write("sea_air", views)
    }

    @Test
    fun seaSheet() {
        val st = (0..2).map { Spec(id(MeshId.SEASTACK_0, it), 3.5f, 22f, 3.5f) }
        val isl = (0..2).map { Spec(id(MeshId.ISLAND_0, it), 1f) }
        val gateBig = Spec(MeshId.DIVE_GATE_BIG, 1f, y = 18f, emissive = 0.4f)
        val gateSmall = Spec(MeshId.DIVE_GATE_SMALL, 1f, y = 12f, emissive = 0.4f)
        val ships = (0..2).map { Spec(id(MeshId.SHIP_0, it), 1f) }
        val flock = Spec(MeshId.FLOCK_0, 1f)
        val tern = Spec(MeshId.FLOCK_2, 1f)
        val plane = Spec(MeshId.SEAPLANE_0, 1f)
        val plane2 = Spec(MeshId.SEAPLANE_1, 1f)
        val views = listOf(
            look("islands 110m", f(0f, 5f, 0f), f(0f, 4f, -25f), 62f, listOf(item(isl[0], -62f, -110f), item(isl[1], 0f, -110f, ry = 40f), item(isl[2], 66f, -110f, ry = 200f)), Look.SURFACE, sea),
            look("islands above", f(0f, 75f, -10f), f(0f, 0f, -70f), 60f, listOf(item(isl[0], -55f, -70f), item(isl[1], 0f, -70f), item(isl[2], 60f, -70f)), Look.SURFACE, sea),
            look("gates", f(0f, 9f, 0f), f(0f, 12f, -60f), 60f, listOf(item(gateBig, 0f, -70f), item(gateSmall, 0f, -130f)), Look.SURFACE, sea),
            look("buoys", f(0f, 4f, 16f), f(0f, 5f, -20f), 55f, listOf(item(Spec(MeshId.START_BUOYS, 1f), 0f, -30f)), Look.SURFACE, sea),
            look(
                "sea life", f(0f, 14f, 12f), f(0f, 6f, -60f), 62f,
                listOf(
                    item(st[0], -30f, -60f), item(st[1], -24f, -90f), item(st[2], 30f, -100f),
                    item(ships[0], -8f, -45f), item(ships[1], 8f, -52f), item(ships[2], 34f, -85f),
                    item(flock, -2f, -32f, y = 11f), item(tern, 14f, -40f, y = 14f), item(plane, 6f, -28f, y = 9f), item(plane2, -12f, -34f, y = 8f),
                ),
                Look.SURFACE, sea,
            ),
            look("stacks 40m", f(0f, 6f, 6f), f(0f, 10f, -40f), 60f, lineup(st, 12f, -40f), Look.SURFACE, sea),
        )
        GalleryKit.write("sea_sheet", views)
    }

    @Test
    fun diveSheet() {
        val glow = f(0.9f, 0.3f, 0.2f, 3f)
        val cyan = f(0.3f, 0.8f, 0.9f, 3f)
        val mine0 = Spec(MeshId.MINE_0, 1.8f, y = 9f, emissive = 0.15f, spec = 0.5f, shine = 40f, rim = glow)
        val mine1 = Spec(MeshId.MINE_1, 1.8f, y = 9f, emissive = 0.15f, spec = 0.5f, shine = 40f, rim = glow)
        val pearl = Spec(MeshId.PEARL, 2f, y = 9f, emissive = 0.7f, spec = 0.8f, shine = 60f, rim = f(0.4f, 0.9f, 1f, 2.5f))
        val jelly0 = Spec(MeshId.JELLY_0, 1f, y = 9f, emissive = 0.55f, spec = 0.2f, rim = f(1f, 0.5f, 0.8f, 3f))
        val jelly1 = Spec(MeshId.JELLY_1, 1f, y = 9f, emissive = 0.55f, spec = 0.2f, rim = f(0.5f, 0.8f, 1f, 3f))
        val shark0 = Spec(MeshId.SHARK_0, 1f, y = 8f, spec = 0.45f, shine = 40f, rim = cyan)
        val shark1 = Spec(MeshId.SHARK_1, 1f, y = 8f, spec = 0.45f, shine = 40f, rim = cyan)
        val manta = Spec(MeshId.MANTA, 1f, y = 9f, spec = 0.4f, shine = 40f, rim = cyan)
        val whale = Spec(MeshId.WHALE, 1f, y = 14f, spec = 0.25f)
        val school0 = Spec(MeshId.SCHOOL_0, 1f, y = 10f, emissive = 0.15f, spec = 0.6f, shine = 50f)
        val school1 = Spec(MeshId.SCHOOL_1, 1f, y = 12f, emissive = 0.15f, spec = 0.6f, shine = 50f)
        val reef = Floor(0f, 300f, floatArrayOf(0.35f, 0.9f, 0.85f))
        val views = listOf(
            look("mines pearl", f(0f, 9f, 12f), f(0f, 9f, -20f), 55f, listOf(item(mine0, -6f, -20f), item(mine1, -2f, -20f), item(pearl, 2.5f, -20f), item(school0, 10f, -26f)), Look.DEEP, deepFloor),
            look("jellies", f(0f, 7f, 10f), f(0f, 7f, -22f), 55f, listOf(item(jelly0, -5.5f, -22f), item(jelly1, 5.5f, -22f)), Look.DEEP, deepFloor),
            look("sharks rear", f(0f, 9f, 0f), f(0f, 8f, -24f), 62f, listOf(item(shark0, -6f, -22f), item(shark1, 0f, -26f), item(manta, 7f, -24f)), Look.DEEP, deepFloor),
            look("sharks side", f(26f, 8f, -22f), f(0f, 8f, -22f), 55f, listOf(item(shark0, 0f, -12f), item(shark1, 0f, -22f), item(manta, 0f, -32f)), Look.DEEP, deepFloor),
            look(
                "far", f(0f, 10f, 0f), f(0f, 8f, -40f), 62f,
                listOf(item(whale, -18f, -75f), item(school0, 8f, -50f), item(school1, -8f, -40f), item(Spec(MeshId.KELP_0, 1f), 22f, -60f), item(Spec(MeshId.CORAL_0, 1f), -30f, -55f)),
                Look.DEEP, reef,
            ),
            look(
                "reef", f(0f, 7f, 14f), f(0f, 3f, -22f), 60f,
                listOf(
                    item(Spec(MeshId.CORAL_0, 1f), -13f, -22f), item(Spec(MeshId.CORAL_1, 1f), 0f, -22f), item(Spec(MeshId.CORAL_2, 1f), 13f, -22f),
                    item(Spec(MeshId.KELP_0, 1f), -26f, -30f), item(Spec(MeshId.KELP_1, 1f), 26f, -30f),
                ),
                Look.DEEP, reef,
            ),
        )
        GalleryKit.write("dive_sheet", views)
    }
}
