package ani.saikou.neo.shared.settings

import androidx.lifecycle.viewModelScope
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val folder = createTempDirectory("settings").toFile()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store = SettingsStore.at(File(folder, "settings.preferences_pb").absolutePath, scope)
    private val viewModels = mutableListOf<SettingsViewModel>()

    private fun viewModel() = SettingsViewModel(store).also { viewModels += it }

    // Waits for each view model's work to finish, so none of it is left to carry on once the test is over.
    private fun endViewModels() =
        runBlocking {
            viewModels.forEach {
                it.viewModelScope.coroutineContext.job
                    .cancelAndJoin()
            }
        }

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    // The view models and then the file's scope end while Main is still the test's. Work a view model was waiting on
    // the file for carries on on Main when it ends, so it has to be over before Main is reset.
    @AfterTest
    fun cleanUp() {
        endViewModels()
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

            val settings = viewModel().state.filterNotNull().first()

            assertEquals(Settings(slowTv = true), settings)
        }

    @Test
    fun choosingAValueSetsItAndSavesIt() =
        withinSeconds {
            val viewModel = viewModel()
            viewModel.state.filterNotNull().first()

            viewModel.choose(Setting.UNAIRED_ANIME, true)

            viewModel.state.filterNotNull().first { it.showUnairedAnime }
            assertTrue(store.settings.first().showUnairedAnime)
        }

    @Test
    fun choosingSubOrDubSavesIt() =
        withinSeconds {
            val viewModel = viewModel()
            viewModel.state.filterNotNull().first()

            viewModel.choose(Setting.SUB_OR_DUB, SubOrDub.SUB)

            viewModel.state.filterNotNull().first { it.subOrDub == SubOrDub.SUB }
            assertEquals(SubOrDub.SUB, store.settings.first().subOrDub)
        }

    // Picking the value a setting already has leaves it as it is, rather than switching it.
    @Test
    fun choosingTheCurrentValueKeepsIt() =
        withinSeconds {
            val viewModel = viewModel()
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
        assertFailsWith<IllegalArgumentException> { viewModel().choose(Setting.SLOW_TV, SubOrDub.SUB) }
    }

    // A view model left running would carry on on Main after the test resets it, and fail whichever test runs next.
    @Test
    fun cleanUpEndsEachViewModelsWork() =
        withinSeconds {
            val viewModel = viewModel()
            viewModel.state.filterNotNull().first()

            endViewModels()

            assertFalse(viewModel.viewModelScope.coroutineContext.job.isActive)
            assertTrue(
                viewModel.viewModelScope.coroutineContext.job.children
                    .none(),
            )
        }
}
