package ani.saikou.neo.shared.discover

import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.OfflineEngine
import ani.saikou.neo.shared.catalogue.RecordedEngine
import ani.saikou.neo.shared.catalogue.clientFor
import ani.saikou.neo.shared.home.RowState
import ani.saikou.neo.shared.search.SearchGenre
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Buffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun discoverViewModel(engine: HttpEngine = RecordedEngine("browse-trending")) =
        DiscoverViewModel(clientFor(engine, main), AnimeSeason.FALL, 2026)

    private val rows =
        listOf(
            DiscoverRow.Season(AnimeSeason.FALL, 2026),
            DiscoverRow.Genre(SearchGenre.ACTION),
            DiscoverRow.Genre(SearchGenre.ROMANCE),
            DiscoverRow.Genre(SearchGenre.COMEDY),
            DiscoverRow.Genre(SearchGenre.FANTASY),
        )

    @Test
    fun startsWithThisSeasonThenAFewGenresLoading() {
        val viewModel = discoverViewModel()

        assertEquals(rows.associateWith { RowState.Loading }, viewModel.state.value.rows)
        assertEquals(
            rows,
            viewModel.state.value.rows.keys
                .toList(),
        )
    }

    @Test
    fun loadsEachRowFromAniList() =
        runTest(main) {
            val engine = RecordedEngine("browse-trending")
            val viewModel = discoverViewModel(engine)

            val loaded = viewModel.settledRows()

            rows.forEach { assertTrue(assertIs<RowState.Loaded>(loaded[it]).items.isNotEmpty()) }
            val bodies = engine.requestBodies
            assertTrue(bodies.any { "\"FALL\"" in it && "2026" in it })
            listOf("Action", "Romance", "Comedy", "Fantasy").forEach { genre -> assertTrue(bodies.any { "\"$genre\"" in it }, genre) }
        }

    @Test
    fun aRowThatFailsLeavesTheOthers() =
        runTest(main) {
            val viewModel = discoverViewModel(SeasonOfflineEngine())

            val loaded = viewModel.settledRows()

            assertEquals(RowState.Failed, loaded[rows.first()])
            rows.drop(1).forEach { assertIs<RowState.Loaded>(loaded[it]) }
            assertFalse(viewModel.state.value.offline)
        }

    @Test
    fun discoverIsOfflineWhenEveryRowFailsForTheNetwork() =
        runTest(main) {
            val viewModel = discoverViewModel(OfflineEngine)

            viewModel.settledRows()
            assertTrue(viewModel.state.value.offline)
        }

    @Test
    fun tryingAgainOnceOnlineLoadsTheRows() =
        runTest(main) {
            val engine = SwitchableEngine()
            val viewModel = discoverViewModel(engine)
            viewModel.settledRows()

            engine.online = true
            viewModel.retry()

            rows.forEach { assertEquals(RowState.Loading, viewModel.state.value.rows[it]) }
            val loaded = viewModel.settledRows()
            rows.forEach { assertIs<RowState.Loaded>(loaded[it]) }
            assertFalse(viewModel.state.value.offline)
        }

    @Test
    fun whileOfflineDiscoverTriesAgainEveryThirtySecondsOnlyWhileShown() =
        runTest(main) {
            val engine = SwitchableEngine()
            val viewModel = discoverViewModel(engine)
            viewModel.settledRows()

            advanceTimeBy(60_000)
            assertEquals(5, engine.requests)

            viewModel.watchOffline(true)
            advanceTimeBy(30_001)
            assertEquals(10, engine.requests)
            engine.online = true
            advanceTimeBy(30_000)

            viewModel.settledRows()
            assertFalse(viewModel.state.value.offline)
            viewModel.watchOffline(false)
        }
}

/** Waits until no row is loading. */
private suspend fun DiscoverViewModel.settledRows() = state.first { state -> RowState.Loading !in state.rows.values }.rows

/** Fails every request as if offline, until [online], then answers with the trending page. Counts the failed requests. */
private class SwitchableEngine : HttpEngine {
    private val recorded = RecordedEngine("browse-trending")
    var online = false
    var requests = 0

    override suspend fun execute(request: HttpRequest): HttpResponse {
        if (!online) {
            requests++
            throw ApolloNetworkException("Unable to resolve host")
        }
        return recorded.execute(request)
    }

    override fun close() {}
}

/** Fails the season request as if the connection dropped, and answers the rest. */
private class SeasonOfflineEngine : HttpEngine {
    private val recorded = RecordedEngine("browse-trending")

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        if ("\"season\"" in body) throw ApolloNetworkException("Unable to resolve host")
        return recorded.execute(request)
    }

    override fun close() {}
}
