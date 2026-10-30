package ani.saikou.neo.shared.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentSearchesStoreTest {
    private val folder = createTempDirectory("recent-searches").toFile()
    private val file = File(folder, "recent_searches.preferences_pb")
    private val scopes = mutableListOf<CoroutineScope>()

    // Each store gets its own scope, so closing one lets another open the same file, as after a restart.
    private fun store(): Pair<RecentSearchesStore, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        return RecentSearchesStore.at(file.absolutePath, scope) to scope
    }

    @AfterTest
    fun cleanUp() {
        scopes.forEach { it.cancel() }
        folder.deleteRecursively()
    }

    @Test
    fun aNewInstallHasNoSearches() =
        runBlocking {
            val (store, _) = store()

            assertEquals(emptyList(), store.searches.first())
        }

    @Test
    fun theNewestSearchComesFirst() =
        runBlocking {
            val (store, _) = store()

            store.add("frieren")
            store.add("one piece")

            assertEquals(listOf("one piece", "frieren"), store.searches.first())
        }

    @Test
    fun onlyTheLastFiveAreKept() =
        runBlocking {
            val (store, _) = store()

            listOf("a", "b", "c", "d", "e", "f").forEach { store.add(it) }

            assertEquals(listOf("f", "e", "d", "c", "b"), store.searches.first())
        }

    @Test
    fun searchingAgainMovesASearchToTheFront() =
        runBlocking {
            val (store, _) = store()
            store.add("frieren")
            store.add("one piece")

            store.add("Frieren")

            assertEquals(listOf("Frieren", "one piece"), store.searches.first())
        }

    @Test
    fun searchesAreKeptAfterARestart() =
        runBlocking {
            val (first, scope) = store()
            first.add("frieren")
            first.add("sakamoto days")
            // DataStore lets the file go once its scope ends.
            scope.coroutineContext.job.cancelAndJoin()

            val (second, _) = store()

            assertEquals(listOf("sakamoto days", "frieren"), second.searches.first())
        }
}
