package ani.saikou.neo.tv

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
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
import androidx.tv.material3.Text
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.home.More
import ani.saikou.neo.shared.home.SeeMoreState
import ani.saikou.neo.tv.home.HomeScreen
import ani.saikou.neo.tv.home.SPOTLIGHT_PLAY_TAG
import ani.saikou.neo.tv.home.SeeMoreScreen
import ani.saikou.neo.tv.home.card
import ani.saikou.neo.tv.home.homeCardTag
import ani.saikou.neo.tv.home.loaded
import ani.saikou.neo.tv.home.posterTag
import ani.saikou.neo.tv.home.seeMoreTag
import ani.saikou.neo.tv.theme.NeoTheme
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

    private fun show(seeMoreState: SeeMoreState = SeeMoreState(List(24) { card(it) }, More.Done)) =
        restoration.setContent {
            NeoTheme {
                NeoNavigation(
                    home = { openDetails, openSeeMore -> HomeScreen(loaded, onOpen = openDetails, onSeeMore = openSeeMore) },
                    seeMore = { row, openDetails ->
                        SeeMoreScreen(row, seeMoreState, openDetails, onLoadMore = {}, onRetry = {})
                    },
                    details = { animeId, _ -> Text("Details for $animeId") },
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
}
