package ani.saikou.neo.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ani.saikou.neo.tv.theme.NeoColors
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NeoButtonsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val colors = NeoColors()

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { NeoTheme { Box(Modifier.fillMaxSize().background(NeoTheme.colors.ink)) { content() } } }
    }

    /** Puts focus on the content once it's attached, as the screens do. */
    @Composable
    private fun Focused(content: @Composable (Modifier) -> Unit) {
        val focus = remember { FocusRequester() }
        content(Modifier.focusRequester(focus))
        LaunchedEffect(Unit) { focus.requestFocus() }
    }

    private fun pixels(tag: String): PixelMap = capture(compose.onNodeWithTag(tag))

    // Robolectric doesn't always redraw the window after setContent, and then the capture is the blank window.
    private fun capture(node: SemanticsNodeInteraction): PixelMap {
        compose.runOnUiThread {
            compose.activity.window.decorView
                .invalidate()
        }
        compose.waitForIdle()
        return node.captureToImage().toPixelMap()
    }

    private fun assertColor(
        expected: Color,
        actual: Color,
    ) = assertEquals(expected.toArgb().toString(16), expected.red, actual.red, 0.01f)

    private fun PixelMap.center() = this[width / 2, height / 2]

    @Test
    fun theMainActionIsA44TallTextButton() {
        show { NeoButton(text = "Episodes", onClick = {}, icon = Play, modifier = Modifier.testTag("main")) }

        compose.onNodeWithTag("main").assertHeightIsEqualTo(44.dp)
        compose.onNodeWithText("Episodes").assertExists()
    }

    @Test
    fun otherActionsAreRoundIconButtonsThatSayWhatTheyDo() {
        show { NeoIconButton(icon = Play, contentDescription = "Add to list", onClick = {}) }

        compose
            .onNodeWithContentDescription("Add to list")
            .assertWidthIsEqualTo(44.dp)
            .assertHeightIsEqualTo(44.dp)
    }

    @Test
    fun buttonsInARowSit16Apart() {
        show {
            NeoButtonRow {
                NeoButton(text = "Resume episode 7", onClick = {}, modifier = Modifier.testTag("first"))
                NeoIconButton(icon = Play, contentDescription = "Details", onClick = {}, modifier = Modifier.testTag("second"))
            }
        }

        val first = compose.onNodeWithTag("first").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("second").getUnclippedBoundsInRoot()
        assertEquals(16.dp, second.left - first.right)
    }

    @Test
    fun theFocusedButtonFillsWithPaperAndGrows() {
        show {
            NeoButtonRow(Modifier.padding(24.dp)) {
                Focused { focus -> NeoButton(text = "Episodes", onClick = {}, modifier = focus.testTag("focused")) }
                NeoButton(text = "Episodes", onClick = {}, modifier = Modifier.testTag("resting"))
            }
        }

        compose.onNodeWithTag("focused").assertIsFocused()
        // Sample beside the label, where only the fill shows. The resting one on its far side, clear of the glow.
        val focused = pixels("focused")
        assertEquals(colors.paper, focused[4, focused.height / 2])
        val resting = pixels("resting")
        assertColor(colors.fillTonal.compositeOver(colors.ink), resting[resting.width - 5, resting.height / 2])
        // Grown to 1.1, the focused one draws past its own edge, half way into the gap.
        val screen = capture(compose.onRoot())
        val density = compose.density.density
        val bounds = compose.onNodeWithTag("focused").getUnclippedBoundsInRoot()
        val y = ((bounds.top + bounds.bottom) / 2).value * density
        val pastEdge = (bounds.right + 2.dp).value * density
        assertEquals(colors.paper, screen[pastEdge.toInt(), y.toInt()])
        val restingRight = compose.onNodeWithTag("resting").getUnclippedBoundsInRoot().right
        assertColor(colors.ink, screen[((restingRight + 2.dp).value * density).toInt(), y.toInt()])
    }

    @Test
    fun dialogButtonsStackAtFullWidth() {
        show {
            NeoDialogButtons(Modifier.width(424.dp)) {
                NeoDialogButton(text = "Resume from 12:04", onClick = {}, icon = Play, modifier = Modifier.testTag("resume"))
                NeoDialogButton(text = "Start over", onClick = {}, modifier = Modifier.testTag("over"))
            }
        }

        compose.onNodeWithTag("resume").assertWidthIsEqualTo(424.dp).assertHeightIsEqualTo(48.dp)
        val resume = compose.onNodeWithTag("resume").getUnclippedBoundsInRoot()
        val over = compose.onNodeWithTag("over").getUnclippedBoundsInRoot()
        assertEquals(resume.left, over.left)
        assertEquals(10.dp, over.top - resume.bottom)
    }

    @Test
    fun dialogButtonsHave12dpCornersNotPillEnds() {
        show { NeoDialogButton(text = "Start over", onClick = {}, modifier = Modifier.width(424.dp).testTag("over")) }

        // 10 dp down the left edge is inside a 12 dp corner but outside a 24 dp pill end.
        val button = pixels("over")
        val density = compose.density.density
        val fill = colors.fillTonal.compositeOver(colors.ink)
        assertColor(fill, button.center())
        assertColor(fill, button[(2 * density).toInt(), (10 * density).toInt()])
    }

    @Test
    fun aDialogButtonCanSayMoreOnASecondLine() {
        show {
            NeoDialogButton(
                text = "Episode 9 · From AniList",
                supportingText = "Updated yesterday",
                onClick = {},
                modifier = Modifier.testTag("pick"),
            )
        }

        compose.onNodeWithText("Updated yesterday").assertExists()
        // 12 dp padding, 22 and 20 dp lines with 2 between, 12 dp padding.
        compose.onNodeWithTag("pick").assertHeightIsEqualTo((12 + 22 + 2 + 20 + 12).dp)
    }

    @Test
    fun theCurrentTabIsASoftPillAndTheOthersAreText() {
        show {
            NeoButtonRow {
                NeoTab(text = "Overview", selected = true, onClick = {}, modifier = Modifier.testTag("current"))
                NeoTab(text = "Related", selected = false, onClick = {}, modifier = Modifier.testTag("other"))
            }
        }

        compose.onNodeWithTag("current").assertIsSelected().assertHeightIsEqualTo(40.dp)
        compose.onNodeWithTag("other").assertIsNotSelected()
        // The current tab has a fill of its own; the other is just its label on the page.
        val screen = capture(compose.onRoot())

        fun pixelAt(
            x: Dp,
            y: Dp,
        ) = screen[(x.value * compose.density.density).toInt(), (y.value * compose.density.density).toInt()]
        val current = compose.onNodeWithTag("current").getUnclippedBoundsInRoot()
        val other = compose.onNodeWithTag("other").getUnclippedBoundsInRoot()
        val middle = (other.top + other.bottom) / 2
        // Its own soft glow shows through the fill, so it's a little brighter than the fill, and far from paper.
        val fill = colors.fillTonal.compositeOver(colors.ink).red
        val pill = pixelAt(current.left + 4.dp, middle).red
        assertTrue("$pill", pill > fill && pill < 0.5f)
        assertColor(colors.ink, pixelAt(other.left + 4.dp, middle))
    }

    @Test
    fun theFocusedTabFillsWithPaperEvenWhenItIsCurrent() {
        show { Focused { focus -> NeoTab(text = "Overview", selected = true, onClick = {}, modifier = focus.testTag("current")) } }

        compose.onNodeWithTag("current").assertIsFocused()
        val tab = pixels("current")
        assertEquals(colors.paper, tab[4, tab.height / 2])
    }

    private companion object {
        val Play =
            ImageVector
                .Builder("Play", 12.dp, 12.dp, 12f, 12f)
                .apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(2f, 1f)
                        lineTo(11f, 6f)
                        lineTo(2f, 11f)
                        close()
                    }
                }.build()
    }
}
