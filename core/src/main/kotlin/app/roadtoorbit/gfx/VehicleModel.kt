package app.roadtoorbit.gfx

import app.roadtoorbit.gfx.Col.rgb
import app.roadtoorbit.gl.Gles
import app.roadtoorbit.gl.GpuMesh
import app.roadtoorbit.math.Mat4
import app.roadtoorbit.math.Mathx
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The three forms of the player's vehicle. */
enum class VehicleMode { CAR, PLANE, ROCKET }

/**
 * Numbers the game needs to dress the ocean-skinned vehicle with effects. Vehicle space: +X right, +Y up, the nose toward -Z;
 * every point is {x, y, z} in metres, indexed by form (car, plane, rocket) where it differs.
 */
object OceanVehicle {
    /** The propeller turns at this rate (degrees per second, clockwise seen from behind), driven by [VehicleVisual.time]: about three turns a second. */
    const val PROP_DEG_PER_SEC = 1080f

    /** The waterplane's pontoons: the keel (lowest point) and the waterline of a float at rest, in vehicle y. The sea touches the keel when the vehicle's y is [KEEL_Y] below the water. */
    const val KEEL_Y = -0.49f
    const val WATERLINE_Y = -0.30f

    /**
     * The waterplane is an amphibian: a wheel in the middle of each float, tucked into it, which [VehicleVisual.gear] lowers by [GEAR_DROP] for a
     * landing on the airstrip. With the gear down the lowest point of the vehicle (the tyres) is [GEAR_BOTTOM] below its origin.
     */
    const val GEAR_DROP = 0.42f
    const val GEAR_BOTTOM = 0.92f

    /** Where the bubble / exhaust emitters sit per form (car, plane, rocket): the car's thruster, the waterplane's propeller wash, the submarine's nozzle mouth. */
    val EXHAUST: Array<FloatArray> = arrayOf(
        floatArrayOf(0f, 0.52f, 2.35f),
        floatArrayOf(0f, 0.62f, -3.1f),
        floatArrayOf(0f, 0.62f, 4.1f),
    )

    /** The pontoons' sterns (left and right): spray / wake of the waterplane skimming the sea. */
    val WAKE: Array<FloatArray> = arrayOf(floatArrayOf(-0.95f, -0.45f, 1.8f), floatArrayOf(0.95f, -0.45f, 1.8f))
}

/**
 * How the vehicle is dressed: the same three forms (car, plane, rocket) in the livery and with the extra parts of the
 * level's world. [OCEAN] turns them into the seabed car, the waterplane and the submarine-rocket.
 */
enum class Skin { STANDARD, OCEAN }

/** Where a part sits, how it is oriented/scaled and how it is tinted in one vehicle form. */
class Pose(
    val x: Float, val y: Float, val z: Float,
    val rx: Float = 0f, val ry: Float = 0f, val rz: Float = 0f,
    val sx: Float = 1f, val sy: Float = 1f, val sz: Float = 1f,
    val tint: Int = Col.WHITE,
)

private fun hidden(x: Float, y: Float, z: Float, tint: Int = Col.WHITE) =
    Pose(x, y, z, sx = 0.001f, sy = 0.001f, sz = 0.001f, tint = tint)

/** What a part does besides sitting where its pose puts it: the wheels spin (the front ones steer), the propeller turns with the clock. */
enum class PartRole { NONE, FRONT_WHEEL, REAR_WHEEL, PROPELLER, GEAR }

/** One articulated piece of the vehicle with a pose per form. */
class PartDef(
    val mesh: MeshId,
    val car: Pose, val plane: Pose, val rocket: Pose,
    val delay: Float = 0f,
    val spec: Float = 0f, val shine: Float = 30f, val emissive: Float = 0f,
    val springy: Boolean = false,
    val role: PartRole = PartRole.NONE,
    val glass: Boolean = false,
) {
    fun pose(mode: VehicleMode): Pose = when (mode) {
        VehicleMode.CAR -> car
        VehicleMode.PLANE -> plane
        VehicleMode.ROCKET -> rocket
    }
}

/**
 * A beam of light that belongs to a lamp of the ocean skin: the cone's pose in vehicle space per form. The pose's tint carries
 * the colour and, in its alpha, how strong the beam is in that form (0 = no beam). It follows the progress of [lamp], so that
 * it moves with the lamp while the vehicle transforms.
 */
class BeamDef(val lamp: PartDef, val car: Pose, val plane: Pose, val rocket: Pose) {
    fun pose(mode: VehicleMode): Pose = when (mode) {
        VehicleMode.CAR -> car
        VehicleMode.PLANE -> plane
        VehicleMode.ROCKET -> rocket
    }
}

/** Parameters describing how the vehicle looks this frame. */
class VehicleVisual {
    /** How the vehicle is dressed (see [Skin]); the part table, the body livery and the flame follow it. */
    var skin = Skin.STANDARD
    var from = VehicleMode.CAR
    var to = VehicleMode.CAR
    /** 0..1 progress of the morph from [from] to [to]; irrelevant while from == to. */
    var morph = 0f
    var wheelSpinDeg = 0f
    var steerDeg = 0f
    /** 0..1 engine flame intensity (scaled further by form). */
    var flame = 0f
    /** The clock in seconds: it flickers the flame and, in the ocean skin, turns the propeller (so it turns when the vehicle stands still). */
    var time = 0f
    /**
     * 0..1: how strongly the vehicle's lights shine. In the ocean skin, above ~0.02, the searchlights throw translucent beams
     * (two narrow ones from the car, a wide one from the submarine, none from the waterplane) whose strength follows this
     * value, so it should be high where it is dark and 0 in daylight. The standard skin ignores it.
     */
    var beam = 0f

    /** 0..1: how far the amphibian waterplane's wheels are lowered out of its floats (the ocean skin's plane form); 0 in flight, 1 for the landing. */
    var gear = 0f
    var visible = true

    fun settle(mode: VehicleMode) {
        from = mode; to = mode; morph = 0f
    }

    /** The form the vehicle mostly resembles right now. */
    val dominant: VehicleMode get() = if (from == to || morph < 0.5f) from else to

    /** Blend weight of [mode] in the current pose (0..1). */
    fun weight(mode: VehicleMode): Float {
        if (from == to) return if (mode == from) 1f else 0f
        var w = 0f
        if (mode == from) w += 1f - morph
        if (mode == to) w += morph
        return w
    }
}

/** Static description of the vehicle: the part table and the lofted-body station data. */
object VehicleModel {
    private val RED = rgb(0xE3232B)
    private val WHITE = rgb(0xF2F4F8)
    private val DARK = rgb(0x24272E)
    private val SILVER = rgb(0xD3DAE4)
    private val METAL = rgb(0x6C7585)
    private val ORANGE = rgb(0xFF6618)

