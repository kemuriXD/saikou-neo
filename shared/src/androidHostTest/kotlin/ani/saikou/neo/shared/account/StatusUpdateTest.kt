package ani.saikou.neo.shared.account

import kotlin.test.Test
import kotlin.test.assertEquals

class StatusUpdateTest {
    private val today = ListDate(2026, 11, 20)

    private fun on(
        status: ListStatus,
        progress: Int,
        started: Boolean = false,
        finished: Boolean = false,
        repeat: Int = 0,
        episodes: Int? = 12,
    ) = ShowEntry(episodes, ListEntry(status, progress, started, repeat, hasFinishDate = finished))

    @Test
    fun aStatusKeepsTheProgressAndDates() =
        assertEquals(ProgressUpdate(4, ListStatus.PAUSED, null), statusUpdate(on(ListStatus.WATCHING, 4), ListStatus.PAUSED, today))

    @Test
    fun aShowNotOnTheListIsAddedAtNoProgress() =
        assertEquals(ProgressUpdate(0, ListStatus.PLANNING, null), statusUpdate(ShowEntry(12, null), ListStatus.PLANNING, today))

    @Test
    fun completedWatchesEveryEpisodeAndFillsEmptyDates() =
        assertEquals(
            ProgressUpdate(12, ListStatus.COMPLETED, today, completedAt = today),
            statusUpdate(on(ListStatus.WATCHING, 4), ListStatus.COMPLETED, today),
        )

    @Test
    fun completedKeepsDatesAlreadyThere() =
        assertEquals(
            ProgressUpdate(12, ListStatus.COMPLETED, null),
            statusUpdate(on(ListStatus.PAUSED, 4, started = true, finished = true), ListStatus.COMPLETED, today),
        )

    @Test
    fun completedFromOffTheListStartsAndFinishesToday() =
        assertEquals(
            ProgressUpdate(12, ListStatus.COMPLETED, today, completedAt = today),
            statusUpdate(ShowEntry(12, null), ListStatus.COMPLETED, today),
        )

    // AniList can't be told the last episode of a show whose count it doesn't know.
    @Test
    fun completedWithNoEpisodeCountKeepsTheProgress() =
        assertEquals(
            ProgressUpdate(4, ListStatus.COMPLETED, today, completedAt = today),
            statusUpdate(on(ListStatus.WATCHING, 4, episodes = null), ListStatus.COMPLETED, today),
        )

    @Test
    fun completingARewatchCountsIt() =
        assertEquals(
            ProgressUpdate(12, ListStatus.COMPLETED, null, repeat = 2),
            statusUpdate(on(ListStatus.REWATCHING, 5, started = true, finished = true, repeat = 1), ListStatus.COMPLETED, today),
        )
}
