package ani.saikou.neo.tv.search

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import ani.saikou.neo.shared.catalogue.SearchFilter
import ani.saikou.neo.shared.search.SearchGenre
import ani.saikou.neo.shared.search.SearchResults
import ani.saikou.neo.shared.search.SearchState
import ani.saikou.neo.tv.home.card
import ani.saikou.neo.tv.home.posterTag
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SearchScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val found = SearchState("fri", SearchResults.Found("fri", List(8) { card(it) }))
    private val typed = mutableListOf<String>()
    private val opened = mutableListOf<Int>()
    private val genres = mutableListOf<SearchGenre>()
    private val filters = mutableListOf<SearchFilter>()

    private fun show(state: SearchState = SearchState()) {
        compose.setContent {
            NeoTheme {
                SearchScreen(
                    state = state,
                    onType = { typed += it.toString() },
                    onDelete = { typed += "delete" },
                    onClear = { typed += "clear" },
                    onRetry = { typed += "retry" },
                    onOpen = { opened += it },
                    onGenre = { genres += it },
                    onFilter = { filters += it },
                )
            }
        }
    }

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    // The keyboard and results are centred together: 240 dp of keys, 32 dp apart from 560 dp of results.
    @Test
    fun theKeyboardSitsLeftOfTheResults() {
        show(found)

        // "Search anime" is a heading like the results', so the field lines up with the first row of results.
        compose
            .onNodeWithText("Search anime")
            .assertLeftPositionInRootIsEqualTo(64.dp)
            .assertTopPositionInRootIsEqualTo(27.dp)
            .assertHeightIsEqualTo(28.dp)
        compose.onNodeWithTag(keyTag('a')).assertLeftPositionInRootIsEqualTo(64.dp).assertTopPositionInRootIsEqualTo(127.dp)
        compose.onNodeWithText("Results for “fri”").assertLeftPositionInRootIsEqualTo(336.dp).assertTopPositionInRootIsEqualTo(27.dp)
        // The chips sit under the heading, the posters under them.
        compose.onNodeWithTag(filterTag(SearchFilter.ALL)).assertLeftPositionInRootIsEqualTo(336.dp).assertTopPositionInRootIsEqualTo(69.dp)
        compose.onNodeWithTag(posterTag(0)).assertLeftPositionInRootIsEqualTo(336.dp).assertTopPositionInRootIsEqualTo(119.dp)
        // Four posters a row, 20 dp apart.
        compose.onNodeWithTag(posterTag(3)).assertLeftPositionInRootIsEqualTo(768.dp)
    }

    @Test
    fun startsOnTheFirstKeyAndTypesWithTheRemote() {
        show()

        compose.onNodeWithTag(keyTag('a')).assertIsFocused()
        press(Key.DirectionRight, times = 5)
        press(Key.DirectionCenter)
        press(Key.DirectionDown, times = 6)
        press(Key.DirectionCenter)

        assertEquals(listOf("f", "clear"), typed)
    }

    @Test
    fun theQueryShowsInTheField() {
        show(found)

        compose.onNodeWithText("fri").assertExists()
    }

    @Test
    fun selectingAResultOpensIt() {
        show(found)

        press(Key.DirectionRight, times = 6)
        compose.onNodeWithTag(posterTag(0)).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf(0), opened)
    }

    @Test
    fun allIsChosenToBeginWith() {
        show(found)

        compose.onNodeWithTag(filterTag(SearchFilter.ALL)).assertIsSelected()
        compose.onNodeWithText("Airing now").assertIsNotSelected()
    }

    @Test
    fun choosingAChipFiltersTheSearch() {
        show(found.copy(filter = SearchFilter.MOVIES))

        press(Key.DirectionRight, times = 6)
        press(Key.DirectionUp)
        // Up from the results lands on the chosen chip.
        compose.onNodeWithTag(filterTag(SearchFilter.MOVIES)).assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        assertEquals(listOf(SearchFilter.AIRING), filters)
    }

    @Test
    fun aChipKeepsFocusWhenItsSearchFindsNothing() {
        var state by mutableStateOf(found.copy(filter = SearchFilter.MOVIES))
        compose.setContent {
            NeoTheme { SearchScreen(state, {}, {}, {}, {}, {}, {}, onFilter = {}) }
        }
        press(Key.DirectionRight, times = 6)
        press(Key.DirectionUp)

        state = SearchState("fri", SearchResults.Found("fri", emptyList()), SearchFilter.MOVIES)
        compose.waitForIdle()

        compose.onNodeWithTag(filterTag(SearchFilter.MOVIES)).assertIsFocused()
    }

    @Test
    fun theChosenChipShowsWhileItsResultsLoad() {
        show(SearchState("fri", SearchResults.Loading, SearchFilter.TV))

        compose.onNodeWithTag(filterTag(SearchFilter.TV)).assertIsSelected()
    }

    // Finding nothing at all is no use narrowing, but a filter that found nothing can be changed back.
    @Test
    fun aFilterThatFoundNothingCanBeChangedBack() {
        show(SearchState("frieren", SearchResults.Found("frieren", emptyList()), SearchFilter.MOVIES))

        compose.onNodeWithTag(filterTag(SearchFilter.MOVIES)).assertIsSelected()
        compose.onNodeWithTag(filterTag(SearchFilter.ALL)).assertExists()
    }

    @Test
    fun beforeTypingGenresGiveSomewhereToStart() {
        show()

        compose.onNodeWithText("Slice of Life").assertExists()
        // The top keys sit level with the second row of tiles.
        press(Key.DirectionRight, times = 6)
        press(Key.DirectionUp)
        press(Key.DirectionCenter)

        assertEquals(listOf(SearchGenre.ACTION), genres)
    }

    @Test
    fun noResultsSaysSoAndOffersGenres() {
        show(SearchState("sakamota dyas", SearchResults.Found("sakamota dyas", emptyList())))

        compose.onNodeWithText("Nothing called “sakamota dyas”").assertTopPositionInRootIsEqualTo(27.dp)
        compose.onNodeWithText("Or try a genre").assertExists()
        compose.onNodeWithText("Comedy").assertExists()
        compose.onNodeWithTag(filterTag(SearchFilter.ALL)).assertDoesNotExist()
    }

    @Test
    fun noResultsSuggestsTheShowYouMeant() {
        show(SearchState("sakamota dyas", SearchResults.Found("sakamota dyas", emptyList(), suggestion = card(7))))

        compose.onNodeWithText("Did you mean").assertExists()
        // Under the heading and the hint, each the design's height, 14 dp apart.
        compose.onNodeWithTag(SUGGESTION_TAG).assertTopPositionInRootIsEqualTo(105.dp)
        press(Key.DirectionRight, times = 6)
        compose.onNodeWithTag(SUGGESTION_TAG).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf(7), opened)
    }

    @Test
    fun aFailedSearchOffersTryAgain() {
        show(SearchState("fri", SearchResults.Failed))

        press(Key.DirectionRight, times = 6)
        compose.onNodeWithText("Try again").assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf("retry"), typed)
    }
}
