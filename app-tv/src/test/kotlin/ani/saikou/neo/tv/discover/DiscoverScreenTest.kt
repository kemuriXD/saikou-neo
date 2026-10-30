package ani.saikou.neo.tv.discover

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.discover.DiscoverRow
import ani.saikou.neo.shared.discover.DiscoverState
import ani.saikou.neo.shared.home.RowState
import ani.saikou.neo.shared.search.SearchGenre
import ani.saikou.neo.tv.details.detailsPosterTag
import ani.saikou.neo.tv.home.card
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
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class DiscoverScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val season = DiscoverRow.Season(AnimeSeason.FALL, 2026)
    private val action = DiscoverRow.Genre(SearchGenre.ACTION)
    private val romance = DiscoverRow.Genre(SearchGenre.ROMANCE)

    private val loaded =
        DiscoverState(
            mapOf(
                season to RowState.Loaded(List(8) { card(it) }),
                action to RowState.Loaded(List(8) { card(100 + it) }),
                romance to RowState.Loaded(List(8) { card(200 + it) }),
            ),
        )

    private val opened = mutableListOf<Int>()

    private fun show(state: DiscoverState = loaded) {
        compose.setContent { NeoTheme { DiscoverScreen(state, onOpen = { opened += it }) } }
    }

    @Test
    fun eachRowIsHeadedByItsTheme() {
        show()

        compose.onNodeWithText("Fall 2026").assertExists()
        compose.onNodeWithText("Action").assertExists()
    }

    @Test
    fun focusStartsOnThisSeasonsFirstPosterAndSelectingItOpensDetails() {
        show()

        compose.onNodeWithTag(detailsPosterTag(0)).assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(0), opened) }
    }

    @Test
    fun downMovesToTheNextRowsFirstPoster() {
        show()

        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionDown)
        }

        compose.onNodeWithTag(detailsPosterTag(100)).assertIsFocused()
    }

    // As after Back from Details: the screen is made again from what it saved.
    @Test
    fun focusReturnsToThePosterItLeftFrom() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { NeoTheme { DiscoverScreen(loaded) } }
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionRight)
        }
        compose.onNodeWithTag(detailsPosterTag(101)).assertIsFocused()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag(detailsPosterTag(101)).assertIsFocused()
    }

    @Test
    fun aLoadingRowPulsesInItsPlace() {
        show(loaded.copy(rows = loaded.rows + (season to RowState.Loading)))

        compose.onNodeWithText("Fall 2026").assertExists()
        compose.onNodeWithTag(DISCOVER_SKELETON_TAG).assertExists()
        // Focus starts on the first row that has loaded.
        compose.onNodeWithTag(detailsPosterTag(100)).assertIsFocused()
    }

    @Test
    fun aRowThatFailedIsLeftOut() {
        show(loaded.copy(rows = loaded.rows + (action to RowState.Failed)))

        compose.onNodeWithText("Action").assertDoesNotExist()
        assertTrue(compose.onAllNodesWithTag(detailsPosterTag(100)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun offlineDiscoverSaysSoAsHomeDoes() {
        show(loaded.copy(offline = true))

        compose.onNodeWithText("No internet connection").assertExists()
    }
}
