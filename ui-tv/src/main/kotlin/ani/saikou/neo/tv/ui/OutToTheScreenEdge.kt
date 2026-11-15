package ani.saikou.neo.tv.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * Widens a row placed at the grid [margin] out to the screen's left edge. A row clips at its own edges, so the first
 * item's growth and glow on focus would otherwise be cut off. The row pads its content by the margin instead, at both
 * ends.
 */
internal fun Modifier.outToTheScreenEdge(margin: Dp) =
    layout { measurable, constraints ->
        val extra = margin.roundToPx()
        val width = constraints.maxWidth + extra
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
    }
