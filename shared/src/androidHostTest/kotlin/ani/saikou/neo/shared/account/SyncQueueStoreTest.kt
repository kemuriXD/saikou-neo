package ani.saikou.neo.shared.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncQueueStoreTest {
    private val folder = createTempDirectory("queue").toFile()
    private val file = File(folder, "anilist_queue.preferences_pb")
    private val scopes = mutableListOf<CoroutineScope>()

    private fun store(): Pair<SyncQueueStore, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        return SyncQueueStore.at(file.absolutePath, scope) to scope
    }

    @AfterTest
    fun cleanUp() {
        scopes.forEach { it.cancel() }
        folder.deleteRecursively()
    }

    @Test
    fun waitingUpdatesAreKeptAfterARestartOnePerShow() =
        runBlocking {
            val (first, scope) = store()
            first.put(PendingProgress(101, 4, userId = 1))
            first.put(PendingProgress(101, 5, userId = 1))
            first.put(PendingProgress(102, 2, userId = 1))
            first.remove(102)
            scope.coroutineContext.job.let {
                scope.cancel()
                it.join()
            }

            val (second, _) = store()

            assertEquals(listOf(PendingProgress(101, 5, userId = 1)), second.all())
        }
}
