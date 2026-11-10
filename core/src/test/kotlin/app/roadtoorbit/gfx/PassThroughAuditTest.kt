package app.roadtoorbit.gfx

import app.roadtoorbit.game.Bot
import app.roadtoorbit.game.Category
import app.roadtoorbit.game.Difficulty
import app.roadtoorbit.game.Entity
import app.roadtoorbit.game.Game
import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.Kind
import app.roadtoorbit.game.LevelSpec
import app.roadtoorbit.game.Levels
import app.roadtoorbit.game.Phase
import app.roadtoorbit.gl.NullGles
import app.roadtoorbit.math.Mat4
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Nothing the player can see and touch may be ghostly: whatever the renderer draws is tested, triangle by triangle, against the
 * vehicle. A mesh that the vehicle can reach and that is not a hazard (which hurts) or something meant to be passed (a pickup, a
 * ring, a gate) is scenery, and scenery has to stay out of the lanes; a hazard that is visibly inside the vehicle must have hit it.
 * The same goes for the fish of the welcome in Atlantis and the car they celebrate.
 *
 * The test hooks into the renderer (the same meshes and matrices that are drawn), plays every level with the test pilot and
 * collects every defect with the first example of it.
 */
class PassThroughAuditTest {
    private val lib = MeshLibrary()

    // ---- geometry helpers --------------------------------------------------------------------------

    private val localBounds = HashMap<MeshId, FloatArray>()

    /** minx, miny, minz, maxx, maxy, maxz of a mesh in its own space. */
    private fun bounds(id: MeshId): FloatArray = localBounds.getOrPut(id) {
        val d = lib.data(id)
        val b = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (i in 0 until d.count) {
            for (k in 0 until 3) {
                b[k] = min(b[k], d.pos[i * 3 + k]); b[3 + k] = max(b[3 + k], d.pos[i * 3 + k])
            }
        }
        b
    }

    /** The world box around a mesh drawn with [m]. */
    private fun worldBounds(id: MeshId, m: FloatArray, out: FloatArray) {
        val b = bounds(id)
        out[0] = Float.MAX_VALUE; out[1] = Float.MAX_VALUE; out[2] = Float.MAX_VALUE
        out[3] = -Float.MAX_VALUE; out[4] = -Float.MAX_VALUE; out[5] = -Float.MAX_VALUE
        for (c in 0 until 8) {
            val x = if (c and 1 == 0) b[0] else b[3]
            val y = if (c and 2 == 0) b[1] else b[4]
            val z = if (c and 4 == 0) b[2] else b[5]
            val wx = m[0] * x + m[4] * y + m[8] * z + m[12]
            val wy = m[1] * x + m[5] * y + m[9] * z + m[13]
            val wz = m[2] * x + m[6] * y + m[10] * z + m[14]
            out[0] = min(out[0], wx); out[3] = max(out[3], wx)
            out[1] = min(out[1], wy); out[4] = max(out[4], wy)
            out[2] = min(out[2], wz); out[5] = max(out[5], wz)
        }
    }

    private fun boxesTouch(w: FloatArray, box: Overlap.Box): Boolean =
        w[0] <= box.cx + box.hx && w[3] >= box.cx - box.hx &&
            w[1] <= box.cy + box.hy && w[4] >= box.cy - box.hy &&
            w[2] <= box.cz + box.hz && w[5] >= box.cz - box.hz

    /** Does any triangle of mesh [id], drawn with [m], touch [box]? */
    private fun meshTouches(id: MeshId, m: FloatArray, box: Overlap.Box): Boolean {
        val d = lib.data(id)
        var i = 0
        while (i + 2 < d.count) {
            val p = d.pos
            val a = i * 3; val b = a + 3; val c = b + 3
            if (Overlap.triangleBox(
                    m[0] * p[a] + m[4] * p[a + 1] + m[8] * p[a + 2] + m[12], m[1] * p[a] + m[5] * p[a + 1] + m[9] * p[a + 2] + m[13], m[2] * p[a] + m[6] * p[a + 1] + m[10] * p[a + 2] + m[14],
                    m[0] * p[b] + m[4] * p[b + 1] + m[8] * p[b + 2] + m[12], m[1] * p[b] + m[5] * p[b + 1] + m[9] * p[b + 2] + m[13], m[2] * p[b] + m[6] * p[b + 1] + m[10] * p[b + 2] + m[14],
                    m[0] * p[c] + m[4] * p[c + 1] + m[8] * p[c + 2] + m[12], m[1] * p[c] + m[5] * p[c + 1] + m[9] * p[c + 2] + m[13], m[2] * p[c] + m[6] * p[c + 1] + m[10] * p[c + 2] + m[14],
                    box,
                )
            ) return true
            i += 3
        }
        return false
    }

