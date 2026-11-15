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
import androidx.compose.ui.test.assertHeightIsEqualTo
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

    /** The rail beside a button that starts with focus. */
    private fun show() =
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
                        onSelect = { chosen += it },
                        onClose = {
                            closes++
                            content.requestFocus()
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
    fun showsEverySectionInOrderWithTheProfileLast() {
        show()

        assertEquals(
            listOf(Section.SEARCH, Section.HOME, Section.DISCOVER, Section.SCHEDULE, Section.SETTINGS, Section.PROFILE),
            Section.entries,
        )
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(RAIL_TAG))).assertCountEquals(Section.entries.size)
        val tops = Section.entries.map { item(it).getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
    }

    // The logo and the profile match, each as far from its end of the screen, with the sections centred between them.
    @Test
    fun theSectionsSitCentredBetweenTheLogoAndTheProfile() {
        show()

        // The logo's box is just its artwork, which is wider than it is tall, so its height matches the picture's.
        val logo =
            compose
                .onNodeWithTag(RAIL_LOGO_TAG, useUnmergedTree = true)
                .assertHeightIsEqualTo(36.dp)
                .assertWidthIsEqualTo((36f * 246 / 228).dp)
                .getUnclippedBoundsInRoot()
        val avatar = compose.onNodeWithTag(RAIL_AVATAR_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(((logo.left + logo.right) / 2).value, ((avatar.left + avatar.right) / 2).value, 0.5f)
        assertEquals(logo.top.value, (540.dp - avatar.bottom).value, 0.5f)
        val above = item(Section.SEARCH).getUnclippedBoundsInRoot().top - logo.bottom
        val below = avatar.top - item(Section.SETTINGS).getUnclippedBoundsInRoot().bottom
        assertEquals(above.value, below.value, 0.5f)
    }

    @Test
    fun theClosedRailIsSlimWithoutLabels() {
        show()

        compose.onNodeWithTag(RAIL_TAG).assertWidthIsEqualTo(72.dp)
        compose.onNodeWithText("Discover").assertDoesNotExist()
        compose.onNodeWithTag(RAIL_SCRIM_TAG).assertDoesNotExist()
    }

    @Test
    fun theProfilePictureIsRound() {
        show()

        compose.onNodeWithTag(RAIL_AVATAR_TAG, useUnmergedTree = true).assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
    }

    // Until signing in brings the account's picture, the profile shows a person icon in the middle of its circle.
    private fun assertPersonIconIsCentred() {
        val picture = compose.onNodeWithTag(RAIL_AVATAR_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val icon =
            compose
                .onNodeWithTag(RAIL_AVATAR_ICON_TAG, useUnmergedTree = true)
                .assertWidthIsEqualTo(18.dp)
                .assertHeightIsEqualTo(18.dp)
                .getUnclippedBoundsInRoot()
        assertEquals(((picture.left + picture.right) / 2).value, ((icon.left + icon.right) / 2).value, 0.5f)
        assertEquals(((picture.top + picture.bottom) / 2).value, ((icon.top + icon.bottom) / 2).value, 0.5f)
    }

    @Test
    fun theProfilePictureHoldsAPersonIconClosedAndOpen() {
        show()
        assertPersonIconIsCentred()

        press(Key.DirectionLeft)
        press(Key.DirectionDown, times = 4)

        item(Section.PROFILE).assertIsFocused()
        compose.onNodeWithText("Profile").assertExists()
        assertPersonIconIsCentred()
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

    // The remote's Back reaches the activity's back handlers, not the focused item's keys.
    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun backClosesTheRail() {
        show()

        press(Key.DirectionLeft)
        back()

        compose.onNodeWithTag(CONTENT_TAG).assertIsFocused()
        assertEquals(1, closes)
    }
}
