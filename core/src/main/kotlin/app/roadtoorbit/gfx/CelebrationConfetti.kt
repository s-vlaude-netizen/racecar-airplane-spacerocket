package app.roadtoorbit.gfx

import app.roadtoorbit.math.Mathx
import app.roadtoorbit.math.Rng
import kotlin.math.cos
import kotlin.math.sin

/**
 * The confetti and the balloons of the welcome on the airstrip: strips of paper that flutter down on both sides of the plane, and balloons
 * that the crowd lets go and that rise into the sky. Like the fish of Atlantis, every piece is a function of the time alone (positions are
 * relative to the plane, which stands at the origin on the runway, nose toward -Z, and to the height of the runway), so the show needs no
 * state and allocates nothing. They are drawn, so they keep out of what is drawn: the paper falls in the strips between the wing tips and the
 * crowd (|x| from 4.8 to 12.2 m, with its sway), the balloons rise out of the crowd and start above the balloons of the people.
 */
internal class CelebrationConfetti(seed: Int = 0xBA11) {
    companion object {
        const val PAPER = 110
        const val BALLOONS = 20
        const val COUNT = PAPER + BALLOONS

        /** The paper falls from this height (m above the runway) and is gone when it reaches the ground, to come again from the top. */
        const val FALL_FROM = 16f

        /**
         * The balloons are let go at this height (m above the runway), rise at about 1.6 m/s and are gone (and come again) after [BALLOON_LIFE] seconds.
         * A balloon's string hangs 2.4 m below it and the balloons in the crowd's hands reach up to 4.6 m (with their bobbing), so these start above
         * both: their strings must not hang through the balloons of the crowd.
         */
        const val BALLOON_START_Y = 7.4f
        const val BALLOON_LIFE = 30f

        /** Seconds after the plane has stopped at which the first of it appears. */
        const val FIRST_AT = 0.15f
    }

    val r = FloatArray(COUNT)
    val g = FloatArray(COUNT)
    val b = FloatArray(COUNT)
    private val delay = FloatArray(COUNT)
    private val phase = FloatArray(COUNT)
    private val x0 = FloatArray(COUNT)
    private val z0 = FloatArray(COUNT)
    private val speed = FloatArray(COUNT)
    private val spin = FloatArray(COUNT)

    init {
        val rng = Rng(seed.toLong())
        for (i in 0 until COUNT) {
            hsv((i * 0.61803f) % 1f, if (i < PAPER) 0.85f else 0.8f, 1.05f, i)
            phase[i] = rng.range(0f, 6.28f)
            delay[i] = rng.range(0f, if (i < PAPER) 3.0f else 4.0f)
            if (i < PAPER) {
                val side = if (rng.chance(0.5f)) -1f else 1f
                x0[i] = side * rng.range(4.8f, 12.2f)
                z0[i] = rng.range(-26f, 10f)
                speed[i] = rng.range(1.4f, 2.6f)
                spin[i] = rng.range(90f, 320f) * (if (rng.chance(0.5f)) -1f else 1f)
            } else {
                val side = if (rng.chance(0.5f)) -1f else 1f
                x0[i] = side * rng.range(16.5f, 20.5f)
                z0[i] = rng.range(-42f, 28f)
                speed[i] = rng.range(1.3f, 1.9f)
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
            4 -> { r[i] = t; g[i] = p; b[i] = q }
            else -> { r[i] = v; g[i] = p; b[i] = p }
        }
    }

    /**
     * Writes where piece [i] is at [t] seconds after the plane stopped to [out] (x, y above the runway, z, then the tumble in degrees about x and about y);
     * returns its size (0 = not there yet, do not draw it). Pieces [0, PAPER) are paper, the rest are balloons.
     */
    fun pose(i: Int, t: Float, out: FloatArray): Float {
        val start = FIRST_AT + delay[i]
        if (t < start) return 0f
        val u = t - start
        if (i < PAPER) {
            // a cycle: the paper falls from the top, swaying and tumbling, and is born again at the top (a long way above the camera's view of the plane)
            val cycle = FALL_FROM / speed[i]
            val a = (u + phase[i] * 2f) % cycle
            out[1] = FALL_FROM - a * speed[i] + 0.1f
            out[0] = x0[i] + 0.9f * sin(1.9f * u + phase[i])
            out[2] = z0[i] + 0.7f * cos(1.4f * u + phase[i] * 1.7f)
            out[3] = 70f * sin(2.3f * u + phase[i]) + spin[i] * 0.1f * u
            out[4] = spin[i] * u
            return 1f * Mathx.smoothstep(0f, 0.4f, u)
        }
        val life = u % BALLOON_LIFE
        out[0] = x0[i] + (if (x0[i] < 0f) -1f else 1f) * 0.12f * life + 0.35f * sin(0.9f * u + phase[i])
        out[1] = BALLOON_START_Y + speed[i] * life
        out[2] = z0[i] + 0.4f * cos(0.7f * u + phase[i])
        out[3] = 4f * sin(1.1f * u + phase[i]); out[4] = 0f
        return Mathx.smoothstep(0f, 0.6f, life)
    }
}
