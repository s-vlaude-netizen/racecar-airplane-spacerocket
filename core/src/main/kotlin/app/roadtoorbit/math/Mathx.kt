package app.roadtoorbit.math

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Scalar helpers used all over the game code. */
object Mathx {
    fun clamp(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v
    fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Inverse lerp, clamped to [0, 1]. */
    fun unlerp(a: Float, b: Float, v: Float): Float = if (a == b) 0f else clamp01((v - a) / (b - a))

    fun smoothstep(e0: Float, e1: Float, v: Float): Float {
        val t = unlerp(e0, e1, v)
        return t * t * (3f - 2f * t)
    }

    fun smoother(t: Float): Float {
        val x = clamp01(t)
        return x * x * x * (x * (x * 6f - 15f) + 10f)
    }

    fun easeOutCubic(t: Float): Float {
        val x = 1f - clamp01(t)
        return 1f - x * x * x
    }

    fun easeInOutCubic(t: Float): Float {
        val x = clamp01(t)
        return if (x < 0.5f) 4f * x * x * x else 1f - pow3(-2f * x + 2f) / 2f
    }

    /** Overshooting ease used for parts that "snap" out when the vehicle transforms. */
    fun easeOutBack(t: Float): Float {
        val x = clamp01(t)
        val c1 = 1.20158f
        val c3 = c1 + 1f
        val u = x - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    private fun pow3(v: Float) = v * v * v

    /** Frame-rate independent exponential approach: moves [current] toward [target]. */
    fun damp(current: Float, target: Float, rate: Float, dt: Float): Float =
        target + (current - target) * exp(-rate * dt)

    /** Moves [current] toward [target] by at most [maxStep]. */
    fun approach(current: Float, target: Float, maxStep: Float): Float {
        val d = target - current
        return if (abs(d) <= maxStep) target else current + if (d > 0f) maxStep else -maxStep
    }

    fun sign(v: Float): Float = if (v < 0f) -1f else 1f

    fun maxOf(a: Float, b: Float, c: Float): Float = max(a, max(b, c))
    fun minOf(a: Float, b: Float, c: Float): Float = min(a, min(b, c))
}
