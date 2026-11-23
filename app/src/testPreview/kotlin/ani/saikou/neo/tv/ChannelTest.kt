package ani.saikou.neo.tv

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

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

    @Test
    fun drawsTheSymbolInGreen() {
        assertEquals(0xFF4FD18B.toInt(), app.getColor(R.color.saikou_neo_symbol))
    }

    @Test
    fun drawsTheIconInAlphaGreen() {
        assertEquals(0xFF00FF7F.toInt(), app.getColor(R.color.saikou_neo_icon_pink))
        assertEquals(0xFF4BFFA2.toInt(), app.getColor(R.color.saikou_neo_icon_pink_light))
        assertEquals(0xFF00C572.toInt(), app.getColor(R.color.saikou_neo_icon_pink_deep))
    }
}
