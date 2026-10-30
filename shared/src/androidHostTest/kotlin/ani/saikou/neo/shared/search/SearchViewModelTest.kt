package ani.saikou.neo.shared.search

import ani.saikou.neo.shared.catalogue.KitsuClient
import ani.saikou.neo.shared.catalogue.OfflineEngine
import ani.saikou.neo.shared.catalogue.QueryEngine
import ani.saikou.neo.shared.catalogue.RecordedEngine
import ani.saikou.neo.shared.catalogue.SearchFilter
import ani.saikou.neo.shared.catalogue.SiteEngine
import ani.saikou.neo.shared.catalogue.clientFor
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun search(
        engine: HttpEngine = RecordedEngine("search"),
        kitsu: String = "none",
    ) = SearchViewModel(clientFor(engine, main), KitsuClient(SiteEngine("kitsu") { kitsu }, main))

    // AniList finds nothing for the text, then has the card for the id Kitsu suggests.
    private fun typoEngine() = QueryEngine("search-none", mapOf("\"operationName\":\"AnimeCard\"" to "search"))

    private fun SearchViewModel.typeEach(text: String) = text.forEach { type(it) }

    @Test
    fun nothingIsSearchedBeforeYouType() =
        runTest(main) {
            val engine = RecordedEngine("search")
            val viewModel = search(engine)
            advanceUntilIdle()

            assertEquals(SearchState(), viewModel.state.value)
            assertTrue(engine.requestBodies.isEmpty())
        }

    @Test
    fun typingSearchesOnceYouPause() =
        runTest(main) {
            val engine = RecordedEngine("search")
            val viewModel = search(engine)

            viewModel.typeEach("fri")
            advanceTimeBy(100)
            viewModel.type('e')
            advanceUntilIdle()

            assertTrue("\"search\":\"frie\"" in engine.requestBodies.single())
            val found = assertIs<SearchResults.Found>(viewModel.state.value.results)
            assertEquals("frie", found.query)
            assertEquals(154587, found.items.first().id)
        }

    @Test
    fun resultsStayWhileTheNextSearchLoads() =
        runTest(main) {
            val viewModel = search()
            viewModel.typeEach("fri")
            advanceUntilIdle()

            viewModel.type('e')

            assertEquals("frie", viewModel.state.value.query)
            assertEquals("fri", assertIs<SearchResults.Found>(viewModel.state.value.results).query)
        }

    @Test
    fun noMatchesFindsNothing() =
        runTest(main) {
            val viewModel = search(RecordedEngine("search-none"))

            viewModel.typeEach("sakamota dyas")
            advanceUntilIdle()

            val found = assertIs<SearchResults.Found>(viewModel.state.value.results)
            assertEquals("sakamota dyas", found.query)
            assertTrue(found.items.isEmpty())
        }

    // AniList only matches whole words, so a word half typed finds nothing. What it found before stays.
    @Test
    fun findingNothingKeepsWhatWasFoundBefore() =
        runTest(main) {
            val viewModel = search(QueryEngine("search", mapOf("\"search\":\"frieren x\"" to "search-none")))
            viewModel.typeEach("frieren")
            advanceUntilIdle()

            viewModel.typeEach(" x")
            advanceUntilIdle()

            assertEquals("frieren x", viewModel.state.value.query)
            assertEquals("frieren", assertIs<SearchResults.Found>(viewModel.state.value.results).query)
        }

    @Test
    fun findingNothingAgainSaysWhatWasSearched() =
        runTest(main) {
            val viewModel = search(RecordedEngine("search-none"))
            viewModel.typeEach("sakamota")
            advanceUntilIdle()

            viewModel.typeEach(" dyas")
            advanceUntilIdle()

            assertEquals("sakamota dyas", assertIs<SearchResults.Found>(viewModel.state.value.results).query)
        }

    @Test
    fun findingNothingSuggestsTheShowKitsuThinksYouMeant() =
        runTest(main) {
            val engine = typoEngine()
            val viewModel = search(engine, kitsu = "sakamota-dyas")

            viewModel.typeEach("sakamota dyas")
            advanceUntilIdle()

            val found = assertIs<SearchResults.Found>(viewModel.state.value.results)
            assertTrue(found.items.isEmpty())
            assertEquals(154587, found.suggestion?.id)
            assertTrue("\"id\":177709" in engine.requestBodies.last())
        }

    @Test
    fun withNothingFromKitsuThereIsNoSuggestion() =
        runTest(main) {
            val viewModel = search(typoEngine(), kitsu = "none")

            viewModel.typeEach("zzzzqqq")
            advanceUntilIdle()

            assertEquals(null, assertIs<SearchResults.Found>(viewModel.state.value.results).suggestion)
        }

    @Test
    fun deleteRemovesTheLastLetterAndClearEmptiesTheQuery() =
        runTest(main) {
            val engine = RecordedEngine("search")
            val viewModel = search(engine)
            viewModel.typeEach("fri")
            viewModel.delete()
            assertEquals("fr", viewModel.state.value.query)

            viewModel.clear()
            advanceUntilIdle()

            assertEquals(SearchState(), viewModel.state.value)
            assertTrue(engine.requestBodies.isEmpty())
        }

    @Test
    fun spacesAloneSearchNothing() =
        runTest(main) {
            val engine = RecordedEngine("search")
            val viewModel = search(engine)

            viewModel.type(' ')
            advanceUntilIdle()

            assertEquals(SearchState(), viewModel.state.value)
            assertTrue(engine.requestBodies.isEmpty())
        }

    @Test
    fun resultsAreForTheQueryWithoutSpacesAtItsEnds() =
        runTest(main) {
            val viewModel = search()

            viewModel.typeEach("frieren ")
            advanceUntilIdle()

            assertEquals("frieren", assertIs<SearchResults.Found>(viewModel.state.value.results).query)
        }

    @Test
    fun allIsChosenToBeginWithAndNarrowsNothing() =
        runTest(main) {
            val engine = RecordedEngine("search")
            val viewModel = search(engine)

            viewModel.typeEach("frieren")
            advanceUntilIdle()

            assertEquals(SearchFilter.ALL, viewModel.state.value.filter)
            assertFalse("\"formatIn\":" in engine.requestBodies.single())
            assertFalse("\"status\":" in engine.requestBodies.single())
        }

    @Test
    fun choosingAFilterSearchesAgainWithItAndKeepsTheQuery() =
        runTest(main) {
            val engine = QueryEngine("search", mapOf("\"formatIn\":[\"MOVIE\"]" to "search-country"))
            val viewModel = search(engine)
            viewModel.typeEach("frieren")
            advanceUntilIdle()

            viewModel.filter(SearchFilter.MOVIES)
            advanceUntilIdle()

            assertTrue("\"search\":\"frieren\"" in engine.requestBodies.last())
            assertEquals("frieren", viewModel.state.value.query)
            assertEquals(SearchFilter.MOVIES, viewModel.state.value.filter)
            assertEquals(99935, assertIs<SearchResults.Found>(viewModel.state.value.results).items.single().id)
        }

    @Test
    fun eachFilterAsksAniListForItsShows() =
        runTest(main) {
            val engine = RecordedEngine("search")
            val viewModel = search(engine)
            viewModel.typeEach("frieren")
            advanceUntilIdle()

            viewModel.filter(SearchFilter.TV)
            advanceUntilIdle()
            assertTrue("\"formatIn\":[\"TV\",\"TV_SHORT\"]" in engine.requestBodies.last())

            viewModel.filter(SearchFilter.AIRING)
            advanceUntilIdle()
            assertTrue("\"status\":\"RELEASING\"" in engine.requestBodies.last())
            assertFalse("\"formatIn\":" in engine.requestBodies.last())
        }

    // Results that ignore the filter would be wrong, so they go while the filtered ones load.
    @Test
    fun aNewFilterLoadsInPlaceOfTheOldResults() =
        runTest(main) {
            val viewModel = search(QueryEngine("search", mapOf("\"formatIn\":[\"MOVIE\"]" to "search-none")))
            viewModel.typeEach("frieren")
            advanceUntilIdle()

            viewModel.filter(SearchFilter.MOVIES)
            assertEquals(SearchResults.Loading, viewModel.state.value.results)
            advanceUntilIdle()

            assertTrue(assertIs<SearchResults.Found>(viewModel.state.value.results).items.isEmpty())
        }

    @Test
    fun aFilterChosenBeforeTypingWaitsForTheQuery() =
        runTest(main) {
            val engine = RecordedEngine("search")
            val viewModel = search(engine)

            viewModel.filter(SearchFilter.TV)
            advanceUntilIdle()
            assertTrue(engine.requestBodies.isEmpty())

            viewModel.typeEach("fri")
            advanceUntilIdle()
            assertTrue("\"formatIn\":[\"TV\",\"TV_SHORT\"]" in engine.requestBodies.single())
        }

    @Test
    fun choosingTheSameFilterAgainSearchesNothing() =
        runTest(main) {
            val engine = RecordedEngine("search")
            val viewModel = search(engine)
            viewModel.typeEach("fri")
            advanceUntilIdle()

            viewModel.filter(SearchFilter.ALL)
            advanceUntilIdle()

            assertEquals(1, engine.requestBodies.size)
        }

    @Test
    fun aFailedSearchCanBeTriedAgain() =
        runTest(main) {
            val viewModel = search(OfflineEngine)
            viewModel.typeEach("fri")
            advanceUntilIdle()
            assertEquals(SearchResults.Failed, viewModel.state.value.results)

            viewModel.retry()

            assertEquals(SearchResults.Loading, viewModel.state.value.results)
        }
}
