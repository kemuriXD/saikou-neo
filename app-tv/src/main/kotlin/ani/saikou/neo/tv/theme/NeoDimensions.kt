package ani.saikou.neo.tv.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing, corners, focus and motion from the Saikou Neo design system. */
@Immutable
data class NeoDimensions(
    val space1: Dp = 4.dp,
    val space2: Dp = 8.dp,
    val space3: Dp = 12.dp,
    val space4: Dp = 16.dp,
    val space6: Dp = 24.dp,
    val space8: Dp = 32.dp,
    // The TV safe area: 5% of a 960 × 540 dp screen. The top nav bar sits on it.
    val safeX: Dp = 48.dp,
    val safeY: Dp = 27.dp,
    // Android TV's 12-column grid. Rows, headings and the focus keyline start at the grid margin.
    val gridMargin: Dp = 58.dp,
    val gridColumn: Dp = 52.dp,
    val gridGutter: Dp = 20.dp,
    // Wide cards span 3 columns (16:9), posters 2 (2:3).
    val cardWideWidth: Dp = 196.dp,
    val cardWideHeight: Dp = 110.dp,
    val cardPosterWidth: Dp = 124.dp,
    val cardPosterHeight: Dp = 186.dp,
    val radiusXs: Dp = 3.dp,
    val radiusSm: Dp = 8.dp,
    val radiusMd: Dp = 10.dp,
    val radiusLg: Dp = 12.dp,
    val radiusXl: Dp = 16.dp,
    val focusScale: Float = 1.05f,
    val focusScaleButton: Float = 1.1f,
    val focusScaleKey: Float = 1.08f,
    val focusRing: Dp = 3.dp,
    val glowFocus: Dp = 18.dp,
    val durationFocusMillis: Int = 150,
    val durationIndicatorMillis: Int = 200,
    val durationBackdropMillis: Int = 400,
    val easeStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
)
