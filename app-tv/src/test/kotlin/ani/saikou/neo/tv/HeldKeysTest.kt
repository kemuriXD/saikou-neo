package ani.saikou.neo.tv

import android.view.KeyEvent
import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HeldKeysTest {
    private val frames = mutableListOf<() -> Unit>()

    // The system's animation speed, such as 0.5 with animations sped up in the developer options.
    private val keys = HeldKeys(systemScale = { 0.5f }, afterNextFrame = { frames += it })

    private fun drawFrame() {
        val drawn = frames.toList()
        frames.clear()
        drawn.forEach { it() }
    }

    private fun down(
        repeat: Int,
        code: Int = KEYCODE_DPAD_DOWN,
    ) = keys.drop(KeyEvent(0L, 0L, ACTION_DOWN, code, repeat))

    private fun up(code: Int = KEYCODE_DPAD_DOWN) = keys.drop(KeyEvent(0L, 0L, ACTION_UP, code, 0))

    @Test
    fun aPressAnimatesAtTheSystemSpeed() {
        down(0)

        assertEquals(0.5f, keys.scaleFactor)
    }

    @Test
    fun holdingADirectionMakesAnimationsInstant() {
        down(0)
        down(1)

        assertEquals(0f, keys.scaleFactor)
    }

    @Test
    fun lettingGoAnimatesAgain() {
        down(0)
        down(1)
        up()

        assertEquals(0.5f, keys.scaleFactor)
    }

    // Focus can leave the app mid-hold, so its key-up never comes. The next press is still a press.
    @Test
    fun aNewPressEndsAHoldWhoseKeyUpWasMissed() {
        down(1)
        down(0, KEYCODE_DPAD_RIGHT)

        assertEquals(0.5f, keys.scaleFactor)
    }

    @Test
    fun repeatsBeforeTheNextFrameAreDropped() {
        down(0)

        assertFalse(down(1))
        assertTrue(down(2))
        assertTrue(down(3))
        drawFrame()
        assertFalse(down(4))
    }

    @Test
    fun pressesAreNeverDropped() {
        down(1)

        assertFalse(down(0, KEYCODE_DPAD_RIGHT))
        assertFalse(up(KEYCODE_DPAD_RIGHT))
    }

    // Holding Select or Back is left to the screen, which may want every repeat.
    @Test
    fun otherKeysAreLeftAlone() {
        assertFalse(down(1, KEYCODE_DPAD_CENTER))
        assertFalse(down(2, KEYCODE_DPAD_CENTER))
        assertEquals(0.5f, keys.scaleFactor)
    }
}
