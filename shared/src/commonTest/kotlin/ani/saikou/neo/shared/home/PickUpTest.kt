package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.account.ListedProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PickUpTest {
    private fun tv(
        episode: Int,
        watched: Duration = 12.minutes,
    ) = WatchProgress(episode, watched, 24.minutes, "still.jpg", savedAt = 300)

    private fun listed(progress: Int) = ListedProgress(201, progress, updatedAt = 500, episodeMinutes = 24)

    // AniList's place is its last episode watched to the end, as Continue watching keeps it, so nothing shows as begun.
    private fun fromList(next: Int) = WatchProgress(next - 1, 24.minutes, 24.minutes, null, 500)

    @Test
    fun withNeitherThereIsNothingToPickUp() {
        assertNull(pickUp(null, null))
    }

    @Test
    fun onlyOnThisTvPicksUpThere() {
        assertEquals(PickUp.At(tv(5)), pickUp(tv(5), null))
    }

    @Test
    fun onlyOnAniListPicksUpAfterItsProgress() {
        assertEquals(PickUp.At(fromList(4)), pickUp(null, listed(3)))
    }

    @Test
    fun bothAtTheSameEpisodeKeepThisTvsPosition() {
        assertEquals(PickUp.At(tv(4)), pickUp(tv(4), listed(3)))
        assertEquals(PickUp.At(tv(3, watched = 22.minutes)), pickUp(tv(3, watched = 22.minutes), listed(3)))
    }

    @Test
    fun aniListFurtherOnWithNothingPartWatchedHerePicksUpOnAniList() {
        assertEquals(PickUp.At(fromList(8)), pickUp(tv(5, watched = 22.minutes), listed(7)))
        assertEquals(PickUp.At(fromList(8)), pickUp(tv(5, watched = 5.seconds), listed(7)))
    }

    @Test
    fun aniListFurtherOnWithAPartWatchedEpisodeHereAsks() {
        assertEquals(PickUp.Ask(fromList(8), tv(5)), pickUp(tv(5), listed(7)))
    }

    @Test
    fun thisTvFurtherOnAsks() {
        assertEquals(PickUp.Ask(fromList(3), tv(5, watched = 22.minutes)), pickUp(tv(5, watched = 22.minutes), listed(2)))
    }

    @Test
    fun aListedEpisodeLengthIsKept() {
        val place = pickUp(null, ListedProgress(201, 0, null, episodeMinutes = null))

        assertEquals(PickUp.At(WatchProgress(0, 24.minutes, 24.minutes, null, null)), place)
    }

    @Test
    fun anAnswerSaysWhereToCarryOn() {
        assertEquals(tv(5), PickUp.Ask(fromList(8), tv(5)).place)
        assertEquals(fromList(8), PickUp.At(fromList(8)).place)
    }
}
