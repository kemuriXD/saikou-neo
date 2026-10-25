package ani.saikou.neo.tv.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.home.HomeState
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.shared.home.RowState
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
// A 1080p TV is 960 x 540 dp.
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class HomeScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val hidden = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)

    private fun show(state: HomeState = loaded) {
        compose.setContent { NeoTheme { HomeScreen(state) } }
    }

    /** Shows [loaded] and picks a show whenever the dots ask, as the ViewModel does. Returns the picks. */
    private fun showPicking(): List<Int> {
        val picks = mutableListOf<Int>()
        compose.setContent {
            var state by remember { mutableStateOf(loaded) }
            NeoTheme {
                HomeScreen(state, onPick = { index ->
                    picks += index
                    state = state.copy(spotlight = state.spotlight!!.copy(index = index, picked = true))
                })
            }
        }
        return picks
    }

    /**
     * [loaded], with a story for the first show far longer than three lines. Robolectric barely measures text, so long
     * lines don't wrap; the story is six short lines instead.
     */
    private fun withStory() =
        loaded.copy(spotlight = loaded.spotlight!!.copy(synopses = mapOf(0 to List(6) { "Line $it of the story." }.joinToString("\n"))))

    /** Shows [loaded], runs [before], then recreates Home as after the app was killed, with [reloaded] instead. */
    private fun restoreWith(
        reloaded: HomeState,
        before: () -> Unit,
    ) {
        val restoration = StateRestorationTester(compose)
        var compositions = 0
        restoration.setContent {
            // remember starts over in the recreated composition, so only that one sees the reloaded rows.
            val first = remember { compositions++ == 0 }
            NeoTheme { HomeScreen(if (first) loaded else reloaded) }
        }
        before()
        restoration.emulateSavedInstanceStateRestore()
    }

    // One press at a time, so the screen catches up in between, as it does on a remote.
    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** From Play, past the dots, into Trending. */
    private fun enterRows() = press(Key.DirectionDown, times = 2)

    @Test
    fun startsOnPlay() {
        show()

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused().assertTextEquals("Play")
    }

    @Test
    fun theSpotlightSitsAboveTheRows() {
        show()

        // A title in words is one 38 dp line here.
        compose
            .onNodeWithTag(SPOTLIGHT_TITLE_TAG)
            .assertTextEquals("Show 0")
            .assertLeftPositionInRootIsEqualTo(58.dp)
            .assertHeightIsEqualTo(38.dp)
        // The buttons end 16 dp above the dots, and the text sits on top of them.
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertLeftPositionInRootIsEqualTo(58.dp).assertTopPositionInRootIsEqualTo(328.dp)
        compose.onNodeWithContentDescription("Add to list").assertWidthIsEqualTo(44.dp)
        compose.onNodeWithContentDescription("Details").assertWidthIsEqualTo(44.dp)
        compose.onNodeWithTag(SPOTLIGHT_DOTS_TAG).assertTopPositionInRootIsEqualTo(388.dp)
        compose.onNodeWithText("Trending").assertTopPositionInRootIsEqualTo(420.dp)
    }

    @Test
    fun theTitleIsTheLogoWhenThereIsOne() {
        val spotlight = loaded.spotlight!!
        show(loaded.copy(spotlight = spotlight.copy(logos = mapOf(0 to Logo.Image("https://anizip.test/logo.png")))))

        compose.onNodeWithTag(SPOTLIGHT_LOGO_TAG).assertContentDescriptionEquals("Show 0").assertHeightIsEqualTo(80.dp)
    }

    @Test
    fun theStoryTakesThreeLinesUnderTheDetails() {
        show(withStory())

        // Three 22 dp lines, cut short with an ellipsis.
        compose.onNodeWithTag(SPOTLIGHT_SYNOPSIS_TAG).assertHeightIsEqualTo(66.dp)
    }

    @Test
    fun aLogoStillLeavesThreeLinesForTheStory() {
        // A logo that's still loading keeps its full 80 dp box.
        val spotlight = withStory().spotlight!!
        show(loaded.copy(spotlight = spotlight.copy(logos = spotlight.logos + (0 to Logo.Loading))))

        compose.onNodeWithTag(SPOTLIGHT_SYNOPSIS_TAG).assertHeightIsEqualTo(66.dp)
    }

    @Test
    fun aTitleOnTwoLinesStillLeavesThreeLinesForTheStory() {
        val long = loaded.spotlight!!.shows[0].let { it.copy(title = it.title.copy(english = "A title on\ntwo lines")) }
        val spotlight = withStory().spotlight!!
        show(loaded.copy(spotlight = spotlight.copy(shows = listOf(long) + spotlight.shows.drop(1))))

        compose.onNodeWithTag(SPOTLIGHT_TITLE_TAG).assertHeightIsEqualTo(76.dp)
        compose.onNodeWithTag(SPOTLIGHT_SYNOPSIS_TAG).assertHeightIsEqualTo(66.dp)
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertTopPositionInRootIsEqualTo(328.dp)
    }

    // Coil skips its own crossfade for art already in memory, which preloaded art always is.
    @Test
    fun theArtFadesIntoTheNextShowsArt() {
        val spotlight = loaded.spotlight!!
        val withArt = spotlight.copy(shows = spotlight.shows.map { it.copy(bannerImageUrl = "https://anilist.test/art-${it.id}.jpg") })
        var state by mutableStateOf(loaded.copy(spotlight = withArt))
        compose.setContent { NeoTheme { HomeScreen(state) } }

        compose.mainClock.autoAdvance = false
        state = state.copy(spotlight = withArt.copy(index = 1))
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)

        compose.onAllNodesWithTag(BACKDROP_ART_TAG).assertCountEquals(2)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onAllNodesWithTag(BACKDROP_ART_TAG).assertCountEquals(1)
    }

    @Test
    fun theNextShowFadesInWhileTheButtonsStayPut() {
        var state by mutableStateOf(withStory())
        compose.setContent { NeoTheme { HomeScreen(state) } }
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()

        compose.mainClock.autoAdvance = false
        state = state.copy(spotlight = state.spotlight!!.copy(index = 1))
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)

        // Halfway, both shows are on screen.
        compose.onAllNodesWithTag(SPOTLIGHT_TITLE_TAG).assertCountEquals(2)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(SPOTLIGHT_TITLE_TAG).assertTextEquals("Show 1")
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused().assertTopPositionInRootIsEqualTo(328.dp)
    }

    @Test
    fun aShowWithoutAStoryHasNoSynopsis() {
        show()

        compose.onNodeWithTag(SPOTLIGHT_SYNOPSIS_TAG).assertDoesNotExist()
    }

    @Test
    fun downGoesFromThePlayButtonToTheDotsAndThenTheRows() {
        show()

        press(Key.DirectionDown)
        compose.onNodeWithTag(SPOTLIGHT_DOTS_TAG).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 0)).assertIsFocused()
    }

    @Test
    fun leftAndRightOnTheDotsChangeTheShow() {
        val picks = showPicking()

        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 2)
        press(Key.DirectionLeft)

        assertEquals(listOf(1, 2, 1), picks)
        compose.onNodeWithTag(SPOTLIGHT_DOTS_TAG).assertIsFocused()
        compose.onNodeWithTag(SPOTLIGHT_TITLE_TAG).assertTextEquals("Show 1")
    }

    @Test
    fun theDotsStopAtTheFirstAndLastShow() {
        val picks = showPicking()

        press(Key.DirectionDown)
        press(Key.DirectionLeft)
        press(Key.DirectionRight, times = 7)

        assertEquals(listOf(1, 2, 3, 4, 5), picks)
        compose.onNodeWithTag(SPOTLIGHT_DOTS_TAG).assertIsFocused()
    }

    @Test
    fun theRowsHideTheSpotlightTextAndComeUp() {
        show()

        enterRows()

        compose.onNodeWithTag(SPOTLIGHT_TAG).assert(hidden)
        compose.onNodeWithTag(SPOTLIGHT_DOTS_TAG).assert(hidden)
        // Heading at 104 dp, cards 34 dp below it, from the 58 dp grid margin, 20 dp apart.
        // Card 1 is unfocused, so its bounds aren't scaled.
        compose.onNodeWithText("Trending").assertTopPositionInRootIsEqualTo(104.dp)
        compose
            .onNodeWithTag(homeCardTag(HomeRow.TRENDING, 1))
            .assertTopPositionInRootIsEqualTo(138.dp)
            .assertLeftPositionInRootIsEqualTo((58 + 196 + 20).dp)
            .assertWidthIsEqualTo(196.dp)
            .assertHeightIsEqualTo(110.dp)
        compose.onNodeWithText("This season").assertTopPositionInRootIsEqualTo(282.dp)
    }

    @Test
    fun upFromTheFirstRowBringsTheSpotlightBack() {
        show()

        enterRows()
        press(Key.DirectionUp)

        compose.onNodeWithTag(SPOTLIGHT_DOTS_TAG).assertIsFocused()
        compose.onNodeWithTag(SPOTLIGHT_TAG).assert(!hidden)
        compose.onNodeWithText("Trending").assertTopPositionInRootIsEqualTo(420.dp)
        press(Key.DirectionUp)
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
    }

    @Test
    fun theSpotlightSaysWhenItIsInView() {
        val inView = mutableListOf<Boolean>()
        compose.setContent { NeoTheme { HomeScreen(loaded, onSpotlightInView = { inView += it }) } }

        enterRows()
        press(Key.DirectionUp)

        assertEquals(listOf(true, false, true), inView)
    }

    @Test
    fun downMovesToTheNextRowAndBringsItUp() {
        show()

        enterRows()
        press(Key.DirectionDown)

        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 0)).assertIsFocused()
        compose.onNodeWithText("This season").assertTopPositionInRootIsEqualTo(104.dp)
    }

    @Test
    fun theLastRowAlsoReachesTheTop() {
        show()

        enterRows()
        press(Key.DirectionDown, times = 2)

        compose.onNodeWithTag(homeCardTag(HomeRow.POPULAR, 0)).assertIsFocused()
        compose.onNodeWithText("Popular").assertTopPositionInRootIsEqualTo(104.dp)
    }

    @Test
    fun aRowReturnsToTheCardYouLeft() {
        show()

        enterRows()
        press(Key.DirectionRight, times = 3)
        press(Key.DirectionDown)
        press(Key.DirectionUp)

        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 3)).assertIsFocused()
    }

    @Test
    fun aRowThatScrolledOffScreenReturnsToTheCardYouLeft() {
        show()

        enterRows()
        press(Key.DirectionRight, times = 5)
        // Two rows down, Trending is off screen and its cards are disposed.
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionUp, times = 2)

        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 5)).assertIsFocused()
    }

    @Test
    fun aRowLeftAtItsEndReturnsToItsLastCard() {
        show()

        enterRows()
        // The last cards can't reach the keyline, so they sit further right than other rows' cards.
        press(Key.DirectionRight, times = 11)
        press(Key.DirectionDown)
        press(Key.DirectionUp)

        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 11)).assertIsFocused()
    }

    @Test
    fun aRowYouHaveNotVisitedStartsAtItsFirstCard() {
        show()

        enterRows()
        press(Key.DirectionRight, times = 11)
        press(Key.DirectionDown)

        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 0)).assertIsFocused()
    }

    @Test
    fun aFailedRowIsLeftOut() {
        show(loaded.copy(rows = loaded.rows + (HomeRow.THIS_SEASON to RowState.Failed)))

        enterRows()
        press(Key.DirectionDown)

        compose.onNodeWithTag(homeCardTag(HomeRow.POPULAR, 0)).assertIsFocused()
    }

    @Test
    fun rowsKeepTheirPlaceWhenHomeIsRecreated() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { NeoTheme { HomeScreen(loaded) } }
        enterRows()
        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 5)

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 5)).assertIsFocused()
        // Card 5 rests on the grid margin, so card 4 ends one gutter before it.
        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 4)).assertLeftPositionInRootIsEqualTo((58 - 20 - 196).dp)
        compose.onNodeWithText("This season").assertTopPositionInRootIsEqualTo(104.dp)
    }

    @Test
    fun focusWaitsForTheSpotlightEvenIfARowLoadsFirst() {
        var state by mutableStateOf(HomeState(loaded.rows + (HomeRow.TRENDING to RowState.Loading)))
        compose.setContent { NeoTheme { HomeScreen(state) } }

        compose.onAllNodes(isFocused()).assertCountEquals(0)

        state = loaded
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
    }

    @Test
    fun withNoSpotlightFocusStartsOnTheFirstCard() {
        show(loaded.copy(spotlight = null))

        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 0)).assertIsFocused()
    }

    @Test
    fun aSavedCardThatIsGoneHandsFocusToTheCardNowInItsPlace() {
        // The app was killed, and This season came back without the saved show.
        val reloaded = (loaded.rows.getValue(HomeRow.THIS_SEASON) as RowState.Loaded).items.filter { it.id != 105 }
        restoreWith(loaded.copy(rows = loaded.rows + (HomeRow.THIS_SEASON to RowState.Loaded(reloaded)))) {
            enterRows()
            press(Key.DirectionDown)
            press(Key.DirectionRight, times = 5)
        }

        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 5)).assertIsFocused()
    }

    @Test
    fun aSavedRowThatFailsHandsFocusToTrending() {
        restoreWith(loaded.copy(rows = loaded.rows + (HomeRow.THIS_SEASON to RowState.Failed))) {
            enterRows()
            press(Key.DirectionDown)
        }

        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 0)).assertIsFocused()
    }

    // Guards against Home ever handling Back itself: Home is the start screen, so Back must reach the activity.
    @Test
    fun backOnHomeLeavesTheApp() {
        show()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        compose.activityRule.scenario.onActivity { assertTrue(it.isFinishing) }
    }

    @Test
    fun eachRowEndsWithSeeMore() {
        val opened = mutableListOf<HomeRow>()
        compose.setContent { NeoTheme { HomeScreen(loaded, onSeeMore = { opened += it }) } }

        enterRows()
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionRight, times = 12)
        compose.onNodeWithTag(seeMoreTag(HomeRow.POPULAR)).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf(HomeRow.POPULAR), opened)
    }

    @Test
    fun aRowLeftOnSeeMoreReturnsToIt() {
        show()

        enterRows()
        press(Key.DirectionRight, times = 12)
        press(Key.DirectionDown)
        press(Key.DirectionUp)

        compose.onNodeWithTag(seeMoreTag(HomeRow.TRENDING)).assertIsFocused()
    }

    @Test
    fun seeMoreKeepsItsRowInPlace() {
        show()

        enterRows()
        press(Key.DirectionRight, times = 12)

        // The button is shorter than a card, but the row rests where it does for a card: cards 34 dp under 104 dp.
        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 11)).assertTopPositionInRootIsEqualTo(138.dp)
    }
}