    /** Part table (order = draw order). Poses are in vehicle space: nose toward -Z, up +Y. */
    val parts: Array<PartDef> = arrayOf(
        // --- wheels / booster pods (tyre then rim) -----------------------------------------------
        wheel(MeshId.WHEEL_TIRE, -0.98f, 0.36f, -1.40f, -1, PartRole.FRONT_WHEEL, rgb(0x15161A), rgb(0xEFF1F5), 0.04f),
        wheel(MeshId.WHEEL_TIRE, 0.98f, 0.36f, -1.40f, 1, PartRole.FRONT_WHEEL, rgb(0x15161A), rgb(0xEFF1F5), 0.04f),
        wheel(MeshId.WHEEL_TIRE, -0.98f, 0.36f, 1.38f, -1, PartRole.REAR_WHEEL, rgb(0x15161A), rgb(0xEFF1F5), 0.08f),
        wheel(MeshId.WHEEL_TIRE, 0.98f, 0.36f, 1.38f, 1, PartRole.REAR_WHEEL, rgb(0x15161A), rgb(0xEFF1F5), 0.08f),
        wheel(MeshId.WHEEL_RIM, -0.98f, 0.36f, -1.40f, -1, PartRole.FRONT_WHEEL, SILVER, rgb(0xC62828), 0.04f),
        wheel(MeshId.WHEEL_RIM, 0.98f, 0.36f, -1.40f, 1, PartRole.FRONT_WHEEL, SILVER, rgb(0xC62828), 0.04f),
        wheel(MeshId.WHEEL_RIM, -0.98f, 0.36f, 1.38f, -1, PartRole.REAR_WHEEL, SILVER, rgb(0xC62828), 0.08f),
        wheel(MeshId.WHEEL_RIM, 0.98f, 0.36f, 1.38f, 1, PartRole.REAR_WHEEL, SILVER, rgb(0xC62828), 0.08f),

        // --- wings (left, right) -----------------------------------------------------------------
        PartDef(
            MeshId.FIN,
            car = Pose(-0.55f, 0.55f, 0.2f, rz = 90f, sx = 1f, sy = 0.01f, sz = 0.6f, tint = RED),
            plane = Pose(-0.38f, 0.58f, 0.35f, rz = 86f, sy = 2.7f, tint = WHITE),
            rocket = Pose(-0.50f, 0.62f, 1.95f, rz = 90f, sy = 0.9f, sz = 0.8f, tint = RED),
            delay = 0.22f, spec = 0.25f, springy = true,
        ),
        PartDef(
            MeshId.FIN,
            car = Pose(0.55f, 0.55f, 0.2f, rz = -90f, sx = 1f, sy = 0.01f, sz = 0.6f, tint = RED),
            plane = Pose(0.38f, 0.58f, 0.35f, rz = -86f, sy = 2.7f, tint = WHITE),
            rocket = Pose(0.50f, 0.62f, 1.95f, rz = -90f, sy = 0.9f, sz = 0.8f, tint = RED),
            delay = 0.22f, spec = 0.25f, springy = true,
        ),

        // --- tail: vertical fin, bottom fin, horizontal stabilisers / car spoiler ----------------
        PartDef(
            MeshId.FIN,
            car = hidden(0f, 0.7f, 1.6f, RED),
            plane = Pose(0f, 0.78f, 1.95f, sy = 1.0f, sz = 0.85f, tint = RED),
            rocket = Pose(0f, 1.05f, 2.30f, sy = 0.85f, sz = 0.8f, tint = RED),
            delay = 0.30f, spec = 0.25f, springy = true,
        ),
        PartDef(
            MeshId.FIN,
            car = hidden(0f, 0.5f, 1.6f, RED),
            plane = hidden(0f, 0.5f, 2.0f, RED),
            rocket = Pose(0f, 0.19f, 2.30f, rz = 180f, sy = 0.85f, sz = 0.8f, tint = RED),
            delay = 0.30f, spec = 0.25f, springy = true,
        ),
        PartDef(
            MeshId.FIN,
            car = Pose(-0.55f, 0.90f, 1.80f, rz = 90f, sy = 0.62f, sz = 0.42f, tint = DARK),
            plane = Pose(-0.22f, 0.66f, 2.25f, rz = 90f, sy = 1.15f, sz = 0.75f, tint = WHITE),
            rocket = hidden(-0.10f, 0.62f, 2.5f, RED),
            delay = 0.28f, spec = 0.25f, springy = true,
        ),
        PartDef(
            MeshId.FIN,
            car = Pose(0.55f, 0.90f, 1.80f, rz = -90f, sy = 0.62f, sz = 0.42f, tint = DARK),
            plane = Pose(0.22f, 0.66f, 2.25f, rz = -90f, sy = 1.15f, sz = 0.75f, tint = WHITE),
            rocket = hidden(0.10f, 0.62f, 2.5f, RED),
            delay = 0.28f, spec = 0.25f, springy = true,
        ),

        // --- cockpit glass -----------------------------------------------------------------------
        PartDef(
            MeshId.CANOPY,
            car = Pose(0f, 0.78f, 0.28f, sx = 0.80f, sy = 0.44f, sz = 1.25f, tint = Col.WHITE),
            plane = Pose(0f, 0.97f, -0.85f, sx = 0.36f, sy = 0.36f, sz = 1.05f, tint = Col.WHITE),
            rocket = Pose(0f, 1.03f, -1.70f, sx = 0.27f, sy = 0.22f, sz = 0.34f, tint = Col.WHITE),
            delay = 0.05f, spec = 0.9f, shine = 70f, glass = true,
        ),

        // --- engine -----------------------------------------------------------------------------
        PartDef(
            MeshId.NOZZLE,
            car = hidden(0f, 0.55f, 2.0f, METAL),
            plane = Pose(0f, 0.62f, 2.95f, sx = 0.62f, sy = 0.62f, sz = 0.9f, tint = METAL),
            rocket = Pose(0f, 0.62f, 3.55f, sx = 1.25f, sy = 1.25f, sz = 1.3f, tint = METAL),
            delay = 0.20f, spec = 0.6f, shine = 40f,
        ),

        // --- lights ------------------------------------------------------------------------------
        PartDef(
            MeshId.LIGHT,
            car = Pose(-0.62f, 0.50f, -2.18f, sx = 0.30f, sy = 0.10f, sz = 0.06f, tint = rgb(0xFFF2C4)),
            plane = Pose(-3.05f, 0.78f, 1.5f, sx = 0.14f, sy = 0.10f, sz = 0.22f, tint = rgb(0xFF2A2A)),
            rocket = Pose(-1.33f, 0.62f, 2.85f, sx = 0.14f, sy = 0.14f, sz = 0.22f, tint = rgb(0x66E6FF)),
            delay = 0.34f, emissive = 0.95f,
        ),
        PartDef(
            MeshId.LIGHT,
            car = Pose(0.62f, 0.50f, -2.18f, sx = 0.30f, sy = 0.10f, sz = 0.06f, tint = rgb(0xFFF2C4)),
            plane = Pose(3.05f, 0.78f, 1.5f, sx = 0.14f, sy = 0.10f, sz = 0.22f, tint = rgb(0x2CFF6A)),
            rocket = Pose(1.33f, 0.62f, 2.85f, sx = 0.14f, sy = 0.14f, sz = 0.22f, tint = rgb(0x66E6FF)),
            delay = 0.34f, emissive = 0.95f,
        ),
        PartDef(
            MeshId.LIGHT,
            car = Pose(-0.66f, 0.60f, 2.19f, sx = 0.34f, sy = 0.09f, sz = 0.05f, tint = rgb(0xFF1E2A)),
            plane = hidden(-0.2f, 0.6f, 2.0f, rgb(0xFF1E2A)),
            rocket = hidden(-0.2f, 0.6f, 2.0f, rgb(0xFF1E2A)),
            delay = 0.0f, emissive = 0.95f,
        ),
        PartDef(
            MeshId.LIGHT,
            car = Pose(0.66f, 0.60f, 2.19f, sx = 0.34f, sy = 0.09f, sz = 0.05f, tint = rgb(0xFF1E2A)),
            plane = hidden(0.2f, 0.6f, 2.0f, rgb(0xFF1E2A)),
            rocket = hidden(0.2f, 0.6f, 2.0f, rgb(0xFF1E2A)),
            delay = 0.0f, emissive = 0.95f,
        ),
    )

