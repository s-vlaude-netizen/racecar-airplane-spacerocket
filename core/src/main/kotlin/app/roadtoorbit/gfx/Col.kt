package app.roadtoorbit.gfx

/** Colours are packed as 0xAARRGGBB ints; these helpers keep mesh-building code readable. */
object Col {
    fun rgb(hex: Int): Int = (0xFF shl 24) or (hex and 0xFFFFFF)

    fun rgba(hex: Int, alpha: Float): Int = (to255(alpha) shl 24) or (hex and 0xFFFFFF)

    fun make(r: Float, g: Float, b: Float, a: Float = 1f): Int =
        (to255(a) shl 24) or (to255(r) shl 16) or (to255(g) shl 8) or to255(b)

    fun r(c: Int): Float = ((c shr 16) and 0xFF) / 255f
    fun g(c: Int): Float = ((c shr 8) and 0xFF) / 255f
    fun b(c: Int): Float = (c and 0xFF) / 255f
    fun a(c: Int): Float = ((c ushr 24) and 0xFF) / 255f

    /** Multiplies RGB by [f] (alpha untouched). */
    fun mul(c: Int, f: Float): Int = make(r(c) * f, g(c) * f, b(c) * f, a(c))

    fun mix(c0: Int, c1: Int, t: Float): Int = make(
        r(c0) + (r(c1) - r(c0)) * t,
        g(c0) + (g(c1) - g(c0)) * t,
        b(c0) + (b(c1) - b(c0)) * t,
        a(c0) + (a(c1) - a(c0)) * t,
    )

    fun withAlpha(c: Int, alpha: Float): Int = (to255(alpha) shl 24) or (c and 0xFFFFFF)

    private fun to255(v: Float): Int {
        val i = (v * 255f + 0.5f).toInt()
        return if (i < 0) 0 else if (i > 255) 255 else i
    }

    const val WHITE = -1 // 0xFFFFFFFF
}
