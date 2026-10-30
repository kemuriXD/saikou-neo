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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.home.FeaturedReason
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.home.HomeState
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.shared.home.RowState
import ani.saikou.neo.tv.theme.NeoDimensions
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.Pulsing
import ani.saikou.neo.tv.ui.fakeImages
import ani.saikou.neo.tv.ui.forgetFakeImages
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
     * [loaded], with a story for the first show far longer than two lines. Robolectric barely measures text, so long
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
            .assertLeftPositionInRootIsEqualTo(96.dp)
            .assertHeightIsEqualTo(38.dp)
        // The buttons end 24 dp above the dots, and the text sits on top of them.
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertLeftPositionInRootIsEqualTo(96.dp).assertTopPositionInRootIsEqualTo(276.dp)
        compose.onNodeWithContentDescription("Add to list").assertWidthIsEqualTo(44.dp)
        compose.onNodeWithContentDescription("Details").assertWidthIsEqualTo(44.dp)
        compose.onNodeWithTag(SPOTLIGHT_DOTS_TAG).assertTopPositionInRootIsEqualTo(344.dp)
        compose.onNodeWithText("Trending").assertTopPositionInRootIsEqualTo(382.dp)
    }

    @Test
    fun theFirstRowsCardsFitWholeUnderTheSpotlight() {
        show(continuing)

        // 382 + 34 + 110 = 526, inside the 540 dp screen.
        compose
            .onNodeWithTag(homeCardTag(HomeRow.CONTINUE_WATCHING, 1))
            .assertTopPositionInRootIsEqualTo(416.dp)
            .assertHeightIsEqualTo(110.dp)
    }

    @Test
    fun theTitleIsTheLogoWhenThereIsOne() {
        fakeImages(compose.activity, 800, 310)
        val spotlight = loaded.spotlight!!
        show(loaded.copy(spotlight = spotlight.copy(logos = mapOf(0 to Logo.Image("https://logos.test/logo.png")))))

        compose.onNodeWithTag(SPOTLIGHT_LOGO_TAG).assertContentDescriptionEquals("Show 0").assertHeightIsEqualTo(80.dp)
    }

    // Spirited Away's logo is almost ten times as wide as it is tall, so it's drawn 360 × 37.
    @Test
    fun aWideLogosSpaceIsOnlyAsTallAsItIsDrawn() {
        fakeImages(compose.activity, 3600, 370)
        showWithLogo()

        compose.waitUntil { compose.onNodeWithTag(SPOTLIGHT_LOGO_TAG).getUnclippedBoundsInRoot().height == 37.dp }
        compose.onNodeWithTag(SPOTLIGHT_LOGO_TAG).assertWidthIsEqualTo(360.dp)
        assertGapUnder(SPOTLIGHT_LOGO_TAG)
    }

    // Tensei Kizoku's logo is 0.8:1, so it's drawn 64 × 80.
    @Test
    fun aTallLogoTakesTheFullHeight() {
        fakeImages(compose.activity, 600, 750)
        showWithLogo()

        compose.waitUntil { compose.onNodeWithTag(SPOTLIGHT_LOGO_TAG).getUnclippedBoundsInRoot().width == 64.dp }
        compose.onNodeWithTag(SPOTLIGHT_LOGO_TAG).assertHeightIsEqualTo(80.dp)
        assertGapUnder(SPOTLIGHT_LOGO_TAG)
    }

    @Test
    fun aTitleInWordsHasTheSameGap() {
        show()

        assertGapUnder(SPOTLIGHT_TITLE_TAG)
    }

    // A logo still on its way after five seconds leaves the title in words until the next show, so it never swaps in.
    @Test
    fun aLogoThatIsSlowToLoadLeavesTheTitleInWords() {
        val ready = CompletableDeferred<Unit>()
        fakeImages(compose.activity, 800, 310, ready = ready)
        showWithLogo()

        compose.mainClock.advanceTimeBy(5_100)
        compose.onNodeWithTag(SPOTLIGHT_TITLE_TAG).assertTextEquals("Show 0")
        ready.complete(Unit)
        compose.waitForIdle()

        compose.onNodeWithTag(SPOTLIGHT_LOGO_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SPOTLIGHT_TITLE_TAG).assertTextEquals("Show 0")
    }

    // The first show comes in one go: its text waits for its logo, even on a TV slower than the logo's own wait.
    @Test
    fun theFirstShowWaitsForItsLogo() {
        val ready = CompletableDeferred<Unit>()
        fakeImages(compose.activity, 800, 310, ready = ready)
        showWithLogo()

        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag(SPOTLIGHT_META_TAG).assertDoesNotExist()
        ready.complete(Unit)
        compose.waitForIdle()

        compose.onNodeWithTag(SPOTLIGHT_LOGO_TAG).assertExists()
        compose.onNodeWithTag(SPOTLIGHT_META_TAG).assertExists()
    }

    @Test
    fun theFirstShowWaitsForItsBackdrop() {
        val ready = CompletableDeferred<Unit>()
        val asked = fakeImages(compose.activity, 800, 310, ready = ready)
        val spotlight = loaded.spotlight!!
        show(loaded.copy(spotlight = spotlight.copy(fanArt = mapOf(0 to "https://art.test/0.jpg"))))

        compose.waitUntil { "https://art.test/0.jpg" in asked }
        compose.onNodeWithTag(SPOTLIGHT_META_TAG).assertDoesNotExist()
        ready.complete(Unit)
        compose.waitForIdle()

        compose.onNodeWithTag(SPOTLIGHT_META_TAG).assertExists()
    }

    @Test
    fun theFirstShowWaitsForItsStoryAndReason() {
        var state by mutableStateOf(loaded.copy(spotlight = loaded.spotlight!!.copy(detailsLoaded = false)))
        compose.setContent { NeoTheme { HomeScreen(state) } }

        compose.onNodeWithTag(SPOTLIGHT_META_TAG).assertDoesNotExist()
        state = loaded
        compose.waitForIdle()

        compose.onNodeWithTag(SPOTLIGHT_META_TAG).assertExists()
    }

    // As the spotlight moves on, Play keeps the old show's label until the new show's text comes in.
    @Test
    fun theMainButtonChangesWithTheText() {
        var state by mutableStateOf(loaded.copy(progress = continuing.progress.mapKeys { (id, _) -> id - 900 }))
        compose.setContent { NeoTheme { HomeScreen(state) } }
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertTextEquals("Resume E7")

        compose.mainClock.autoAdvance = false
        state = state.copy(spotlight = state.spotlight!!.copy(index = 1))
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertTextEquals("Resume E7")
        compose.mainClock.advanceTimeBy(1_000)

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertTextEquals("Resume E8")
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun focusSaysWhichCardItIsOn() {
        val rested = mutableListOf<AnimeCard?>()
        compose.setContent { NeoTheme { HomeScreen(loaded, onRest = { rested += it }) } }

        enterRows()
        press(Key.DirectionRight)

        assertEquals(listOf(0, 1), rested.filterNotNull().map { it.id })
        // Back up to the spotlight, focus is on no card.
        press(Key.DirectionUp, times = 2)
        assertNull(rested.last())
    }

    // So Details opens with the logo already loaded.
    @Test
    fun theLogoOfTheCardFocusRestsOnStartsLoading() {
        val asked = fakeImages(compose.activity, 800, 310)

        show(loaded.copy(restingLogo = Logo.Image("https://logos.test/rested.png")))

        compose.waitUntil { "https://logos.test/rested.png" in asked }
    }

    private fun showWithLogo() {
        val spotlight = loaded.spotlight!!
        show(loaded.copy(spotlight = spotlight.copy(logos = mapOf(0 to Logo.Image("https://logos.test/logo.png")))))
    }

    /** The line under the title sits 8 dp below it. */
    private fun assertGapUnder(titleTag: String) {
        val title = compose.onNodeWithTag(titleTag).getUnclippedBoundsInRoot()
        val meta = compose.onNodeWithTag(SPOTLIGHT_META_TAG).getUnclippedBoundsInRoot()
        assertEquals(8f, (meta.top - title.bottom).value, 0.01f)
    }

    @After
    fun forgetImages() = forgetFakeImages()

    @Test
    fun theStoryTakesTwoLinesUnderTheDetails() {
        show(withStory())

        // Two 20 dp lines, cut short with an ellipsis.
        compose.onNodeWithTag(SPOTLIGHT_SYNOPSIS_TAG).assertHeightIsEqualTo(40.dp)
    }

    @Test
    fun aLongStoryIsNoWiderThanTheDesign() {
        show(loaded.copy(spotlight = loaded.spotlight!!.copy(synopses = mapOf(0 to "A story that goes on. ".repeat(40)))))

        compose.onNodeWithTag(SPOTLIGHT_SYNOPSIS_TAG).assertWidthIsEqualTo(360.dp)
    }

    /** Shows [loaded] until the first show is in, then [next], such as a later show still loading its logo. */
    private fun showAfterFirst(next: HomeState) {
        var state by mutableStateOf(loaded)
        compose.setContent { NeoTheme { HomeScreen(state) } }
        compose.waitForIdle()
        state = next
        compose.waitForIdle()
    }

    @Test
    fun aLogoStillLeavesTwoLinesForTheStory() {
        // A logo that's still loading keeps its full 80 dp box.
        val spotlight = withStory().spotlight!!
        showAfterFirst(loaded.copy(spotlight = spotlight.copy(logos = spotlight.logos + (0 to Logo.Loading))))

        compose.onNodeWithTag(SPOTLIGHT_SYNOPSIS_TAG).assertHeightIsEqualTo(40.dp)
    }

    @Test
    fun aReasonAndALogoStillLeaveTwoLinesForTheStory() {
        val spotlight = withStory().spotlight!!
        showAfterFirst(
            loaded.copy(
                spotlight =
                    spotlight.copy(
                        logos = spotlight.logos + (0 to Logo.Loading),
                        reasons = mapOf(0 to FeaturedReason.Trending(1)),
                    ),
            ),
        )

        compose.onNodeWithTag(SPOTLIGHT_SYNOPSIS_TAG).assertHeightIsEqualTo(40.dp)
        // The line fits in the spotlight's space, which starts 70 dp down.
        assertTrue(compose.onNodeWithTag(SPOTLIGHT_REASON_TAG).getUnclippedBoundsInRoot().top >= 70.dp)
    }

    @Test
    fun aTitleOnTwoLinesStillLeavesTwoLinesForTheStory() {
        val long = loaded.spotlight!!.shows[0].let { it.copy(title = it.title.copy(english = "A title on\ntwo lines")) }
        val spotlight = withStory().spotlight!!
        show(loaded.copy(spotlight = spotlight.copy(shows = listOf(long) + spotlight.shows.drop(1))))

        compose.onNodeWithTag(SPOTLIGHT_TITLE_TAG).assertHeightIsEqualTo(76.dp)
        compose.onNodeWithTag(SPOTLIGHT_SYNOPSIS_TAG).assertHeightIsEqualTo(40.dp)
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertTopPositionInRootIsEqualTo(276.dp)
    }

    // Coil skips its own crossfade for art already in memory, which preloaded art always is.
    @Test
    fun theArtFadesIntoTheNextShowsArt() {
        val spotlight = loaded.spotlight!!
        val withArt = spotlight.copy(shows = spotlight.shows.map { it.copy(bannerImageUrl = "https://anilist.test/art-${it.id}.jpg") })
        fakeImages(compose.activity, 10, 10)
        var state by mutableStateOf(loaded.copy(spotlight = withArt))
        compose.setContent { NeoTheme { HomeScreen(state) } }
        compose.waitForIdle()

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
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused().assertTopPositionInRootIsEqualTo(276.dp)
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
        // Heading at 30 dp, cards 34 dp below it, from 96 dp, clear of the rail, 20 dp apart.
        // Card 1 is unfocused, so its bounds aren't scaled.
        compose.onNodeWithText("Trending").assertTopPositionInRootIsEqualTo(30.dp)
        compose
            .onNodeWithTag(homeCardTag(HomeRow.TRENDING, 1))
            .assertTopPositionInRootIsEqualTo(64.dp)
            .assertLeftPositionInRootIsEqualTo((96 + 196 + 20).dp)
            .assertWidthIsEqualTo(196.dp)
            .assertHeightIsEqualTo(110.dp)
        // Three whole rows, 24 dp apart, with Popular's cards ending at 510 dp.
        compose.onNodeWithText("This season").assertTopPositionInRootIsEqualTo(198.dp)
        compose.onNodeWithText("Popular").assertTopPositionInRootIsEqualTo(366.dp)
        compose.onNodeWithTag(homeCardTag(HomeRow.POPULAR, 1)).assertTopPositionInRootIsEqualTo(400.dp)
    }

    @Test
    fun upFromTheFirstRowBringsTheSpotlightBack() {
        show()

        enterRows()
        press(Key.DirectionUp)

        compose.onNodeWithTag(SPOTLIGHT_DOTS_TAG).assertIsFocused()
        compose.onNodeWithTag(SPOTLIGHT_TAG).assert(!hidden)
        compose.onNodeWithText("Trending").assertTopPositionInRootIsEqualTo(382.dp)
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

    private val offline =
        HomeState(
            rows =
                HomeRow.entries.associateWith {
                    RowState.Failed
                } + (HomeRow.CONTINUE_WATCHING to RowState.Loaded(emptyList())),
            offline = true,
        )

    @Test
    fun offlineHomeSaysSoWithTryAgainFocused() {
        show(offline)

        compose.onNodeWithText("This TV isn’t online").assertExists()
        compose.onNodeWithText("Try again").assertIsFocused()
        compose.onNodeWithTag(SPOTLIGHT_TAG).assertDoesNotExist()
    }

    @Test
    fun tryAgainAndNetworkSettingsDoWhatTheySay() {
        val calls = mutableListOf<String>()
        compose.setContent {
            NeoTheme { HomeScreen(offline, onRetry = { calls += "retry" }, onNetworkSettings = { calls += "settings" }) }
        }

        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        assertEquals(listOf("retry", "settings"), calls)
    }

    @Test
    fun theOfflineScreenSaysWhenItIsInView() {
        var state by mutableStateOf(offline)
        val inView = mutableListOf<Boolean>()
        compose.setContent { NeoTheme { HomeScreen(state, onOfflineInView = { inView += it }) } }
        compose.waitForIdle()

        state = loaded
        compose.waitForIdle()

        assertEquals(listOf(true, false), inView)
    }

    @Test
    fun downMovesToTheNextRowWithoutScrolling() {
        show()

        enterRows()
        press(Key.DirectionDown)

        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 0)).assertIsFocused()
        compose.onNodeWithText("This season").assertTopPositionInRootIsEqualTo(198.dp)
    }

    @Test
    fun theLastRowLeavesAllThreeRowsOnScreen() {
        show()

        enterRows()
        press(Key.DirectionDown, times = 2)

        compose.onNodeWithTag(homeCardTag(HomeRow.POPULAR, 0)).assertIsFocused()
        compose.onNodeWithText("Trending").assertTopPositionInRootIsEqualTo(30.dp)
        compose.onNodeWithText("Popular").assertTopPositionInRootIsEqualTo(366.dp)
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
    fun aRowTwoRowsAwayReturnsToTheCardYouLeft() {
        show()

        enterRows()
        press(Key.DirectionRight, times = 5)
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
        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 4)).assertLeftPositionInRootIsEqualTo((96 - 20 - 196).dp)
        compose.onNodeWithText("This season").assertTopPositionInRootIsEqualTo(198.dp)
    }

    @Test
    fun continueWatchingShowsTheEpisodeTheTitleAndTimeLeft() {
        show(continuing)

        compose.onNodeWithText("Continue watching").assertExists()
        compose.onNodeWithText("Show 900").assertExists()
        compose.onNodeWithText("E7 · 14 min left").assertExists()
        // The board's large episode number was a stand-in, so the card leaves it out.
        compose.onNodeWithText("07", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aShowYouAreWatchingKeepsItsUsualCardInOtherRows() {
        show(continuing.copy(progress = continuing.progress + (0 to continuing.progress.getValue(900))))

        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 0)).assert(hasText("TV · 2026 · 12 episodes"))
    }

    @Test
    fun continueWatchingIsLeftOutWithNothingToContinue() {
        show()

        compose.onNodeWithText("Continue watching").assertDoesNotExist()
        compose.onNodeWithText("Trending").assertExists()
    }

    @Test
    fun downFromTheDotsGoesToContinueWatching() {
        show(continuing)

        enterRows()

        compose.onNodeWithTag(homeCardTag(HomeRow.CONTINUE_WATCHING, 0)).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 0)).assertIsFocused()
    }

    @Test
    fun continueWatchingHasNoSeeMore() {
        show(continuing)

        compose.onNodeWithTag(homeCardTag(HomeRow.CONTINUE_WATCHING, 2)).assertExists()
        compose.onNodeWithTag(seeMoreTag(HomeRow.CONTINUE_WATCHING)).assertDoesNotExist()
    }

    @Test
    fun aContinueWatchingCardKeepsFocusWhenHomeIsRecreated() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { NeoTheme { HomeScreen(continuing) } }
        enterRows()
        press(Key.DirectionRight)

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag(homeCardTag(HomeRow.CONTINUE_WATCHING, 1)).assertIsFocused()
    }

    @Test
    fun playIsReadyWhileHomeLoads() {
        show(HomeState(HomeRow.entries.associateWith { RowState.Loading }))

        // In the same place as once the show is in, with only Play to press.
        compose
            .onNodeWithTag(SPOTLIGHT_PLAY_TAG)
            .assertIsFocused()
            .assertTextEquals("Play")
            .assertTopPositionInRootIsEqualTo(276.dp)
        compose.onNodeWithContentDescription("Details").assertDoesNotExist()
        compose.onNodeWithText("Continue watching").assertTopPositionInRootIsEqualTo(382.dp)
        press(Key.DirectionDown)
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
    }

    @Test
    fun playOpensTheShowOnceItArrives() {
        val opened = mutableListOf<Int>()
        var state by mutableStateOf(HomeState(loaded.rows + (HomeRow.TRENDING to RowState.Loading)))
        compose.setContent { NeoTheme { HomeScreen(state, onOpen = { opened += it }) } }

        press(Key.DirectionCenter)
        state = loaded
        compose.waitForIdle()
        press(Key.DirectionCenter)

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
        assertEquals(listOf(0), opened)
    }

    @Test
    fun playKeepsFocusUntilTheSpotlightArrivesEvenIfLowerRowsLoadFirst() {
        show(HomeState(loaded.rows + (HomeRow.TRENDING to RowState.Loading)))

        press(Key.DirectionDown)
        press(Key.DirectionRight)

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
    }

    @Test
    fun rightOnTheDetailsButtonKeepsFocusOnIt() {
        show()

        press(Key.DirectionRight, times = 3)

        compose.onNodeWithContentDescription("Details").assertIsFocused()
    }

    @Test
    fun aLineAboveTheTitleSaysWhyTheShowIsFeatured() {
        show(loaded.copy(spotlight = loaded.spotlight!!.copy(reasons = mapOf(0 to FeaturedReason.Trending(2)))))

        compose.onNodeWithTag(SPOTLIGHT_REASON_TAG).assertTextEquals("#2 TRENDING")
        val reason = compose.onNodeWithTag(SPOTLIGHT_REASON_TAG).getUnclippedBoundsInRoot()
        assertTrue(reason.bottom <= compose.onNodeWithTag(SPOTLIGHT_TITLE_TAG).getUnclippedBoundsInRoot().top)
    }

    @Test
    fun aHighlyRatedShowSaysItsPlace() {
        show(loaded.copy(spotlight = loaded.spotlight!!.copy(reasons = mapOf(0 to FeaturedReason.HighestRated(1)))))

        compose.onNodeWithTag(SPOTLIGHT_REASON_TAG).assertTextEquals("#1 HIGHEST RATED")
    }

    @Test
    fun theReasonIsLeftOutWithNothingToSay() {
        show()

        compose.onNodeWithTag(SPOTLIGHT_REASON_TAG).assertDoesNotExist()
    }

    @Test
    fun aShowWithProgressOffersResume() {
        show(loaded.copy(progress = continuing.progress.mapKeys { (id, _) -> id - 900 }))

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertTextEquals("Resume E7")
    }

    // Focus grows Play from its middle, and room for that keeps the design's gap to the next button.
    @Test
    fun theFocusedMainButtonKeepsItsGap() {
        show()

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
        // Bounds leave out the focus scale, so the drawn edge is half the growth further right.
        val play = compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).getUnclippedBoundsInRoot()
        val drawnRight = play.right + play.width * (NeoDimensions().focusScaleButton - 1) / 2
        val add = compose.onNodeWithContentDescription("Add to list").getUnclippedBoundsInRoot()
        assertEquals(16f, (add.left - drawnRight).value, 0.5f)
    }

    @Test
    fun theMetaLineStartsWithTheRating() {
        val spotlight = loaded.spotlight!!
        show(loaded.copy(spotlight = spotlight.copy(shows = listOf(spotlight.show.copy(averageScore = 84)) + spotlight.shows.drop(1))))

        compose.onNodeWithTag(SPOTLIGHT_META_TAG).assertTextEquals("★ 8.4 · TV · 2026 · 12 episodes")
    }

    @Test
    fun withNoSpotlightPlayKeepsFocus() {
        show(loaded.copy(spotlight = null))

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
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

        // The button is shorter than a card, but the row rests where it does for a card: cards 34 dp under 30 dp.
        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 11)).assertTopPositionInRootIsEqualTo(64.dp)
    }

    private val pulsing = SemanticsMatcher.expectValue(Pulsing, true)

    @Test
    fun whileHomeLoadsItsPlaceholdersPulse() {
        show(HomeState(rows = HomeRow.entries.associateWith { RowState.Loading }))

        // Five spotlight bars and four dots, then five cards in each row on screen.
        val count = compose.onAllNodes(pulsing, useUnmergedTree = true).fetchSemanticsNodes().size
        assertTrue("$count placeholders pulse", count >= 5 + 4 + 5)
    }

    @Test
    fun aCardPulsesUntilItsPictureIsIn() {
        val ready = CompletableDeferred<Unit>()
        fakeImages(compose.activity, 800, 310, ready = ready)
        val trending = (loaded.rows.getValue(HomeRow.TRENDING) as RowState.Loaded).items
        val withPicture = trending.mapIndexed { i, card -> if (i == 0) card.copy(coverImageUrl = "https://example.test/0.jpg") else card }
        show(loaded.copy(rows = loaded.rows + (HomeRow.TRENDING to RowState.Loaded(withPicture))))

        fun pulsingIn(index: Int) =
            compose.onAllNodes(pulsing and hasAnyAncestor(hasTestTag(homeCardTag(HomeRow.TRENDING, index))), useUnmergedTree = true)

        pulsingIn(0).assertCountEquals(1)
        // Without a picture, nothing is coming, so the card stays still.
        pulsingIn(1).assertCountEquals(0)
        ready.complete(Unit)
        compose.waitForIdle()
        pulsingIn(0).assertCountEquals(0)
    }

    // Play holds focus while Home loads, so the remote has somewhere to start, but it only shows with the show.
    @Test
    fun playStaysHiddenUntilTheShowIsIn() {
        var state by mutableStateOf(HomeState(rows = HomeRow.entries.associateWith { RowState.Loading }))
        compose.setContent { NeoTheme { HomeScreen(state) } }

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused().assert(hidden)
        state = loaded
        compose.waitForIdle()
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused().assert(!hidden)
    }
}
