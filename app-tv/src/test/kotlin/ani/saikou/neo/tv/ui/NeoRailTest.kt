package ani.saikou.neo.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CONTENT_TAG = "content"

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NeoRailTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val chosen = mutableListOf<Section>()
    private var closes = 0

    /** The rail beside a button that starts with focus. [closes] says whether closing the rail finds somewhere to go. */
    private fun show(closes: Boolean = true) =
        compose.setContent {
            NeoTheme {
                val content = remember { FocusRequester() }
                LaunchedEffect(Unit) { content.requestFocus() }
                Box(Modifier.fillMaxSize()) {
                    NeoButton(
                        "Play",
                        onClick = {},
                        modifier = Modifier.padding(start = 300.dp, top = 200.dp).focusRequester(content).testTag(CONTENT_TAG),
                    )
                    NeoRail(
                        current = Section.HOME,
                        onSelect = { chosen += it },
                        onClose = {
                            this@NeoRailTest.closes++
                            closes && content.requestFocus()
                        },
                    )
                }
            }
        }

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun item(section: Section) = compose.onNodeWithTag(railTag(section))

    @Test
    fun showsEverySectionInOrderWithSettingsAndProfileAtTheBottom() {
        show()

        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(RAIL_TAG))).assertCountEquals(Section.entries.size)
        val tops = Section.entries.map { item(it).getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
        assertTrue(item(Section.SETTINGS).getUnclippedBoundsInRoot().top > 400.dp)
    }

    @Test
    fun theClosedRailIsSlimWithoutLabels() {
        show()

        compose.onNodeWithTag(RAIL_TAG).assertWidthIsEqualTo(72.dp)
        compose.onNodeWithText("Discover").assertDoesNotExist()
        compose.onNodeWithTag(RAIL_SCRIM_TAG).assertDoesNotExist()
    }

    @Test
    fun leftOpensTheRailOnTheCurrentSectionWithLabelsAndTheScreenDimmed() {
        show()

        press(Key.DirectionLeft)

        item(Section.HOME).assertIsFocused()
        compose.onNodeWithTag(RAIL_TAG).assertWidthIsEqualTo(240.dp)
        compose.onNodeWithText("Discover").assertExists()
        compose.onNodeWithTag(RAIL_SCRIM_TAG).assertExists()
    }

    @Test
    fun theCurrentSectionIsSelected() {
        show()

        item(Section.HOME).assertIsSelected()
        item(Section.SEARCH).assertIsNotSelected()
    }

    @Test
    fun selectOpensASection() {
        show()

        press(Key.DirectionLeft)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertEquals(listOf(Section.DISCOVER), chosen)
    }

    @Test
    fun rightClosesTheRail() {
        show()

        press(Key.DirectionLeft)
        press(Key.DirectionRight)

        compose.onNodeWithTag(CONTENT_TAG).assertIsFocused()
        compose.onNodeWithTag(RAIL_TAG).assertWidthIsEqualTo(72.dp)
        assertEquals(1, closes)
    }

    @Test
    fun backClosesTheRail() {
        show()

        press(Key.DirectionLeft)
        press(Key.Back)

        compose.onNodeWithTag(CONTENT_TAG).assertIsFocused()
        assertEquals(1, closes)
    }

    // On a section that isn't built, the rail is all there is to focus.
    @Test
    fun withNowhereToGoTheRailKeepsFocus() {
        show(closes = false)

        press(Key.DirectionLeft)
        press(Key.DirectionRight)

        item(Section.HOME).assertIsFocused()
    }
}
