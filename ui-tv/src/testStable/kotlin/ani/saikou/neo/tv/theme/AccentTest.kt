package ani.saikou.neo.tv.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class AccentTest {
    private val colors = NeoColors()

    @Test
    fun usesTheSaikouLogoPinks() {
        assertEquals(Color(0xFFFF0080), colors.pink)
        assertEquals(Color(0xFFFF4BA8), colors.pinkLight)
        assertEquals(Color(0xFFC50053), colors.pinkDeep)
    }
}
