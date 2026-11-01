package ani.saikou.neo.shared.sources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamExtractionTest {
    // urlMatches

    @Test
    fun aStarMatchesTheRunBeforeALiteralSuffix() {
        assertTrue(urlMatches("*.m3u8", "https://cdn.example/video/master.m3u8"))
    }

    @Test
    fun aSuffixPatternRejectsAnExtraQuery() {
        // A plain *.m3u8 is anchored, so a source that wants to allow a query writes its own trailing *.
        assertFalse(urlMatches("*.m3u8", "https://cdn.example/master.m3u8?token=abc"))
        assertTrue(urlMatches("*.m3u8*", "https://cdn.example/master.m3u8?token=abc"))
    }

    @Test
    fun aDotInThePatternIsLiteralNotAnyCharacter() {
        assertFalse(urlMatches("*.m3u8", "https://cdn.example/masterXm3u8"))
    }

    @Test
    fun aQuestionMarkMatchesExactlyOneCharacter() {
        assertTrue(urlMatches("https://cdn.example/seg?.ts", "https://cdn.example/seg1.ts"))
        assertFalse(urlMatches("https://cdn.example/seg?.ts", "https://cdn.example/seg12.ts"))
    }

    @Test
    fun aPatternWithoutWildcardsMatchesOnlyTheExactUrl() {
        assertTrue(urlMatches("https://cdn.example/a.m3u8", "https://cdn.example/a.m3u8"))
        assertFalse(urlMatches("https://cdn.example/a.m3u8", "https://cdn.example/a.m3u8/b"))
    }

    @Test
    fun regexMetacharactersInThePatternAreLiteral() {
        assertTrue(urlMatches("*/a+b/(c)/x.ts", "https://h/a+b/(c)/x.ts"))
        assertFalse(urlMatches("*/a+b/(c)/x.ts", "https://h/ab/c/x.ts"))
    }

    @Test
    fun aStarCanMatchNothing() {
        assertTrue(urlMatches("https://h/a*b", "https://h/ab"))
    }

    // extractionDone

    @Test
    fun aUrlOnlyRecipeIsDoneWhenTheUrlIsSeen() {
        assertFalse(extractionDone(urlWanted = true, scriptWanted = false, matchedUrl = null, scriptValue = null))
        assertTrue(extractionDone(urlWanted = true, scriptWanted = false, matchedUrl = "u", scriptValue = null))
    }

    @Test
    fun aScriptOnlyRecipeIsDoneWhenTheValueIsNonBlank() {
        assertFalse(extractionDone(urlWanted = false, scriptWanted = true, matchedUrl = null, scriptValue = null))
        assertFalse(extractionDone(urlWanted = false, scriptWanted = true, matchedUrl = null, scriptValue = "   "))
        assertTrue(extractionDone(urlWanted = false, scriptWanted = true, matchedUrl = null, scriptValue = "key"))
    }

    @Test
    fun aRecipeWithBothNeedsBoth() {
        assertFalse(extractionDone(urlWanted = true, scriptWanted = true, matchedUrl = "u", scriptValue = null))
        assertFalse(extractionDone(urlWanted = true, scriptWanted = true, matchedUrl = null, scriptValue = "v"))
        assertTrue(extractionDone(urlWanted = true, scriptWanted = true, matchedUrl = "u", scriptValue = "v"))
    }

    // decodeJsEvalResult

    @Test
    fun aJsonNullDecodesToNull() {
        assertNull(decodeJsEvalResult(null))
        assertNull(decodeJsEvalResult("null"))
    }

    @Test
    fun aQuotedStringIsUnquotedAndUnescaped() {
        assertEquals("the key", decodeJsEvalResult("\"the key\""))
        assertEquals("a\"b\\c", decodeJsEvalResult("\"a\\\"b\\\\c\""))
        assertEquals("line1\nline2", decodeJsEvalResult("\"line1\\nline2\""))
        assertEquals("a/b", decodeJsEvalResult("\"a\\/b\""))
    }

    @Test
    fun aUnicodeEscapeIsDecoded() {
        assertEquals("✓", decodeJsEvalResult("\"\\u2713\""))
    }

    @Test
    fun aBareValueIsReturnedAsIs() {
        assertEquals("42", decodeJsEvalResult("42"))
        assertEquals("true", decodeJsEvalResult("true"))
    }
}
