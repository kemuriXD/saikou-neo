package ani.saikou.neo.tv.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.TOP_BAR_LOGO_TAG
import ani.saikou.neo.tv.ui.TOP_BAR_TAG
import ani.saikou.neo.tv.ui.TopTab
import ani.saikou.neo.tv.ui.topTabTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class HomeTopBarTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val hidden = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)
    private val chosen = mutableListOf<TopTab>()

    private fun show() = compose.setContent { NeoTheme { HomeScreen(loaded, onTab = { chosen += it }) } }

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun tab(tab: TopTab) = compose.onNodeWithTag(topTabTag(tab))

    @Test
    fun theBarSitsAtTheTopWithHomeAsTheCurrentTab() {
        show()

        // The bar is 44 dp tall at 18 dp, with 40 dp tabs in its middle and the logo 48 dp in.
        tab(TopTab.HOME).assertIsSelected().assertTopPositionInRootIsEqualTo(20.dp).assertHeightIsEqualTo(40.dp)
        tab(TopTab.DISCOVER).assertIsNotSelected()
        compose.onNodeWithTag(TOP_BAR_LOGO_TAG).assertLeftPositionInRootIsEqualTo(48.dp).assertHeightIsEqualTo(26.dp)
    }

    // Signed out, there's no Library button: just the four tabs.
    @Test
    fun theBarHasOnlyTheFourTabs() {
        show()

        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(TOP_BAR_TAG))).assertCountEquals(4)
    }

    @Test
    fun upFromPlayReachesTheHomeTab() {
        show()

        press(Key.DirectionUp)

        tab(TopTab.HOME).assertIsFocused()
    }

    @Test
    fun leftAndRightMoveAlongTheBar() {
        show()

        press(Key.DirectionUp)
        press(Key.DirectionRight, times = 3)
        tab(TopTab.SETTINGS).assertIsFocused()
        press(Key.DirectionLeft)

        tab(TopTab.SEARCH).assertIsFocused()
    }

    @Test
    fun downFromTheBarReturnsToPlay() {
        show()

        press(Key.DirectionUp)
        press(Key.DirectionRight)
        press(Key.DirectionDown)

        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).assertIsFocused()
    }

    @Test
    fun choosingATabOpensIt() {
        show()

        press(Key.DirectionUp)
        press(Key.DirectionRight, times = 2)
        press(Key.DirectionCenter)

        assertEquals(listOf(TopTab.SEARCH), chosen)
    }

    @Test
    fun theBarSlidesAwayWhenFocusMovesIntoTheRows() {
        show()

        press(Key.DirectionDown, times = 2)

        compose.onNodeWithTag(TOP_BAR_TAG).assert(hidden)
        assertTrue(compose.onNodeWithTag(TOP_BAR_TAG).getUnclippedBoundsInRoot().bottom <= 0.dp)
    }

    @Test
    fun theBarComesBackWithTheSpotlight() {
        show()

        press(Key.DirectionDown, times = 2)
        press(Key.DirectionUp, times = 3)

        tab(TopTab.HOME).assertIsFocused().assertTopPositionInRootIsEqualTo(20.dp)
    }
}
