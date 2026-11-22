package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.playback.LastServer
import ani.saikou.neo.shared.playback.ServerEdition
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WatchHistoryStoreTest {
    private val folder = createTempDirectory("history").toFile()
    private val file = File(folder, "watch_history.preferences_pb")
    private val scopes = mutableListOf<CoroutineScope>()

    // Each store gets its own scope, so closing one lets another open the same file, as after a restart.
    private fun store(): Pair<WatchHistoryStore, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        return WatchHistoryStore.at(file.absolutePath, scope) to scope
    }

    @AfterTest
    fun cleanUp() {
        scopes.forEach { it.cancel() }
        folder.deleteRecursively()
    }

    private val seven = WatchProgress(7, 12.minutes + 4.seconds, 24.minutes, "https://img.example/7;still.jpg", savedAt = 1_793_921_400)

    @Test
    fun aNewInstallHasNoProgress() =
        runBlocking {
            val (store, _) = store()

            assertEquals(emptyMap(), store.shows.first())
        }

    @Test
    fun savedProgressIsKeptAfterARestart() =
        runBlocking {
            val (first, scope) = store()
            first.save(1535, seven)
            first.save(21, seven.copy(episode = 2, still = null, savedAt = null))
            first.shows.first { it.size == 2 }
            scope.coroutineContext.job.cancelAndJoin()

            val (second, _) = store()

            assertEquals(mapOf(1535 to seven, 21 to seven.copy(episode = 2, still = null, savedAt = null)), second.shows.first())
        }

    @Test
    fun aShowKeepsOnlyItsLatestProgress() =
        runBlocking {
            val (store, _) = store()
            store.save(1535, seven)
            store.save(1535, seven.copy(episode = 8, watched = 30.seconds))

            assertEquals(mapOf(1535 to seven.copy(episode = 8, watched = 30.seconds)), store.shows.first { it[1535]?.episode == 8 })
        }
}

class LastServerStoreTest {
    private val folder = createTempDirectory("history").toFile()
    private val file = File(folder, "watch_history.preferences_pb")
    private val scopes = mutableListOf<CoroutineScope>()

    private fun store(): Pair<WatchHistoryStore, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        return WatchHistoryStore.at(file.absolutePath, scope) to scope
    }

    @AfterTest
    fun cleanUp() {
        scopes.forEach { it.cancel() }
        folder.deleteRecursively()
    }

    // Names come from sources, so they can hold anything the encoding might split on.
    private val dub = LastServer("com.example.demo", "Demo; Dub", ServerEdition.DUB, audio = "English; 5.1")

    @Test
    fun theLastServerIsKeptAfterARestartBesideTheProgress() =
        runBlocking {
            val (first, scope) = store()
            first.save(1535, WatchProgress(7, 1.minutes, 24.minutes, null))
            first.saveServer(1535, dub)
            first.saveServer(21, dub.copy(name = "Demo, Sub", edition = ServerEdition.SUB, audio = null))
            first.lastServers.first { it.size == 2 }
            scope.coroutineContext.job.cancelAndJoin()

            val (second, _) = store()

            assertEquals(
                mapOf(1535 to dub, 21 to dub.copy(name = "Demo, Sub", edition = ServerEdition.SUB, audio = null)),
                second.lastServers.first(),
            )
            assertEquals(setOf(1535), second.shows.first().keys)
        }

    @Test
    fun aShowKeepsOnlyItsLatestServer() =
        runBlocking {
            val (store, _) = store()
            store.saveServer(1535, dub)
            store.saveServer(1535, dub.copy(name = "Other"))

            assertEquals(mapOf(1535 to dub.copy(name = "Other")), store.lastServers.first { it[1535]?.name == "Other" })
        }
}
