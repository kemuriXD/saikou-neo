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
    // The TV safe area: 5% of a 960 × 540 dp screen.
    val safeX: Dp = 48.dp,
    val safeY: Dp = 27.dp,
    val radiusXs: Dp = 3.dp,
    val radiusSm: Dp = 8.dp,
    val radiusMd: Dp = 10.dp,
    val radiusLg: Dp = 12.dp,
    val radiusXl: Dp = 16.dp,
    val focusScale: Float = 1.05f,
    val focusScaleKey: Float = 1.08f,
    val focusRing: Dp = 3.dp,
    val glowFocus: Dp = 18.dp,
    val durationFocusMillis: Int = 150,
    val durationIndicatorMillis: Int = 200,
    val durationBackdropMillis: Int = 400,
    val easeStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
)
