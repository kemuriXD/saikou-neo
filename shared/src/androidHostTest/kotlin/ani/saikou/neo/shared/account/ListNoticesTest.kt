package ani.saikou.neo.shared.account

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// ADR 0056: one notice at a time, for 8 s, held while the player is full screen.
@OptIn(ExperimentalCoroutinesApi::class)
class ListNoticesTest {
    private val watched =
        ListNotice(101, 1, null, NoticeChange.Updated(ListStatus.WATCHING, 4, byEpisode = true), null, fromWatching = true)
    private val marked = watched.copy(fromWatching = false)
    private val incoming = MutableSharedFlow<ListNotice>(extraBufferCapacity = 8)
    private val undone = mutableListOf<ListNotice>()

    private fun TestScope.notices(undoWorks: Boolean = true): ListNotices {
        val notices =
            ListNotices(backgroundScope, incoming) {
                undone += it
                undoWorks
            }
        runCurrent()
        return notices
    }

    private fun TestScope.tell(notice: ListNotice) {
        incoming.tryEmit(notice)
        runCurrent()
    }

    @Test
    fun aNoticeShowsForEightSeconds() =
        runTest {
            val notices = notices()

            tell(marked)
            assertEquals(ShownNotice(marked), notices.shown.value)
            advanceTimeBy(7_999)
            assertEquals(ShownNotice(marked), notices.shown.value)
            advanceTimeBy(2)
            assertNull(notices.shown.value)
        }

    @Test
    fun aWatchingNoticeWaitsWhileThePlayerIsFullScreen() =
        runTest {
            val notices = notices()
            notices.playing(fullScreen = true)

            tell(watched)
            advanceTimeBy(60_000)
            assertNull(notices.shown.value)

            notices.playing(fullScreen = false)
            runCurrent()
            assertEquals(ShownNotice(watched), notices.shown.value)
            advanceTimeBy(8_001)
            assertNull(notices.shown.value)
        }

    @Test
    fun itStaysWhileUndoHasFocusAndGoesEightSecondsAfter() =
        runTest {
            val notices = notices()

            tell(marked)
            notices.undoFocused(true)
            advanceTimeBy(30_000)
            assertEquals(ShownNotice(marked), notices.shown.value)

            notices.undoFocused(false)
            advanceTimeBy(7_999)
            assertEquals(ShownNotice(marked), notices.shown.value)
            advanceTimeBy(2)
            assertNull(notices.shown.value)
        }

    @Test
    fun aNewerNoticeTakesTheOneShowingsPlace() =
        runTest {
            val notices = notices()
            val newer = marked.copy(animeId = 202)

            tell(marked)
            advanceTimeBy(5_000)
            tell(newer)
            advanceTimeBy(5_000)

            assertEquals(ShownNotice(newer), notices.shown.value)
        }

    @Test
    fun undoThatWorksClosesTheNoticeAndRunsOnce() =
        runTest {
            val notices = notices()

            tell(marked)
            notices.undo()
            notices.undo()
            runCurrent()

            assertEquals(listOf(marked), undone)
            assertNull(notices.shown.value)
        }

    @Test
    fun undoThatFailsSaysSoForEightSeconds() =
        runTest {
            val notices = notices(undoWorks = false)

            tell(marked)
            notices.undoFocused(true)
            notices.undo()
            runCurrent()
            assertEquals(ShownNotice(marked, failed = true), notices.shown.value)

            // Undo has gone from the notice, so it lost focus.
            notices.undoFocused(false)
            notices.undo()
            advanceTimeBy(8_001)
            assertEquals(listOf(marked), undone)
            assertNull(notices.shown.value)
        }

    // Outside the player, pressing Undo takes focus off it first, which starts the 8 s again.
    @Test
    fun aSlowUndoThatFailsStillSaysSo() =
        runTest {
            val notices =
                ListNotices(backgroundScope, incoming) {
                    delay(20_000)
                    false
                }
            runCurrent()

            tell(marked)
            notices.undoFocused(true)
            runCurrent()
            notices.undoFocused(false)
            notices.undo()
            advanceTimeBy(19_000)
            assertEquals(ShownNotice(marked), notices.shown.value)

            advanceTimeBy(1_001)
            assertEquals(ShownNotice(marked, failed = true), notices.shown.value)
            advanceTimeBy(7_999)
            assertEquals(ShownNotice(marked, failed = true), notices.shown.value)
            advanceTimeBy(2)
            assertNull(notices.shown.value)
        }
}
