package ani.saikou.neo.shared.pairing

import kotlin.test.Test
import kotlin.test.assertEquals

class TvCodeTest {
    @Test
    fun lettersTurnIntoCapitals() {
        assertEquals("K7P2QX", normalizeTvCode("k7p2qx"))
    }

    @Test
    fun spacesAndDashesFromAPasteAreDropped() {
        assertEquals("K7P2QX", normalizeTvCode(" K7P 2QX "))
        assertEquals("K7P2QX", normalizeTvCode("k7p-2qx"))
    }

    @Test
    fun onlyTheFirstSixAreKept() {
        assertEquals("ABCDEF", normalizeTvCode("abcdefghij"))
    }

    @Test
    fun lettersAndDigitsOutsideAsciiAreDropped() {
        // An accented letter, an Arabic-Indic three and a full-width A all count as letters or digits to Kotlin.
        assertEquals("AB", normalizeTvCode("aé٣Ａb"))
    }
}
