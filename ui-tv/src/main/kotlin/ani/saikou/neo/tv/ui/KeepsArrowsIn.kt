package ani.saikou.neo.tv.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties

/**
 * A focus group the remote's arrows can't leave. A screen that opens or comes back over it can still take focus, which
 * it couldn't if every exit were cancelled (#357).
 */
fun Modifier.keepsArrowsIn(): Modifier =
    focusProperties { onExit = { if (requestedFocusDirection != FocusDirection.Enter) cancelFocusChange() } }.focusGroup()
