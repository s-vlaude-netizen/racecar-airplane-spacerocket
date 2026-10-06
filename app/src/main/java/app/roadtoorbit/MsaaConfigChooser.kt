package app.roadtoorbit

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/**
 * Picks an RGB888 + 24-bit depth ES3 config, preferring 4x then 2x multisampling and degrading
 * gracefully to no MSAA / 16-bit depth on devices that cannot provide it.
 */
class MsaaConfigChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        val attempts = listOf(
            spec(depth = 24, samples = 4),
            spec(depth = 24, samples = 2),
            spec(depth = 24, samples = 0),
            spec(depth = 16, samples = 0),
        )
        for (attrs in attempts) {
            val count = IntArray(1)
            if (!egl.eglChooseConfig(display, attrs, null, 0, count) || count[0] <= 0) continue
            val configs = arrayOfNulls<EGLConfig>(count[0])
            if (!egl.eglChooseConfig(display, attrs, configs, count[0], count)) continue
            // prefer an exact RGB888 match with the requested depth/samples
            for (c in configs) {
                if (c == null) continue
                if (value(egl, display, c, EGL10.EGL_RED_SIZE) == 8 && value(egl, display, c, EGL10.EGL_GREEN_SIZE) == 8 &&
                    value(egl, display, c, EGL10.EGL_BLUE_SIZE) == 8
                ) return c
            }
            configs[0]?.let { return it }
        }
        throw IllegalStateException("No suitable EGL config found")
    }

    private fun value(egl: EGL10, display: EGLDisplay, config: EGLConfig, attr: Int): Int {
        val out = IntArray(1)
        return if (egl.eglGetConfigAttrib(display, config, attr, out)) out[0] else 0
    }

    private fun spec(depth: Int, samples: Int): IntArray {
        val a = ArrayList<Int>()
        fun put(vararg v: Int) { for (x in v) a.add(x) }
        put(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 0)
        put(EGL10.EGL_DEPTH_SIZE, depth, EGL10.EGL_STENCIL_SIZE, 0)
        put(EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT)
        put(EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR)
        if (samples > 0) put(EGL_SAMPLE_BUFFERS, 1, EGL_SAMPLES, samples)
        put(EGL10.EGL_NONE)
        return a.toIntArray()
    }

    private companion object {
        const val EGL_OPENGL_ES3_BIT_KHR = 0x0040
        const val EGL_SAMPLE_BUFFERS = 0x3032
        const val EGL_SAMPLES = 0x3031
    }
}
