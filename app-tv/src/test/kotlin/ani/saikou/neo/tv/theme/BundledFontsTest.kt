package ani.saikou.neo.tv.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Font
import java.io.File

class BundledFontsTest {
    // Gradle runs unit tests from the module folder.
    private val fonts =
        File("src/main/res/font")
            .listFiles { file -> file.extension == "ttf" }!!
            .sortedBy { it.name }
            .map { it.name to Font.createFont(Font.TRUETYPE_FONT, it) }

    @Test
    fun bundlesTheFiveFonts() {
        assertTrue(fonts.size == 5)
    }

    @Test
    fun coverLatinIncludingRomajiMacrons() {
        // English text, European accents, and the long vowels in romanised Japanese titles.
        val latin = "AZaz09 éñüçßøœ ŌōŪūĀā €“”’…–—"
        fonts.forEach { (name, font) ->
            latin.forEach { char -> assertTrue("$name is missing '$char'", font.canDisplay(char)) }
        }
    }

    @Test
    fun leaveOtherScriptsToTheSystemFont() {
        // Cyrillic, Greek and Vietnamese aren't needed, so their glyphs aren't bundled.
        val other = "ЖжΩλẠạỆệ"
        fonts.forEach { (name, font) ->
            other.forEach { char -> assertFalse("$name still has '$char'", font.canDisplay(char)) }
        }
    }
}
