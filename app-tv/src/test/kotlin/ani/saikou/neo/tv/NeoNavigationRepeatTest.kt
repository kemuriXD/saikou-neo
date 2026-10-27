package ani.saikou.neo.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import androidx.tv.material3.Text
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A franchise can be walked in a loop, such as season 3 to its prequel and back to season 3 from there. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NeoNavigationRepeatTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val restoration = StateRestorationTester(compose)

    /** Home opens show 1. Each show's button marks it as left and opens the other show. */
    private fun show() =
        restoration.setContent {
            NeoTheme {
                NeoNavigation(
                    home = { openDetails, _, _ -> Opener("Home", "new") { openDetails(1) } },
                    seeMore = { _, _ -> },
                    details = { animeId, openDetails, _ ->
                        var left by rememberSaveable { mutableStateOf(false) }
                        Opener("Show $animeId", if (left) "left" else "new") {
                            left = true
                            openDetails(if (animeId == 1) 2 else 1)
                        }
                    },
                )
            }
        }

    @androidx.compose.runtime.Composable
    private fun Opener(
        name: String,
        state: String,
        open: () -> Unit,
    ) {
        val focus = remember { FocusRequester() }
        Column {
            Text("$name is $state")
            NeoButton("Open", onClick = open, modifier = Modifier.focusRequester(focus))
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }

    private fun select() {
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }

    private fun back() {
        Espresso.pressBack()
        compose.waitForIdle()
    }

    @Test
    fun openingAShowAgainOpensItAfresh() {
        show()
        select()
        select()
        select()

        compose.onNodeWithText("Show 1 is new").assertExists()
    }

    @Test
    fun backFromTheSecondVisitFindsTheFirstAsItWasLeft() {
        show()
        select()
        select()
        select()

        back()
        compose.onNodeWithText("Show 2 is left").assertExists()
        back()
        compose.onNodeWithText("Show 1 is left").assertExists()
    }

    @Test
    fun repeatVisitsSurviveRecreation() {
        show()
        select()
        select()
        select()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithText("Show 1 is new").assertExists()
        back()
        back()
        compose.onNodeWithText("Show 1 is left").assertExists()
    }
}
