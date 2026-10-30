package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AniZipEngine
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.ArtLibrary
import ani.saikou.neo.shared.catalogue.ArtSources
import ani.saikou.neo.shared.catalogue.FanartTvClient
import ani.saikou.neo.shared.catalogue.FanartTvEngine
import ani.saikou.neo.shared.catalogue.LoadProblem
import ani.saikou.neo.shared.catalogue.OfflineEngine
import ani.saikou.neo.shared.catalogue.QueryEngine
import ani.saikou.neo.shared.catalogue.RecordedEngine
import ani.saikou.neo.shared.catalogue.TmdbClient
import ani.saikou.neo.shared.catalogue.card
import ani.saikou.neo.shared.catalogue.clientFor
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Buffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SeeMoreViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    // Page 1 is browse-trending (195516, 195539, more to come). Page 2 is browse-last (3285, 8899, 12807, the end).
    private fun pagedEngine() = QueryEngine("browse-trending", mapOf("\"page\":2" to "browse-last"))

    private fun seeMore(
        engine: HttpEngine = pagedEngine(),
        row: HomeRow = HomeRow.TRENDING,
        aniZip: AniZipEngine = AniZipEngine("mappings"),
    ): SeeMoreViewModel {
        val client = clientFor(engine, main)
        val sources =
            ArtSources(
                client,
                AniZipClient(aniZip, main),
                FanartTvClient("test-project-key", FanartTvEngine("tv"), main),
                TmdbClient(null, OfflineEngine, main),
            )
        return SeeMoreViewModel(client, ArtLibrary(sources, CoroutineScope(main)), row, AnimeSeason.FALL, 2026)
    }

    private fun SeeMoreViewModel.ids() = state.value.items.map { it.id }

    @Test
    fun recommendationsPageThroughTheShowsRecommendations() =
        runTest(main) {
            val client = clientFor(RecordedEngine("recommendations"), main)
            val sources =
                ArtSources(
                    client,
                    AniZipClient(AniZipEngine("mappings"), main),
                    FanartTvClient(null, OfflineEngine, main),
                    TmdbClient(null, OfflineEngine, main),
                )
            val viewModel = SeeMoreViewModel.recommendations(client, ArtLibrary(sources, CoroutineScope(main)), animeId = 1535)
            advanceUntilIdle()

            assertEquals(20, viewModel.state.value.items.size)
            assertEquals(
                "Death Note",
                viewModel.state.value.show
                    ?.english,
            )
            assertEquals(More.Ready, viewModel.state.value.more)
        }

    @Test
    fun startsLoadingWithNoShows() {
        val viewModel = seeMore()

        assertEquals(SeeMoreState(emptyList(), More.Loading), viewModel.state.value)
    }

    @Test
    fun loadsTheRowsFirstPage() =
        runTest(main) {
            val viewModel = seeMore()
            advanceUntilIdle()

            assertEquals(listOf(195516, 195539), viewModel.ids())
            assertEquals(More.Ready, viewModel.state.value.more)
        }

    @Test
    fun asksForTheRowsOwnList() =
        runTest(main) {
            val engine = RecordedEngine("browse-trending")
            seeMore(engine, HomeRow.THIS_SEASON)
            advanceUntilIdle()

            assertTrue(engine.requestBodies.single().let { "\"season\":\"FALL\"" in it && "\"seasonYear\":2026" in it })
        }

    @Test
    fun loadMoreAddsTheNextPageUntilThereAreNoneLeft() =
        runTest(main) {
            val engine = pagedEngine()
            val viewModel = seeMore(engine)
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            assertEquals(listOf(195516, 195539, 3285, 8899, 12807), viewModel.ids())
            assertEquals(More.Done, viewModel.state.value.more)
            viewModel.loadMore()
            advanceUntilIdle()
            assertEquals(2, engine.requestBodies.size)
        }

    @Test
    fun loadMoreAsksOnceWhileAPageIsOnItsWay() =
        runTest(main) {
            val engine = pagedEngine()
            val viewModel = seeMore(engine)
            advanceUntilIdle()

            repeat(3) { viewModel.loadMore() }
            advanceUntilIdle()

            assertEquals(2, engine.requestBodies.size)
        }

    @Test
    fun aFailedFirstPageShowsNothing() =
        runTest(main) {
            val viewModel = seeMore(OfflineEngine)
            advanceUntilIdle()

            assertEquals(SeeMoreState(emptyList(), More.Failed, problem = LoadProblem.NO_NETWORK), viewModel.state.value)
        }

    @Test
    fun beingRateLimitedSaysSo() =
        runTest(main) {
            val viewModel = seeMore(RecordedEngine("rate-limited", status = 429))
            advanceUntilIdle()

            assertEquals(LoadProblem.RATE_LIMITED, viewModel.state.value.problem)
        }

    @Test
    fun tryingAgainClearsTheProblem() =
        runTest(main) {
            val engine = FlakyEngine(offline = true)
            val viewModel = seeMore(engine)
            advanceUntilIdle()

            engine.offline = false
            viewModel.retry()

            assertEquals(null, viewModel.state.value.problem)
        }

    @Test
    fun aFailedLaterPageKeepsTheShowsAndWaitsForRetry() =
        runTest(main) {
            val engine = FlakyEngine()
            val viewModel = seeMore(engine)
            advanceUntilIdle()
            engine.offline = true
            viewModel.loadMore()
            advanceUntilIdle()

            assertEquals(SeeMoreState(viewModel.state.value.items, More.Failed, problem = LoadProblem.NO_NETWORK), viewModel.state.value)
            assertEquals(listOf(195516, 195539), viewModel.ids())
            engine.offline = false
            viewModel.loadMore()
            advanceUntilIdle()
            assertEquals(More.Failed, viewModel.state.value.more)

            viewModel.retry()
            advanceUntilIdle()
            assertEquals(listOf(195516, 195539, 3285, 8899, 12807), viewModel.ids())
            assertTrue(engine.requests.last().contains("\"page\":2"))
        }

    @Test
    fun retryLoadsAFailedFirstPage() =
        runTest(main) {
            val engine = FlakyEngine(offline = true)
            val viewModel = seeMore(engine)
            advanceUntilIdle()

            engine.offline = false
            viewModel.retry()
            advanceUntilIdle()

            assertEquals(listOf(195516, 195539), viewModel.ids())
        }

    @Test
    fun showsAlreadyShownAreNotRepeated() =
        runTest(main) {
            // AniList's order moves between requests, so a later page can bring back a show from an earlier one.
            val viewModel = seeMore(RecordedEngine("browse-trending"))
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            assertEquals(listOf(195516, 195539), viewModel.ids())
        }

    @Test
    fun restingOnAPosterFindsItsArt() =
        runTest(main) {
            val aniZip = AniZipEngine("mappings")
            val viewModel = seeMore(aniZip = aniZip)

            viewModel.rest(card(424242, "Rested on"))

            assertEquals(
                Logo.Image("https://assets.fanart.tv/fanart/sakamoto-days-66866dc54d0e7.png"),
                viewModel.state
                    .first {
                        it.restingLogo !=
                            null
                    }.restingLogo,
            )
            assertEquals(1, aniZip.urls.count { "=424242" in it })
        }
}

/** Pages like the recordings, or fails every request while [offline]. */
private class FlakyEngine(
    var offline: Boolean = false,
) : HttpEngine {
    private val pages = QueryEngine("browse-trending", mapOf("\"page\":2" to "browse-last"))
    val requests = mutableListOf<String>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        return if (offline) OfflineEngine.execute(request) else pages.execute(request)
    }

    override fun close() {}
}
