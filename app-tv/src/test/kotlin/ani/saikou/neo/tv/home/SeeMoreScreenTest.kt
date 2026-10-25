package ani.saikou.neo.tv.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.shared.home.More
import ani.saikou.neo.shared.home.SeeMoreState
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.fakeImages
import ani.saikou.neo.tv.ui.forgetFakeImages
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SeeMoreScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // Four rows of six, with more to come.
    private val ready = SeeMoreState(List(24) { card(it) }, More.Ready)

    private val opened = mutableListOf<Int>()
    private var loadMoreCalls = 0
    private var retries = 0
    private val rested = mutableListOf<AnimeCard?>()

    private fun show(state: SeeMoreState = ready) {
        compose.setContent {
            NeoTheme {
                SeeMoreScreen(
                    row = HomeRow.TRENDING,
                    state = state,
                    onOpen = { opened += it },
                    onLoadMore = { loadMoreCalls++ },
                    onRetry = { retries++ },
                    onRest = { rested += it },
                )
            }
        }
    }

    @Test
    fun focusSaysWhichPosterItIsOn() {
        show()

        compose.onNodeWithTag(posterTag(0)).assertIsFocused()
        press(Key.DirectionRight)

        assertEquals(ready.items.take(2), rested)
    }

    // So Details opens with the logo already loaded.
    @Test
    fun theLogoOfThePosterFocusRestsOnStartsLoading() {
        val asked = fakeImages(compose.activity, 800, 310)

        show(ready.copy(restingLogo = Logo.Image("https://logos.test/rested.png")))

        compose.waitUntil { "https://logos.test/rested.png" in asked }
    }

    @After
    fun forgetImages() = forgetFakeImages()

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun showsTheRowsTitleOverSixPostersARow() {
        show()

        compose.onNodeWithText("Trending").assertTopPositionInRootIsEqualTo(40.dp)
        compose.onNodeWithTag(posterTag(0)).assertLeftPositionInRootIsEqualTo(58.dp).assertTopPositionInRootIsEqualTo(104.dp)
        compose.onNodeWithTag(posterTag(5)).assertLeftPositionInRootIsEqualTo(778.dp)
        compose.onNodeWithTag(posterTag(6)).assertLeftPositionInRootIsEqualTo(58.dp)
    }

    @Test
    fun startsOnTheFirstShow() {
        show()

        compose.onNodeWithTag(posterTag(0)).assertIsFocused()
    }

    @Test
    fun selectingAShowOpensIt() {
        show()

        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        assertEquals(listOf(1), opened)
    }

    @Test
    fun theFocusedRowRestsUnderTheTitle() {
        show()

        press(Key.DirectionDown)

        compose.onNodeWithTag(posterTag(6)).assertIsFocused().assertTopPositionInRootIsEqualTo(104.dp)
    }

    @Test
    fun nearingTheEndLoadsMore() {
        show()

        press(Key.DirectionDown)
        assertEquals(0, loadMoreCalls)

        // The third of four rows is within two rows of the end.
        press(Key.DirectionDown)
        assertEquals(1, loadMoreCalls)
    }

    @Test
    fun whileLoadingThePostersAreSkeletons() {
        show(SeeMoreState())

        compose.onNodeWithText("Trending").assertExists()
        compose.onAllNodesWithTag(POSTER_SKELETON_TAG).assertCountEquals(12)
    }

    @Test
    fun aFailedFirstPageOffersTryAgain() {
        show(SeeMoreState(emptyList(), More.Failed))

        compose.onNodeWithText("Couldn’t load these shows").assertExists()
        compose.onNodeWithText("Try again").assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(1, retries)
    }

    @Test
    fun aFailedLaterPageOffersTryAgainAfterTheLastShow() {
        show(SeeMoreState(List(10) { card(it) }, More.Failed))

        compose.onNodeWithText("Couldn’t load more").assertExists()
        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 4)
        compose.onNodeWithText("Try again").assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(1, retries)
    }

    @Test
    fun focusComesBackToTheSameShowAfterRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            NeoTheme { SeeMoreScreen(HomeRow.TRENDING, ready, onOpen = {}, onLoadMore = {}, onRetry = {}) }
        }

        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 2)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag(posterTag(8)).assertIsFocused()
    }
}
