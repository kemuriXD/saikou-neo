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
import ani.saikou.neo.tv.home.HomeScreen
import ani.saikou.neo.tv.home.homeCardTag
import ani.saikou.neo.tv.home.loaded
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

    private fun show() =
        restoration.setContent {
            NeoTheme {
                NeoNavigation(
                    home = { openDetails -> HomeScreen(loaded, onOpen = openDetails) },
                    details = { animeId -> Text("Details for $animeId") },
                )
            }
        }

    private fun press(
        key: Key,
        times: Int = 1,
    ) = compose.onRoot().performKeyInput { repeat(times) { pressKey(key) } }

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

        press(Key.DirectionRight, times = 5)
        press(Key.DirectionCenter)

        compose.onNodeWithText("Details for 5").assertExists()
    }

    @Test
    fun backReturnsToTheCardYouOpened() {
        show()

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

        press(Key.DirectionDown)
        press(Key.DirectionRight, times = 5)
        press(Key.DirectionCenter)
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Details for 105").assertExists()
        back()
        compose.onNodeWithTag(homeCardTag(HomeRow.THIS_SEASON, 5)).assertIsFocused()
    }
}
