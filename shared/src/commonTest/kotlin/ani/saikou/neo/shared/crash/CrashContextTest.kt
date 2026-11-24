package ani.saikou.neo.shared.crash

import ani.saikou.neo.shared.sources.InstalledSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CrashContextTest {
    private val reporter = RecordingReporter()
    private val scrubber = CrashLogScrubber()
    private val source = InstalledSource("gotaku", "Go Taku", "1.4")

    @BeforeTest
    fun install() = CrashContext.install(reporter, scrubber)

    @AfterTest
    fun reset() = CrashContext.reset()

    @Test
    fun theScreenBecomesAKey() {
        CrashContext.screen("details")

        assertEquals("details", reporter.keys["screen"])
    }

    @Test
    fun playingNamesTheAnimeEpisodeAndExtensionWithItsVersion() {
        CrashContext.playing(this, animeId = 21, episode = 3, source = source)

        assertEquals(
            mapOf("anime_id" to "21", "episode" to "3", "extension" to "gotaku", "extension_version" to "1.4"),
            reporter.keys,
        )
    }

    @Test
    fun stoppingBlanksThePlayersKeys() {
        CrashContext.playing(this, 21, 3, source)
        CrashContext.stoppedPlaying(this)

        assertEquals(setOf(""), reporter.keys.values.toSet())
    }

    @Test
    fun aSnapshotHoldsTheKeysSetWithoutTheBlankOnes() {
        CrashContext.screen("player")
        CrashContext.playing(this, 21, 3, source)
        assertEquals("3", CrashContext.snapshot()["episode"])

        CrashContext.stoppedPlaying(this)

        assertEquals(mapOf("screen" to "player"), CrashContext.snapshot())
    }

    @Test
    fun anOldPlayerStoppingLeavesTheNextOnesKeys() {
        // Up next opens the next episode's player before the last one is cleared.
        val old = Any()
        val next = Any()
        CrashContext.playing(old, 21, 3, source)
        CrashContext.playing(next, 21, 4, source)
        CrashContext.stoppedPlaying(old)

        assertEquals("4", reporter.keys["episode"])
    }

    @Test
    fun aRecoveredErrorIsSentWithTheCleanedTextOnly() {
        scrubber.sourceTerms = setOf("gotaku")
        val error = IllegalStateException("gotaku got 403 from https://cdn.example.org/a?token=secret for show 21")

        CrashContext.recovered("Player", "Playback stopped: episode 3", error)

        val (message, sent) = reporter.recovered.single()
        assertEquals("Player: Playback stopped: episode [n]: IllegalStateException: [source] got 403 from [url] for show [id]", message)
        assertSame(error, sent)
        assertFalse("secret" in message)
    }

    @Test
    fun causesAreCleanedToo() {
        val error = RuntimeException("outer", IllegalArgumentException("at api.example.org"))

        CrashContext.recovered("Sync", "A list change failed", error)

        assertEquals(
            "Sync: A list change failed: RuntimeException: outer <- caused by IllegalArgumentException: at [host]",
            reporter.recovered.single().first,
        )
    }

    @Test
    fun nothingIsRecordedWhileReportsAreOff() {
        CrashContext.enabled = false
        CrashContext.recovered("Player", "Playback stopped")
        CrashContext.enabled = true
        CrashContext.recovered("Player", "Playback stopped again")

        assertEquals(listOf("Player: Playback stopped again"), reporter.recovered.map { it.first })
    }

    @Test
    fun aLongMessageIsCut() {
        CrashContext.recovered("Player", "x".repeat(5_000))

        assertTrue(
            reporter.recovered
                .single()
                .first.length <= 1_001,
        )
    }

    @Test
    fun aReporterThatThrowsDoesNotBreakTheCaller() {
        val throwing =
            object : CrashReporter by NoCrashReporter {
                override fun setKey(
                    key: String,
                    value: String,
                ): Unit = error("broken")

                override fun recordNonFatal(
                    message: String,
                    error: Throwable?,
                ): Unit = error("broken")
            }
        CrashContext.install(throwing, scrubber)

        CrashContext.screen("home")
        CrashContext.playing(this, 1, 1, source)
        CrashContext.recovered("Player", "Playback stopped")
    }

    @Test
    fun withNoReporterNothingHappens() {
        CrashContext.reset()

        CrashContext.screen("home")
        CrashContext.recovered("Player", "Playback stopped")
    }
}
