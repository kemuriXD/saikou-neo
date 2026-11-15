package ani.saikou.neo.tv.details

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.shared.details.DetailsSection
import ani.saikou.neo.shared.details.DetailsSection.EPISODES
import ani.saikou.neo.shared.details.DetailsSection.OVERVIEW
import ani.saikou.neo.shared.details.DetailsSection.WATCH_ORDER
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class DetailsSectionsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var inside = false

    /** A main button over three tabs, each with a button of its own in its section. */
    private fun show() {
        compose.setContent {
            NeoTheme {
                var selected by remember { mutableStateOf(OVERVIEW) }
                val main = remember { FocusRequester() }
                Column {
                    NeoButton("Main", onClick = {}, modifier = Modifier.focusRequester(main))
                    DetailsSections(
                        sections = listOf(OVERVIEW, EPISODES, WATCH_ORDER),
                        selected = { selected },
                        onSelect = { selected = it },
                        onInside = { inside = it },
                    ) { section -> NeoButton("${section.name} content", onClick = {}) }
                }
                LaunchedEffect(Unit) { main.requestFocus() }
            }
        }
    }

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    private fun tab(section: DetailsSection) = compose.onNodeWithTag(detailsTabTag(section))

    @Test
    fun downFromAboveReachesTheTabs() {
        show()

        press(Key.DirectionDown)

        tab(OVERVIEW).assertIsFocused().assertIsSelected()
    }

    @Test
    fun rightSelectsTheNextTabAndShowsItsSectionWithNothingToPress() {
        show()
        press(Key.DirectionDown)

        press(Key.DirectionRight)

        tab(EPISODES).assertIsFocused().assertIsSelected()
        tab(OVERVIEW).assertIsNotSelected()
        compose.onNodeWithText("EPISODES content").assertExists()
        compose.onNodeWithText("OVERVIEW content").assertDoesNotExist()
    }

    @Test
    fun downGoesIntoTheSectionAndUpComesBackToItsTab() {
        show()
        press(Key.DirectionDown)
        press(Key.DirectionRight)

        press(Key.DirectionDown)
        compose.onNodeWithText("EPISODES content").assertIsFocused()
        assertEquals(true, inside)

        press(Key.DirectionUp)
        tab(EPISODES).assertIsFocused()
        assertEquals(false, inside)
    }

    @Test
    fun comingBackDownFromAboveLandsOnTheSelectedTab() {
        show()
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        press(Key.DirectionUp)
        press(Key.DirectionDown)

        tab(WATCH_ORDER).assertIsFocused()
    }
}
