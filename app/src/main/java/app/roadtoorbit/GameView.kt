package app.roadtoorbit

import android.content.Context
import android.opengl.GLSurfaceView

/** The 3D surface. The HUD and touch handling live in a transparent [HudView] drawn on top. */
class GameView(context: Context, val renderer: GameRenderer) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(MsaaConfigChooser())
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
