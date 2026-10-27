package ani.saikou.neo.shared.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val folder = createTempDirectory("settings").toFile()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store = SettingsStore.at(File(folder, "settings.preferences_pb").absolutePath, scope)

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun cleanUp() {
        Dispatchers.resetMain()
        scope.cancel()
        folder.deleteRecursively()
    }

    // The file is read and written on real threads, so a test waits for them, but never for long.
    private fun withinSeconds(test: suspend () -> Unit) = runBlocking { withTimeout(5_000) { test() } }

    @Test
    fun showsTheStoredSettings() =
        withinSeconds {
            store.update { it.copy(slowTv = true) }

            val settings = SettingsViewModel(store).state.filterNotNull().first()

            assertEquals(Settings(slowTv = true), settings)
        }

    @Test
    fun togglingASettingSwitchesItAndSavesIt() =
        withinSeconds {
            val viewModel = SettingsViewModel(store)
            viewModel.state.filterNotNull().first()

            viewModel.toggle(Setting.UNAIRED_ANIME)

            assertTrue(viewModel.state.filterNotNull().first { it.showUnairedAnime }.showUnairedAnime)
            assertTrue(store.settings.first().showUnairedAnime)
        }

    @Test
    fun eachSettingReadsItsOwnValue() {
        val settings = Settings(showUnairedAnime = true, showUpcomingEpisodes = false, slowTv = true)

        assertTrue(settings.isOn(Setting.UNAIRED_ANIME))
        assertFalse(settings.isOn(Setting.UPCOMING_EPISODES))
        assertTrue(settings.isOn(Setting.SLOW_TV))
    }
}
