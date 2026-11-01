package ani.saikou.neo.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import ani.saikou.neo.tv.theme.NeoTheme
import coil3.compose.AsyncImage

private const val DIMMED = 0.6f

/**
 * A source's logo (ADR 0028), [icon], as a file or PNG bytes, or else the first letter of [name] in pink on a tile of
 * [background]. The letter tile shows until the logo is drawn, then the logo fades in on its own, with no tile behind
 * it: logos are PNGs made to stand alone. The logo fits the space, centred and never cropped. On a row you can't pick,
 * [dimmed] draws the logo at 60%. Decoration only: the row already says the source's name.
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
    // False while the logo loads and when it fails.
    var drawn by remember(icon) { mutableStateOf(false) }
    val shown by animateFloatAsState(
        targetValue =
            if (!drawn) {
                0f
            } else if (dimmed) {
                DIMMED
            } else {
                1f
            },
        animationSpec = tween(NeoTheme.dimensions.durationFocusMillis),
        label = "logo",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (icon == null || !drawn) {
            LetterTile(name, shape, background, letterSize)
        }
        if (icon != null) {
            AsyncImage(
                model = icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { drawn = true },
                onError = { drawn = false },
                modifier = Modifier.fillMaxSize().alpha(shown),
            )
        }
    }
}

@Composable
private fun LetterTile(
    name: String,
    shape: Shape,
    background: Color,
    letterSize: TextUnit,
) {
    Box(Modifier.fillMaxSize().clip(shape).background(background, shape), contentAlignment = Alignment.Center) {
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
}
