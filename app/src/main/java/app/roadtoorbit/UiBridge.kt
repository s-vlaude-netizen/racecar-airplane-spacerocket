package app.roadtoorbit

import app.roadtoorbit.game.GameInput
import app.roadtoorbit.game.HudBuffer
import java.util.concurrent.ConcurrentLinkedQueue

/** Things the UI can ask the game thread to do. */
enum class UiAction { PLAY, RETRY, MENU, TOGGLE_SOUND, TOGGLE_TILT, CALIBRATE_TILT }

/**
 * Shared state between the UI thread (touch, HUD drawing, sensors) and the GL thread (simulation and
 * rendering). Everything crossing the boundary is volatile, a snapshot, or a queue.
 */
class UiBridge {
    val input = GameInput()
    val hud = HudBuffer()
    val actions = ConcurrentLinkedQueue<UiAction>()

    @Volatile var paused = false
    @Volatile var soundOn = true
    @Volatile var tiltOn = false

    /** True while a finger is on the on-screen stick (tilt steering then stands down). */
    @Volatile var stickActive = false

    /** Ask the tilt controller to re-centre on the current phone attitude (e.g. at the start of a run). */
    @Volatile var tiltRecalibrate = true

    /** Set when the GL setup fails so the HUD can show something more useful than a black screen. */
    @Volatile var fatalError: String? = null

    /** Set by the GL thread once the first frame has been rendered. */
    @Volatile var ready = false

    fun post(a: UiAction) {
        actions.add(a)
    }
}
