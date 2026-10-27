package ani.saikou.neo.shared.settings

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

class SettingsStoreTest {
    private val folder = createTempDirectory("settings").toFile()
    private val file = File(folder, "settings.preferences_pb")
    private val scopes = mutableListOf<CoroutineScope>()

    // Each store gets its own scope, so closing one lets another open the same file, as after a restart.
    private fun store(): Pair<SettingsStore, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        return SettingsStore.at(file.absolutePath, scope) to scope
    }

    @AfterTest
    fun cleanUp() {
        scopes.forEach { it.cancel() }
        folder.deleteRecursively()
    }

    @Test
    fun aNewInstallHasTheDefaults() =
        runBlocking {
            val (store, _) = store()

            assertEquals(Settings(showUnairedAnime = false, showUpcomingEpisodes = true, slowTv = false), store.settings.first())
        }

    @Test
    fun aChangeIsKeptAfterARestart() =
        runBlocking {
            val (first, scope) = store()
            first.update { it.copy(showUnairedAnime = true, slowTv = true) }
            // DataStore lets the file go once its scope ends.
            scope.coroutineContext.job.cancelAndJoin()

            val (second, _) = store()

            assertEquals(Settings(showUnairedAnime = true, showUpcomingEpisodes = true, slowTv = true), second.settings.first())
        }

    @Test
    fun eachSettingIsSavedOnItsOwn() =
        runBlocking {
            val (store, _) = store()

            store.update { it.copy(showUpcomingEpisodes = false) }

            assertEquals(Settings(showUpcomingEpisodes = false), store.settings.first())
        }
}
