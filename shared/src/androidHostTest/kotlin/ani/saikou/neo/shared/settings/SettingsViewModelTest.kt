package ani.saikou.neo.shared.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val folder = createTempDirectory("settings").toFile()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store = SettingsStore.at(File(folder, "settings.preferences_pb").absolutePath, scope)

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    // The file's scope ends first, while Main is still the test's: ending it finishes any save a view model is waiting on,
    // and that save carries on on Main.
    @AfterTest
    fun cleanUp() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        Dispatchers.resetMain()
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
    fun choosingAValueSetsItAndSavesIt() =
        withinSeconds {
            val viewModel = SettingsViewModel(store)
            viewModel.state.filterNotNull().first()

            viewModel.choose(Setting.UNAIRED_ANIME, true)

            viewModel.state.filterNotNull().first { it.showUnairedAnime }
            assertTrue(store.settings.first().showUnairedAnime)
        }

    @Test
    fun choosingSubOrDubSavesIt() =
        withinSeconds {
            val viewModel = SettingsViewModel(store)
            viewModel.state.filterNotNull().first()

            viewModel.choose(Setting.SUB_OR_DUB, SubOrDub.SUB)

            viewModel.state.filterNotNull().first { it.subOrDub == SubOrDub.SUB }
            assertEquals(SubOrDub.SUB, store.settings.first().subOrDub)
        }

    // Picking the value a setting already has leaves it as it is, rather than switching it.
    @Test
    fun choosingTheCurrentValueKeepsIt() =
        withinSeconds {
            val viewModel = SettingsViewModel(store)
            viewModel.state.filterNotNull().first()

            viewModel.choose(Setting.UPCOMING_EPISODES, true)
            viewModel.choose(Setting.SLOW_TV, true)

            viewModel.state.filterNotNull().first { it.slowTv }
            assertEquals(Settings(showUpcomingEpisodes = true, slowTv = true), store.settings.first())
        }

    @Test
    fun eachSettingReadsItsOwnValue() {
        val settings = Settings(showUnairedAnime = true, showUpcomingEpisodes = false, slowTv = true, subOrDub = SubOrDub.DUB)

        assertEquals(true, settings[Setting.UNAIRED_ANIME])
        assertEquals(false, settings[Setting.UPCOMING_EPISODES])
        assertEquals(true, settings[Setting.SLOW_TV])
        assertEquals(SubOrDub.DUB, settings[Setting.SUB_OR_DUB])
    }

    // Each setting's value is one of its choices, so the panel always has the current one marked.
    @Test
    fun eachValueIsOneOfItsChoices() {
        Setting.entries.forEach { setting -> assertTrue(Settings()[setting] in setting.choices, "$setting") }
        assertEquals(listOf(SubOrDub.SUB, SubOrDub.DUB, SubOrDub.BOTH), Setting.SUB_OR_DUB.choices)
    }

    @Test
    fun aValueThatIsntAChoiceIsRefused() {
        assertFailsWith<IllegalArgumentException> { SettingsViewModel(store).choose(Setting.SLOW_TV, SubOrDub.SUB) }
    }
}