    /** Where the engine flame starts and how big it is, per form. */
    val flamePose = arrayOf(
        Pose(0f, 0.52f, 2.30f, sx = 0.14f, sy = 0.14f, sz = 1.3f),
        Pose(0f, 0.62f, 3.45f, sx = 0.30f, sy = 0.30f, sz = 2.2f),
        Pose(0f, 0.62f, 4.25f, sx = 0.62f, sy = 0.62f, sz = 3.8f),
    )

    /** Half extents of the vehicle's collision body per form (x, y, z) and its centre height. */
    fun hitHalfExtents(mode: VehicleMode): FloatArray = when (mode) {
        VehicleMode.CAR -> floatArrayOf(0.9f, 0.55f, 2.0f)
        VehicleMode.PLANE -> floatArrayOf(1.7f, 0.5f, 2.0f)
        VehicleMode.ROCKET -> floatArrayOf(0.9f, 0.9f, 2.6f)
    }

    private fun wheel(
        mesh: MeshId, x: Float, y: Float, z: Float, side: Int, role: PartRole,
        carTint: Int, rocketTint: Int, delay: Float,
    ): PartDef {
        val hiddenAt = hidden(side * 0.25f, 0.55f, if (z < 0) -0.4f else 0.9f, carTint)
        // pods sit on the diagonals around the tail and point along Z (tyre mesh axis is X)
        val podY = if (role == PartRole.FRONT_WHEEL) 0.62f + 0.56f else 0.62f - 0.56f
        val pod = Pose(side * 0.56f, podY, 2.2f, ry = 90f, sx = 5.2f, sy = 0.50f, sz = 0.50f, tint = rocketTint)
        return PartDef(
            mesh,
            car = Pose(x, y, z, tint = carTint),
            plane = hiddenAt,
            rocket = pod,
            delay = delay,
            spec = if (mesh == MeshId.WHEEL_RIM) 0.8f else 0.1f,
            shine = 40f,
            role = role,
        )
    }

    // ---- lofted body ---------------------------------------------------------------------------

    private const val STATIONS = 12
    private val U = floatArrayOf(0f, 0.035f, 0.09f, 0.17f, 0.27f, 0.39f, 0.52f, 0.65f, 0.77f, 0.88f, 0.96f, 1f)

    /** Ring angles in degrees: denser at the top so a racing stripe gets crisp edges. */
    private val RING_DEG = floatArrayOf(
        0f, 15f, 30f, 45f, 60f, 72f, 84f, 90f, 96f, 108f, 120f, 135f, 150f, 165f, 180f,
        195f, 210f, 225f, 240f, 255f, 270f, 285f, 300f, 315f, 330f, 345f,
    )
    private val RING = RING_DEG.size
    private val RING_COS = FloatArray(RING) { cos(RING_DEG[it] * Mat4.DEG2RAD) }
    private val RING_SIN = FloatArray(RING) { sin(RING_DEG[it] * Mat4.DEG2RAD) }

    /** Upper bound for the vertex count of a built body (used to size the dynamic buffer). */
    val maxBodyVertices: Int = ((STATIONS - 1) * RING * 6) + 2 * RING * 3

    private class Form(val length: Float, val n: Float, val w: FloatArray, val h: FloatArray, val c: FloatArray)

    private val forms = arrayOf(
        Form(
            4.4f, 3.2f,
            w = floatArrayOf(0.50f, 0.72f, 0.88f, 0.95f, 0.97f, 0.97f, 0.96f, 0.96f, 0.95f, 0.92f, 0.88f, 0.84f),
            h = floatArrayOf(0.11f, 0.17f, 0.22f, 0.26f, 0.28f, 0.29f, 0.29f, 0.29f, 0.28f, 0.27f, 0.25f, 0.23f),
            c = floatArrayOf(0.36f, 0.40f, 0.45f, 0.50f, 0.53f, 0.55f, 0.56f, 0.57f, 0.57f, 0.57f, 0.56f, 0.55f),
        ),
        Form(
            5.6f, 2.4f,
            w = floatArrayOf(0.03f, 0.14f, 0.27f, 0.38f, 0.45f, 0.47f, 0.46f, 0.43f, 0.37f, 0.30f, 0.22f, 0.17f),
            h = floatArrayOf(0.03f, 0.14f, 0.27f, 0.38f, 0.45f, 0.47f, 0.46f, 0.43f, 0.37f, 0.30f, 0.24f, 0.20f),
            c = FloatArray(STATIONS) { 0.61f + 0.05f * Mathx.smoothstep(0.5f, 1f, U[it]) },
        ),
        Form(
            6.4f, 2.0f,
            w = floatArrayOf(0.02f, 0.10f, 0.22f, 0.36f, 0.47f, 0.54f, 0.56f, 0.56f, 0.56f, 0.56f, 0.54f, 0.50f),
            h = floatArrayOf(0.02f, 0.10f, 0.22f, 0.36f, 0.47f, 0.54f, 0.56f, 0.56f, 0.56f, 0.56f, 0.54f, 0.50f),
            c = FloatArray(STATIONS) { 0.62f },
        ),
    )

    // scratch for body generation (single-threaded: the GL/render thread only)
    private val gx = FloatArray(STATIONS * RING)
    private val gy = FloatArray(STATIONS * RING)
    private val gz = FloatArray(STATIONS * RING)
    private val nx = FloatArray(STATIONS * RING)
    private val ny = FloatArray(STATIONS * RING)
    private val nz = FloatArray(STATIONS * RING)
    private val cx = FloatArray(STATIONS)
    private val cy = FloatArray(STATIONS)
    private val cz = FloatArray(STATIONS)

    private fun superE(v: Float, e: Float): Float = (if (v < 0f) -1f else 1f) * abs(v).pow(e)

