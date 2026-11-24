package ani.saikou.neo.shared.account

import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.home.SyncedSides
import ani.saikou.neo.shared.home.WatchHistory
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.shared.home.YourList
import ani.saikou.neo.shared.log.AppLog
import ani.saikou.neo.shared.log.LogLevel
import ani.saikou.neo.shared.log.RecordingSink
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.settings.UpdateAniList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
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

        val restored = mutableListOf<Pair<Int, ListEntry>>()

        override suspend fun restoreEntry(
            token: AniListToken,
            mediaId: Int,
            entry: ListEntry,
        ): AniListResult<Unit> {
            calls++
            failWith?.let { return AniListResult.Failure(it) }
            restored += mediaId to entry
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
        yourList: YourList? = null,
    ) = AniListSync(backgroundScope, { account }, client, queue, { today }, now = { 1_800_000_000 }, { settings }, answers, yourList)

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
    fun failedSendIsLogged() =
        runTest {
            val sink = RecordingSink()
            AppLog.addSink(sink)
            try {
                val sync = sync(FakeClient(failWith = AniListError.Network("offline")), MemoryQueue())

                sync.watched(101, 4)
                runCurrent()

                assertTrue(sink.lines.any { it.level == LogLevel.Warn && it.tag == "AniList" && "kept for later" in it.message })
            } finally {
                AppLog.removeSink(sink)
            }
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

    // Details read 10, then the phone set 2: unmarking episode 8 would move AniList up to 7.
    @Test
    fun markingUnwatchedAboveAniListsProgressNowSendsNothing() =
        runTest {
            val client = FakeClient(ShowEntry(12, ListEntry(ListStatus.WATCHING, 2, hasStartDate = true)))
            val queue = MemoryQueue().apply { items[101] = PendingProgress(101, 9, userId = 1) }

            assertFalse(sync(client, queue).markUnwatched(101, episode = 8, forUser = 1))
            assertEquals(emptyList(), client.saved)
            assertEquals(listOf(PendingProgress(101, 9, userId = 1)), queue.all())
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

    // ADR 0056: each change the TV makes says what it changed, with the entry as it was, so Undo can put it back.
    private val title = AnimeTitle("Romaji", "English", null)
    private val watching3 = ListEntry(ListStatus.WATCHING, 3, hasStartDate = true, id = 555)

    @Test
    fun watchingSaysWhatChangedAndHowItWas() =
        runTest {
            val sync = sync(FakeClient(ShowEntry(12, watching3)), MemoryQueue())
            val told = async { sync.notices.first() }
            runCurrent()

            sync.watching(101, 4, title)
            runCurrent()

            val notice = told.await()
            assertEquals(NoticeChange.Updated(ListStatus.WATCHING, 4, byEpisode = true), notice.change)
            assertEquals(watching3, notice.before)
            assertEquals(title, notice.title)
            assertTrue(notice.fromWatching)
        }

    @Test
    fun anUpdateThatCantBeSentIsSavedForLater() =
        runTest {
            val queue = MemoryQueue().apply { items[101] = PendingProgress(101, 3, userId = 1) }
            val sync = sync(FakeClient(failWith = AniListError.Network("offline")), queue)
            val told = async { sync.notices.first() }
            runCurrent()

            sync.watching(101, 4, title)
            runCurrent()

            val notice = told.await()
            assertEquals(NoticeChange.SavedForLater(PendingProgress(101, 4, 1), replaced = PendingProgress(101, 3, 1)), notice.change)
            assertEquals(title, notice.title)
        }

    // #504: the episode was already waiting from an earlier try, so watching it again changed nothing in the queue and
    // said nothing. It's still saved for later, and Undo leaves the queue as it was.
    @Test
    fun anEpisodeAlreadyWaitingIsStillSavedForLater() =
        runTest {
            val queue = MemoryQueue().apply { items[101] = PendingProgress(101, 4, userId = 1) }
            val sync = sync(FakeClient(failWith = AniListError.Network("offline")), queue)
            val told = async { sync.notices.first() }
            runCurrent()

            sync.watching(101, 4, title)
            runCurrent()

            val notice = told.await()
            assertEquals(NoticeChange.SavedForLater(PendingProgress(101, 4, 1), replaced = PendingProgress(101, 4, 1)), notice.change)
            assertTrue(sync.undo(notice))
            assertEquals(listOf(PendingProgress(101, 4, 1)), queue.all())
        }

    // A later episode waiting isn't replaced by an earlier one watched again, so nothing new is saved and nothing is said.
    @Test
    fun anEarlierEpisodeWatchedAgainSaysNothing() =
        runTest {
            val queue = MemoryQueue().apply { items[101] = PendingProgress(101, 5, userId = 1) }
            val sync = sync(FakeClient(failWith = AniListError.Network("offline")), queue)
            val told = async { sync.notices.first() }
            runCurrent()

            sync.watching(101, 4, title)
            runCurrent()

            assertFalse(told.isCompleted)
            told.cancel()
        }

    @Test
    fun updatesSentLaterFromTheQueueSayNothing() =
        runTest {
            val queue = MemoryQueue().apply { items[101] = PendingProgress(101, 4, userId = 1) }
            val sync = sync(FakeClient(), queue)
            val told = async { sync.notices.first() }
            runCurrent()

            sync.flush()
            runCurrent()

            assertFalse(told.isCompleted)
            told.cancel()
        }

    @Test
    fun theListButtonAndMarksSayWhatChanged() =
        runTest {
            val client = FakeClient(ShowEntry(12, watching3, title = title))
            val sync = sync(client, MemoryQueue())
            val told = mutableListOf<ListNotice>()
            val collecting = launch { sync.notices.collect { told += it } }
            runCurrent()

            sync.changeStatus(101, ListStatus.PAUSED, forUser = 1)
            sync.markWatched(101, upTo = 5, forUser = 1)
            sync.markUnwatched(101, episode = 3, forUser = 1)
            sync.remove(101, forUser = 1)
            runCurrent()
            collecting.cancel()

            assertEquals(
                listOf(
                    NoticeChange.Updated(ListStatus.PAUSED, 3, byEpisode = false),
                    NoticeChange.Updated(ListStatus.WATCHING, 5, byEpisode = true),
                    NoticeChange.Updated(ListStatus.WATCHING, 2, byEpisode = true),
                    NoticeChange.Removed,
                ),
                told.map { it.change },
            )
            assertTrue(told.all { it.before == watching3 && it.title == title && !it.fromWatching })
        }

    // #512: a mark AniList didn't take went back on Details with nothing to say why.
    @Test
    fun aMarkThatWasntSavedSaysSo() =
        runTest {
            val sync = sync(FakeClient(failWith = AniListError.Network("offline")), MemoryQueue())
            val told = mutableListOf<ListNotice>()
            val collecting = launch { sync.notices.collect { told += it } }
            runCurrent()

            assertFalse(sync.markWatched(101, upTo = 5, forUser = 1, title = title))
            assertFalse(sync.markUnwatched(101, episode = 3, forUser = 1, title = title))
            runCurrent()
            collecting.cancel()

            assertEquals(
                listOf(NoticeChange.NotSaved(5, watched = true), NoticeChange.NotSaved(3, watched = false)),
                told.map { it.change },
            )
            assertTrue(told.all { it.title == title && it.animeId == 101 && it.userId == 1 && !it.fromWatching })
            assertFalse(sync.undo(told.first()))
        }

    @Test
    fun undoPutsTheEntryBackAsItWas() =
        runTest {
            val client = FakeClient(ShowEntry(12, watching3.copy(progress = 4)))
            val notice = ListNotice(101, 1, title, NoticeChange.Updated(ListStatus.WATCHING, 4, byEpisode = true), watching3)

            assertTrue(sync(client, MemoryQueue()).undo(notice))
            assertEquals(listOf(101 to watching3), client.restored)
        }

    @Test
    fun undoTakesAShowThatWasntOnTheListOffAgain() =
        runTest {
            val client = FakeClient(ShowEntry(12, ListEntry(ListStatus.WATCHING, 1, hasStartDate = true, id = 777)))
            val notice = ListNotice(101, 1, title, NoticeChange.Updated(ListStatus.WATCHING, 1, byEpisode = true), before = null)

            assertTrue(sync(client, MemoryQueue()).undo(notice))
            assertEquals(listOf(777), client.removed)
            assertEquals(emptyList(), client.restored)
        }

    @Test
    fun undoingARemoveBringsTheWholeEntryBack() =
        runTest {
            val before = watching3.copy(score = 85, notes = "Rewatch the finale", private = true, startedAt = EntryDate(2026, 10, 1))
            val client = FakeClient(ShowEntry(12, null))
            val notice = ListNotice(101, 1, title, NoticeChange.Removed, before)

            assertTrue(sync(client, MemoryQueue()).undo(notice))
            assertEquals(listOf(101 to before), client.restored)
        }

    @Test
    fun undoSendsNothingOnceTheEntryHasChangedSince() =
        runTest {
            val client = FakeClient(ShowEntry(12, watching3.copy(progress = 6)))
            val notice = ListNotice(101, 1, title, NoticeChange.Updated(ListStatus.WATCHING, 4, byEpisode = true), watching3)

            assertFalse(sync(client, MemoryQueue()).undo(notice))
            assertEquals(emptyList(), client.restored)
            assertEquals(emptyList(), client.removed)
        }

    // A score, note or private flag set elsewhere while the notice showed would be lost.
    @Test
    fun undoSendsNothingOnceSomethingTheTvDidntTouchHasChanged() =
        runTest {
            val notice = ListNotice(101, 1, title, NoticeChange.Updated(ListStatus.WATCHING, 4, byEpisode = true), watching3)
            val client = FakeClient()

            for (now in listOf(
                watching3.copy(progress = 4, score = 90),
                watching3.copy(progress = 4, notes = "New"),
                watching3.copy(progress = 4, private = true),
            )) {
                client.entry = ShowEntry(12, now)
                assertFalse(sync(client, MemoryQueue()).undo(notice))
            }
            assertEquals(emptyList(), client.restored)
        }

    @Test
    fun undoForSomeoneElseInPrivateModeOrOfflineSendsNothing() =
        runTest {
            val client = FakeClient(ShowEntry(12, watching3.copy(progress = 4)))
            val notice = ListNotice(101, 1, title, NoticeChange.Updated(ListStatus.WATCHING, 4, byEpisode = true), watching3)
            val someoneElse = SignedInAccount(AniListProfile(2, "Other", null), me.token)

            assertFalse(sync(client, MemoryQueue(), account = someoneElse).undo(notice))
            assertFalse(sync(client, MemoryQueue(), settings = Settings(privateMode = true)).undo(notice))
            assertEquals(0, client.calls)
            client.failWith = AniListError.Network("offline")
            assertFalse(sync(client, MemoryQueue()).undo(notice))
            assertEquals(emptyList(), client.restored)
        }

    @Test
    fun undoingAnUpdateSavedForLaterPutsTheQueueBack() =
        runTest {
            val client = FakeClient()
            val queue = MemoryQueue().apply { items[101] = PendingProgress(101, 4, userId = 1) }
            val sync = sync(client, queue)

            val replacing =
                ListNotice(101, 1, title, NoticeChange.SavedForLater(PendingProgress(101, 4, 1), PendingProgress(101, 3, 1)), null)
            assertTrue(sync.undo(replacing))
            assertEquals(listOf(PendingProgress(101, 3, userId = 1)), queue.all())

            queue.items[101] = PendingProgress(101, 4, userId = 1)
            assertTrue(sync.undo(replacing.copy(change = NoticeChange.SavedForLater(PendingProgress(101, 4, 1), replaced = null))))
            assertEquals(emptyList(), queue.all())
            assertEquals(0, client.calls)
        }

    // Sent since it was saved, it's on AniList, and the queue no longer has it to take back.
    @Test
    fun undoingAnUpdateAlreadySentSaysSo() =
        runTest {
            val notice = ListNotice(101, 1, title, NoticeChange.SavedForLater(PendingProgress(101, 4, 1), null), null)

            assertFalse(sync(FakeClient(), MemoryQueue()).undo(notice))
        }

    private class ForgetfulList : YourList {
        var forgot = 0

        override suspend fun shows() = emptyList<ListedProgress>()

        override suspend fun forget() {
            forgot++
        }
    }

    // Home and Details keep the list for a minute, and would otherwise work from the progress before (#478).
    @Test
    fun watchingAndUndoDropTheKeptList() =
        runTest {
            val list = ForgetfulList()
            val client = FakeClient(ShowEntry(12, watching3))
            val sync = sync(client, MemoryQueue(), yourList = list)
            val told = async { sync.notices.first() }
            runCurrent()

            sync.watching(101, 4, title)
            runCurrent()
            assertEquals(1, list.forgot)

            client.entry = ShowEntry(12, watching3.copy(progress = 4))
            assertTrue(sync.undo(told.await()))
            assertEquals(2, list.forgot)
        }

    @Test
    fun theListButtonAndMarksDropTheKeptListToo() =
        runTest {
            val list = ForgetfulList()
            val sync = sync(FakeClient(ShowEntry(12, watching3)), MemoryQueue(), yourList = list)

            sync.changeStatus(101, ListStatus.PAUSED, forUser = 1)
            sync.markWatched(101, upTo = 5, forUser = 1)
            sync.markUnwatched(101, episode = 3, forUser = 1)
            sync.remove(101, forUser = 1)

            assertEquals(4, list.forgot)
        }

    // A change AniList didn't take leaves the kept list alone (#494).
    @Test
    fun aChangeAniListRefusedKeepsTheList() =
        runTest {
            val list = ForgetfulList()
            val client = FakeClient(ShowEntry(12, watching3), failWith = AniListError.Network("offline"))
            val sync = sync(client, MemoryQueue(), yourList = list)

            assertFalse(sync.changeStatus(101, ListStatus.PAUSED, forUser = 1))
            assertFalse(sync.markWatched(101, upTo = 5, forUser = 1))

            assertEquals(0, list.forgot)
        }

    // Keeps one entry as AniList does, including the rewatch AniList adds itself when Rewatching becomes Completed,
    // whatever rewatch count the save sends.
    private class AniListLikeClient(
        var entry: ListEntry?,
        private val episodes: Int = 26,
    ) : ProgressClient {
        override suspend fun showEntry(
            token: AniListToken,
            mediaId: Int,
        ): AniListResult<ShowEntry> =
            AniListResult.Success(ShowEntry(episodes, entry, finished = true, title = AnimeTitle("Romaji", null, null)))

        override suspend fun saveProgress(
            token: AniListToken,
            mediaId: Int,
            update: ProgressUpdate,
        ): AniListResult<Unit> {
            val was = entry ?: ListEntry(ListStatus.WATCHING, 0, hasStartDate = false, id = 555)
            save(
                was.copy(
                    status = update.status ?: was.status,
                    progress = update.progress,
                    repeat = update.repeat ?: was.repeat,
                    hasStartDate = was.hasStartDate || update.startedAt != null,
                    hasFinishDate = was.hasFinishDate || update.completedAt != null,
                ),
            )
            return AniListResult.Success(Unit)
        }

        override suspend fun removeEntry(
            token: AniListToken,
            entryId: Int,
        ): AniListResult<Unit> {
            entry = null
            return AniListResult.Success(Unit)
        }

        override suspend fun restoreEntry(
            token: AniListToken,
            mediaId: Int,
            entry: ListEntry,
        ): AniListResult<Unit> {
            save(entry.copy(id = this.entry?.id ?: entry.id))
            return AniListResult.Success(Unit)
        }

        private fun save(new: ListEntry) {
            val finishesRewatch = entry?.status == ListStatus.REWATCHING && new.status == ListStatus.COMPLETED
            entry = if (finishesRewatch) new.copy(repeat = entry!!.repeat + 1) else new
        }
    }

    private suspend fun TestScope.watchAndUndo(
        client: AniListLikeClient,
        episode: Int,
    ): Boolean {
        val sync = sync(client, MemoryQueue())
        val told = async { sync.notices.first() }
        runCurrent()
        sync.watching(101, episode)
        runCurrent()
        return sync.undo(told.await())
    }

    // ADR 0048: episode 1 of a Completed show starts a rewatch. Undo puts it back to Completed, with its count as it was.
    @Test
    fun undoingARewatchStartKeepsTheRewatchCount() =
        runTest {
            val completed =
                ListEntry(
                    ListStatus.COMPLETED,
                    26,
                    hasStartDate = true,
                    repeat = 0,
                    hasFinishDate = true,
                    id = 555,
                    startedAt = EntryDate(2026, 1, 5),
                    completedAt = EntryDate(2026, 3, 30),
                )
            val client = AniListLikeClient(completed)

            assertTrue(watchAndUndo(client, episode = 1))
            assertEquals(completed, client.entry)
        }

    @Test
    fun undoingAFinishedRewatchKeepsTheRewatchCount() =
        runTest {
            val rewatching =
                ListEntry(
                    ListStatus.REWATCHING,
                    25,
                    hasStartDate = true,
                    repeat = 1,
                    hasFinishDate = true,
                    id = 555,
                    startedAt = EntryDate(2026, 1, 5),
                    completedAt = EntryDate(2026, 3, 30),
                )
            val client = AniListLikeClient(rewatching)

            assertTrue(watchAndUndo(client, episode = 26))
            assertEquals(rewatching, client.entry)
        }

    // Such as a queue file that can't be read.
    private class BrokenQueue : SyncQueue {
        override suspend fun all(): List<PendingProgress> = throw IOException("unreadable")

        override suspend fun put(pending: PendingProgress): Unit = throw IOException("unreadable")

        override suspend fun remove(animeId: Int): Unit = throw IOException("unreadable")
    }

    @Test
    fun aStorageErrorFailsAChangeInsteadOfThrowing() =
        runTest {
            val client = FakeClient(ShowEntry(12, watching3.copy(progress = 4)))
            val sync = sync(client, BrokenQueue())
            val updated = ListNotice(101, 1, title, NoticeChange.Updated(ListStatus.WATCHING, 4, byEpisode = true), watching3)
            val savedForLater = ListNotice(101, 1, title, NoticeChange.SavedForLater(PendingProgress(101, 4, 1), null), null)

            assertFalse(sync.remove(101, forUser = 1))
            assertFalse(sync.markWatched(101, upTo = 5, forUser = 1))
            assertFalse(sync.markUnwatched(101, episode = 3, forUser = 1))
            assertFalse(sync.movedBack(101, 2, forUser = 1))
            assertFalse(sync.undo(updated))
            assertFalse(sync.undo(savedForLater))
        }

    // ADR 0056: what can't go yet is saved for later and says so, an expired token included.
    @Test
    fun anUpdateQueuedForAnExpiredTokenIsSavedForLater() =
        runTest {
            val expired = SignedInAccount(me.profile, AniListToken("old-token", 1_000))
            val sync = sync(FakeClient(), MemoryQueue(), account = expired)
            val told = async { sync.notices.first() }
            runCurrent()

            sync.watching(101, 4, title)
            runCurrent()

            assertEquals(NoticeChange.SavedForLater(PendingProgress(101, 4, 1), replaced = null), told.await().change)
        }

    // ADR 0053: updates queued before Off, or before Ask was answered, still go.
    @Test
    fun offAndAskStillSendWhatsAlreadyQueued() =
        runTest {
            for (setting in listOf(UpdateAniList.OFF, UpdateAniList.ASK)) {
                val client = FakeClient()
                val queue = MemoryQueue().apply { items[202] = PendingProgress(202, 4, userId = 1) }
                val sync = sync(client, queue, settings = Settings(updateAniList = setting), answers = MemoryAnswers())

                sync.watching(101, 2)
                runCurrent()

                assertEquals(listOf(202 to ProgressUpdate(4, null, null)), client.saved)
                assertEquals(emptyList(), queue.all())
            }
        }

    // Each try could time out while the list button, marks and Undo wait for the lock.
    @Test
    fun aNetworkFailureStopsTheRestOfTheQueueForThatTry() =
        runTest {
            val client = FakeClient(failWith = AniListError.Network("offline"))
            val waiting =
                listOf(PendingProgress(201, 2, userId = 1), PendingProgress(202, 3, userId = 1), PendingProgress(203, 4, userId = 1))
            val queue = MemoryQueue().apply { waiting.forEach { items[it.animeId] = it } }

            sync(client, queue).flush()
            runCurrent()

            assertEquals(1, client.calls)
            assertEquals(waiting, queue.all())
        }

    // The episode just watched goes first, so it can say it was saved for later.
    @Test
    fun theEpisodeJustWatchedIsTriedFirst() =
        runTest {
            val client = FakeClient(failWith = AniListError.Network("offline"))
            val queue = MemoryQueue().apply { items[201] = PendingProgress(201, 2, userId = 1) }
            val sync = sync(client, queue)
            val told = async { sync.notices.first() }
            runCurrent()

            sync.watching(101, 4, title)
            runCurrent()

            assertEquals(1, client.calls)
            assertEquals(NoticeChange.SavedForLater(PendingProgress(101, 4, 1), replaced = null), told.await().change)
        }
}
