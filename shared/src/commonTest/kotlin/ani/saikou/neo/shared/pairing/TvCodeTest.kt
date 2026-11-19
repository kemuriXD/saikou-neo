package ani.saikou.neo.shared.pairing

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun aNewCodeMixesLettersAndDigitsWithNoRepeats() {
        repeat(2_000) { seed ->
            val code = newTvCode(Random(seed))
            assertEquals(TV_CODE_LENGTH, code.length, code)
            assertEquals(code.length, code.toSet().size, "repeats in $code")
            assertTrue(code.any { it in '2'..'9' }, "no digit in $code")
            assertTrue(code.any { it in 'A'..'Z' }, "no letter in $code")
            assertTrue(code.none { it in "01OIL" }, "hard to read character in $code")
        }
    }
}