    /**
     * Rebuilds the lofted fuselage into [out] for a morph between [from] and [to] at [t] (0..1).
     * The cross-section is a superellipse whose width/height/centre/squareness/length all blend,
     * so the car body flows smoothly into the plane fuselage and on into the rocket tube.
     */
    fun buildBody(out: MeshData, from: VehicleMode, to: VehicleMode, t: Float, skin: Skin = Skin.STANDARD) {
        out.clear()
        val table = if (skin == Skin.OCEAN) oceanForms else forms
        val a = table[from.ordinal]
        val b = table[to.ordinal]
        val k = if (from == to) 0f else Mathx.easeInOutCubic(t)
        val len = Mathx.lerp(a.length, b.length, k)
        val n = Mathx.lerp(a.n, b.n, k)
        val e = 2f / n

        for (i in 0 until STATIONS) {
            val w = Mathx.lerp(a.w[i], b.w[i], k)
            val h = Mathx.lerp(a.h[i], b.h[i], k)
            val c = Mathx.lerp(a.c[i], b.c[i], k)
            val z = (U[i] - 0.5f) * len
            cx[i] = 0f; cy[i] = c; cz[i] = z
            for (j in 0 until RING) {
                val idx = i * RING + j
                gx[idx] = w * superE(RING_COS[j], e)
                gy[idx] = c + h * superE(RING_SIN[j], e)
                gz[idx] = z
            }
        }

        // smooth normals from the grid
        for (i in 0 until STATIONS) {
            val i0 = if (i > 0) i - 1 else i
            val i1 = if (i < STATIONS - 1) i + 1 else i
            for (j in 0 until RING) {
                val jp = (j + 1) % RING
                val jm = (j + RING - 1) % RING
                val ring0 = i * RING + jm
                val ring1 = i * RING + jp
                val tx = gx[ring1] - gx[ring0]; val ty = gy[ring1] - gy[ring0]; val tz = gz[ring1] - gz[ring0]
                val l0 = i0 * RING + j
                val l1 = i1 * RING + j
                val ux = gx[l1] - gx[l0]; val uy = gy[l1] - gy[l0]; val uz = gz[l1] - gz[l0]
                var ox = ty * uz - tz * uy
                var oy = tz * ux - tx * uz
                var oz = tx * uy - ty * ux
                val l = sqrt(ox * ox + oy * oy + oz * oz)
                val idx = i * RING + j
                if (l < 1e-6f) {
                    // pinched ring at the very tip: point along the axis
                    ox = 0f; oy = 0f; oz = if (i == 0) -1f else 1f
                } else {
                    ox /= l; oy /= l; oz /= l
                }
                nx[idx] = ox; ny[idx] = oy; nz[idx] = oz
            }
        }

        val tMix = if (from == to) 0f else t
        for (i in 0 until STATIONS - 1) {
            val uMid = (U[i] + U[i + 1]) * 0.5f
            for (j in 0 until RING) {
                val jn = (j + 1) % RING
                // ring angle at the centre of this quad (handles the 345 -> 360 wrap)
                val a0 = RING_DEG[j]
                val a1 = if (jn == 0) 360f else RING_DEG[jn]
                val mid = (a0 + a1) * 0.5f * Mat4.DEG2RAD
                val colA = paint(skin, from, uMid, cos(mid), sin(mid))
                val color = if (from == to) colA else Col.mix(colA, paint(skin, to, uMid, cos(mid), sin(mid)), Mathx.smoothstep(0.1f, 0.9f, tMix))
                val vA = i * RING + j
                val vB = i * RING + jn
                val vC = (i + 1) * RING + jn
                val vD = (i + 1) * RING + j
                // (A, B, C) and (A, C, D): CCW seen from outside
                vert(out, vA, color); vert(out, vB, color); vert(out, vC, color)
                vert(out, vA, color); vert(out, vC, color); vert(out, vD, color)
            }
        }

        // end caps (nose faces -Z, tail faces +Z)
        val noseColor = capColor(skin, from, to, tMix, nose = true)
        val tailColor = capColor(skin, from, to, tMix, nose = false)
        for (j in 0 until RING) {
            val jn = (j + 1) % RING
            out.vertex(cx[0], cy[0], cz[0], 0f, 0f, -1f, noseColor)
            vertFlat(out, jn, 0, 0f, 0f, -1f, noseColor)
            vertFlat(out, j, 0, 0f, 0f, -1f, noseColor)
            val last = STATIONS - 1
            out.vertex(cx[last], cy[last], cz[last], 0f, 0f, 1f, tailColor)
            vertFlat(out, j, last, 0f, 0f, 1f, tailColor)
            vertFlat(out, jn, last, 0f, 0f, 1f, tailColor)
        }
    }

    private fun vert(out: MeshData, idx: Int, color: Int) {
        out.vertex(gx[idx], gy[idx], gz[idx], nx[idx], ny[idx], nz[idx], color)
    }

    private fun vertFlat(out: MeshData, j: Int, station: Int, fx: Float, fy: Float, fz: Float, color: Int) {
        val idx = station * RING + j
        out.vertex(gx[idx], gy[idx], gz[idx], fx, fy, fz, color)
    }

    private fun capColor(skin: Skin, from: VehicleMode, to: VehicleMode, t: Float, nose: Boolean): Int {
        fun one(m: VehicleMode): Int = when (m) {
            VehicleMode.CAR -> DARK
            VehicleMode.PLANE -> if (nose) RED else rgb(0x3A3D45)
            VehicleMode.ROCKET -> if (nose) RED else rgb(0x3A3D45)
        }
        fun oneOcean(m: VehicleMode): Int = when (m) {
            VehicleMode.CAR -> O_UNDER
            VehicleMode.PLANE -> ORANGE
            VehicleMode.ROCKET -> if (nose) ORANGE else rgb(0x3A3D45)
        }
        if (skin == Skin.OCEAN) return if (from == to) oneOcean(from) else Col.mix(oneOcean(from), oneOcean(to), t)
        return if (from == to) one(from) else Col.mix(one(from), one(to), t)
    }

    /** Livery for one form at body position (u along the length, ring direction cos/sin). */
    private fun paint(skin: Skin, mode: VehicleMode, u: Float, c: Float, s: Float): Int =
        if (skin == Skin.OCEAN) paintOcean(mode, u, c, s) else paintStandard(mode, u, c, s)

    private fun paintStandard(mode: VehicleMode, u: Float, c: Float, s: Float): Int {
        return when (mode) {
            VehicleMode.CAR -> when {
                s < -0.45f -> DARK
                u < 0.03f -> DARK
                s > 0.2f && abs(c) < 0.14f -> WHITE // racing stripe
                else -> RED
            }
            VehicleMode.PLANE -> when {
                s < -0.6f -> rgb(0xB9C0CB)
                u < 0.12f -> RED
                s > 0.5f && abs(c) < 0.16f -> RED
                u > 0.93f -> rgb(0x3A3D45)
                else -> WHITE
            }
            VehicleMode.ROCKET -> when {
                u < 0.30f -> RED
                u > 0.50f && u < 0.62f -> RED
                u > 0.88f && u < 0.94f -> RED
                u > 0.95f -> rgb(0x3A3D45)
                else -> WHITE
            }
        }
    }

    // ---- the ocean skin ------------------------------------------------------------------------
    // Waterplane (PLANE): yellow, white and navy with orange tips, two pontoons, a propeller. Submarine-rocket (ROCKET): teal
    // and white with orange bands, conning tower, periscope, portholes, ballast pods. Seabed car (CAR): orange with a white
    // stripe, chunky tyres, a bubble dome, a roof light bar, twin searchlights. Orange and navy run through all three.

    private val O_YELLOW = rgb(0xFFC41F)
    private val O_NAVY = rgb(0x1B3C78)
    private val O_TEAL = rgb(0x1CB5AE)
    private val O_TEAL_DARK = rgb(0x0E6F73)
    private val O_UNDER = rgb(0x1B2230)
    private val O_TYRE = rgb(0x15171C)
    private val O_STEEL = rgb(0x4B5870)
    private val O_AQUA = rgb(0x7DF2FF)
    private val O_LAMP = rgb(0xFFF3CC)
    private val O_AMBER = rgb(0xFFB41E)
    private val O_TAIL = rgb(0xFF2A22)

