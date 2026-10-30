package ani.saikou.neo.tv.details

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailsTintTest {
    private val fallback = Color(0xFFFF4F91)

    @Test
    fun theTintIsTheCoverColour() {
        assertEquals(Color(0xFFE4A15D), detailsTint("#e4a15d", fallback))
    }

    @Test
    fun withoutACoverColourTheTintFallsBack() {
        assertEquals(fallback, detailsTint(null, fallback))
        assertEquals(fallback, detailsTint("nonsense", fallback))
    }
}
