package ani.saikou.neo.tv

import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ani.saikou.neo.tv.theme.KeylineScrolling
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.view.KeyEvent as NativeKeyEvent

/** Lists jump to the focused item while a key is held, and animate for a single press. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class HeldListsTest {
    // Every frame counts as drawn at once: dropping repeats is HeldKeysTest's job.
    private val keys = HeldKeys(systemScale = { 1f }, afterNextFrame = { it() })

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(effectContext = keys)

    /** Thirty rows, each taller than a fifth of the screen, with the first focused. */
    private fun show(keyline: Boolean = false) {
        compose.setContent {
            NeoTheme {
                if (keyline) KeylineScrolling { Rows() } else Rows()
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    @Composable
    private fun Rows() {
        val first = remember { FocusRequester() }
        LazyColumn(Modifier.fillMaxSize()) {
            items(30) { index ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("row$index")
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .focusable(),
                )
            }
        }
        LaunchedEffect(Unit) { first.requestFocus() }
    }

    /** Sends a key the way MainActivity does: through [keys] first. */
    private fun send(
        action: Int,
        repeat: Int = 0,
    ) {
        val event = NativeKeyEvent(0L, 0L, action, KEYCODE_DPAD_DOWN, repeat)
        if (!keys.drop(event)) compose.onRoot().performKeyPress(KeyEvent(event))
    }

    private fun settle() = compose.mainClock.advanceTimeBy(2_000)

    private fun top(index: Int): Dp =
        compose
            .onNodeWithTag("row$index")
            .getUnclippedBoundsInRoot()
            .top

    /** Holds Down for [repeats] key repeats, with a frame after each. */
    private fun hold(repeats: Int) {
        send(ACTION_DOWN)
        settle()
        for (repeat in 1..repeats) {
            send(ACTION_DOWN, repeat)
            compose.mainClock.advanceTimeByFrame()
        }
    }

    // Each repeat would otherwise start a smooth scroll that the next one cuts short.
    @Test
    fun heldDownJumpsToTheFocusedRow() {
        show()
        hold(repeats = 8)
        val afterOneFrame = top(9)
        settle()

        compose.onNodeWithTag("row9").assertIsFocused()
        assertEquals(top(9), afterOneFrame)
    }

    @Test
    fun keylineListsJumpToo() {
        show(keyline = true)
        hold(repeats = 8)
        val afterOneFrame = top(9)
        settle()

        compose.onNodeWithTag("row9").assertIsFocused()
        assertEquals(top(9), afterOneFrame)
    }

    @Test
    fun aPressAfterLettingGoAnimates() {
        show(keyline = true)
        hold(repeats = 8)
        send(ACTION_UP)
        settle()

        send(ACTION_DOWN)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val early = top(10)
        settle()

        compose.onNodeWithTag("row10").assertIsFocused()
        assertNotEquals(top(10), early)
    }
}
