package app.roadtoorbit

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import app.roadtoorbit.audio.AudioEngine
import app.roadtoorbit.game.Phase
import kotlin.math.abs

/** Hosts the 3D view and the HUD overlay; owns the lifecycle, keyboard / gamepad input and audio. */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var bridge: UiBridge
    private lateinit var audio: AudioEngine
    private lateinit var renderer: GameRenderer
    private lateinit var glView: GameView
    private lateinit var hud: HudView
    private lateinit var tilt: TiltController

    private var keyLeft = false
    private var keyRight = false
    private var keyUp = false
    private var keyDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        prefs = Prefs(this)
        bridge = UiBridge().apply {
            soundOn = prefs.soundOn
            tiltOn = prefs.tiltOn
        }
        audio = AudioEngine(applicationContext, bridge.soundOn)
        renderer = GameRenderer(bridge, audio, prefs) { strong ->
            glView.post {
                glView.performHapticFeedback(if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
        glView = GameView(this, renderer)
        hud = HudView(this, bridge)
        tilt = TiltController(this, bridge)

        val root = FrameLayout(this)
        root.addView(glView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(hud, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        tilt.start()
        enterImmersive()
    }

    override fun onPause() {
        // never leave a run ticking in the background
        if (isGameplay()) bridge.paused = true
        bridge.input.neutral()
        tilt.stop()
        renderer.onAppPaused()
        glView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    private fun isGameplay(): Boolean {
        val p = bridge.hud.latest.phase
        return p == Phase.RUN || p == Phase.COUNTDOWN
    }

    @Suppress("DEPRECATION")
    private fun enterImmersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    @Deprecated("Predictive back is disabled in the manifest so this classic callback keeps working")
    override fun onBackPressed() {
        when (bridge.hud.latest.phase) {
            Phase.RUN, Phase.COUNTDOWN -> bridge.paused = !bridge.paused
            Phase.MENU -> super.onBackPressed()
            else -> bridge.post(UiAction.MENU)
        }
    }

    // ---- keyboard / gamepad ---------------------------------------------------------------------------

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        val first = down && e.repeatCount == 0
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> { keyLeft = down; applyKeys(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> { keyRight = down; applyKeys(); return true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> { keyUp = down; applyKeys(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> { keyDown = down; applyKeys(); return true }
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_B -> {
                bridge.input.boost = down; return true
            }
            KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_BUTTON_X -> {
                if (first) bridge.input.requestTransform()
                return true
            }
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                if (first) confirm()
                if (e.keyCode == KeyEvent.KEYCODE_SPACE) bridge.input.boost = down
                return true
            }
            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_START -> {
                if (first && isGameplay()) bridge.paused = !bridge.paused
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    /** Enter / A button: start, resume or retry depending on where we are. */
    private fun confirm() {
        if (bridge.paused) { bridge.paused = false; return }
        when (bridge.hud.latest.phase) {
            Phase.MENU, Phase.VICTORY -> { bridge.tiltRecalibrate = true; bridge.post(UiAction.PLAY) }
            Phase.GAME_OVER -> bridge.post(UiAction.RETRY)
            Phase.RUN -> bridge.input.requestTransform()
            else -> Unit
        }
    }

    private fun applyKeys() {
        if (bridge.stickActive) return
        bridge.input.steerX = (if (keyRight) 1f else 0f) - (if (keyLeft) 1f else 0f)
        bridge.input.steerY = (if (keyUp) 1f else 0f) - (if (keyDown) 1f else 0f)
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK && e.action == MotionEvent.ACTION_MOVE) {
            if (!bridge.stickActive) {
                val x = dead(e.getAxisValue(MotionEvent.AXIS_X)).let { if (it == 0f) dead(e.getAxisValue(MotionEvent.AXIS_HAT_X)) else it }
                val y = dead(-e.getAxisValue(MotionEvent.AXIS_Y)).let { if (it == 0f) dead(-e.getAxisValue(MotionEvent.AXIS_HAT_Y)) else it }
                bridge.input.steerX = x
                bridge.input.steerY = y
            }
            val trigger = maxOf(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS))
            bridge.input.boost = trigger > 0.4f
            return true
        }
        return super.onGenericMotionEvent(e)
    }

    private fun dead(v: Float): Float = if (abs(v) < 0.15f) 0f else v
}
