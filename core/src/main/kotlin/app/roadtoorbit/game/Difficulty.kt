package app.roadtoorbit.game

/**
 * Difficulty presets. Hard has fewer shields, tighter obstacle spacing and higher speeds, and pays more
 * points; Easy is the reverse. Best scores are tracked per difficulty.
 */
enum class Difficulty(val label: String, val maxHealth: Int, val gapScale: Float, val speedScale: Float, val scoreScale: Float) {
    EASY("EASY", 4, 1.25f, 0.92f, 0.8f),
    NORMAL("NORMAL", 3, 1.0f, 1.0f, 1.0f),
    HARD("HARD", 2, 0.78f, 1.1f, 1.4f);

    fun next(): Difficulty = values()[(ordinal + 1) % values().size]
}
