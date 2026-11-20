package ani.saikou.neo.shared.account

import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.home.SyncedSides
import ani.saikou.neo.shared.home.WatchHistory
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.shared.home.YourList
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.settings.UpdateAniList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class AniListSyncTest {
    private val today = ListDate(2026, 11, 20)
    private val me = SignedInAccount(AniListProfile(1, "Kemuri", null), AniListToken("the-token", 2_000_000_000))

    private class FakeClient(
        var entry: ShowEntry = ShowEntry(12, ListEntry(ListStatus.WATCHING, 3, hasStartDate = true)),
        var failWith: AniListError? = null,
    ) : ProgressClient {
        val saved = mutableListOf<Pair<Int, ProgressUpdate>>()
        var calls = 0

        override suspend fun showEntry(
            token: AniListToken,
            mediaId: Int,
        ): AniListResult<ShowEntry> {
            calls++
            return failWith?.let { AniListResult.Failure(it) } ?: AniListResult.Success(entry)
        }

        override suspend fun saveProgress(
            token: AniListToken,
            mediaId: Int,
            update: ProgressUpdate,
        ): AniListResult<Unit> {
            calls++
            failWith?.let { return AniListResult.Failure(it) }
            saved += mediaId to update
            return AniListResult.Success(Unit)
        }

        val removed = mutableListOf<Int>()

        override suspend fun removeEntry(
            token: AniListToken,
            entryId: Int,
        ): AniListResult<Unit> {
            calls++
            failWith?.let { return AniListResult.Failure(it) }
            removed += entryId
            return AniListResult.Success(Unit)
        }
    }

    private class MemoryQueue : SyncQueue {
        val items = linkedMapOf<Int, PendingProgress>()

        override suspend fun all() = items.values.toList()

        override suspend fun put(pending: PendingProgress) {
            items[pending.animeId] = pending
        }

        override suspend fun remove(animeId: Int) {
            items.remove(animeId)
        }
    }

    private fun TestScope.sync(
        client: ProgressClient,
        queue: SyncQueue,
        account: SignedInAccount? = me,
        settings: Settings = Settings(),
        answers: ShowAnswers? = null,
    ) = AniListSync(backgroundScope, { account }, client, queue, { today }, now = { 1_800_000_000 }, { settings }, answers)

    @Test
    fun aWatchedEpisodeMovesTheProgress() =
        runTest {
            val client = FakeClient()
            val queue = MemoryQueue()

            sync(client, queue).watched(101, 4)
            runCurrent()

            assertEquals(listOf(101 to ProgressUpdate(4, null, null)), client.saved)
            assertEquals(emptyList(), queue.all())
        }

    @Test
    fun nothingIsSentOrQueuedWhenSignedOut() =
        runTest {
            val client = FakeClient()
            val queue = MemoryQueue()

            sync(client, queue, account = null).watched(101, 4)
            runCurrent()

            assertEquals(0, client.calls)
            assertEquals(emptyList(), queue.all())
        }

    @Test
    fun anUpdateThatFailsIsSentLater() =
        runTest {
            val client = FakeClient(failWith = AniListError.Network("offline"))
            val queue = MemoryQueue()
            val sync = sync(client, queue)

            sync.watched(101, 4)
            runCurrent()
            assertEquals(listOf(PendingProgress(101, 4, userId = 1)), queue.all())

            client.failWith = null
            sync.flush()
            runCurrent()

            assertEquals(listOf(101 to ProgressUpdate(4, null, null)), client.saved)
            assertEquals(emptyList(), queue.all())
        }

    @Test
    fun theQueueKeepsTheNewestEpisodePerShow() =
        runTest {
            val client = FakeClient(failWith = AniListError.Network("offline"))
            val queue = MemoryQueue()
            val sync = sync(client, queue)

            sync.watched(101, 4)
            sync.watched(101, 5)
            sync.watched(101, 2)
            runCurrent()

            assertEquals(listOf(PendingProgress(101, 5, userId = 1)), queue.all())
        }

    @Test
    fun aQueuedUpdateIsNeverSentToAnotherAccount() =
        runTest {
            val client = FakeClient()
            val queue = MemoryQueue().apply { items[101] = PendingProgress(101, 4, userId = 2) }

            sync(client, queue).flush()
            runCurrent()

            assertEquals(0, client.calls)
            assertEquals(emptyList(), queue.all())
        }

    @Test
    fun aQueuedUpdateAniListHasPassedIsDropped() =
        runTest {
            val client = FakeClient(entry = ShowEntry(12, ListEntry(ListStatus.WATCHING, 6, hasStartDate = true)))
            val queue = MemoryQueue().apply { items[101] = PendingProgress(101, 5, userId = 1) }

            sync(client, queue).flush()
            runCurrent()

            assertEquals(emptyList(), client.saved)
            assertEquals(emptyList(), queue.all())
        }

    @Test
    fun anUpdateAniListRejectsIsDropped() =
        runTest {
            val client = FakeClient(failWith = AniListError.Http(400))
            val queue = MemoryQueue()

            sync(client, queue).watched(101, 4)
            runCurrent()

            assertEquals(emptyList(), queue.all())
        }

    @Test
    fun anExpiredTokenIsNotSentAndTheUpdateWaits() =
        runTest {
            val client = FakeClient()
            val queue = MemoryQueue()
            val expired = SignedInAccount(me.profile, AniListToken("old-token", 1_000))

            sync(client, queue, account = expired).watched(101, 4)
            runCurrent()

            assertEquals(0, client.calls)
            assertEquals(listOf(PendingProgress(101, 4, userId = 1)), queue.all())
        }

    @Test
    fun aRefusedTokenKeepsTheUpdateForTheSameUser() =
        runTest {
            val client = FakeClient(failWith = AniListError.SignedOut)
            val queue = MemoryQueue()

            sync(client, queue).watched(101, 4)
            runCurrent()

            assertEquals(listOf(PendingProgress(101, 4, userId = 1)), queue.all())
        }

    // Picking this TV's place when AniList is further on (ADR 0050).
    @Test
    fun movingBackSetsOnlyTheProgress() =
        runTest {
            val client = FakeClient(entry = ShowEntry(12, ListEntry(ListStatus.WATCHING, 7, hasStartDate = true)))

            assertTrue(sync(client, MemoryQueue()).movedBack(101, 4, forUser = 1))
            assertEquals(listOf(101 to ProgressUpdate(4, null, null)), client.saved)
        }

    @Test
    fun movingBackDropsTheUsersUpdateWaitingForTheShow() =
        runTest {
            val queue = MemoryQueue()
            queue.put(PendingProgress(101, 9, userId = 1))

            sync(FakeClient(), queue).movedBack(101, 4, forUser = 1)

            assertEquals(emptyList(), queue.all())
        }

    // Details asks again next time, and nothing waiting is lost.
    @Test
    fun aMoveBackThatFailsSaysSoAndKeepsWhatsWaiting() =
        runTest {
            val queue = MemoryQueue()
            queue.put(PendingProgress(101, 9, userId = 1))

            assertFalse(sync(FakeClient(failWith = AniListError.Network("offline")), queue).movedBack(101, 4, forUser = 1))
            assertEquals(listOf(PendingProgress(101, 9, userId = 1)), queue.all())
        }

    @Test
    fun movingBackWithAnExpiredTokenSendsNothingAndKeepsWhatsWaiting() =
        runTest {
            val client = FakeClient()
            val queue = MemoryQueue()
            queue.put(PendingProgress(101, 9, userId = 1))
            val expired = SignedInAccount(me.profile, AniListToken("old-token", 1_000))

            assertFalse(sync(client, queue, account = expired).movedBack(101, 4, forUser = 1))
            assertEquals(0, client.calls)
            assertEquals(1, queue.all().size)
        }

    @Test
    fun movingBackSignedOutSendsNothing() =
        runTest {
            val client = FakeClient()

            assertFalse(sync(client, MemoryQueue(), account = null).movedBack(101, 4, forUser = 1))
            assertEquals(0, client.calls)
        }

    // The list was read for someone else, such as before the TV was paired again.
    @Test
    fun movingBackForAnotherUserSendsNothing() =
        runTest {
            val client = FakeClient()

            assertFalse(sync(client, MemoryQueue()).movedBack(101, 4, forUser = 2))
            assertEquals(0, client.calls)
        }

    @Test
    fun anEpisodeWatchedForAnotherUserSendsNothing() =
        runTest {
            val client = FakeClient()
            val queue = MemoryQueue()

            sync(client, queue).watched(101, 4, forUser = 2)
            runCurrent()

            assertEquals(0, client.calls)
            assertEquals(emptyList(), queue.all())
        }

    private class RecordedList : YourList {
        val moved = mutableListOf<Pair<Int, Int>>()

        override suspend fun shows() = emptyList<ListedProgress>()

        override suspend fun moved(
            animeId: Int,
            progress: Int,
        ) {
            moved += animeId to progress
        }
    }

    private fun listed(progress: Int) = ListedProgress(101, progress, null, 24, userId = 1)

    // Picking this TV's place where it's further on goes through the usual rules, so it can complete the show.
    @Test
    fun pickingThisTvFurtherOnMovesAniListForward() =
        runTest {
            val client = FakeClient()
            val yourList = RecordedList()

            SyncedSides(NoHistory, sync(client, MemoryQueue()), yourList).moveAniList(101, watched = 5, listed = listed(3))
            runCurrent()

            assertEquals(listOf(101 to ProgressUpdate(5, null, null)), client.saved)
            assertEquals(listOf(101 to 5), yourList.moved)
        }

    @Test
    fun pickingThisTvBehindMovesAniListBack() =
        runTest {
            val client = FakeClient(entry = ShowEntry(12, ListEntry(ListStatus.WATCHING, 7, hasStartDate = true)))
            val yourList = RecordedList()

            SyncedSides(NoHistory, sync(client, MemoryQueue()), yourList).moveAniList(101, watched = 4, listed = listed(7))

            assertEquals(listOf(101 to ProgressUpdate(4, null, null)), client.saved)
            assertEquals(listOf(101 to 4), yourList.moved)
        }

    // The kept list stays as AniList has it, so Details asks again.
    @Test
    fun aMoveBackAniListDidntTakeLeavesTheList() =
        runTest {
            val yourList = RecordedList()
            val sync = sync(FakeClient(failWith = AniListError.Network("offline")), MemoryQueue())

            SyncedSides(NoHistory, sync, yourList).moveAniList(101, watched = 4, listed = listed(7))

            assertEquals(emptyList(), yourList.moved)
        }

    @Test
    fun pickingAniListSavesItsPlaceHereNow() {
        val history =
            object : WatchHistory {
                val saved = mutableListOf<Pair<Int, WatchProgress>>()
                override val shows = flowOf(emptyMap<Int, WatchProgress>())

                override fun save(
                    animeId: Int,
                    progress: WatchProgress,
                ) {
                    saved += animeId to progress
                }
            }
        val place = WatchProgress(8, Duration.ZERO, 24.minutes, null, 500)

        SyncedSides(history, null, null, now = { 900 }).saveHere(101, place)

        assertEquals(listOf(101 to place.copy(savedAt = 900)), history.saved)
    }

    private object NoHistory : WatchHistory {
        override val shows = flowOf(emptyMap<Int, WatchProgress>())

        override fun save(
            animeId: Int,
            progress: WatchProgress,
        ) = Unit
    }

    @Test
    fun theEntryIsTheSignedInUsers() =
        runTest {
            val client = FakeClient()

            assertEquals(OnYourList(1, client.entry), sync(client, MemoryQueue()).entry(101))
        }

    @Test
    fun thereIsNoEntrySignedOutWithAnExpiredTokenOrWhenAniListCantBeReached() =
        runTest {
            val expired = SignedInAccount(me.profile, AniListToken("old-token", 1_000))

            assertNull(sync(FakeClient(), MemoryQueue(), account = null).entry(101))
            assertNull(sync(FakeClient(), MemoryQueue(), account = expired).entry(101))
            assertNull(sync(FakeClient(failWith = AniListError.Network("offline")), MemoryQueue()).entry(101))
        }

    // The entry is read again, so dates set elsewhere since the button showed are kept.
    @Test
    fun aStatusIsSavedFromAniListsEntryNow() =
        runTest {
            val client = FakeClient()

            val sync = sync(client, MemoryQueue())
            val told = async { sync.changes.first() }
            runCurrent()

            assertTrue(sync.changeStatus(101, ListStatus.COMPLETED, forUser = 1))
            assertEquals(listOf(101 to ProgressUpdate(12, ListStatus.COMPLETED, null, completedAt = today)), client.saved)
            assertEquals(ListChange(101, ListStatus.COMPLETED, userId = 1, progress = 12), told.await())
        }

    @Test
    fun aStatusForSomeoneElseIsNotSent() =
        runTest {
            val client = FakeClient()

            assertFalse(sync(client, MemoryQueue()).changeStatus(101, ListStatus.PAUSED, forUser = 2))
            assertEquals(0, client.calls)
        }

    @Test
    fun aStatusAniListDoesntTakeSaysSo() =
        runTest {
            assertFalse(sync(FakeClient(failWith = AniListError.Network("offline")), MemoryQueue()).changeStatus(101, ListStatus.PAUSED, 1))
        }

    // A waiting update would add the show back.
    @Test
    fun removingDeletesTheEntryAndWhatsWaitingForIt() =
        runTest {
            val client = FakeClient(ShowEntry(12, ListEntry(ListStatus.WATCHING, 3, true, id = 555)))
            val queue = MemoryQueue().apply { items[101] = PendingProgress(101, 4, userId = 1) }

            val sync = sync(client, queue)
            val told = async { sync.changes.first() }
            runCurrent()

            assertTrue(sync.remove(101, forUser = 1))
            assertEquals(listOf(555), client.removed)
            assertEquals(emptyList(), queue.all())
            assertEquals(ListChange(101, null, userId = 1), told.await())
        }

    @Test
    fun removingAShowThatsNotOnTheListIsDone() =
        runTest {
            val client = FakeClient(ShowEntry(12, null))

            assertTrue(sync(client, MemoryQueue()).remove(101, forUser = 1))
            assertEquals(emptyList(), client.removed)
        }

    @Test
    fun aRemoveForSomeoneElseOrThatFailsDeletesNothing() =
        runTest {
            val client = FakeClient(ShowEntry(12, ListEntry(ListStatus.WATCHING, 3, true, id = 555)))
            val queue = MemoryQueue().apply { items[101] = PendingProgress(101, 4, userId = 1) }

            assertFalse(sync(client, queue).remove(101, forUser = 2))
            assertEquals(0, client.calls)
            client.failWith = AniListError.Network("offline")
            assertFalse(sync(client, queue).remove(101, forUser = 1))
            assertEquals(1, queue.all().size)
        }

    // ADR 0052: Details follows each change the TV makes, so a watched episode tells it the new progress.
    @Test
    fun aWatchedEpisodeSaysWhereTheProgressIsNow() =
        runTest {
            val sync = sync(FakeClient(), MemoryQueue())
            val told = async { sync.changes.first() }
            runCurrent()

            sync.watched(101, 4)
            runCurrent()

            assertEquals(ListChange(101, ListStatus.WATCHING, userId = 1, progress = 4), told.await())
        }

    @Test
    fun markingWatchedMovesTheProgressLikeWatching() =
        runTest {
            val client = FakeClient(ShowEntry(12, ListEntry(ListStatus.PLANNING, 0, hasStartDate = false)))
            val sync = sync(client, MemoryQueue())
            val told = async { sync.changes.first() }
            runCurrent()

            assertTrue(sync.markWatched(101, upTo = 8, forUser = 1))
            assertEquals(listOf(101 to ProgressUpdate(8, ListStatus.WATCHING, today)), client.saved)
            assertEquals(ListChange(101, ListStatus.WATCHING, userId = 1, progress = 8), told.await())
        }

    // An update waiting for an episode the mark covers would change nothing; a later one is still worth sending.
    @Test
    fun markingWatchedDropsOnlyTheUpdatesItCovers() =
        runTest {
            val queue = MemoryQueue().apply { items[101] = PendingProgress(101, 6, userId = 1) }
            assertTrue(sync(FakeClient(), queue).markWatched(101, upTo = 8, forUser = 1))
            assertEquals(emptyList(), queue.all())

            queue.items[101] = PendingProgress(101, 10, userId = 1)
            assertTrue(sync(FakeClient(), queue).markWatched(101, upTo = 8, forUser = 1))
            assertEquals(listOf(PendingProgress(101, 10, userId = 1)), queue.all())
        }

    @Test
    fun markingWatchedWhereAniListIsAlreadySendsNothing() =
        runTest {
            val client = FakeClient(ShowEntry(12, ListEntry(ListStatus.WATCHING, 8, hasStartDate = true)))

            assertTrue(sync(client, MemoryQueue()).markWatched(101, upTo = 8, forUser = 1))
            assertEquals(emptyList(), client.saved)
        }

    @Test
    fun markingUnwatchedSetsOnlyTheProgressAndDropsWhatsWaiting() =
        runTest {
            val client = FakeClient(ShowEntry(12, ListEntry(ListStatus.COMPLETED, 12, hasStartDate = true), finished = true))
            val queue = MemoryQueue().apply { items[101] = PendingProgress(101, 4, userId = 1) }
            val sync = sync(client, queue)
            val told = async { sync.changes.first() }
            runCurrent()

            assertTrue(sync.markUnwatched(101, episode = 12, forUser = 1))
            assertEquals(listOf(101 to ProgressUpdate(11, null, null)), client.saved)
            assertEquals(emptyList(), queue.all())
            assertEquals(ListChange(101, ListStatus.COMPLETED, userId = 1, progress = 11), told.await())
        }

    // Taken off the list elsewhere since Details read it: a save would add it back.
    @Test
    fun markingUnwatchedAShowNoLongerOnTheListSendsNothing() =
        runTest {
            val client = FakeClient(ShowEntry(12, null))

            assertFalse(sync(client, MemoryQueue()).markUnwatched(101, episode = 3, forUser = 1))
            assertEquals(emptyList(), client.saved)
        }

    @Test
    fun aMarkForSomeoneElseOrThatFailsSendsNothing() =
        runTest {
            val client = FakeClient()
            val queue = MemoryQueue().apply { items[101] = PendingProgress(101, 2, userId = 1) }

            assertFalse(sync(client, queue).markWatched(101, upTo = 8, forUser = 2))
            assertFalse(sync(client, queue).markUnwatched(101, episode = 2, forUser = 2))
            assertEquals(0, client.calls)
            client.failWith = AniListError.Network("offline")
            assertFalse(sync(client, queue).markWatched(101, upTo = 8, forUser = 1))
            assertFalse(sync(client, queue).markUnwatched(101, episode = 2, forUser = 1))
            assertEquals(1, queue.all().size)
        }

    private class MemoryAnswers : ShowAnswers {
        val kept = mutableMapOf<Pair<Int, Int>, Boolean>()

        override suspend fun answer(
            userId: Int,
            animeId: Int,
        ) = kept[userId to animeId]

        override suspend fun save(
            userId: Int,
            animeId: Int,
            update: Boolean,
        ) {
            kept[userId to animeId] = update
        }
    }

    @Test
    fun alwaysSendsWhatsWatchedWithoutAsking() =
        runTest {
            val client = FakeClient()
            val sync = sync(client, MemoryQueue(), answers = MemoryAnswers())

            assertNull(sync.asksFirst(101))
            sync.watching(101, 4)
            runCurrent()

            assertEquals(listOf(101 to ProgressUpdate(4, null, null)), client.saved)
        }

    @Test
    fun offSendsNothingWatchedButMarksStillGo() =
        runTest {
            val client = FakeClient()
            val queue = MemoryQueue()
            val sync = sync(client, queue, settings = Settings(updateAniList = UpdateAniList.OFF), answers = MemoryAnswers())

            assertNull(sync.asksFirst(101))
            sync.watching(101, 4)
            runCurrent()
            assertEquals(emptyList(), client.saved)
            assertEquals(emptyList(), queue.all())

            assertTrue(sync.markWatched(101, upTo = 5, forUser = 1))
            assertEquals(
                5,
                client.saved
                    .single()
                    .second.progress,
            )
        }

    @Test
    fun askHoldsAShowsFirstEpisodeUntilAnswered() =
        runTest {
            val client = FakeClient()
            val queue = MemoryQueue()
            val sync = sync(client, queue, settings = Settings(updateAniList = UpdateAniList.ASK), answers = MemoryAnswers())

            assertEquals(1, sync.asksFirst(101))
            sync.watching(101, 4)
            runCurrent()

            assertEquals(emptyList(), client.saved)
            assertEquals(emptyList(), queue.all())
        }

    @Test
    fun answeringYesSendsTheEpisodeAndStopsAsking() =
        runTest {
            val client = FakeClient()
            val answers = MemoryAnswers()
            val sync = sync(client, MemoryQueue(), settings = Settings(updateAniList = UpdateAniList.ASK), answers = answers)

            sync.answer(101, forUser = 1, update = true, episode = 4)
            runCurrent()
            assertNull(sync.asksFirst(101))
            sync.watching(101, 5)
            runCurrent()

            assertEquals(mapOf((1 to 101) to true), answers.kept)
            assertEquals(listOf(4, 5), client.saved.map { it.second.progress })
        }

    @Test
    fun answeringNoSendsNothingNowOrLater() =
        runTest {
            val client = FakeClient()
            val answers = MemoryAnswers()
            val sync = sync(client, MemoryQueue(), settings = Settings(updateAniList = UpdateAniList.ASK), answers = answers)

            sync.answer(101, forUser = 1, update = false, episode = 4)
            runCurrent()
            assertNull(sync.asksFirst(101))
            sync.watching(101, 5)
            runCurrent()

            assertEquals(mapOf((1 to 101) to false), answers.kept)
            assertEquals(emptyList(), client.saved)
        }

    @Test
    fun anAnswerIsOnlyForTheUserWhoGaveIt() =
        runTest {
            val client = FakeClient()
            val answers = MemoryAnswers().apply { kept[2 to 101] = true }
            val sync = sync(client, MemoryQueue(), settings = Settings(updateAniList = UpdateAniList.ASK), answers = answers)

            assertEquals(1, sync.asksFirst(101))
            sync.answer(101, forUser = 2, update = true, episode = 4)
            runCurrent()

            assertEquals(emptyList(), client.saved)
            assertEquals(1, sync.asksFirst(101))
        }

    @Test
    fun thereIsNothingToAskSignedOut() =
        runTest {
            val sync = sync(FakeClient(), MemoryQueue(), account = null, settings = Settings(updateAniList = UpdateAniList.ASK))

            assertNull(sync.asksFirst(101))
        }

    @Test
    fun privateModeSendsAndQueuesNothing() =
        runTest {
            val client = FakeClient()
            val queue = MemoryQueue().apply { items[202] = PendingProgress(202, 2, userId = 1) }
            val sync =
                sync(client, queue, settings = Settings(privateMode = true, updateAniList = UpdateAniList.ASK), answers = MemoryAnswers())

            assertNull(sync.asksFirst(101))
            sync.watching(101, 4)
            sync.watched(101, 4)
            sync.answer(101, forUser = 1, update = true, episode = 4)
            sync.flush()
            runCurrent()

            assertEquals(emptyList(), client.saved)
            assertEquals(listOf(PendingProgress(202, 2, userId = 1)), queue.all())
        }

    @Test
    fun privateModeRefusesEveryOtherWriteAndHidesTheEntry() =
        runTest {
            val client = FakeClient()
            val sync = sync(client, MemoryQueue(), settings = Settings(privateMode = true))

            assertNull(sync.entry(101))
            assertFalse(sync.changeStatus(101, ListStatus.COMPLETED, forUser = 1))
            assertFalse(sync.remove(101, forUser = 1))
            assertFalse(sync.markWatched(101, upTo = 5, forUser = 1))
            assertFalse(sync.markUnwatched(101, episode = 2, forUser = 1))
            assertFalse(sync.movedBack(101, 2, forUser = 1))
            assertEquals(0, client.calls)
        }
}
