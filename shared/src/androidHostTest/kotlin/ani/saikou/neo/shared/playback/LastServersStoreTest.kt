package ani.saikou.neo.shared.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LastServersStoreTest {
    private val folder = createTempDirectory("last-servers").toFile()
    private val file = File(folder, "last_servers.preferences_pb")
    private val scopes = mutableListOf<CoroutineScope>()

    // Each store gets its own scope, so closing one lets another open the same file, as after a restart.
    private fun store(): Pair<LastServersStore, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        return LastServersStore.at(file.absolutePath, scope) to scope
    }

    @AfterTest
    fun cleanUp() {
        scopes.forEach { it.cancel() }
        folder.deleteRecursively()
    }

    @Test
    fun aShowNeverPlayedHasNoLastServer() =
        runBlocking {
            assertNull(store().first.get(1535))
        }

    @Test
    fun eachShowKeepsItsOwnServer() =
        runBlocking {
            val (store, _) = store()

            store.set(1535, LastServer("com.example.demo", "Demo", ServerEdition.SUB))
            store.set(21, LastServer("com.example.example", "Server 2", ServerEdition.HARD_SUB))
            store.set(1535, LastServer("com.example.demo", "Demo", ServerEdition.DUB))

            assertEquals(LastServer("com.example.demo", "Demo", ServerEdition.DUB), store.get(1535))
            assertEquals(LastServer("com.example.example", "Server 2", ServerEdition.HARD_SUB), store.get(21))
        }

    @Test
    fun theLastServerIsThereAfterARestart() =
        runBlocking {
            val (first, scope) = store()
            first.set(1535, LastServer("com.example.demo", "Demo", ServerEdition.SUB))
            scope.coroutineContext.job.cancelAndJoin()

            assertEquals(LastServer("com.example.demo", "Demo", ServerEdition.SUB), store().first.get(1535))
        }
}