    /** The ocean's lofts: the car keeps the standard one; the waterplane has a blunter nose for the propeller, the submarine a rounded bow. */
    private val oceanForms = arrayOf(
        forms[0],
        Form(
            5.6f, 2.3f,
            w = floatArrayOf(0.17f, 0.31f, 0.42f, 0.48f, 0.50f, 0.50f, 0.47f, 0.41f, 0.32f, 0.23f, 0.15f, 0.11f),
            h = floatArrayOf(0.17f, 0.31f, 0.42f, 0.48f, 0.50f, 0.50f, 0.47f, 0.42f, 0.34f, 0.26f, 0.19f, 0.15f),
            c = FloatArray(STATIONS) { 0.61f + 0.05f * Mathx.smoothstep(0.5f, 1f, U[it]) },
        ),
        Form(
            6.4f, 2.0f,
            w = floatArrayOf(0.20f, 0.36f, 0.46f, 0.52f, 0.55f, 0.56f, 0.56f, 0.56f, 0.56f, 0.54f, 0.50f, 0.44f),
            h = floatArrayOf(0.20f, 0.36f, 0.46f, 0.52f, 0.55f, 0.56f, 0.56f, 0.56f, 0.56f, 0.54f, 0.50f, 0.44f),
            c = FloatArray(STATIONS) { 0.62f },
        ),
    )

    private fun paintOcean(mode: VehicleMode, u: Float, c: Float, s: Float): Int = when (mode) {
        VehicleMode.CAR -> when {
            s < -0.45f -> O_UNDER
            u < 0.03f -> O_UNDER
            s > 0.2f && abs(c) < 0.14f -> WHITE // the stripe, with a navy line on each side
            s > 0.2f && abs(c) < 0.26f -> O_NAVY
            else -> ORANGE
        }
        VehicleMode.PLANE -> when {
            u < 0.11f -> ORANGE
            u > 0.93f -> ORANGE
            s < -0.3f -> WHITE
            abs(s) < 0.3f -> O_NAVY // the stripe along the flanks
            else -> O_YELLOW
        }
        VehicleMode.ROCKET -> when {
            u < 0.10f -> ORANGE
            u < 0.17f -> WHITE
            u > 0.95f -> rgb(0x3A3D45)
            u > 0.88f -> ORANGE
            u > 0.77f -> WHITE
            s < -0.5f -> O_TEAL_DARK
            else -> O_TEAL
        }
    }

    private fun hid(x: Float, y: Float, z: Float, tint: Int, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f) =
        Pose(x, y, z, rx, ry, rz, 0.001f, 0.001f, 0.001f, tint)

    /** Tyres and rims of the car (chunky) that become the ballast pods of the submarine; hidden on the waterplane. */
    private fun oceanWheel(mesh: MeshId, x: Float, z: Float, side: Int, role: PartRole, carTint: Int, podTint: Int, delay: Float): PartDef {
        val rim = mesh == MeshId.WHEEL_RIM
        val car = if (rim) Pose(x, 0.50f, z, sx = 1.66f, sy = 1.34f, sz = 1.34f, tint = carTint) else Pose(x, 0.50f, z, sx = 1.52f, sy = 1.40f, sz = 1.40f, tint = carTint)
        val podY = if (role == PartRole.FRONT_WHEEL) 0.62f + 0.56f else 0.62f - 0.56f
        val pod = if (rim) Pose(side * 0.56f, podY, 2.15f, ry = 90f, sx = 6.0f, sy = 0.60f, sz = 0.60f, tint = podTint) else Pose(side * 0.56f, podY, 2.15f, ry = 90f, sx = 5.6f, sy = 0.56f, sz = 0.56f, tint = podTint)
        return PartDef(
            mesh, car = car, plane = hid(side * 0.25f, 0.55f, if (z < 0) -0.4f else 0.9f, carTint), rocket = pod,
            delay = delay, spec = if (rim) 0.8f else 0.1f, shine = 40f, role = role, emissive = if (rim) 0f else 0.25f,
        )
    }

    /** A float strut: a navy box from the pontoon up to the fuselage, leaning inward. */
    private fun strut(side: Int, z: Float) = PartDef(
        MeshId.LIGHT,
        car = hid(side * 0.3f, 0.4f, z, O_NAVY),
        plane = Pose(side * 0.625f, 0.13f, z, rz = side * 50f, sx = 0.075f, sy = 0.9f, sz = 0.12f, tint = O_NAVY),
        rocket = hid(side * 0.3f, 0.4f, z, O_NAVY),
        delay = 0f, spec = 0.2f,
    )

    /** A wheel of the amphibian: in the middle of a float, hidden in it until the gear goes down (see [OceanVehicle.GEAR_DROP]); only the waterplane has them. */
    private fun gearWheel(side: Int, z: Float) = PartDef(
        MeshId.WHEEL_TIRE,
        car = hid(side * 0.95f, -0.28f, z, O_TYRE),
        plane = Pose(side * 0.95f, -0.28f, z, sx = 0.80f, sy = 0.62f, sz = 0.62f, tint = O_TYRE),
        rocket = hid(side * 0.95f, -0.28f, z, O_TYRE),
        delay = 0f, spec = 0.1f, shine = 40f, role = PartRole.GEAR,
    )

    private fun pontoon(side: Int) = PartDef(
        MeshId.PONTOON,
        car = hid(side * 0.4f, 0.4f, 0.3f, WHITE),
        plane = Pose(side * 0.95f, -0.30f, 0.15f, sx = 0.42f, sy = 0.38f, sz = 3.3f, tint = WHITE),
        rocket = hid(side * 0.4f, 0.4f, 0.3f, WHITE),
        delay = 0f, spec = 0.35f, shine = 40f,
    )

    /** An elliptical wing: a pontoon lying along X with its pointed end at the tip; root hidden inside the fuselage. */
    private fun wing(side: Int) = PartDef(
        MeshId.PONTOON,
        car = hid(side * 0.3f, 0.62f, 0.15f, O_YELLOW, ry = -side * 90f),
        plane = Pose(side * 1.025f, 0.66f, 0f, ry = -side * 90f, sx = 1.25f, sy = 0.13f, sz = 4.05f, tint = O_YELLOW),
        rocket = hid(side * 0.3f, 0.62f, 0.15f, O_YELLOW, ry = -side * 90f),
        delay = 0.22f, spec = 0.3f, springy = true,
    )

    /** A round window on the hull, 35 degrees above the waterline, facing outward and up. */
    private fun porthole(side: Int, z: Float): PartDef {
        val a = 35f * Mat4.DEG2RAD
        val x = side * 0.545f * cos(a)
        val y = 0.62f + 0.545f * sin(a)
        return PartDef(
            MeshId.PORTHOLE,
            car = hid(x * 0.5f, 0.62f, z, O_AQUA),
            plane = hid(x * 0.5f, 0.62f, z, O_AQUA),
            rocket = Pose(x, y, z, rx = 35f, ry = if (side < 0) 90f else -90f, sx = 0.34f, sy = 0.34f, sz = 0.34f, tint = O_AQUA),
            delay = 0.30f, emissive = 0.9f,
        )
    }

