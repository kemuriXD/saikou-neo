package ani.saikou.neo.tv.sources

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SourceIconFileTest {
    @get:Rule val folder = TemporaryFolder()

    private fun saved(icon: Drawable) = folder.newFile("icon.png").also { assertTrue(saveIcon(icon, it)) }

    @Test
    fun anIconIsSavedAsASmallSquarePng() {
        val png = BitmapFactory.decodeFile(saved(ColorDrawable(Color.RED)).path)

        assertEquals(ICON_PIXELS, png.width)
        assertEquals(ICON_PIXELS, png.height)
        assertEquals(Color.RED, png.getPixel(48, 48))
    }

    @Test
    fun anAdaptiveIconFillsTheSquareWithoutTheLaunchersMask() {
        // The foreground is a dot in the middle third of the layer, the part a launcher keeps.
        val dot =
            object : ColorDrawable(Color.WHITE) {
                override fun draw(canvas: Canvas) {
                    val third = bounds.width() / 3
                    canvas.save()
                    canvas.clipRect(bounds.left + third, bounds.top + third, bounds.right - third, bounds.bottom - third)
                    super.draw(canvas)
                    canvas.restore()
                }
            }
        val png = BitmapFactory.decodeFile(saved(AdaptiveIconDrawable(ColorDrawable(Color.BLUE), dot)).path)

        // A masked icon would have clear corners.
        assertEquals(Color.BLUE, png.getPixel(0, 0))
        assertEquals(Color.BLUE, png.getPixel(ICON_PIXELS - 1, ICON_PIXELS - 1))
        assertEquals(Color.WHITE, png.getPixel(48, 48))
        // The layer is 144 px with its middle third at 48 to 96, drawn 24 px up and left: the dot spans 24 to 72.
        assertEquals(Color.BLUE, png.getPixel(20, 48))
        assertEquals(Color.WHITE, png.getPixel(26, 48))
    }

    @Test
    fun anIconThatThrowsLeavesNoFile() {
        val broken =
            object : Drawable() {
                override fun draw(canvas: Canvas) = throw IllegalStateException("broken")

                override fun setAlpha(alpha: Int) {}

                override fun setColorFilter(colorFilter: ColorFilter?) {}

                @Deprecated("Deprecated in Java")
                override fun getOpacity() = PixelFormat.OPAQUE
            }
        val file = folder.newFile("broken.png")

        assertFalse(saveIcon(broken, file))
        assertFalse(file.exists())
    }
}
