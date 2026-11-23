package ani.saikou.neo.tv.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

// Alpha and beta builds have a green icon, so their accent is green too.
class AccentTest {
    private val colors = NeoColors()

    @Test
    fun usesTheGreens() {
        assertEquals(Color(0xFF00984C), colors.pink)
        assertEquals(Color(0xFF4BFFA2), colors.pinkLight)
        assertEquals(Color(0xFF007239), colors.pinkDeep)
    }
}