    /**
     * How much of mesh [id] (drawn with [m]) lies outside the hazard's collision shape (a sphere or a box, in the world): the share of its
     * surface area whose triangles are more than [limit] outside (so thin things such as tentacles, horns, antennas and chains do not
     * count), and the farthest vertex.
     */
    private class Outside(val share: Float, val farthest: Float)

    private fun outside(e: Entity, id: MeshId, m: FloatArray, cy: Float, limit: Float): Outside {
        val d = lib.data(id)
        val floor = if (e.kind == Kind.SHIP) cy - e.hy else -Float.MAX_VALUE // what is under the water of a ship does not count
        var far = 0f
        var area = 0.0
        var areaOut = 0.0
        val c = FloatArray(9)
        var i = 0
        while (i + 2 < d.count) {
            var cx = 0f; var cyy = 0f; var cz = 0f
            for (k in 0 until 3) {
                val x = d.pos[(i + k) * 3]; val y = d.pos[(i + k) * 3 + 1]; val z = d.pos[(i + k) * 3 + 2]
                val wx = m[0] * x + m[4] * y + m[8] * z + m[12]
                val wy = m[1] * x + m[5] * y + m[9] * z + m[13]
                val wz = m[2] * x + m[6] * y + m[10] * z + m[14]
                c[k * 3] = wx; c[k * 3 + 1] = wy; c[k * 3 + 2] = wz
                cx += wx / 3f; cyy += wy / 3f; cz += wz / 3f
                val o = distanceOutside(e, wx, wy, wz, cy)
                if (o > far) far = o
            }
            val ux = c[3] - c[0]; val uy = c[4] - c[1]; val uz = c[5] - c[2]
            val vx = c[6] - c[0]; val vy = c[7] - c[1]; val vz = c[8] - c[2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            val a = 0.5 * kotlin.math.sqrt((nx * nx + ny * ny + nz * nz).toDouble())
            if (cyy < floor) { i += 3; continue }
            area += a
            if (distanceOutside(e, cx, cyy, cz, cy) > limit) areaOut += a
            i += 3
        }
        return Outside(if (area > 0.0) (areaOut / area).toFloat() else 0f, far)
    }

    private fun distanceOutside(e: Entity, wx: Float, wy: Float, wz: Float, cy: Float): Float {
        val dx = wx - e.x; val dy = wy - cy; val dz = wz - e.z
        return if (e.radius > 0f) kotlin.math.sqrt(dx * dx + dy * dy + dz * dz) - e.radius else {
            val ox = max(0f, kotlin.math.abs(dx) - e.hx); val oy = max(0f, kotlin.math.abs(dy) - e.hy); val oz = max(0f, kotlin.math.abs(dz) - e.hz)
            kotlin.math.sqrt(ox * ox + oy * oy + oz * oz)
        }
    }

    /** How deep (m) two hazards' collision shapes (spheres and boxes) are inside each other; 0 when they are apart. */
    private fun penetration(a: Entity, ay: Float, b: Entity, by: Float): Float {
        val dx = abs(a.x - b.x); val dy = abs(ay - by); val dz = abs(a.z - b.z)
        if (a.radius > 0f && b.radius > 0f) return max(0f, a.radius + b.radius - kotlin.math.sqrt(dx * dx + dy * dy + dz * dz))
        if (a.radius <= 0f && b.radius <= 0f) {
            val ox = a.hx + b.hx - dx; val oy = a.hy + b.hy - dy; val oz = a.hz + b.hz - dz
            return if (ox > 0f && oy > 0f && oz > 0f) min(ox, min(oy, oz)) else 0f
        }
        val sphere = if (a.radius > 0f) a else b; val box = if (a.radius > 0f) b else a
        val sy = if (sphere === a) ay else by; val boxY = if (box === a) ay else by
        val cx = (sphere.x).coerceIn(box.x - box.hx, box.x + box.hx)
        val cy = sy.coerceIn(boxY - box.hy, boxY + box.hy)
        val cz = (sphere.z).coerceIn(box.z - box.hz, box.z + box.hz)
        val ex = sphere.x - cx; val ey = sy - cy; val ez = sphere.z - cz
        return max(0f, sphere.radius - kotlin.math.sqrt(ex * ex + ey * ey + ez * ez))
    }

    // ---- the vehicle's visual extent per form (its body and every part, at rest) ---------------------

    private fun vehicleBounds(mode: VehicleMode, skin: Skin): FloatArray = VehicleExtents.of(lib, mode, skin)

    // ---- findings ----------------------------------------------------------------------------------

    private class Finding(val key: String, var frames: Int, var depth: Float, var example: String)

    private val findings = LinkedHashMap<String, Finding>()

    /** Records one frame of the defect [key]; [depth] is how far (m) the mesh reaches into the vehicle's volume, [example] describes it. */
    private fun found(key: String, depth: Float, example: () -> String) {
        val f = findings[key]
        if (f == null) findings[key] = Finding(key, 1, depth, example())
        else {
            f.frames++
            if (depth > f.depth) { f.depth = depth; f.example = example() }
        }
    }

    private class Inst(var e: Entity?, var id: MeshId) {
        val m = FloatArray(16)
    }

    // ---- the audit ---------------------------------------------------------------------------------

    /** Margin: a mesh must reach this far into the vehicle's volume to count (grazing contact is forgiven). */
    private val margin = 0.25f

    /**
     * The vehicle's hit box is smaller than what it looks like (wing tips, fins, the nose and the tail, the tower of the submarine
     * stick out of it on purpose, so that the game forgives a graze). A hazard has to reach this deep into the vehicle's volume to
     * count as "inside the vehicle".
     */
    private val hazardMargin = 1.7f

    /** Two solid things may touch, but not be this deep inside each other. */
    private val solidOverlap = 0.6f

    /** How far (m) a hazard's model may stick out of its own collision shape for the part that does so to count. */
    private val protrusionLimit = 0.45f

    /**
     * How much of a hazard's surface may stick out of its collision shape (by [protrusionLimit]): thin parts (tentacles, horns, chains,
     * legs, ropes) are not worth a hit box of their own; a body that is mostly outside its shape is a pass-through zone.
     */
    private fun allowedOutside(kind: Kind): Float = when (kind) {
        Kind.JELLY, Kind.MINE, Kind.BARREL, Kind.BALLOON -> 0.22f // tentacles, horns and a chain, the ropes and the basket
        Kind.CRAB -> 0.5f // eight thin legs
        else -> 0.12f
    }

    /** How deep (in steps of 0.25 m, from [margin]) mesh [id] drawn with [m] reaches into the box [make] builds for a margin; 0 when it does not. */
    private fun depth(id: MeshId, m: FloatArray, wb: FloatArray, make: (Float) -> Overlap.Box): Float {
        var d = 0f
        var mg = margin
        while (mg <= 3f) {
            val b = make(mg)
            if (b.hx <= 0f || b.hy <= 0f || b.hz <= 0f || !boxesTouch(wb, b) || !meshTouches(id, m, b)) break
            d = mg
            mg += 0.25f
        }
        return d
    }

    private fun audit(level: LevelSpec, seed: Long, difficulty: Difficulty) {
        val scene = SceneRenderer(NullGles(), lib)
        scene.resize(1600, 740)
        val game = Game(seed)
        game.level = level
        game.difficulty = difficulty
        val input = GameInput()
        val bot = Bot(game)
        game.startRun(seed)

        val pool = ArrayList<Inst>()
        var used = 0
        scene.renderer.litObserver = { mesh, model ->
            val id = scene.renderer.meshes.idOf(mesh)
            val e = scene.renderer.observedEntity
            if (id != null && (e != null || id == MeshId.CONFETTI_FISH)) {
                if (used == pool.size) pool.add(Inst(null, id))
                val inst = pool[used++]
                inst.e = e; inst.id = id
                System.arraycopy(model, 0, inst.m, 0, 16)
            }
        }

        val dt = 1f / 60f
        var t = 0f
        val wb = FloatArray(6)
        val box = Overlap.Box()
        val tag = "${level.name}"
        var frame = 0
        var calm = 0f
        while (t < 520f && !(game.phase == Phase.VICTORY && game.phaseTime > 7f) && game.phase != Phase.GAME_OVER) {
            bot.control(input, dt)
            game.update(dt, input)
            scene.rig.update(game, dt)
            while (game.pollSfx() != null) { /* drain */ }
            used = 0
            scene.render(game)
            t += dt
            val p = game.player
            if (!p.alive || (game.phase != Phase.RUN && game.phase != Phase.FINALE && game.phase != Phase.VICTORY)) continue
            val leg = game.leg
            val vb = vehicleBounds(leg.mode, p.visual.skin)
            val ground = if (game.phase == Phase.FINALE || game.phase == Phase.VICTORY) game.finaleGroundY else game.look.groundY
            val finale = game.phase == Phase.FINALE || game.phase == Phase.VICTORY

            // the vehicle where it is now (shrunk by a margin) ...
            val vy = p.y + p.bounce
            // (a margin never takes more than 55 % of a half extent: a small vehicle keeps a core to test against)
            val nowOf = { mg: Float ->
                val hx = (vb[3] - vb[0]) / 2; val hy = (vb[4] - vb[1]) / 2; val hz = (vb[5] - vb[2]) / 2
                Overlap.Box(
                    p.x + (vb[0] + vb[3]) / 2, vy + (vb[1] + vb[4]) / 2, (vb[2] + vb[5]) / 2,
                    hx - min(mg, 0.55f * hx), hy - min(mg, 0.55f * hy), hz - min(mg, 0.55f * hz),
                )
            }
            // ... and everywhere it can go in this leg (only when it is not changing shape)
            val lo = if (leg.mode == VehicleMode.CAR) vy else leg.minY
            val hi = if (leg.mode == VehicleMode.CAR) vy else game.flightCeiling(leg)
            val reachOf = { mg: Float ->
                Overlap.Box(
                    (vb[0] + vb[3]) / 2, (lo + vb[1] + hi + vb[4]) / 2, (vb[2] + vb[5]) / 2,
                    leg.maxX + (vb[3] - vb[0]) / 2 - mg, (hi - lo + vb[4] - vb[1]) / 2 - mg, (vb[5] - vb[2]) / 2 - mg,
                )
            }
            val now = nowOf(margin)
            val reach = reachOf(margin)
            val roaming = !game.xf.active && !finale

            // the fish of the welcome: not inside the car, the merfolk or the flags
            if (finale) {
                val carBox = nowOf(0.05f)
                val fb = FloatArray(6)
                for (k in 0 until used) {
                    val fish = pool[k]
                    if (fish.e != null) continue
                    worldBounds(fish.id, fish.m, fb)
                    if (boxesTouch(fb, carBox) && meshTouches(fish.id, fish.m, carBox)) {
                        found("$tag: a fish of the welcome swims through the car", 0.05f) { "fish at (%.1f, %.1f, %.1f); car x=%.1f, celebration time %.1f".format(fish.m[12], fish.m[13], fish.m[14], p.x, game.celebrationTime) }
                    }
                    val fishBox = Overlap.Box((fb[0] + fb[3]) / 2, (fb[1] + fb[4]) / 2, (fb[2] + fb[5]) / 2, (fb[3] - fb[0]) / 2 - 0.05f, (fb[4] - fb[1]) / 2 - 0.05f, (fb[5] - fb[2]) / 2 - 0.05f)
                    for (j in 0 until used) {
                        val other = pool[j]
                        val oe = other.e ?: continue
                        if (oe.kind != Kind.SEASCAPE || oe.variant < 17) continue // the merfolk (17..19) and the flags (20)
                        worldBounds(other.id, other.m, wb)
                        if (boxesTouch(wb, fishBox) && meshTouches(other.id, other.m, fishBox)) {
                            found("$tag: a fish of the welcome swims through ${other.id}", 0.05f) { "fish at (%.1f, %.1f, %.1f); ${other.id} at x=%.1f z=%.1f, celebration time %.1f".format(fish.m[12], fish.m[13], fish.m[14], oe.x, oe.z, game.celebrationTime) }
                        }
                    }
                }
            }

            // solid things must not be inside each other (traffic that catches up with slower traffic, ships longer than the gap between them)
            if (frame++ % 3 == 0) {
                for (a in 0 until used) {
                    val ea = pool[a].e ?: continue
                    // (what has passed the vehicle is behind the camera: nobody sees it)
                    if (ea.kind.category != Category.HAZARD || ea.hit || ea.kind == Kind.STORM || ea.z > 6f || ea.z < -350f) continue
                    if (ea.radius <= 0f && ea.hx <= 0f) continue
                    for (b in a + 1 until used) {
                        val eb = pool[b].e ?: continue
                        if (eb === ea || eb.kind.category != Category.HAZARD || eb.hit || eb.kind == Kind.STORM || (eb.radius <= 0f && eb.hx <= 0f) || eb.z > 6f) continue
                        if (abs(ea.z - eb.z) > 70f || abs(ea.x - eb.x) > 40f) continue
                        // (boulders and crabs are thrown across the road at the last moment, at a lane the spawner cannot know a car will be in)
                        if (ea.kind == Kind.BOULDER || ea.kind == Kind.CRAB || eb.kind == Kind.BOULDER || eb.kind == Kind.CRAB) continue
                        val pen = penetration(ea, game.entityY(ea), eb, game.entityY(eb))
                        if (pen > solidOverlap) {
                            val names = listOf("${ea.kind}/${pool[a].id}", "${eb.kind}/${pool[b].id}").sorted()
                            found("$tag: ${names[0]} and ${names[1]} are inside each other", pen) { "%.1f m deep: ${ea.kind} at (%.1f, %.1f, %.1f) and ${eb.kind} at (%.1f, %.1f, %.1f)".format(pen, ea.x, game.entityY(ea), ea.z, eb.x, game.entityY(eb), eb.z) }
                        }
                    }
                }
            }

            // the camera must not be inside anything either (it swings around the car in the arrival, and trucks pass it in the next lane).
            // A hazard that passes close to a flying vehicle is a near miss, and what passes through a blinking vehicle passes through the
            // camera behind it a moment later: neither counts.
            calm = if (p.invuln > 0f || p.blink > 0f) 0f else calm + dt
            val cam = scene.rig.camera
            val camBox = Overlap.Box(cam.ex, cam.ey, cam.ez, 0.3f, 0.3f, 0.3f)
            for (k in 0 until used) {
                val inst = pool[k]
                val e = inst.e ?: continue
                if (e.hit || e.kind == Kind.CLOUD || e.kind.category == Category.PICKUP || e.kind.category == Category.GATE && e.kind != Kind.GATE_ARCH) continue
                if (e.kind.category == Category.HAZARD && (leg.mode != VehicleMode.CAR || calm < 0.7f)) continue
                worldBounds(inst.id, inst.m, wb)
                if (boxesTouch(wb, camBox) && meshTouches(inst.id, inst.m, camBox)) {
                    found("$tag: the camera is inside ${e.kind}/${inst.id}", 0.3f) { "camera at (%.1f, %.1f, %.1f), ${e.kind} at x=%.1f y=%.1f z=%.1f; ${game.phase} leg ${game.legIndex}".format(cam.ex, cam.ey, cam.ez, e.x, game.entityY(e), e.z) }
                }
            }

            for (k in 0 until used) {
                val inst = pool[k]
                val e = inst.e
                worldBounds(inst.id, inst.m, wb)
                if (e == null) continue // the fish of the welcome were audited above
                if (e.hit) continue
                val cat = e.kind.category
                val describe = { "${e.kind}/${e.variant} ${inst.id} at x=%.1f y=%.1f z=%.1f scale=%.2f ry=%.0f; player x=%.1f y=%.1f, leg ${game.legIndex} ${leg.mode}".format(e.x, game.entityY(e), e.z, e.scale, e.ry, p.x, p.y) }
                when {
                    e.kind == Kind.CLOUD -> Unit // fluff: a plane flying through the edge of a cloud is what a cloud is for
                    cat == Category.DECOR || e.kind == Kind.GATE_ARCH -> {
                        // scenery: must not reach into where the vehicle can be
                        if (roaming && boxesTouch(wb, reach) && meshTouches(inst.id, inst.m, reach)) {
                            found("$tag: ${e.kind}/${inst.id} stands in the ${leg.mode} lanes", depth(inst.id, inst.m, wb, reachOf), describe)
                        } else if (!roaming && boxesTouch(wb, now) && meshTouches(inst.id, inst.m, now)) {
                            found("$tag: ${e.kind}/${inst.id} cuts through the ${leg.mode} (while it changes or arrives)", depth(inst.id, inst.m, wb, nowOf), describe)
                        }
                    }
                    cat == Category.HAZARD -> {
                        // a hazard that is visibly inside the vehicle must have hit it (unless it is blinking: it cannot be hurt then)
                        val core = nowOf(hazardMargin)
                        // (the tentacles of a jellyfish and the chain of a mine are thin and hang far below the body that hits: brushing them is no hit)
                        val thin = e.kind == Kind.JELLY || e.kind == Kind.MINE
                        if (!thin && p.invuln <= 0f && p.blink <= 0f && !game.xf.active && boxesTouch(wb, core) && meshTouches(inst.id, inst.m, core)) {
                            found("$tag: ${e.kind}/${inst.id} is inside the ${leg.mode} and did not hit it", depth(inst.id, inst.m, wb, nowOf), describe)
                        }
                        // and what a hazard looks like is what it hits: its model stays within its collision shape
                        if (e.age < 0.1f && (e.radius > 0f || e.hx > 0f)) {
                            val o = outside(e, inst.id, inst.m, game.entityY(e), protrusionLimit)
                            if (o.share > allowedOutside(e.kind)) found("$tag: ${e.kind}/${inst.id} sticks out of its hit shape", o.share * 100f) { "${(o.share * 100).toInt()} percent of its surface is more than $protrusionLimit m outside, up to ${"%.1f".format(o.farthest)} m; " + describe() }
                        }
                    }
                }
            }
        }
        scene.renderer.litObserver = null
    }

    @Test
    fun sceneryStaysOutOfTheLanesAndHazardsHitWhatTheyTouch() {
        for (level in Levels.ALL) {
            for (seed in 1L..4L) audit(level, seed, Difficulty.NORMAL)
            audit(level, 9L, Difficulty.HARD)
            audit(level, 10L, Difficulty.EASY)
        }
        for (mode in VehicleMode.values()) {
            for (skin in Skin.values()) {
                val b = vehicleBounds(mode, skin); val h = VehicleModel.hitHalfExtents(mode)
                println("VEHICLE $mode/$skin: visual x %.2f..%.2f y %.2f..%.2f z %.2f..%.2f; hit half extents (%.2f, %.2f, %.2f) x(0.88, 0.9, 0.9)".format(b[0], b[3], b[1], b[4], b[2], b[5], h[0], h[1], h[2]))
            }
        }
        println("PASS-THROUGH AUDIT: ${findings.size} kinds of defect")
        for (f in findings.values.sortedBy { it.key }) println("  ${f.key}: ${f.frames} frames, up to %.2f deep; e.g. ${f.example}".format(f.depth))
        assertTrue(findings.isEmpty(), "pass-through defects:\n" + findings.values.sortedBy { it.key }.joinToString("\n") { "  ${it.key}: ${it.frames} frames, up to %.2f deep; e.g. ${it.example}".format(it.depth) })
    }
}
