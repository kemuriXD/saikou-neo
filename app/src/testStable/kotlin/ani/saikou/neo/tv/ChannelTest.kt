package ani.saikou.neo.tv

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

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

    @Test
    fun drawsTheSymbolInPink() {
        assertEquals(0xFFFF4F91.toInt(), app.getColor(R.color.saikou_neo_symbol))
    }

    @Test
    fun drawsTheIconInPink() {
        assertEquals(0xFFFF0080.toInt(), app.getColor(R.color.saikou_neo_icon_pink))
        assertEquals(0xFFFF4BA8.toInt(), app.getColor(R.color.saikou_neo_icon_pink_light))
        assertEquals(0xFFC50053.toInt(), app.getColor(R.color.saikou_neo_icon_pink_deep))
    }
}
