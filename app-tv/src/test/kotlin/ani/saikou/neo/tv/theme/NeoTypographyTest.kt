package ani.saikou.neo.tv.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class NeoTypographyTest {
    private val type = NeoTypography()

    @Test
    fun titlesUseBricolageGrotesque() {
        listOf(type.display, type.headline, type.titleLarge, type.rowTitle, type.title).forEach {
            assertEquals(NeoFonts.bricolageGrotesque, it.fontFamily)
        }
    }

    @Test
    fun everythingElseUsesAtkinsonHyperlegibleNext() {
        listOf(type.bodyLarge, type.body, type.labelLarge, type.label, type.caption, type.overline, type.timecode).forEach {
            assertEquals(NeoFonts.atkinsonHyperlegibleNext, it.fontFamily)
        }
    }

    @Test
    fun keepsTheSizesAndWeights() {
        assertStyle(type.display, 44, 50, FontWeight.Bold)
        assertStyle(type.headline, 30, 38, FontWeight.Bold)
        assertStyle(type.titleLarge, 24, 32, FontWeight.Bold)
        assertStyle(type.rowTitle, 18, 24, FontWeight.Bold)
        assertStyle(type.title, 18, 24, FontWeight.Medium)
        assertStyle(type.bodyLarge, 15, 22, FontWeight.Normal)
        assertStyle(type.body, 14, 20, FontWeight.Normal)
        assertStyle(type.labelLarge, 16, 22, FontWeight.Medium)
        assertStyle(type.label, 14, 20, FontWeight.Medium)
        assertStyle(type.caption, 12, 16, FontWeight.Normal)
        assertStyle(type.overline, 12, 16, FontWeight.Medium)
        assertStyle(type.timecode, 13, 18, FontWeight.Medium)
    }

    @Test
    fun playerTimesUseTabularFigures() {
        assertEquals("tnum", type.timecode.fontFeatureSettings)
    }

    private fun assertStyle(
        style: TextStyle,
        size: Int,
        lineHeight: Int,
        weight: FontWeight,
    ) {
        assertEquals(size.sp, style.fontSize)
        assertEquals(lineHeight.sp, style.lineHeight)
        assertEquals(weight, style.fontWeight)
    }
}
