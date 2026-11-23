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
        assertEquals(Accent(Color(0xFFFF0080), Color(0xFFFF4BA8)), Accent.Stable)
    }

    // Stable's pinks and the alpha and beta greens, whichever build runs the tests.
    @Test
    fun pinkStandsOutWhereThePhoneUsesIt() {
        listOf(Accent.Stable, Accent.Preview).forEach { accent ->
            // "Neo" in the title, on ink.
            assertContrast(accent.pink, PhoneColors.ink, 4.5f)
            // Ink on the pink Pair a TV button and on the avatar's pink circle.
            assertContrast(PhoneColors.ink, accent.pink, 4.5f)
            // TV icons on the raised sheet.
            assertContrast(accent.pink, PhoneColors.surfaceRaised, 3f)
            // The paired tick on its tonal circle takes the lighter pink.
            assertContrast(accent.pinkLight, PhoneColors.fillTonal.compositeOver(PhoneColors.ink), 3f)
        }
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
