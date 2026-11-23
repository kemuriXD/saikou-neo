package ani.saikou.neo.phone.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class AccentTest {
    @Test
    fun usesTheSaikouLogoPinks() {
        assertEquals(Color(0xFFFF0080), PhoneColors.pink)
        assertEquals(Color(0xFFFF4BA8), PhoneColors.pinkLight)
    }
}
