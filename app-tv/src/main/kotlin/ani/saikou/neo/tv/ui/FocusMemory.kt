package ani.saikou.neo.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Which item on a screen last had focus, named by a key such as "episode-3" rather than by where it sits. It's saved
 * with the screen, so when the screen comes back, after Back or after Android recreated the app, focus returns to that
 * item. Give a screen one with [RememberFocus], and mark its items with [remembersFocus].
 */
@Stable
class FocusMemory internal constructor(
    internal var focused: String?,
    restoring: String?,
) {
    /** The item to take focus back once it shows, until it does or [restoreOr] gives up on it. */
    internal var restoring by mutableStateOf(restoring)

    /**
     * Gives the remembered item a frame to show and take focus back. Without one, or if it doesn't show, focus goes
     * where the screen starts it, by [first].
     */
    suspend fun restoreOr(first: () -> Unit) {
        if (restoring != null) {
            withFrameNanos {}
            val shown = restoring == null
            restoring = null
            if (shown) return
        }
        first()
    }
}

// Only the key is saved. A fresh screen has nothing to restore; one brought back restores what it saved.
private val FocusMemorySaver = Saver<FocusMemory, String>(save = { it.focused }, restore = { FocusMemory(it, it) })

// The screen's memory, for remembersFocus to keep track in. Without one, items aren't remembered.
private val LocalFocusMemory = compositionLocalOf<FocusMemory?> { null }

/** Gives a screen's [content] a [FocusMemory], saved with the screen, for its items to mark with [remembersFocus]. */
@Composable
fun RememberFocus(content: @Composable (FocusMemory) -> Unit) {
    val memory = rememberSaveable(saver = FocusMemorySaver) { FocusMemory(null, null) }
    CompositionLocalProvider(LocalFocusMemory provides memory) { content(memory) }
}

/** Remembers when this item has focus, as [key], and takes focus back when the screen comes back to it. */
@Composable
fun Modifier.remembersFocus(key: String): Modifier {
    val memory = LocalFocusMemory.current ?: return this
    val requester = remember { FocusRequester() }
    if (memory.restoring == key) {
        LaunchedEffect(Unit) {
            requester.requestFocus()
            memory.restoring = null
        }
    }
    return focusRequester(requester).onFocusChanged { if (it.isFocused) memory.focused = key }
}
