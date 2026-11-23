package ani.saikou.neo.phone.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

// Alpha and beta builds have a green icon, so their accent is green too.
class AccentTest {
    @Test
    fun usesTheGreens() {
        assertEquals(Color(0xFF00984C), PhoneColors.pink)
        assertEquals(Color(0xFF4BFFA2), PhoneColors.pinkLight)
    }
}
