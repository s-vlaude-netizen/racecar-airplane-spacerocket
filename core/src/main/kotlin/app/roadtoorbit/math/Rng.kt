package app.roadtoorbit.math

/** Small, allocation-free, deterministic PRNG (xorshift64*) so levels and tests are reproducible. */
class Rng(seed: Long) {
    private var s: Long = mix(seed)

    init {
        if (s == 0L) s = 0x9E3779B97F4A7C15uL.toLong()
    }

    fun nextLong(): Long {
        var x = s
        x = x xor (x ushr 12)
        x = x xor (x shl 25)
        x = x xor (x ushr 27)
        s = x
        return x * 0x2545F4914F6CDD1DL
    }

    /** Uniform in [0, 1). */
    fun float(): Float = ((nextLong() ushr 40).toInt()) / 16777216f

    fun range(a: Float, b: Float): Float = a + (b - a) * float()

    /** Uniform integer in [0, n). */
    fun int(n: Int): Int = ((nextLong() ushr 33) % n).toInt()

    fun intRange(a: Int, bInclusive: Int): Int = a + int(bInclusive - a + 1)

    fun chance(p: Float): Boolean = float() < p

    fun sign(): Float = if (nextLong() < 0) -1f else 1f

    companion object {
        private fun mix(seed: Long): Long {
            var z = seed + -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
