package ani.saikou.neo.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

// Alpha and beta builds install next to stable, so they need their own ID, name
// and green art to tell them apart.
@RunWith(RobolectricTestRunner::class)
class ChannelTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun usesThePreviewApplicationId() {
        assertEquals("ani.saikou.neo.preview", app.packageName)
    }

    @Test
    fun isLabelledAsAPreview() {
        assertEquals("Saikou Neo Preview", app.getString(R.string.app_name))
    }

    // The rail, the phone and the splash lockup draw the round mark. Its top half is the light icon pink.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun drawsTheRoundMarkInGreen() {
        val mark = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
        app.getDrawable(R.drawable.saikou_neo_mark)!!.apply { setBounds(0, 0, 72, 72) }.draw(Canvas(mark))

        assertEquals(0xFF4BFFA2.toInt(), mark.getPixel(38, 8))
    }

    @Test
    fun drawsTheIconInAlphaGreen() {
        assertEquals(0xFF00FF7F.toInt(), app.getColor(R.color.saikou_neo_icon_pink))
        assertEquals(0xFF4BFFA2.toInt(), app.getColor(R.color.saikou_neo_icon_pink_light))
        assertEquals(0xFF00C572.toInt(), app.getColor(R.color.saikou_neo_icon_pink_deep))
    }
}
