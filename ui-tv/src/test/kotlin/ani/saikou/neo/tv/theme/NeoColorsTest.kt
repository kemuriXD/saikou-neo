package ani.saikou.neo.tv.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeoColorsTest {
    private val colors = NeoColors()

    @Test
    fun brandColoursMatchTheSignalArtwork() {
        assertEquals(Color(0xFF11121C), colors.ink)
        assertEquals(Color(0xFFF6F5F2), colors.paper)
        assertEquals(Color(0xFFFF4F91), colors.pink)
    }

    @Test
    fun textOnInkIsReadableFromAcrossTheRoom() {
        assertReadable(colors.paper, colors.ink)
        assertReadable(colors.textMuted, colors.ink)
        assertReadable(colors.textFaint, colors.ink)
        assertReadable(colors.pink, colors.ink)
        assertReadable(colors.warning, colors.ink)
        assertReadable(colors.textMuted, colors.surfaceScrim.compositeOver(colors.ink))
        assertReadable(colors.paper, colors.fillTonal.compositeOver(colors.ink))
    }

    @Test
    fun textOnFocusAndPinkIsReadable() {
        assertReadable(colors.onFocus, colors.focus)
        assertReadable(colors.onFocusMuted, colors.focus)
        assertReadable(colors.pinkDeep, colors.focus)
        assertReadable(colors.ink, colors.pink)
    }

    private fun assertReadable(
        text: Color,
        background: Color,
    ) {
        val ratio = contrastRatio(text.compositeOver(background), background)
        assertTrue("Contrast $ratio is below 4.5:1", ratio >= 4.5f)
    }

    /** WCAG 2.1 contrast ratio between two opaque colours. */
    private fun contrastRatio(
        a: Color,
        b: Color,
    ): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }
}
