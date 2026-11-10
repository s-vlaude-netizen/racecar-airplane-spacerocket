package app.roadtoorbit.gfx

import app.roadtoorbit.math.Mathx
import app.roadtoorbit.math.Rng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The little fish of the welcome in Atlantis: an escort that swims in from both sides and keeps company with the car, and fish
 * confetti that is thrown up in front of the car when it stops and then settles into schools that circle it. Every fish is a
 * function of the time alone (positions are relative to the car, which stands at the origin, nose toward -Z), so the whole show
 * needs no state and no allocation: [pose] writes where fish [i] is at time [t] and where it is a moment later, for its heading.
 *
 * The fish are drawn, so they must not be inside anything that is drawn: [keepClear] bends every position (the same way at any
 * moment, so the paths stay smooth) so that the fish keep out of the car, which they swim over instead, and out of the merfolk who
 * line the avenue, which the low ones stay between and the high ones fly over. The flags are strung across the avenue where
 * no fish goes (OceanSpawner.welcome).
 */
internal class CelebrationFish(seed: Int = 0xA71A) {
    companion object {
        const val ESCORT = 30
        const val CONFETTI = 96
        const val COUNT = ESCORT + CONFETTI

        /** Seconds after the start of the arrival at which the confetti is thrown. */
        const val LAUNCH_AT = 4.5f
        private const val DRAG = 1.15f
        private const val SINK = 2.6f

        // what the fish keep out of (metres, relative to the car): the car's body, with the room a fish needs (a fish is up to 1.4 m long)
        const val CAR_HALF_X = 1.4f
        const val CAR_MID_Z = 0.1f
        const val CAR_HALF_Z = 2.35f
        const val CAR_ROOF = 1.6f
        const val ROOM = 1.25f

        /** The merfolk stand at |x| >= 8.7 m, 3.7 m tall with raised arms, and are up to 1.5 m wide: below [CROWD_HEIGHT] the fish stay within this. */
        const val CROWD_FREE = 5.8f
        const val CROWD_HEIGHT = 5.8f
    }

    val r = FloatArray(COUNT)
    val g = FloatArray(COUNT)
    val b = FloatArray(COUNT)
    private val size = FloatArray(COUNT)
    private val delay = FloatArray(COUNT)
    private val phase = FloatArray(COUNT)
    private val ax = FloatArray(COUNT); private val ay = FloatArray(COUNT); private val az = FloatArray(COUNT) // escort: angle, height, depth; confetti: start
    private val vx = FloatArray(COUNT); private val vy = FloatArray(COUNT); private val vz = FloatArray(COUNT)
    private val rad = FloatArray(COUNT)
    private val spin = FloatArray(COUNT)
    private val side = FloatArray(COUNT)

    init {
        val rng = Rng(seed.toLong())
        for (i in 0 until COUNT) {
            // golden-ratio hues: neighbours never share a colour
            hsv((i * 0.61803f) % 1f, 0.85f, 1.1f, i)
            phase[i] = rng.range(0f, 6.28f)
            if (i < ESCORT) {
                size[i] = rng.range(0.9f, 1.3f)
                delay[i] = rng.range(0f, 2.2f)
                ax[i] = rng.range(0f, 6.28f)
                ay[i] = rng.range(1.2f, 4.6f) // below the flags, which hang from 7 m
                az[i] = -rng.range(4f, 20f)
                rad[i] = rng.range(4f, 11f)
                spin[i] = (if (i % 3 == 1) -1f else 1f) * rng.range(0.5f, 0.9f)
                side[i] = rng.sign()
            } else {
                size[i] = rng.range(0.7f, 1.2f)
                delay[i] = rng.range(0f, 0.4f)
                ax[i] = rng.range(-6f, 6f); ay[i] = 0.8f; az[i] = -14f + rng.range(-8f, 8f)
                vx[i] = rng.range(-6f, 6f); vy[i] = rng.range(8f, 15f); vz[i] = rng.range(-5f, 5f)
                rad[i] = rng.range(7f, 20f)
                spin[i] = (if (rng.chance(0.5f)) -1f else 1f) * rng.range(0.3f, 0.65f)
                ay[i] = ay[i]
            }
        }
    }

