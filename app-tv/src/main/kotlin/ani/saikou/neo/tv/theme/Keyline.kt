package ani.saikou.neo.tv.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Scrolls so the focused item starts at [keyline], the way TV rows browse:
 * each press moves the row by one item, and focus always rests in the same place.
 * Near the end the list can't scroll further, so focus moves across the last items.
 */
@OptIn(ExperimentalFoundationApi::class)
private class KeylineBringIntoViewSpec(
    private val keyline: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = offset - keyline
}

/** Applies keyline scrolling to the scrollable containers in [content], such as a row's LazyRow. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeylineScrolling(
    keyline: Dp = NeoTheme.dimensions.safeX,
    content: @Composable () -> Unit,
) {
    val keylinePx = with(LocalDensity.current) { keyline.toPx() }
    val spec = remember(keylinePx) { KeylineBringIntoViewSpec(keylinePx) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}
