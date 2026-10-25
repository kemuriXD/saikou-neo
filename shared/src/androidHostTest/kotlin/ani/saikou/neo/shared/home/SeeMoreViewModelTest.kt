package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.OfflineEngine
import ani.saikou.neo.shared.catalogue.QueryEngine
import ani.saikou.neo.shared.catalogue.RecordedEngine
import ani.saikou.neo.shared.catalogue.clientFor
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    ) = SeeMoreViewModel(clientFor(engine, main), row, AnimeSeason.FALL, 2026)

    private fun SeeMoreViewModel.ids() = state.value.items.map { it.id }

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

            assertEquals(SeeMoreState(emptyList(), More.Failed), viewModel.state.value)
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

            assertEquals(SeeMoreState(viewModel.state.value.items, More.Failed), viewModel.state.value)
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
