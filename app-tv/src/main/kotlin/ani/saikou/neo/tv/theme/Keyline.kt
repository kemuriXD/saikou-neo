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
 * With an [itemSize], smaller items are centred where an item that size would be, such as a button beside cards.
 */
@OptIn(ExperimentalFoundationApi::class)
private class KeylineBringIntoViewSpec(
    private val keyline: Float,
    private val itemSize: Float?,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = if (itemSize == null) offset - keyline else offset + (size - itemSize) / 2 - keyline
}

/** Applies keyline scrolling to the scrollable containers in [content], such as a row's LazyRow. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeylineScrolling(
    keyline: Dp = NeoTheme.dimensions.gridMargin,
    itemSize: Dp? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val keylinePx = with(density) { keyline.toPx() }
    val itemSizePx = itemSize?.let { with(density) { it.toPx() } }
    val spec = remember(keylinePx, itemSizePx) { KeylineBringIntoViewSpec(keylinePx, itemSizePx) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}
