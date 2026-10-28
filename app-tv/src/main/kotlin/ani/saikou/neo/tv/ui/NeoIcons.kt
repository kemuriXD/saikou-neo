package ani.saikou.neo.tv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The design's icons. Buttons tint them with their content colour. */
object NeoIcons {
    val Play: ImageVector by lazy {
        icon("Play", viewport = 12f) {
            path(fill = SolidColor(Color.Black)) {
                moveTo(2f, 1f)
                lineTo(11f, 6f)
                lineTo(2f, 11f)
                close()
            }
        }
    }

    val Add: ImageVector by lazy {
        icon("Add") {
            stroke(2.4f) {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }
    }

    val Arrow: ImageVector by lazy {
        icon("Arrow") {
            stroke(2.2f) {
                moveTo(5f, 12f)
                lineTo(19f, 12f)
                moveTo(13f, 6f)
                lineTo(19f, 12f)
                lineTo(13f, 18f)
            }
        }
    }

    val Star: ImageVector by lazy {
        icon("Star") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2.5f)
                lineTo(14.9f, 8.6f)
                lineTo(21.5f, 9.4f)
                lineTo(16.6f, 13.9f)
                lineTo(17.9f, 20.5f)
                lineTo(12f, 17.2f)
                lineTo(6.1f, 20.5f)
                lineTo(7.4f, 13.9f)
                lineTo(2.5f, 9.4f)
                lineTo(9.1f, 8.6f)
                close()
            }
        }
    }

    val Chevron: ImageVector by lazy {
        icon("Chevron") {
            stroke(2.4f) {
                moveTo(9f, 6f)
                lineTo(15f, 12f)
                lineTo(9f, 18f)
            }
        }
    }

    val Info: ImageVector by lazy {
        icon("Info") {
            stroke(2f) {
                moveTo(3f, 12f)
                arcToRelative(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 18f, dy1 = 0f)
                arcToRelative(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -18f, dy1 = 0f)
                moveTo(12f, 11f)
                lineTo(12f, 16.5f)
                moveTo(12f, 7.5f)
                lineTo(12f, 8f)
            }
        }
    }
}

private fun icon(
    name: String,
    viewport: Float = 24f,
    paths: ImageVector.Builder.() -> Unit,
) = ImageVector.Builder(name, viewport.dp, viewport.dp, viewport, viewport).apply(paths).build()

/** Round-ended lines, as the design draws its outline icons. */
private fun ImageVector.Builder.stroke(
    width: Float,
    pathBuilder: PathBuilder.() -> Unit,
) = path(
    stroke = SolidColor(Color.Black),
    strokeLineWidth = width,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = pathBuilder,
)
