package ani.saikou.neo.tv.details

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import ani.saikou.neo.tv.theme.NeoColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsDimTest {
    private val colors = NeoColors()

    /** The contrast of [text] over a white backdrop under [inkAlpha] of ink. */
    private fun contrastOverWhite(
        text: Color,
        inkAlpha: Float,
    ): Float {
        val behind = colors.ink.copy(alpha = inkAlpha).compositeOver(Color.White)
        val front = text.compositeOver(behind)
        val (light, dark) = listOf(front.luminance(), behind.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    // The synopsis starts 466 dp down a 540 dp screen. Over the brightest backdrop, its text stays readable anywhere.
    @Test
    fun theTextUnderTheOverviewTabStaysReadableOverWhite() {
        val synopsis = 466f / 540
        assertTrue(contrastOverWhite(colors.textMuted, detailsDimDown(synopsis)) >= 4.5f)
        assertTrue(contrastOverWhite(colors.textFaint, detailsDimDown(510f / 540)) >= 4.5f)
    }

    // From the board: the art shows at the top right.
    @Test
    fun theTopRightOfTheArtIsClear() {
        assertEquals(0f, detailsDimAcross(1f), 0f)
        assertEquals(0f, detailsDimDown(0.5f), 0f)
    }

    // Lifted, the synopsis sits at the top, so the whole backdrop dims under it.
    @Test
    fun theLiftedPageStaysReadableOverWhite() {
        assertTrue(contrastOverWhite(colors.textMuted, 0.85f) >= 4.5f)
    }
}
