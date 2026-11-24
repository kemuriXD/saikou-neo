package ani.saikou.neo.shared.crash

import kotlin.test.Test
import kotlin.test.assertEquals

class CrashLogScrubberTest {
    private val scrubber = CrashLogScrubber()

    @Test
    fun showIdsAndEpisodeNumbersAreReplacedAndTheServerToo() {
        assertEquals(
            "Playing show [id] episode [n] on [server]",
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

    @Test
    fun bareHostsAreReplaced() {
        assertEquals(
            "Unable to resolve host \"[host]\": gone",
            scrubber.scrub("Unable to resolve host \"api.example.org\": gone"),
        )
        assertEquals("Hostname [host] not verified", scrubber.scrub("Hostname api.example.org not verified"))
        assertEquals("Failed to connect to [host]/[ip]:443", scrubber.scrub("Failed to connect to api.example.org/1.2.3.4:443"))
    }

    @Test
    fun escapedUrlsAreReplaced() {
        assertEquals("Got [url]", scrubber.scrub("""Got https:\/\/host.example.org\/x"""))
    }

    @Test
    fun protocolRelativeUrlsAreReplaced() {
        assertEquals("Image [url]", scrubber.scrub("Image //cdn.host.example.org/a.png"))
    }

    @Test
    fun markersAndPlainWordsStayIntact() {
        scrubber.sourceTerms = setOf("gotaku")

        assertEquals(
            "Found source [source] 3 at [url] show [id] episode [n]",
            scrubber.scrub("Found source gotaku 3 at https://a.b.org/x show 1 episode 2"),
        )
    }

    @Test
    fun otherIdAndEpisodeFormsAreReplaced() {
        assertEquals("episode: [n]", scrubber.scrub("episode: 3"))
        assertEquals("episode=[n]", scrubber.scrub("episode=3"))
        assertEquals("ep [n] failed", scrubber.scrub("ep 3 failed"))
        assertEquals("Opening S[n]E[n]", scrubber.scrub("Opening S01E03"))
        assertEquals("showId=[id]", scrubber.scrub("showId=21"))
        assertEquals("mediaId: [id]", scrubber.scrub("mediaId: 21"))
        assertEquals("anilist [id] saved", scrubber.scrub("anilist 21 saved"))
        assertEquals("Episode #[n]", scrubber.scrub("Episode #12.5"))
    }

    @Test
    fun wordsThatOnlyEndInANumberStay() {
        assertEquals("Media3 player 2 failed", scrubber.scrub("Media3 player 2 failed"))
    }

    @Test
    fun pathsWithNoSchemeAreReplaced() {
        assertEquals("HTTP 403 for [path]", scrubber.scrub("HTTP 403 for /watch/one-piece-100"))
        assertEquals("[path] is gone", scrubber.scrub("/watch/one-piece-100 is gone"))
        assertEquals("Done 3 / 5", scrubber.scrub("Done 3 / 5"))
    }

    @Test
    fun hostsWithUnderscoresAreReplaced() {
        assertEquals("Resolving [host] failed", scrubber.scrub("Resolving my_cdn.example.to failed"))
    }

    @Test
    fun doubleEncodedQueriesDoNotSurvive() {
        val scrubbed = scrubber.scrub("Fetching [host]/…%253Ftoken%253Dsecret123 failed")

        assertEquals(false, scrubbed.contains("secret123"), scrubbed)
    }
}
