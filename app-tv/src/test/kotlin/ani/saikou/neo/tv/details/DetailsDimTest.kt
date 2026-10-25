package ani.saikou.neo.tv.details

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import ani.saikou.neo.tv.theme.NeoColors
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsDimTest {
    private val colors = NeoColors()

    /** The contrast of [text] over a white backdrop dimmed as Details dims it [across] the screen. */
    private fun contrastOverWhite(
        text: Color,
        across: Float,
    ): Float {
        val behind = colors.ink.copy(alpha = detailsDimAt(across)).compositeOver(Color.White)
        val front = text.compositeOver(behind)
        val (light, dark) = listOf(front.luminance(), behind.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    // The brightest backdrop there is. The text column ends 638 dp across a 960 dp screen.
    @Test
    fun textKeeps4point5To1EvenOverWhite() {
        val column = 638f / 960
        assertTrue(contrastOverWhite(colors.paper, column) >= 4.5f)
        assertTrue(contrastOverWhite(colors.textMuted, column) >= 4.5f)
        // The facts' labels end before halfway, and the pink overline in the first quarter.
        assertTrue(contrastOverWhite(colors.textFaint, 0.5f) >= 4.5f)
        assertTrue(contrastOverWhite(colors.pink, 0.25f) >= 4.5f)
    }

    // From the board: the art shows through on the right, where there's no text.
    @Test
    fun theRightOfTheBackdropIsLightlyDimmed() {
        assertTrue(detailsDimAt(0.82f) <= 0.35f)
        assertTrue(detailsDimAt(1f) <= 0.15f)
    }
}