    private fun navLight(side: Int, planeTint: Int, lensX: Float) = PartDef(
        MeshId.LIGHT,
        car = Pose(lensX, 1.455f, 0.10f, sx = 0.2f, sy = 0.1f, sz = 0.17f, tint = O_AMBER),
        plane = Pose(side * 2.9f, 0.68f, 0f, sx = 0.14f, sy = 0.10f, sz = 0.22f, tint = planeTint),
        rocket = Pose(side * 1.33f, 0.62f, 2.85f, sx = 0.14f, sy = 0.14f, sz = 0.22f, tint = rgb(0x66E6FF)),
        delay = 0.34f, emissive = 0.95f,
    )

    private fun lens(x: Float) = PartDef(
        MeshId.LIGHT,
        car = Pose(x, 1.455f, 0.10f, sx = 0.2f, sy = 0.1f, sz = 0.17f, tint = rgb(0xFFF6DC)),
        plane = hid(x, 1.0f, 0.1f, rgb(0xFFF6DC)),
        rocket = hid(x, 1.1f, 0.1f, rgb(0xFFF6DC)),
        delay = 0.30f, emissive = 0.95f,
    )

    private fun tailLight(side: Int) = PartDef(
        MeshId.LIGHT,
        car = Pose(side * 0.66f, 0.60f, 2.19f, sx = 0.38f, sy = 0.11f, sz = 0.06f, tint = O_TAIL),
        plane = hid(side * 0.2f, 0.6f, 2.0f, O_TAIL),
        rocket = hid(side * 0.2f, 0.6f, 2.0f, O_TAIL),
        delay = 0f, emissive = 0.95f,
    )

    private fun stabiliser(side: Int) = PartDef(
        MeshId.FIN,
        car = Pose(side * 0.55f, 0.90f, 1.80f, rz = -side * 90f, sy = 0.62f, sz = 0.42f, tint = O_NAVY),
        plane = Pose(side * 0.22f, 0.66f, 1.9f, rz = -side * 90f, sy = 1.1f, sz = 0.7f, tint = O_YELLOW),
        rocket = Pose(side * 0.50f, 0.62f, 2.30f, rz = -side * 90f, sy = 0.95f, sz = 0.85f, tint = ORANGE),
        emissive = 0.25f,
        delay = 0.28f, spec = 0.25f, springy = true,
    )

    /** The searchlights: one at the submarine's nose that splits into the car's twin lamps. */
    private val lampL = PartDef(
        MeshId.SEARCHLIGHT,
        car = Pose(-0.58f, 0.54f, -2.12f, sx = 0.5f, sy = 0.5f, sz = 0.5f, tint = O_LAMP),
        plane = hid(0f, 0.62f, -2.8f, O_LAMP),
        rocket = Pose(0f, 0.62f, -3.14f, sx = 0.5f, sy = 0.5f, sz = 0.5f, tint = O_LAMP),
        delay = 0.30f, spec = 0.3f, emissive = 0.85f,
    )
    private val lampR = PartDef(
        MeshId.SEARCHLIGHT,
        car = Pose(0.58f, 0.54f, -2.12f, sx = 0.5f, sy = 0.5f, sz = 0.5f, tint = O_LAMP),
        plane = hid(0f, 0.62f, -2.8f, O_LAMP),
        rocket = hid(0f, 0.62f, -3.1f, O_LAMP),
        delay = 0.30f, spec = 0.3f, emissive = 0.85f,
    )

    /** Part table of the ocean skin (order = draw order), poses in vehicle space like [parts]. */
    val oceanParts: Array<PartDef> = arrayOf(
        oceanWheel(MeshId.WHEEL_TIRE, -1.02f, -1.34f, -1, PartRole.FRONT_WHEEL, O_TYRE, ORANGE, 0.04f),
        oceanWheel(MeshId.WHEEL_TIRE, 1.02f, -1.34f, 1, PartRole.FRONT_WHEEL, O_TYRE, ORANGE, 0.04f),
        oceanWheel(MeshId.WHEEL_TIRE, -1.02f, 1.36f, -1, PartRole.REAR_WHEEL, O_TYRE, ORANGE, 0.08f),
        oceanWheel(MeshId.WHEEL_TIRE, 1.02f, 1.36f, 1, PartRole.REAR_WHEEL, O_TYRE, ORANGE, 0.08f),
        oceanWheel(MeshId.WHEEL_RIM, -1.02f, -1.34f, -1, PartRole.FRONT_WHEEL, SILVER, O_NAVY, 0.04f),
        oceanWheel(MeshId.WHEEL_RIM, 1.02f, -1.34f, 1, PartRole.FRONT_WHEEL, SILVER, O_NAVY, 0.04f),
        oceanWheel(MeshId.WHEEL_RIM, -1.02f, 1.36f, -1, PartRole.REAR_WHEEL, SILVER, O_NAVY, 0.08f),
        oceanWheel(MeshId.WHEEL_RIM, 1.02f, 1.36f, 1, PartRole.REAR_WHEEL, SILVER, O_NAVY, 0.08f),
        wing(-1), wing(1),
        PartDef(
            MeshId.FIN,
            car = hid(0f, 0.7f, 1.6f, ORANGE),
            plane = Pose(0f, 0.80f, 1.75f, sy = 1.05f, sz = 0.8f, tint = ORANGE),
            rocket = Pose(0f, 1.05f, 2.30f, sy = 0.85f, sz = 0.8f, tint = ORANGE),
            emissive = 0.25f,
            delay = 0.30f, spec = 0.25f, springy = true,
        ),
        PartDef(
            MeshId.FIN,
            car = hid(0f, 0.5f, 1.6f, ORANGE),
            plane = hid(0f, 0.5f, 2.0f, ORANGE),
            rocket = Pose(0f, 0.19f, 2.30f, rz = 180f, sy = 0.85f, sz = 0.8f, tint = ORANGE),
            emissive = 0.25f,
            delay = 0.30f, spec = 0.25f, springy = true,
        ),
        stabiliser(-1), stabiliser(1),
        pontoon(-1), pontoon(1),
        gearWheel(-1, -0.6f), gearWheel(-1, 0.9f), gearWheel(1, -0.6f), gearWheel(1, 0.9f),
        strut(-1, -0.55f), strut(-1, 0.75f), strut(1, -0.55f), strut(1, 0.75f),
        PartDef(
            MeshId.PROP,
            car = hid(0f, 0.62f, -2.9f, WHITE),
            plane = Pose(0f, 0.62f, -2.74f, sx = 0.98f, sy = 0.98f, sz = 1.0f, tint = WHITE),
            rocket = hid(0f, 0.62f, -2.9f, WHITE),
            delay = 0.10f, spec = 0.3f, role = PartRole.PROPELLER,
        ),
        PartDef(
            MeshId.CANOPY,
            car = Pose(0f, 0.74f, 0.08f, sx = 0.88f, sy = 0.66f, sz = 1.30f, tint = Col.WHITE),
            plane = Pose(0f, 0.98f, -0.95f, sx = 0.38f, sy = 0.40f, sz = 1.1f, tint = Col.WHITE),
            rocket = hid(0f, 1.1f, -1.0f, Col.WHITE),
            delay = 0.05f, spec = 0.9f, shine = 70f, glass = true,
        ),
        PartDef(
            MeshId.NOZZLE,
            car = hid(0f, 0.55f, 2.0f, O_STEEL),
            plane = hid(0f, 0.62f, 2.8f, O_STEEL),
            rocket = Pose(0f, 0.62f, 3.55f, sx = 1.25f, sy = 1.25f, sz = 1.3f, tint = O_STEEL),
            delay = 0.20f, spec = 0.6f, shine = 40f,
        ),
        navLight(-1, rgb(0xFF2A2A), -0.36f), navLight(1, rgb(0x2CFF6A), 0.36f),
        tailLight(-1), tailLight(1),
        PartDef(
            MeshId.LIGHT,
            car = Pose(0f, 1.405f, 0.10f, sx = 1.0f, sy = 0.07f, sz = 0.20f, tint = O_NAVY),
            plane = hid(0f, 1.0f, 0.1f, O_NAVY),
            rocket = hid(0f, 1.1f, 0.1f, O_NAVY),
            delay = 0.30f, spec = 0.2f,
        ),
        lens(-0.12f), lens(0.12f),
        PartDef(
            MeshId.TOWER,
            car = hid(0f, 0.9f, -0.5f, O_TEAL),
            plane = hid(0f, 0.9f, -0.5f, O_TEAL),
            rocket = Pose(0f, 1.02f, -0.55f, sx = 1.0f, sy = 0.8f, sz = 1.05f, tint = O_TEAL),
            delay = 0.30f, spec = 0.3f,
        ),
        PartDef(
            MeshId.PERISCOPE,
            car = Pose(0.62f, 0.77f, 1.50f, sx = 0.7f, sy = 0.75f, sz = 0.7f, tint = WHITE),
            plane = hid(0.3f, 1.0f, 1.0f, WHITE),
            rocket = Pose(0f, 1.78f, -0.40f, sx = 0.5f, sy = 0.5f, sz = 0.5f, tint = WHITE),
            delay = 0.30f, spec = 0.4f,
        ),
        porthole(-1, -1.9f), porthole(-1, 0.95f), porthole(-1, 1.55f),
        porthole(1, -1.9f), porthole(1, 0.95f), porthole(1, 1.55f),
        lampL, lampR,
    )

