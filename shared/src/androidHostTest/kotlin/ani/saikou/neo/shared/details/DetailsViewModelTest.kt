package ani.saikou.neo.shared.details

import ani.saikou.neo.shared.catalogue.OfflineEngine
import ani.saikou.neo.shared.catalogue.RecordedEngine
import ani.saikou.neo.shared.catalogue.clientFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun startsLoading() {
        val viewModel = DetailsViewModel(clientFor(RecordedEngine("details")), animeId = 195516)

        assertEquals(DetailsState.Loading, viewModel.state.value)
    }

    @Test
    fun loadsTheAnimeItWasGiven() =
        runTest(main) {
            val engine = RecordedEngine("details")
            val viewModel = DetailsViewModel(clientFor(engine), animeId = 195516)

            val loaded = assertIs<DetailsState.Loaded>(viewModel.settled())
            assertEquals(195516, loaded.details.card.id)
            assertEquals(listOf("Drama", "Mystery"), loaded.details.genres)
            assertEquals("Crunchyroll", loaded.synopsis.source)
            assertTrue(engine.requestBodies.single().contains("\"id\":195516"))
        }

    @Test
    fun anAnimeAniListDoesNotKnowFails() =
        runTest(main) {
            val viewModel = DetailsViewModel(clientFor(RecordedEngine("details-not-found")), animeId = 1)

            assertEquals(DetailsState.Failed, viewModel.settled())
        }

    @Test
    fun noConnectionFails() =
        runTest(main) {
            val viewModel = DetailsViewModel(clientFor(OfflineEngine), animeId = 195516)

            assertEquals(DetailsState.Failed, viewModel.settled())
        }
}

/** Apollo runs requests on its own dispatcher, so wait until loading ends. */
private suspend fun DetailsViewModel.settled() = state.first { it != DetailsState.Loading }
