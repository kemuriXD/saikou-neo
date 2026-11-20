package ani.saikou.neo.shared.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressUpdateTest {
    private val today = ListDate(2026, 11, 20)

    private fun on(
        status: ListStatus,
        progress: Int,
        started: Boolean = false,
    ) = ShowEntry(episodes = 12, entry = ListEntry(status, progress, started))

    @Test
    fun aShowNotOnTheListBecomesWatchingFromToday() =
        assertEquals(ProgressUpdate(3, ListStatus.WATCHING, today), progressUpdate(ShowEntry(12, null), 3, today))

    @Test
    fun planningPausedAndDroppedBecomeWatching() {
        for (status in listOf(ListStatus.PLANNING, ListStatus.PAUSED, ListStatus.DROPPED)) {
            assertEquals(ProgressUpdate(5, ListStatus.WATCHING, today), progressUpdate(on(status, 2), 5, today))
        }
    }

    @Test
    fun aStartDateAlreadyThereIsKept() =
        assertEquals(ProgressUpdate(5, ListStatus.WATCHING, null), progressUpdate(on(ListStatus.PAUSED, 2, started = true), 5, today))

    @Test
    fun aWatchingShowOnlyMovesItsProgress() =
        assertEquals(ProgressUpdate(4, null, null), progressUpdate(on(ListStatus.WATCHING, 3), 4, today))

    @Test
    fun progressNeverGoesDown() {
        assertNull(progressUpdate(on(ListStatus.WATCHING, 4), 4, today))
        assertNull(progressUpdate(on(ListStatus.WATCHING, 6), 2, today))
        assertNull(progressUpdate(on(ListStatus.PAUSED, 6), 2, today))
    }

    private fun finished(
        status: ListStatus?,
        progress: Int = 0,
        started: Boolean = false,
        repeat: Int = 0,
        episodes: Int = 12,
    ) = ShowEntry(episodes, status?.let { ListEntry(it, progress, started, repeat) }, finished = true)

    @Test
    fun theLastEpisodeOfAFinishedShowCompletesItToday() =
        assertEquals(
            ProgressUpdate(12, ListStatus.COMPLETED, null, completedAt = today),
            progressUpdate(finished(ListStatus.WATCHING, 11, started = true), 12, today),
        )

    @Test
    fun aShowCompletedStraightFromPlanningGetsBothDates() =
        assertEquals(
            ProgressUpdate(1, ListStatus.COMPLETED, today, completedAt = today),
            progressUpdate(finished(null, episodes = 1), 1, today),
        )

    @Test
    fun theNewestEpisodeOfAnAiringShowLeavesItWatching() =
        assertEquals(ProgressUpdate(12, null, null), progressUpdate(on(ListStatus.WATCHING, 11), 12, today))

    @Test
    fun episodeOneOfACompletedShowStartsARewatch() =
        assertEquals(
            ProgressUpdate(1, ListStatus.REWATCHING, null),
            progressUpdate(finished(ListStatus.COMPLETED, 12, started = true, repeat = 2), 1, today),
        )

    @Test
    fun anotherEpisodeOfACompletedShowChangesNothing() = assertNull(progressUpdate(finished(ListStatus.COMPLETED, 12), 5, today))

    @Test
    fun aRewatchMovesItsProgressUp() {
        assertEquals(ProgressUpdate(4, null, null), progressUpdate(finished(ListStatus.REWATCHING, 3), 4, today))
        assertNull(progressUpdate(finished(ListStatus.REWATCHING, 4), 2, today))
    }

    @Test
    fun finishingARewatchCompletesItAndCountsTheRewatch() =
        assertEquals(
            ProgressUpdate(12, ListStatus.COMPLETED, null, repeat = 3),
            progressUpdate(finished(ListStatus.REWATCHING, 11, repeat = 2), 12, today),
        )

    @Test
    fun watchingACompletedFilmAgainCountsARewatch() =
        assertEquals(
            ProgressUpdate(1, null, null, repeat = 1),
            progressUpdate(finished(ListStatus.COMPLETED, 1, episodes = 1), 1, today),
        )

    @Test
    fun episodeZeroAndEpisodesPastTheTotalNeverCount() {
        assertNull(progressUpdate(ShowEntry(12, null), 0, today))
        assertNull(progressUpdate(ShowEntry(12, null), 13, today))
    }

    @Test
    fun withoutAKnownTotalAnyLaterEpisodeCounts() =
        assertEquals(
            ProgressUpdate(1100, null, null),
            progressUpdate(ShowEntry(null, ListEntry(ListStatus.WATCHING, 1099, true)), 1100, today),
        )
}