    /** The light beams (drawn additively when [VehicleVisual.beam] is on): the car's two narrow ones and the submarine's wide one. */
    val oceanBeams: Array<BeamDef> = arrayOf(
        BeamDef(
            lampL,
            car = Pose(-0.58f, 0.54f, -2.22f, rx = -2.5f, ry = 1.5f, sx = 4.6f, sy = 4.6f, sz = 20f, tint = Col.rgba(0xFFF0C8, 1f)),
            plane = Pose(0f, 0.62f, -3.24f, sx = 11f, sy = 11f, sz = 30f, tint = Col.rgba(0xD8F6FF, 0f)),
            rocket = Pose(0f, 0.62f, -3.24f, sx = 11f, sy = 11f, sz = 30f, tint = Col.rgba(0xD8F6FF, 1f)),
        ),
        BeamDef(
            lampR,
            car = Pose(0.58f, 0.54f, -2.22f, rx = -2.5f, ry = -1.5f, sx = 4.6f, sy = 4.6f, sz = 20f, tint = Col.rgba(0xFFF0C8, 1f)),
            plane = Pose(0.58f, 0.54f, -2.22f, rx = -2.5f, ry = -1.5f, sx = 4.6f, sy = 4.6f, sz = 20f, tint = Col.rgba(0xFFF0C8, 0f)),
            rocket = Pose(0.58f, 0.54f, -2.22f, rx = -2.5f, ry = -1.5f, sx = 4.6f, sy = 4.6f, sz = 20f, tint = Col.rgba(0xFFF0C8, 0f)),
        ),
    )

    /** The exhaust flame of the ocean skin per form (no flame for the waterplane, which has a propeller). */
    val oceanFlamePose = arrayOf(
        Pose(0f, 0.52f, 2.30f, sx = 0.14f, sy = 0.14f, sz = 1.3f),
        Pose(0f, 0.62f, 2.9f, sx = 0.10f, sy = 0.10f, sz = 0.4f),
        Pose(0f, 0.62f, 4.25f, sx = 0.62f, sy = 0.62f, sz = 3.8f),
    )

    /** How much of [VehicleVisual.flame] each form of the ocean skin shows: the car a thruster puff, the waterplane none, the submarine all. */
    val oceanFlameLevel = floatArrayOf(0.6f, 0f, 1f)

    fun partsFor(skin: Skin): Array<PartDef> = if (skin == Skin.OCEAN) oceanParts else parts

    /** Eases a part's progress: staggered start ([PartDef.delay]) then smooth (or springy) motion. */
    fun partProgress(part: PartDef, morph: Float): Float {
        val local = Mathx.clamp01((morph - part.delay) / 0.65f)
        return if (part.springy) Mathx.easeOutBack(local) else Mathx.easeInOutCubic(local)
    }
}

/**
 * Draws the vehicle: regenerates the lofted body when the morph changed, then every articulated
 * part, then the engine flame (additive).
 */
class VehicleRenderer(gl: Gles, private val renderer: Renderer) {
    private val body = GpuMesh.dynamic(gl, VehicleModel.maxBodyVertices + 64)
    private val bodyData = MeshData(VehicleModel.maxBodyVertices + 64)
    private var builtFrom: VehicleMode? = null
    private var builtTo: VehicleMode? = null
    private var builtMorph = -1f
    private var builtSkin = Skin.STANDARD

    private val mat = Material()
    private val partM = FloatArray(16)
    private val worldM = FloatArray(16)

    // the ocean skin's propeller: where it sits (before it turns), whether it is out, and its tint, for the blur drawn behind it
    private val propM = FloatArray(16)
    private val ghostM = FloatArray(16)
    private var propShown = false
    private var propTint = Col.WHITE

