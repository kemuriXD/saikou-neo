package ani.saikou.neo.shared.details

import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AniZipEngine
import ani.saikou.neo.shared.catalogue.OfflineEngine
import ani.saikou.neo.shared.catalogue.QueryEngine
import ani.saikou.neo.shared.catalogue.RecordedEngine
import ani.saikou.neo.shared.catalogue.SilentEngine
import ani.saikou.neo.shared.catalogue.clientFor
import com.apollographql.apollo.network.http.HttpEngine
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    // On the test's dispatcher, so no art is still on its way once a test ends.
    private val aniZipEngine = AniZipEngine("mappings")
    private val aniZip = AniZipClient(aniZipEngine, main)

    private fun detailsViewModel(
        engine: HttpEngine = RecordedEngine("details"),
        aniZip: AniZipClient = this.aniZip,
    ) = DetailsViewModel(clientFor(engine, main), aniZip, animeId = 195516)

    @Test
    fun startsLoading() {
        val viewModel = detailsViewModel()

        assertEquals(DetailsState.Loading, viewModel.state.value)
    }

    @Test
    fun loadsTheAnimeItWasGiven() =
        runTest(main) {
            val engine = RecordedEngine("details")
            val viewModel = detailsViewModel(engine)

            val loaded = assertIs<DetailsState.Loaded>(viewModel.settled())
            assertEquals(195516, loaded.details.card.id)
            assertEquals(listOf("Drama", "Mystery"), loaded.details.genres)
            assertEquals("Crunchyroll", loaded.synopsis.source)
            assertTrue(engine.requestBodies.single().contains("\"id\":195516"))
        }

    @Test
    fun anAnimeAniListDoesNotKnowFails() =
        runTest(main) {
            val viewModel = detailsViewModel(RecordedEngine("details-not-found"))

            assertEquals(DetailsState.Failed, viewModel.settled())
        }

    @Test
    fun noConnectionFails() =
        runTest(main) {
            val viewModel = detailsViewModel(OfflineEngine)

            assertEquals(DetailsState.Failed, viewModel.settled())
        }

    @Test
    fun theBackdropIsTheShowsFanArt() =
        runTest(main) {
            val viewModel = detailsViewModel()

            val loaded = viewModel.state.first { (it as? DetailsState.Loaded)?.backdrop != null } as DetailsState.Loaded
            assertEquals("https://artworks.thetvdb.com/banners/v4/series/423732/backgrounds/6654700131bab.jpg", loaded.backdrop)
            assertEquals("https://api.ani.zip/mappings?anilist_id=195516", aniZipEngine.urls.single())
        }

    // The Apothecary Diaries movie has no fan art, but the series it belongs to does.
    @Test
    fun withoutFanArtTheBackdropIsItsSeriesFanArt() =
        runTest(main) {
            val engine = QueryEngine("details", mapOf("AnimeParents" to "parents"))
            val aniZip = AniZipClient(AniZipEngine("mappings-unknown", byId = mapOf(21175 to "mappings-parent")), main)
            val viewModel = detailsViewModel(engine, aniZip)

            val loaded = viewModel.state.first { (it as? DetailsState.Loaded)?.backdrop != null } as DetailsState.Loaded
            assertEquals("https://artworks.thetvdb.com/banners/fanart/original/295068-1.jpg", loaded.backdrop)
        }

    @Test
    fun withoutFanArtTheBackdropIsTheBanner() =
        runTest(main) {
            val viewModel = detailsViewModel(aniZip = AniZipClient(OfflineEngine, main))

            val loaded = viewModel.state.first { (it as? DetailsState.Loaded)?.backdrop != null } as DetailsState.Loaded
            assertEquals(loaded.details.card.bannerImageUrl, loaded.backdrop)
        }

    // So the art fades in once, rather than AniList's first and then the fan art.
    @Test
    fun theBackdropWaitsForAniZip() =
        runTest(main) {
            val viewModel = detailsViewModel(aniZip = AniZipClient(SilentEngine, main))

            assertNull(assertIs<DetailsState.Loaded>(viewModel.settled()).backdrop)
        }
}

/** Waits until loading ends. */
private suspend fun DetailsViewModel.settled() = state.first { it != DetailsState.Loading }
