package ani.saikou.neo.tv.ui

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A soft glow around a rounded box, like the design's outer shadow. TV Material's glow also shows through a transparent
 * or see-through box, which makes it look filled. A [radius] of 0, as in the light look, draws nothing.
 */
internal fun Modifier.outerGlow(
    color: Color,
    radius: Dp,
    corner: Dp,
) = drawWithCache {
    val paint =
        android.graphics.Paint().apply {
            this.color = color.toArgb()
            if (radius > 0.dp) maskFilter = BlurMaskFilter(radius.toPx(), BlurMaskFilter.Blur.OUTER)
        }
    onDrawBehind {
        if (color.alpha > 0f && radius > 0.dp) {
            drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, corner.toPx(), corner.toPx(), paint) }
        }
    }
}
