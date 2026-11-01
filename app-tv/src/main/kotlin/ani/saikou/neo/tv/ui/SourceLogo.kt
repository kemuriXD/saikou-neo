package ani.saikou.neo.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import ani.saikou.neo.tv.theme.NeoTheme
import coil3.compose.AsyncImage
import kotlin.math.abs

private val Inset = 4.dp
private const val DIMMED = 0.6f

/**
 * A source's tile (ADR 0028): its logo, [icon], as a file or PNG bytes, or else the first letter of [name] in pink on
 * [background]. The letter shows until the logo is drawn, then the logo fades in. A square logo fills the tile; any
 * other shape fits inside a 4 dp inset. On a row you can't pick, [dimmed] draws the logo at 60%. Decoration only: the
 * row already says the source's name.
 */
@Composable
internal fun SourceLogo(
    name: String,
    icon: Any?,
    size: Dp,
    radius: Dp,
    background: Color,
    modifier: Modifier = Modifier,
    letterSize: TextUnit = 18.sp,
    dimmed: Boolean = false,
) {
    val shape = RoundedCornerShape(radius)
    // The logo's own size once it's drawn, or null while it loads or when it fails.
    var drawn by remember(icon) { mutableStateOf<Size?>(null) }
    val shown by animateFloatAsState(
        targetValue =
            if (drawn == null) {
                0f
            } else if (dimmed) {
                DIMMED
            } else {
                1f
            },
        animationSpec = tween(NeoTheme.dimensions.durationFocusMillis),
        label = "logo",
    )
    Box(modifier.size(size).clip(shape).background(background, shape), contentAlignment = Alignment.Center) {
        if (icon == null || drawn == null) {
            Text(
                name.firstOrNull()?.uppercase().orEmpty(),
                style =
                    NeoTheme.typography.headline.copy(
                        fontSize = letterSize,
                        lineHeight = letterSize * 1.33f,
                        fontWeight = FontWeight.Bold,
                    ),
                color = NeoTheme.colors.pink,
            )
        }
        if (icon != null) {
            val square = drawn?.let { abs(it.width - it.height) < 1f } ?: true
            AsyncImage(
                model = icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { drawn = it.painter.intrinsicSize },
                onError = { drawn = null },
                modifier = Modifier.fillMaxSize().padding(if (square) 0.dp else Inset).alpha(shown),
            )
        }
    }
}
