package ani.saikou.neo.shared.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WatchProgressTest {
    private fun progress(watched: kotlin.time.Duration) = WatchProgress(episode = 7, watched = watched, length = 24.minutes, still = null)

    @Test
    fun minutesLeftRoundsUp() {
        assertEquals(12, progress(12.minutes).minutesLeft)
        assertEquals(12, progress(12.minutes + 1.seconds).minutesLeft)
    }

    @Test
    fun aFewSecondsLeftIsStillAMinute() = assertEquals(1, progress(24.minutes - 20.seconds).minutesLeft)

    @Test
    fun fractionIsHowMuchWasWatched() {
        assertEquals(0.5f, progress(12.minutes).fraction)
        assertEquals(1f, progress(30.minutes).fraction)
    }

    @Test
    fun resumesTheSameEpisodeFromWhereItStopped() = assertEquals(12.minutes.inWholeMilliseconds, progress(12.minutes).resumeAtMs(7))

    @Test
    fun anotherEpisodeStartsFromTheBeginning() = assertEquals(null, progress(12.minutes).resumeAtMs(8))

    @Test
    fun theFirstFewSecondsAreNotWorthResuming() = assertEquals(null, progress(9.seconds).resumeAtMs(7))

    @Test
    fun anEpisodeWatchedToTheCreditsStartsOver() = assertEquals(null, progress(23.minutes).resumeAtMs(7))

    @Test
    fun anEpisodeNotWatchedToTheEndIsStillUpNext() = assertEquals(progress(12.minutes), progress(12.minutes).upNext(aired = 7))

    @Test
    fun anEpisodeWatchedToTheEndMovesOnToTheNextFromItsStart() {
        val watched = progress(23.minutes).copy(still = "still", savedAt = 100)

        assertEquals(WatchProgress(8, Duration.ZERO, 24.minutes, "still", 100), watched.upNext(aired = 12))
    }

    @Test
    fun theLastAiredEpisodeWatchedToTheEndLeavesNothingUpNext() {
        assertNull(progress(23.minutes).upNext(aired = 7))
        assertNull(progress(23.minutes).upNext(aired = 5))
    }

    @Test
    fun withoutAnAiredCountTheNextEpisodeIsUpNext() = assertEquals(8, progress(23.minutes).upNext(aired = null)?.episode)

    @Test
    fun airedEpisodesAreThoseBeforeTheNextToAirOrElseTheTotal() {
        assertEquals(4, airedEpisodes(total = 12, nextToAir = 5))
        assertEquals(4, airedEpisodes(total = null, nextToAir = 5))
        assertEquals(12, airedEpisodes(total = 12, nextToAir = null))
        assertNull(airedEpisodes(total = null, nextToAir = null))
    }

    @Test
    fun daysSinceCountsCalendarDaysInTheViewersZone() {
        // 2026-11-05 23:30 UTC is the 6th at UTC+1.
        val saved = 1_793_921_400L
        val oneHour = 3_600
        assertEquals(0, daysSince(saved, saved + 600, zoneOffsetSeconds = 0))
        assertEquals(1, daysSince(saved, saved + oneHour, zoneOffsetSeconds = 0))
        assertEquals(0, daysSince(saved, saved + oneHour, zoneOffsetSeconds = oneHour))
        assertEquals(2, daysSince(saved, saved + 2 * 86_400L, zoneOffsetSeconds = 0))
    }
}
