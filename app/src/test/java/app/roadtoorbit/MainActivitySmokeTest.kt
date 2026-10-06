package app.roadtoorbit

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import java.util.Random
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private fun bridgeOf(activity: MainActivity): UiBridge {
        val f = MainActivity::class.java.getDeclaredField("bridge")
        f.isAccessible = true
        return f.get(activity) as UiBridge
    }

    /** Whatever order the system pauses, resumes, focuses and pokes the activity in, nothing may throw. */
    @Test fun survivesRandomLifecycleFocusKeysAndJoystick() {
        val rnd = Random(2026)
        val keys = intArrayOf(
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_HOME,
        )
        repeat(5) {
            val controller = Robolectric.buildActivity(MainActivity::class.java)
            val activity = controller.create().start().resume().visible().get()
            var resumed = true
            repeat(60) {
                when (rnd.nextInt(7)) {
                    0 -> if (resumed) { controller.pause(); resumed = false }
                    1 -> if (!resumed) { controller.resume(); resumed = true }
                    2 -> activity.onWindowFocusChanged(rnd.nextBoolean())
                    3, 4 -> {
                        val code = keys[rnd.nextInt(keys.size)]
                        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                        if (rnd.nextInt(4) != 0) activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) // sometimes the key-up never comes
                    }
                    5 -> {
                        val coords = MotionEvent.PointerCoords().apply {
                            setAxisValue(MotionEvent.AXIS_X, rnd.nextFloat() * 2 - 1); setAxisValue(MotionEvent.AXIS_Y, rnd.nextFloat() * 2 - 1)
                            setAxisValue(MotionEvent.AXIS_RTRIGGER, rnd.nextFloat()); setAxisValue(MotionEvent.AXIS_GAS, rnd.nextFloat())
                        }
                        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_UNKNOWN }
                        val t = SystemClock.uptimeMillis()
                        val e = MotionEvent.obtain(t, t, MotionEvent.ACTION_MOVE, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_JOYSTICK, 0)
                        activity.onGenericMotionEvent(e)
                    }
                    else -> if (resumed) { controller.pause(); controller.resume() } // a quick background/foreground flick
                }
            }
            if (!resumed) controller.resume()
            controller.pause().stop().destroy()
        }
    }

    @Test fun aCrashReportLeftBehindIsShownAtLaunchAndDismissingItClearsTheFile() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        CrashReporter.install(app)
        CrashReporter.save("The app was closed", "music", OutOfMemoryError("Failed to allocate a 32 byte allocation"))
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val bridge = bridgeOf(activity)
        assertNotNull("the previous crash is handed to the HUD", bridge.crashReport)
        assertTrue(bridge.crashReport!!.contains("OutOfMemoryError"))
        controller.pause().stop().destroy()
        CrashReporter.clear(app)

        val clean = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
        assertNull("nothing to show when the last session ended normally", bridgeOf(clean.get()).crashReport)
        clean.pause().stop().destroy()
    }
}
