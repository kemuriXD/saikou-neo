package ani.saikou.neo.tv

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.home.More
import ani.saikou.neo.shared.home.SeeMoreState
import ani.saikou.neo.shared.search.SearchGenre
import ani.saikou.neo.shared.search.SearchResults
import ani.saikou.neo.shared.search.SearchState
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.tv.home.HomeScreen
import ani.saikou.neo.tv.home.SPOTLIGHT_PLAY_TAG
import ani.saikou.neo.tv.home.SeeMoreScreen
import ani.saikou.neo.tv.home.card
import ani.saikou.neo.tv.home.homeCardTag
import ani.saikou.neo.tv.home.loaded
import ani.saikou.neo.tv.home.posterTag
import ani.saikou.neo.tv.home.seeMoreTag
import ani.saikou.neo.tv.search.SUGGESTION_TAG
import ani.saikou.neo.tv.search.SearchScreen
import ani.saikou.neo.tv.search.genreTag
import ani.saikou.neo.tv.settings.SettingsCategory
import ani.saikou.neo.tv.settings.SettingsScreen
import ani.saikou.neo.tv.settings.settingsCategoryTag
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.RAIL_SCRIM_TAG
import ani.saikou.neo.tv.ui.RAIL_TAG
import ani.saikou.neo.tv.ui.Section
import ani.saikou.neo.tv.ui.railTag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NeoNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val restoration = StateRestorationTester(compose)

    private fun show(
        seeMoreState: SeeMoreState = SeeMoreState(List(24) { card(it) }, More.Done),
        searchState: SearchState = SearchState(),
    ) = restoration.setContent {
        NeoTheme {
            NeoNavigation(
                home = { openDetails, openSeeMore ->
                    HomeScreen(loaded, onOpen = openDetails, onSeeMore = openSeeMore)
                },
                seeMore = { row, openDetails ->
                    SeeMoreScreen(row, seeMoreState, openDetails, onLoadMore = {}, onRetry = {})
                },
                details = { animeId, _, _, openSources ->
                    // Focused, as the streams page's Add a source is, so Settings opens with focus coming from it.
                    val add = remember { FocusRequester() }
                    LaunchedEffect(Unit) { add.requestFocus() }
                    Column {
                        Text("Details for $animeId")
                        Button(
                            onClick = openSources,
                            modifier = Modifier.focusRequester(add).testTag("open-sources"),
                        ) { Text("Add a source") }
                    }
                },
                sources = { SettingsScreen(Settings(), onChoose = { _, _ -> }, start = SettingsCategory.SOURCES) },
                search = { openDetails, openGenre ->
                    SearchScreen(
                        searchState,
                        onType = {},
                        onDelete = {},
                        onClear = {},
                        onRetry = {},
                        onOpen = openDetails,
                        onGenre = openGenre,
                        onFilter = {},
                        onRecent = {},
                    )
                },
                genre = { genre, _ -> Text("Most popular ${genre.aniListName}") },
            )
        }
    }

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** From the spotlight's Play button, past its dots, into Trending. */
    private fun enterRows() = press(Key.DirectionDown, times = 2)

    private fun back() {
        // Key input doesn't wait for the screen it opened, and Back is only handled once that screen is shown.
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun backOnHomeStillLeavesTheApp() {
        show()

        back()

        compose.activityRule.scenario.onActivity { assertTrue(it.isFinishing) }
    }

    @Test
    fun selectingACardOpensItsDetails() {
        show()

        enterRows()
        press(Key.DirectionRight, times = 5)
        press(Key.DirectionCenter)

        compose.onNodeWithText("Details for 5").assertExists()
    }

    @Test
    fun backFromTheSpotlightReturnsToPlay() {
        show()

        press(Key.DirectionCenter)
        compose.onNodeWithText("Details for 0").assertExists()
        back()

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
    }

    @Test
    fun backReturnsToTheCardYouOpened() {
        show()

        enterRows()
        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 5)
        press(Key.DirectionCenter)
        back()

        compose.onNodeWithText("Details for 105").assertDoesNotExist()
        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 5)).assertIsFocused()
    }

    // A TV remote has no back swipe, so Back fades between screens even when Android previews it.
    @Test
    fun aPreviewedBackDoesNotShrinkTheScreen() {
        show()

        press(Key.DirectionCenter)
        compose.waitForIdle()
        compose.runOnUiThread {
            val dispatcher = compose.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.9f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()

        compose.onNodeWithText("Details for 0").assertLeftPositionInRootIsEqualTo(0.dp).assertTopPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun backReturnsToTheCardYouOpenedAfterTheAppIsRecreated() {
        show()

        enterRows()
        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 5)
        press(Key.DirectionCenter)
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Details for 105").assertExists()
        back()
        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 5)).assertIsFocused()
    }

    // Pressing the remote's Home button stops the app without recreating it.
    @Test
    fun focusStaysOnTheCardWhenTheAppComesBackFromTheBackground() {
        show()

        enterRows()
        press(Key.DirectionRight, times = 3)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 3)).assertIsFocused()
    }

    /** From Play to the end of Trending, and into its See more page. */
    private fun openTrendingSeeMore() {
        enterRows()
        press(Key.DirectionRight, times = 12)
        press(Key.DirectionCenter)
    }

    @Test
    fun seeMoreOpensTheRowsPageAndBackReturnsToTheButton() {
        show()

        openTrendingSeeMore()
        compose.onNodeWithTag(posterTag(0)).assertIsFocused()
        back()

        compose.onNodeWithTag(seeMoreTag(HomeRow.TRENDING)).assertIsFocused()
    }

    @Test
    fun backFromDetailsReturnsToTheSameShowOnSeeMore() {
        show()

        openTrendingSeeMore()
        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 2)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Details for 8").assertExists()
        back()

        compose.onNodeWithTag(posterTag(8)).assertIsFocused()
    }

    @Test
    fun seeMoreStaysOnTheBackStackAfterTheAppIsRecreated() {
        show()

        openTrendingSeeMore()
        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 2)
        press(Key.DirectionCenter)
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Details for 8").assertExists()
        back()
        compose.onNodeWithTag(posterTag(8)).assertIsFocused()
        back()
        compose.onNodeWithTag(seeMoreTag(HomeRow.TRENDING)).assertIsFocused()
    }

    @Test
    fun pressesWhileSeeMoreOpensDoNotMoveHomesFocus() {
        // Still loading, so See more has nothing to focus yet, while Home fades out with its button focused.
        show(SeeMoreState())

        enterRows()
        press(Key.DirectionRight, times = 12)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(100)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.autoAdvance = true
        back()

        compose.onNodeWithTag(seeMoreTag(HomeRow.TRENDING)).assertIsFocused()
    }

    @Test
    fun leftFromPlayOpensTheRailOnHome() {
        show()

        press(Key.DirectionLeft)

        compose.onNodeWithTag(railTag(Section.HOME)).assertIsFocused().assertIsSelected()
        compose.onNodeWithTag(RAIL_SCRIM_TAG).assertExists()
    }

    @Test
    fun leftFromARowsFirstCardOpensTheRail() {
        show()

        enterRows()
        press(Key.DirectionLeft)

        compose.onNodeWithTag(railTag(Section.HOME)).assertIsFocused()
    }

    @Test
    fun rightClosesTheRailBackToWhereFocusWasNotTheNearestItem() {
        show()

        press(Key.DirectionLeft)
        press(Key.DirectionDown, times = 3)
        press(Key.DirectionRight)

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
        compose.onNodeWithTag(RAIL_SCRIM_TAG).assertDoesNotExist()
    }

    @Test
    fun backClosesTheRailBeforeLeavingTheApp() {
        show()

        press(Key.DirectionLeft)
        back()

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
        compose.activityRule.scenario.onActivity { assertFalse(it.isFinishing) }
    }

    /** From Play, left into the rail and along it to [section], and opens it. */
    private fun openSection(section: Section) {
        press(Key.DirectionLeft)
        val steps = section.ordinal - Section.HOME.ordinal
        press(if (steps < 0) Key.DirectionUp else Key.DirectionDown, times = kotlin.math.abs(steps))
        press(Key.DirectionCenter)
    }

    // The rail is only on Home. A section's screen is all its own, and Back returns to Home.
    @Test
    fun choosingASectionOpensItWithoutTheRail() {
        show()

        openSection(Section.SCHEDULE)

        compose.onNodeWithText("Coming in a future update.").assertExists()
        compose.onNodeWithTag(RAIL_TAG).assertDoesNotExist()
    }

    @Test
    fun backFromASectionReturnsToHome() {
        show()

        openSection(Section.DISCOVER)
        back()

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
    }

    @Test
    fun choosingHomeClosesTheRail() {
        show()

        openSection(Section.HOME)

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
        compose.onNodeWithTag(RAIL_SCRIM_TAG).assertDoesNotExist()
    }

    @Test
    fun aSectionStaysOpenAfterTheAppIsRecreated() {
        show()

        openSection(Section.SEARCH)
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Search anime").assertExists()
        back()
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
    }

    @Test
    fun detailsCanOpenSourcesAndBackReturnsToIt() {
        show()

        press(Key.DirectionCenter)
        compose.onNodeWithTag("open-sources").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(settingsCategoryTag(SettingsCategory.SOURCES)).assertIsFocused().assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(settingsCategoryTag(SettingsCategory.SOURCES)).assertIsSelected()
        back()

        compose.onNodeWithText("Details for 0").assertExists()
    }

    // Search takes focus from the rail. Back has to find Home's Play, not open the rail again.
    @Test
    fun backFromSearchReturnsToPlay() {
        show()
        openSection(Section.SEARCH)
        back()
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
    }

    @Test
    fun backFromTheSuggestedShowReturnsToItsCard() {
        show(searchState = SearchState("naurto", SearchResults.Found("naurto", emptyList(), suggestion = card(7))))
        openSection(Section.SEARCH)

        press(Key.DirectionRight, times = 6)
        press(Key.DirectionCenter)

        compose.onNodeWithText("Details for 7").assertExists()
        back()
        compose.onNodeWithTag(SUGGESTION_TAG).assertIsFocused()
    }

    @Test
    fun aGenreFromSearchOpensItsPageAndBackReturnsToItsTile() {
        show()
        openSection(Section.SEARCH)

        press(Key.DirectionRight, times = 6)
        press(Key.DirectionUp)
        press(Key.DirectionCenter)

        compose.onNodeWithText("Most popular Action").assertExists()
        back()
        compose.onNodeWithTag(genreTag(SearchGenre.ACTION)).assertIsFocused()
    }

    @Test
    fun rightFromTheRailReturnsToTheRowYouLeft() {
        show()

        enterRows()
        press(Key.DirectionDown)
        press(Key.DirectionLeft)
        compose.onNodeWithTag(railTag(Section.HOME)).assertIsFocused()
        press(Key.DirectionRight)

        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 0)).assertIsFocused()
    }

    // Home's content starts clear of the closed rail, as in the design.
    @Test
    fun homeStartsClearOfTheRail() {
        show()

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertLeftPositionInRootIsEqualTo(96.dp)
        enterRows()
        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 0)).assertLeftPositionInRootIsEqualTo(96.dp)
    }

    @Test
    fun seeMoreAndDetailsHaveNoRail() {
        show()

        openTrendingSeeMore()
        compose.onNodeWithTag(RAIL_TAG).assertDoesNotExist()
        press(Key.DirectionCenter)

        compose.onNodeWithText("Details for 0").assertExists()
        compose.onNodeWithTag(RAIL_TAG).assertDoesNotExist()
    }
}
