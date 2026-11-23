package ani.saikou.neo.phone

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// The app takes the TV module's override first, so its ChannelTest can't see a missing phone override.
@RunWith(RobolectricTestRunner::class)
class IconColoursTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun turnsTheIconPinksGreen() {
        assertEquals(0xFF00FF7F.toInt(), app.getColor(R.color.saikou_neo_icon_pink))
        assertEquals(0xFF4BFFA2.toInt(), app.getColor(R.color.saikou_neo_icon_pink_light))
        assertEquals(0xFF00C572.toInt(), app.getColor(R.color.saikou_neo_icon_pink_deep))
    }
}
