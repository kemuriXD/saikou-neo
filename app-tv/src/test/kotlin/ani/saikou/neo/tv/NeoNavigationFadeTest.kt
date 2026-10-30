package ani.saikou.neo.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.tv.home.HomeScreen
import ani.saikou.neo.tv.home.SPOTLIGHT_PLAY_TAG
import ani.saikou.neo.tv.home.loaded
import ani.saikou.neo.tv.theme.NeoDimensions
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.RAIL_LOGO_TAG
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The rail is part of Home, so it comes and goes with Home's crossfade. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NeoNavigationFadeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show() =
        compose.setContent {
            NeoTheme {
                NeoNavigation(
                    home = { openDetails, openSeeMore -> HomeScreen(loaded, onOpen = openDetails, onSeeMore = openSeeMore) },
                    seeMore = { _, _ -> },
                    details = { _, _, _ -> Box {} },
                )
            }
        }

    // Robolectric doesn't always redraw the window on its own, and then the capture is stale. A node can't be captured
    // while it's fading, so this captures the whole screen.
    private fun screen(): PixelMap {
        compose.runOnUiThread {
            compose.activity.window.decorView
                .invalidate()
        }
        compose.mainClock.advanceTimeByFrame()
        return compose.onRoot().captureToImage().toPixelMap()
    }

    /** The logo's brightest pixel on the [screen], which is solid, so it only changes when the rail itself fades. */
    private fun PixelMap.brightestInLogo(): Pair<Int, Int> {
        val logo = with(compose.density) { compose.onNodeWithTag(RAIL_LOGO_TAG).getBoundsInRoot().toRect() }
        val xs = logo.left.toInt() until logo.right.toInt()
        val ys = logo.top.toInt() until logo.bottom.toInt()
        return xs.flatMap { x -> ys.map { y -> x to y } }.maxBy { (x, y) -> this[x, y].luminance() }
    }

    @Test
    fun backFromDetailsFadesTheRailInWithHome() {
        show()
        compose.waitForIdle()
        val settled = screen()
        val (x, y) = settled.brightestInLogo()
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()

        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        // Home takes a couple of frames to come back, and its fade starts then.
        while (compose.onAllNodesWithTag(RAIL_LOGO_TAG).fetchSemanticsNodes().isEmpty()) compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(QUARTER_FADE)

        assertNotEquals(settled[x, y], screen()[x, y])
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun openingDetailsFadesTheRailOutWithHome() {
        show()
        compose.waitForIdle()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(SPOTLIGHT_PLAY_TAG).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(QUARTER_FADE)

        compose.onNodeWithTag(RAIL_LOGO_TAG).assertExists()
        compose.mainClock.autoAdvance = true
    }

    private companion object {
        val QUARTER_FADE = NeoDimensions().durationBackdropMillis / 4L
    }
}
