package ani.saikou.neo.tv

import android.view.KeyEvent
import androidx.compose.ui.MotionDurationScale

private val directions =
    setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)

/**
 * Keeps lists moving while a direction key is held. As the window's animation speed, it makes animations instant from
 * the first key repeat until the key is let go, so a list jumps to the focused item instead of starting a smooth
 * scroll the next repeat cuts short. A repeat that comes before the next frame is dropped, so a slow screen doesn't
 * build up moves that carry on after the key is let go. A single press animates at the [systemScale].
 */
class HeldKeys(
    private val systemScale: () -> Float,
    private val afterNextFrame: (() -> Unit) -> Unit,
) : MotionDurationScale {
    private var held = false
    private var drawn = true

    override val scaleFactor: Float get() = if (held) 0f else systemScale()

    /** Notes [event], and whether to drop it. */
    fun drop(event: KeyEvent): Boolean {
        if (event.keyCode !in directions) return false
        when {
            event.action != KeyEvent.ACTION_DOWN || event.repeatCount == 0 -> {
                held = false
            }

            !drawn -> {
                return true
            }

            else -> {
                held = true
                drawn = false
                afterNextFrame { drawn = true }
            }
        }
        return false
    }
}
