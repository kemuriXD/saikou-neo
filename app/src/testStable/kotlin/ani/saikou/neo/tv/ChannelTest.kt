package ani.saikou.neo.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
class ChannelTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun usesTheStableApplicationId() {
        assertEquals("ani.saikou.neo", app.packageName)
    }

    @Test
    fun isLabelledSaikouNeo() {
        assertEquals("Saikou Neo", app.getString(R.string.app_name))
    }

    // The rail, the phone and the splash lockup draw the round mark. Its top half is the light icon pink.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun drawsTheRoundMarkInPink() {
        val mark = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
        app.getDrawable(R.drawable.saikou_neo_mark)!!.apply { setBounds(0, 0, 72, 72) }.draw(Canvas(mark))

        assertEquals(0xFFFF4BA8.toInt(), mark.getPixel(38, 8))
    }

    @Test
    fun drawsTheIconInPink() {
        assertEquals(0xFFFF0080.toInt(), app.getColor(R.color.saikou_neo_icon_pink))
        assertEquals(0xFFFF4BA8.toInt(), app.getColor(R.color.saikou_neo_icon_pink_light))
        assertEquals(0xFFC50053.toInt(), app.getColor(R.color.saikou_neo_icon_pink_deep))
    }
}
