package ani.saikou.neo.tv.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.home.HomeState
import ani.saikou.neo.shared.home.RowState
import ani.saikou.neo.tv.theme.NeoTheme
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

    private fun show(state: HomeState = loaded) {
        compose.setContent { NeoTheme { HomeScreen(state) } }
    }

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

    private fun press(
        key: Key,
        times: Int = 1,
    ) = compose.onRoot().performKeyInput { repeat(times) { pressKey(key) } }

    @Test
    fun startsOnTheFirstTrendingCard() {
        show()

        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 0)).assertIsFocused()
    }

    @Test
    fun theFocusedRowRestsUnderTheSpotlight() {
        show()

        // Heading at 300 dp, cards 34 dp below it, from the 58 dp grid margin, 20 dp apart.
        // Card 1 is unfocused, so its bounds aren't scaled.
        compose.onNodeWithText("Trending").assertTopPositionInRootIsEqualTo(300.dp)
        compose
            .onNodeWithTag(homeCardTag(HomeRow.TRENDING, 1))
            .assertTopPositionInRootIsEqualTo(334.dp)
            .assertLeftPositionInRootIsEqualTo((58 + 196 + 20).dp)
            .assertWidthIsEqualTo(196.dp)
            .assertHeightIsEqualTo(110.dp)
        // The next row's heading peeks at 478 dp.
        compose.onNodeWithText("This season").assertTopPositionInRootIsEqualTo(478.dp)
    }

    @Test
    fun downMovesToTheNextRowAndBringsItUnderTheSpotlight() {
        show()

        press(Key.DirectionDown)

        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 0)).assertIsFocused()
        compose.onNodeWithText("This season").assertTopPositionInRootIsEqualTo(300.dp)
    }

    @Test
    fun theLastRowAlsoReachesTheSpotlightLine() {
        show()

        press(Key.DirectionDown, times = 2)

        compose.onNodeWithTag(homeCardTag(HomeRow.POPULAR, 0)).assertIsFocused()
        compose.onNodeWithText("Popular").assertTopPositionInRootIsEqualTo(300.dp)
    }

    @Test
    fun aRowReturnsToTheCardYouLeft() {
        show()

        press(Key.DirectionRight, times = 3)
        press(Key.DirectionDown)
        press(Key.DirectionUp)

        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 3)).assertIsFocused()
    }

    @Test
    fun aRowThatScrolledOffScreenReturnsToTheCardYouLeft() {
        show()

        press(Key.DirectionRight, times = 5)
        // Two rows down, Trending is off screen and its cards are disposed.
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionUp, times = 2)

        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 5)).assertIsFocused()
    }

    @Test
    fun aRowLeftAtItsEndReturnsToItsLastCard() {
        show()

        // The last cards can't reach the keyline, so they sit further right than other rows' cards.
        press(Key.DirectionRight, times = 11)
        press(Key.DirectionDown)
        press(Key.DirectionUp)

        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 11)).assertIsFocused()
    }

    @Test
    fun aRowYouHaveNotVisitedStartsAtItsFirstCard() {
        show()

        press(Key.DirectionRight, times = 11)
        press(Key.DirectionDown)

        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 0)).assertIsFocused()
    }

    @Test
    fun theSpotlightShowsTheFocusedShow() {
        show()

        press(Key.DirectionRight)

        compose.onNodeWithTag(SPOTLIGHT_TITLE_TAG).assertTextEquals("Show 1")
    }

    @Test
    fun aFailedRowIsLeftOut() {
        show(loaded.copy(rows = loaded.rows + (HomeRow.THIS_SEASON to RowState.Failed)))

        press(Key.DirectionDown)

        compose.onNodeWithTag(homeCardTag(HomeRow.POPULAR, 0)).assertIsFocused()
    }

    @Test
    fun rowsKeepTheirPlaceWhenHomeIsRecreated() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { NeoTheme { HomeScreen(loaded) } }
        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 5)

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 5)).assertIsFocused()
        // Card 5 rests on the grid margin, so card 4 ends one gutter before it.
        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 4)).assertLeftPositionInRootIsEqualTo((58 - 20 - 196).dp)
        compose.onNodeWithText("This season").assertTopPositionInRootIsEqualTo(300.dp)
    }

    @Test
    fun focusWaitsForTrendingEvenIfAnotherRowLoadsFirst() {
        var state by mutableStateOf(loaded.copy(rows = loaded.rows + (HomeRow.TRENDING to RowState.Loading)))
        compose.setContent { NeoTheme { HomeScreen(state) } }

        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 0)).assertIsNotFocused()

        state = loaded
        compose.onNodeWithTag(homeCardTag(HomeRow.TRENDING, 0)).assertIsFocused()
    }

    @Test
    fun aSavedCardThatIsGoneHandsFocusToTheCardNowInItsPlace() {
        // The app was killed, and This season came back without the saved show.
        val reloaded = (loaded.rows.getValue(HomeRow.THIS_SEASON) as RowState.Loaded).items.filter { it.id != 105 }
        restoreWith(loaded.copy(rows = loaded.rows + (HomeRow.THIS_SEASON to RowState.Loaded(reloaded)))) {
            press(Key.DirectionDown)
            press(Key.DirectionRight, times = 5)
        }

        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 5)).assertIsFocused()
    }

    @Test
    fun aSavedRowThatFailsHandsFocusToTrending() {
        restoreWith(loaded.copy(rows = loaded.rows + (HomeRow.THIS_SEASON to RowState.Failed))) {
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
}
