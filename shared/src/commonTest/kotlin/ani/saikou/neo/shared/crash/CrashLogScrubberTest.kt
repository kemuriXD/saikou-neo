package ani.saikou.neo.shared.crash

import kotlin.test.Test
import kotlin.test.assertEquals

class CrashLogScrubberTest {
    private val scrubber = CrashLogScrubber()

    @Test
    fun showIdsAndEpisodeNumbersAreReplaced() {
        assertEquals(
            "Playing show [id] episode [n] on Vidstreaming",
            scrubber.scrub("Playing show 21 episode 3 on Vidstreaming"),
        )
    }

    @Test
    fun urlsAreReplacedWhole() {
        assertEquals("GET [url] -> 500", scrubber.scrub("GET https://api.example.org/v1/x -> 500"))
        assertEquals("[url] failed", scrubber.scrub("http://10.0.2.2:8799 failed"))
    }

    @Test
    fun sourceTermsAreReplacedIgnoringCase() {
        scrubber.sourceTerms = setOf("gotaku", "Go Taku")

        assertEquals("Found source [source] 3", scrubber.scrub("Found source GoTaku 3"))
        assertEquals("[source] gave no servers", scrubber.scrub("Go Taku gave no servers"))
    }

    @Test
    fun shortTermsAreIgnored() {
        scrubber.sourceTerms = setOf("ab", "x")

        assertEquals("ab and x stay", scrubber.scrub("ab and x stay"))
    }

    @Test
    fun theLongerTermWinsOverOneInsideIt() {
        scrubber.sourceTerms = setOf("taku", "gotaku")

        assertEquals("[source]", scrubber.scrub("gotaku"))
    }

    @Test
    fun decimalEpisodeNumbersAreReplacedWhole() {
        assertEquals("Episode [n] failed", scrubber.scrub("Episode 12.5 failed"))
    }

    @Test
    fun theResultIsRedactedAgain() {
        val scrubbed = scrubber.scrub("Header was Bearer abcdef123456 here")

        assertEquals(false, scrubbed.contains("abcdef123456"), scrubbed)
    }
}
