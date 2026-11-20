package ani.saikou.neo.shared.account

import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    ) = AniListSync(backgroundScope, { account }, client, queue, { today }, now = { 1_800_000_000 })

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
}
