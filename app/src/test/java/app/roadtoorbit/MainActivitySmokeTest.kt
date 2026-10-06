package app.roadtoorbit

import android.view.KeyEvent
import android.view.ViewGroup
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The activity's startup and lifecycle code (views, preferences, audio engine, sensors, immersive mode, key
 * bindings) otherwise only ever runs on a device. There is no GL surface under Robolectric, so the render
 * thread idles, but everything on the main thread is exercised.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MainActivitySmokeTest {
    @Test fun launchesAndSurvivesTheLifecycle() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().start().resume().visible().get()

        val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        assertTrue("3D view first", root.getChildAt(0) is GameView)
        assertTrue("HUD overlay on top", root.getChildAt(1) is HudView)

        for (code in listOf(
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_BUTTON_A,
        )) {
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }

        // leaving and coming back must not crash or leave the game ticking
        controller.pause().resume().pause().stop().destroy()
    }
}