    /** [vehicleMatrix] places the vehicle in the world (translation + yaw/pitch/roll). */
    fun draw(v: VehicleVisual, vehicleMatrix: FloatArray) {
        if (!v.visible) return
        val ocean = v.skin == Skin.OCEAN
        val tMorph = if (v.from == v.to) 0f else v.morph
        if (builtFrom != v.from || builtTo != v.to || builtSkin != v.skin || abs(builtMorph - tMorph) > 1e-4f) {
            VehicleModel.buildBody(bodyData, v.from, v.to, tMorph, v.skin)
            body.update(bodyData)
            builtFrom = v.from; builtTo = v.to; builtMorph = tMorph; builtSkin = v.skin
        }

        // fuselage
        if (ocean) { mat.reset().gloss(0.55f, 48f).rim(0.12f, 0.34f, 0.50f, 2.6f); mat.emissive = 0.22f } else mat.reset().gloss(0.55f, 48f).rim(0.10f, 0.14f, 0.22f, 3f)
        renderer.drawLit(body, vehicleMatrix, mat)
        propShown = false
        val propAngle = (v.time * OceanVehicle.PROP_DEG_PER_SEC) % 360f

        val steerW = v.weight(VehicleMode.CAR)
        for (part in VehicleModel.partsFor(v.skin)) {
            val p = VehicleModel.partProgress(part, tMorph)
            val a = part.pose(v.from)
            val b = if (v.from == v.to) a else part.pose(v.to)
            val x = Mathx.lerp(a.x, b.x, p); val y = Mathx.lerp(a.y, b.y, p); val z = Mathx.lerp(a.z, b.z, p)
            var rx = Mathx.lerp(a.rx, b.rx, p); var ry = Mathx.lerp(a.ry, b.ry, p); val rz = Mathx.lerp(a.rz, b.rz, p)
            val sx = Mathx.lerp(a.sx, b.sx, p); val sy = Mathx.lerp(a.sy, b.sy, p); val sz = Mathx.lerp(a.sz, b.sz, p)
            val tint = Col.mix(a.tint, b.tint, Mathx.clamp01(p))

            if (part.role == PartRole.FRONT_WHEEL) ry += v.steerDeg * steerW
            val yy = if (part.role == PartRole.GEAR) y - OceanVehicle.GEAR_DROP * v.gear * v.weight(VehicleMode.PLANE) else y // (the wheels are only there in the plane's form)
            Mat4.setTrs(partM, x, yy, z, rx, ry, rz, sx, sy, sz)
            if (part.role == PartRole.GEAR) Mat4.rotateX(partM, v.wheelSpinDeg)
            // wheel spin happens about the part's own X axis, innermost
            if ((part.role == PartRole.FRONT_WHEEL || part.role == PartRole.REAR_WHEEL) && steerW > 0.01f) Mat4.rotateX(partM, v.wheelSpinDeg)
            if (part.role == PartRole.PROPELLER) {
                // the propeller turns clockwise seen from behind, driven by the clock
                Mat4.copy(propM, partM)
                propShown = sx > 0.05f
                propTint = tint
                Mat4.rotateZ(partM, -propAngle)
            }
            Mat4.multiply(worldM, vehicleMatrix, partM)

            mat.reset().tintColor(tint).gloss(part.spec, part.shine)
            mat.emissive = part.emissive
            if (part.glass) mat.rim(0.35f, 0.55f, 0.85f, 2.5f)
            renderer.drawLit(renderer.meshes[part.mesh], worldM, mat)
        }

        if (ocean && propShown) drawPropBlur(vehicleMatrix, propAngle)
        drawFlame(v, vehicleMatrix)
        if (ocean && v.beam > 0.02f) drawBeams(v, vehicleMatrix, tMorph)
    }

    /** Two faint copies of the propeller trailing behind it (alpha blended), so that its turning reads as a smooth blur. */
    private fun drawPropBlur(vehicleMatrix: FloatArray, angle: Float) {
        renderer.setBlend(Blend.ALPHA)
        for (k in 1..2) {
            Mat4.copy(ghostM, propM)
            Mat4.rotateZ(ghostM, -angle + k * 24f)
            Mat4.multiply(worldM, vehicleMatrix, ghostM)
            mat.reset().tintColor(propTint).gloss(0.1f, 20f)
            mat.a = if (k == 1) 0.30f else 0.14f
            renderer.drawLit(renderer.meshes[MeshId.PROP], worldM, mat)
        }
        renderer.setBlend(Blend.OPAQUE)
    }

    /** The searchlights' beams: translucent cones, additive, drawn after everything opaque; their strength is [VehicleVisual.beam]. */
    private fun drawBeams(v: VehicleVisual, vehicleMatrix: FloatArray, tMorph: Float) {
        val beam = Mathx.clamp01(v.beam)
        var started = false
        for (bd in VehicleModel.oceanBeams) {
            val p = Mathx.clamp01(VehicleModel.partProgress(bd.lamp, tMorph))
            val a = bd.pose(v.from)
            val b = if (v.from == v.to) a else bd.pose(v.to)
            val tint = Col.mix(a.tint, b.tint, p)
            val alpha = Col.a(tint) * beam
            if (alpha < 0.01f) continue
            if (!started) { renderer.setBlend(Blend.ADDITIVE); started = true }
            Mat4.setTrs(
                partM, Mathx.lerp(a.x, b.x, p), Mathx.lerp(a.y, b.y, p), Mathx.lerp(a.z, b.z, p),
                Mathx.lerp(a.rx, b.rx, p), Mathx.lerp(a.ry, b.ry, p), Mathx.lerp(a.rz, b.rz, p),
                Mathx.lerp(a.sx, b.sx, p), Mathx.lerp(a.sy, b.sy, p), Mathx.lerp(a.sz, b.sz, p),
            )
            Mat4.multiply(worldM, vehicleMatrix, partM)
            mat.reset().tint(Col.r(tint), Col.g(tint), Col.b(tint), alpha)
            mat.emissive = 1f; mat.fogScale = 0.3f
            renderer.drawLit(renderer.meshes[MeshId.LIGHT_CONE], worldM, mat)
        }
        if (started) renderer.setBlend(Blend.OPAQUE)
    }

    private fun drawFlame(v: VehicleVisual, vehicleMatrix: FloatArray) {
        val ocean = v.skin == Skin.OCEAN
        val k = Mathx.easeInOutCubic(v.morph)
        val poses = if (ocean) VehicleModel.oceanFlamePose else VehicleModel.flamePose
        val a = poses[v.from.ordinal]
        val b = poses[v.to.ordinal]
        val t = if (v.from == v.to) 0f else Mathx.clamp01((v.morph - 0.2f) / 0.65f)
        val e = Mathx.easeInOutCubic(t)
        val level = if (ocean) {
            v.flame * Mathx.lerp(VehicleModel.oceanFlameLevel[v.from.ordinal], VehicleModel.oceanFlameLevel[v.to.ordinal], e)
        } else {
            v.flame * Mathx.lerp(
                if (v.from == VehicleMode.CAR) 0.6f else 1f,
                if (v.to == VehicleMode.CAR) 0.6f else 1f,
                e,
            )
        }
        if (level < 0.02f) return
        val flicker = 1f + 0.12f * sin(v.time * 47f) + 0.08f * sin(v.time * 83f + 1f)
        val z = Mathx.lerp(a.z, b.z, e); val y = Mathx.lerp(a.y, b.y, e)
        val rad = Mathx.lerp(a.sx, b.sx, e) * (0.6f + 0.4f * level)
        val len = Mathx.lerp(a.sz, b.sz, e) * level * flicker
        renderer.setBlend(Blend.ADDITIVE)
        Mat4.setTrs(partM, 0f, y, z - 0.05f, 0f, 0f, 0f, rad, rad, len)
        Mat4.multiply(worldM, vehicleMatrix, partM)
        // the ocean's flame is cyan: the same cone as the white-hot core, tinted
        if (ocean) mat.reset().tint(0.35f, 0.9f, 1f, 0.8f) else mat.reset().tint(1f, 1f, 1f, 0.85f)
        mat.emissive = 1f; mat.fogScale = 0.2f
        renderer.drawLit(renderer.meshes[if (ocean) MeshId.FLAME_INNER else MeshId.FLAME_OUTER], worldM, mat)
        Mat4.setTrs(partM, 0f, y, z - 0.02f, 0f, 0f, 0f, rad * 0.55f, rad * 0.55f, len * 0.62f)
        Mat4.multiply(worldM, vehicleMatrix, partM)
        mat.reset().tint(1f, 1f, 1f, 1f)
        mat.emissive = 1f; mat.fogScale = 0.2f
        renderer.drawLit(renderer.meshes[MeshId.FLAME_INNER], worldM, mat)
        renderer.setBlend(Blend.OPAQUE)
    }

    fun release() = body.release()
}
