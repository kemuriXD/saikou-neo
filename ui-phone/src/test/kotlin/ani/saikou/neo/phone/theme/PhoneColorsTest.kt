package ani.saikou.neo.phone.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneColorsTest {
    @Test
    fun pinksMatchTheSaikouLogo() {
        assertEquals(Color(0xFFFF0080), PhoneColors.pink)
        assertEquals(Color(0xFFFF4BA8), PhoneColors.pinkLight)
    }

    @Test
    fun pinkStandsOutWhereThePhoneUsesIt() {
        // "Neo" in the title, on ink.
        assertContrast(PhoneColors.pink, PhoneColors.ink, 4.5f)
        // Ink on the pink Pair a TV button and on the avatar's pink circle.
        assertContrast(PhoneColors.ink, PhoneColors.pink, 4.5f)
        // TV icons on the raised sheet.
        assertContrast(PhoneColors.pink, PhoneColors.surfaceRaised, 3f)
        // The paired tick on its tonal circle takes the lighter pink.
        assertContrast(PhoneColors.pinkLight, PhoneColors.fillTonal.compositeOver(PhoneColors.ink), 3f)
    }

    private fun assertContrast(
        front: Color,
        background: Color,
        minimum: Float,
    ) {
        val (light, dark) = listOf(front.compositeOver(background).luminance(), background.luminance()).sortedDescending()
        val ratio = (light + 0.05f) / (dark + 0.05f)
        assertTrue("Contrast $ratio is below $minimum:1", ratio >= minimum)
    }
}