    private fun hsv(h: Float, s: Float, v: Float, i: Int) {
        val k = (h * 6f).toInt()
        val f = h * 6f - k
        val p = v * (1f - s); val q = v * (1f - f * s); val t = v * (1f - (1f - f) * s)
        when (k % 6) {
            0 -> { r[i] = v; g[i] = t; b[i] = p }
            1 -> { r[i] = q; g[i] = v; b[i] = p }
            2 -> { r[i] = p; g[i] = v; b[i] = t }
            3 -> { r[i] = p; g[i] = q; b[i] = v }
            4 -> { r[i] = t; g[i] = p; b[i] = v }
            else -> { r[i] = v; g[i] = p; b[i] = q }
        }
    }

    private fun place(i: Int, t: Float, out: FloatArray) {
        if (i < ESCORT) {
            val u = t - delay[i]
            val a = ax[i] + spin[i] * u
            val x = cos(a) * rad[i]
            val z = az[i] + 3.5f * sin(1.1f * u + phase[i] * 1.3f)
            // they swim in from the side they come from
            val come = Mathx.smoothstep(0f, 2.6f, u)
            out[0] = Mathx.lerp(side[i] * 42f, x, come)
            out[1] = ay[i] + 0.7f * sin(1.7f * u + phase[i])
            out[2] = z - 50f * (1f - come) // they swim out to meet the car, along the avenue
        } else {
            val u = t - LAUNCH_AT - delay[i]
            val e = 1f - exp(-DRAG * u)
            val bx = ax[i] + vx[i] * e / DRAG + 1.3f * sin(2.6f * u + phase[i])
            val by = ay[i] + vy[i] * e / DRAG - SINK * (u / DRAG - e / (DRAG * DRAG))
            val bz = az[i] + vz[i] * e / DRAG + 1.1f * cos(2.1f * u + phase[i])
            // after a few seconds the confetti joins the schools that circle the car
            val j = Mathx.smoothstep(2.4f, 4.8f, u)
            val a = spin[i] * u + phase[i]
            val ox = cos(a) * rad[i]
            val oz = -8f + sin(a) * rad[i] * 0.8f
            val oy = 6.9f + 1.0f * sin(0.7f * u + phase[i] * 2f) // above the crowd
            out[0] = Mathx.lerp(bx, ox, j)
            out[1] = Mathx.lerp(by, oy, j)
            out[2] = Mathx.lerp(bz, oz, j)
        }
    }

    /** Bends position [p] (x, y, z) so that it is neither inside the car nor inside the crowd; a smooth function of the position. */
    private fun keepClear(p: FloatArray) {
        // the crowd lines the avenue: fish lower than its heads (4.3 m with the arms up, plus the fish's own size) stay between its two
        // sides, fish above them may go anywhere
        val roam = Mathx.lerp(CROWD_FREE, 40f, Mathx.smoothstep(CROWD_HEIGHT - 0.4f, CROWD_HEIGHT + 0.4f, p[1]))
        p[0] = roam * tanh(p[0] / roam)
        // the car: a fish that would cross it rises over it, the less the farther it is from the car's outline
        val dx = max(0f, abs(p[0]) - (CAR_HALF_X + ROOM))
        val dz = max(0f, abs(p[2] - CAR_MID_Z) - (CAR_HALF_Z + ROOM))
        val away = hypot(dx, dz)
        p[1] += max(0f, CAR_ROOF + ROOM - p[1]) * (1f - Mathx.smoothstep(0f, 1.6f, away))
    }

    /**
     * Writes the position of fish [i] at time [t] to [now] and where it will be 0.05 s later to [soon]; returns its size (0 =
     * not there yet, so do not draw it).
     */
    fun pose(i: Int, t: Float, now: FloatArray, soon: FloatArray): Float {
        val start = if (i < ESCORT) delay[i] else LAUNCH_AT + delay[i]
        if (t < start) return 0f
        place(i, t, now)
        place(i, t + 0.05f, soon)
        keepClear(now)
        keepClear(soon)
        // a fish grows in over its first moments instead of popping up
        return size[i] * Mathx.smoothstep(0f, 0.5f, t - start)
    }
}
