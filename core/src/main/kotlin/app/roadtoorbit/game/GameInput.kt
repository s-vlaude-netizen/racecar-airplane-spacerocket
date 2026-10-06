package app.roadtoorbit.game

/**
 * What the player is doing right now. The Android layer (touch, tilt, keyboard) writes into this;
 * the game reads it once per update. One-shot actions are consumed with the take* functions.
 */
class GameInput {
    /** Steering in [-1, 1]; x: right is positive, y: up is positive. */
    @Volatile var steerX = 0f
    @Volatile var steerY = 0f
    @Volatile var boost = false

    @Volatile private var transformRequested = false
    @Volatile private var startRequested = false

    fun requestTransform() { transformRequested = true }
    fun requestStart() { startRequested = true }

    fun takeTransform(): Boolean {
        val r = transformRequested
        transformRequested = false
        return r
    }

    fun takeStart(): Boolean {
        val r = startRequested
        startRequested = false
        return r
    }

    fun clearActions() {
        transformRequested = false
        startRequested = false
    }

    fun neutral() {
        steerX = 0f; steerY = 0f; boost = false
    }
}
