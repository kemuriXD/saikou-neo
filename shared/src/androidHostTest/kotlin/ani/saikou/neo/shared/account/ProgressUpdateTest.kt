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

    @Test
    fun completedAndRewatchingAreLeftAlone() {
        assertNull(progressUpdate(on(ListStatus.COMPLETED, 12), 3, today))
        assertNull(progressUpdate(on(ListStatus.REWATCHING, 1), 3, today))
    }

    @Test
    fun episodeZeroAndEpisodesPastTheTotalNeverCount() {
        assertNull(progressUpdate(ShowEntry(12, null), 0, today))
        assertNull(progressUpdate(ShowEntry(12, null), 13, today))
    }

    @Test
    fun withoutAKnownTotalAnyLaterEpisodeCounts() =
        assertEquals(ProgressUpdate(1100, null, null), progressUpdate(ShowEntry(null, ListEntry(ListStatus.WATCHING, 1099, true)), 1100, today))
}
