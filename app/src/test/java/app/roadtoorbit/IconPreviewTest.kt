package app.roadtoorbit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the adaptive launcher icon (background + foreground, circular mask) for a visual check. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IconPreviewTest {
    @Test fun renderIcon() {
        val ctx = RuntimeEnvironment.getApplication()
        val size = 432
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val mask = Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) }
        c.clipPath(mask)
        for (id in intArrayOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground)) {
            val d = ctx.getDrawable(id)!!
            d.setBounds(0, 0, size, size)
            d.draw(c)
        }
        val dir = File(System.getProperty("hudDir") ?: "build/hud")
        dir.mkdirs()
        File(dir, "icon.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(dir, "icon.png").length() > 1000)
    }
}
