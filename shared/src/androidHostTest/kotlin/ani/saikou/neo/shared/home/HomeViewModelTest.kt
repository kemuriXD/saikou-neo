package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.RecordedEngine
import ani.saikou.neo.shared.catalogue.clientFor
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Buffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun startsWithEveryRowLoading() {
        val viewModel = HomeViewModel(clientFor(RecordedEngine("browse-trending")), AnimeSeason.FALL, 2026)

        assertEquals(HomeRow.entries.map { RowState.Loading }, HomeRow.entries.map { viewModel.state.value.rows[it] })
    }

    @Test
    fun loadsTheRowsInOrder() =
        runTest(main) {
            val viewModel = HomeViewModel(clientFor(RecordedEngine("browse-trending")), AnimeSeason.FALL, 2026)

            val rows = viewModel.settledRows()
            assertEquals(listOf(HomeRow.TRENDING, HomeRow.THIS_SEASON, HomeRow.POPULAR), rows.keys.toList())
            rows.values.forEach { assertEquals(195516, assertIs<RowState.Loaded>(it).items.first().id) }
        }

    @Test
    fun thisSeasonAsksForTheGivenSeason() =
        runTest(main) {
            val engine = RecordedEngine("browse-trending")
            HomeViewModel(clientFor(engine), AnimeSeason.SPRING, 2027).settledRows()

            assertTrue(engine.requestBodies.any { "\"season\":\"SPRING\"" in it && "\"seasonYear\":2027" in it })
        }

    @Test
    fun aFailedRowDoesNotStopTheOthers() =
        runTest(main) {
            val viewModel = HomeViewModel(clientFor(SeasonOfflineEngine()), AnimeSeason.FALL, 2026)

            val rows = viewModel.settledRows()
            assertIs<RowState.Loaded>(rows[HomeRow.TRENDING])
            assertEquals(RowState.Failed, rows[HomeRow.THIS_SEASON])
            assertIs<RowState.Loaded>(rows[HomeRow.POPULAR])
        }
}

/** Apollo runs requests on its own dispatcher, so wait until no row is loading. */
private suspend fun HomeViewModel.settledRows() = state.first { state -> RowState.Loading !in state.rows.values }.rows

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
