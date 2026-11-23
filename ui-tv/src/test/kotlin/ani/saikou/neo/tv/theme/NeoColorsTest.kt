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
    fun brandColoursMatchTheSaikouLogo() {
        assertEquals(Color(0xFF11121C), colors.ink)
        assertEquals(Color(0xFFF6F5F2), colors.paper)
        assertEquals(Color(0xFFFF0080), colors.pink)
        assertEquals(Color(0xFFFF4BA8), colors.pinkLight)
        assertEquals(Color(0xFFC50053), colors.pinkDeep)
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
    fun pinkTextIsReadableOnEachSurfaceItSitsOn() {
        // Overlines on the episode menu and the list panel, and values on the settings rows.
        assertReadable(colors.pink, colors.surface)
        // Raised panels and tiles, such as the AniList question and a source's letter tile, take the lighter pink.
        assertReadable(colors.pinkLight, colors.surfaceRaised)
    }

    @Test
    fun thePlayingEpisodeLineKeepsItsContrast() {
        // The player's episode list tints the playing row with paper over the drawer. The old pink read at 4.4:1 there;
        // the logo pink would drop to 3.6:1, so the row takes the lighter pink, which keeps it at 4.4:1.
        val row = colors.paper.copy(alpha = 0.12f).compositeOver(colors.ink.copy(alpha = 0.94f).compositeOver(Color.Black))
        val ratio = contrastRatio(colors.pinkLight, row)
        assertTrue("Contrast $ratio is below 4.4:1", ratio >= 4.4f)
    }

    @Test
    fun pinkShapesAndIconsStandOut() {
        // Ticks, rings and bars on raised surfaces, and the list button's tick when focused.
        assertStandsOut(colors.pink, colors.surfaceRaised)
        assertStandsOut(colors.pink, colors.focus)
        // A switch's paper knob on its pink track.
        assertStandsOut(colors.paper, colors.pink)
    }

    @Test
    fun textOnFocusAndPinkIsReadable() {
        assertReadable(colors.onFocus, colors.focus)
        assertReadable(colors.onFocusMuted, colors.focus)
        assertReadable(colors.pinkDeep, colors.focus)
        assertReadable(colors.ink, colors.pink)
        // A profile's initial on its pink-deep circle.
        assertReadable(colors.paper, colors.pinkDeep)
    }

    private fun assertReadable(
        text: Color,
        background: Color,
    ) {
        val ratio = contrastRatio(text.compositeOver(background), background)
        assertTrue("Contrast $ratio is below 4.5:1", ratio >= 4.5f)
    }

    private fun assertStandsOut(
        shape: Color,
        background: Color,
    ) {
        val ratio = contrastRatio(shape.compositeOver(background), background)
        assertTrue("Contrast $ratio is below 3:1", ratio >= 3f)
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
