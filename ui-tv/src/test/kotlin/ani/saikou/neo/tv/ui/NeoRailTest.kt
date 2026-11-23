package ani.saikou.neo.tv.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInspectionMode
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
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.tv.theme.NeoTheme
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CONTENT_TAG = "content"

@OptIn(ExperimentalTestApi::class, ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class NeoRailTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val chosen = mutableListOf<Section>()
    private var closes = 0

    // The AniList account signed in on the TV, or null while signed out.
    private var account by mutableStateOf<AniListProfile?>(null)

    /** The rail beside a button that starts with focus, inside [around]. */
    private fun show(around: @Composable (@Composable () -> Unit) -> Unit = { it() }) =
        compose.setContent {
            around {
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
                            profile = account,
                        )
                    }
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

    // The logo and the profile are centred the same way in from each end, with the sections centred between them.
    @Test
    fun theSectionsSitCentredBetweenTheLogoAndTheProfile() {
        show()

        val logo = compose.onNodeWithTag(RAIL_LOGO_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val avatar = compose.onNodeWithTag(RAIL_AVATAR_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(((logo.left + logo.right) / 2).value, ((avatar.left + avatar.right) / 2).value, 0.5f)
        assertEquals(((logo.top + logo.bottom) / 2).value, (540.dp - (avatar.top + avatar.bottom) / 2).value, 0.5f)
        val above = item(Section.SEARCH).getUnclippedBoundsInRoot().top - (logo.top + logo.bottom) / 2
        val below = (avatar.top + avatar.bottom) / 2 - item(Section.SETTINGS).getUnclippedBoundsInRoot().bottom
        assertEquals(above.value, below.value, 0.5f)
    }

    // The round mark fills the design's 36 dp logo box, the same size as the picture at the other end.
    @Test
    fun theLogoIsRoundAndAsBigAsThePicture() {
        show()

        compose
            .onNodeWithTag(RAIL_LOGO_TAG, useUnmergedTree = true)
            .assertHeightIsEqualTo(36.dp)
            .assertWidthIsEqualTo(36.dp)
        compose.onNodeWithTag(RAIL_AVATAR_TAG, useUnmergedTree = true).assertHeightIsEqualTo(36.dp)
    }

    @Test
    fun theLogoStaysPutAsTheRailOpens() {
        show()
        val closed = compose.onNodeWithTag(RAIL_LOGO_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

        press(Key.DirectionLeft)
        compose.onNodeWithText("Discover").assertExists()

        assertEquals(closed, compose.onNodeWithTag(RAIL_LOGO_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot())
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

    private val kemuri = AniListProfile(5412345, "Kemuri", "https://anilist.co/user/5412345", "https://example.invalid/a.png")

    private fun openOnProfile() {
        press(Key.DirectionLeft)
        press(Key.DirectionDown, times = 4)
        item(Section.PROFILE).assertIsFocused()
    }

    @Test
    fun signedInTheClosedRailShowsTheFirstLetterUntilThePictureLoads() {
        account = kemuri
        show()

        compose.onNodeWithTag(RAIL_AVATAR_ICON_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("K", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(RAIL_AVATAR_TAG, useUnmergedTree = true).assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
    }

    @Test
    fun signedInALoadedPictureHasNothingBehindIt() {
        account = kemuri
        val picture = AsyncImagePreviewHandler { ColorDrawable(Color.TRANSPARENT).asImage() }
        show { content ->
            CompositionLocalProvider(LocalInspectionMode provides true, LocalAsyncImagePreviewHandler provides picture) { content() }
        }

        compose.onNodeWithText("K", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun signedInTheOpenRailShowsTheAniListNameAndLogo() {
        account = kemuri
        show()

        openOnProfile()

        compose.onNodeWithText("Kemuri", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Profile").assertDoesNotExist()
        compose.onNodeWithTag(RAIL_ANILIST_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun signedOutTheOpenRailHasNoAniListLogo() {
        show()

        openOnProfile()

        compose.onNodeWithTag(RAIL_ANILIST_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun signingInAndOutUpdatesTheRailStraightAway() {
        show()

        account = kemuri
        compose.waitForIdle()
        compose.onNodeWithText("K", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(RAIL_AVATAR_ICON_TAG, useUnmergedTree = true).assertDoesNotExist()

        account = null
        compose.waitForIdle()
        compose.onNodeWithText("K", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(RAIL_AVATAR_ICON_TAG, useUnmergedTree = true).assertExists()
    }
}
