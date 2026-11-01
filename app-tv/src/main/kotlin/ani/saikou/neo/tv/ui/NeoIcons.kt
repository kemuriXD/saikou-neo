package ani.saikou.neo.tv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.addPathNodes
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

    // The rail's sections.

    val Search: ImageVector by lazy {
        icon("Search") {
            stroke(2f) {
                circle(11f, 11f, 6f)
                moveTo(16f, 16f)
                lineTo(20f, 20f)
            }
        }
    }

    val Home: ImageVector by lazy {
        icon("Home") {
            stroke(2f) {
                moveTo(4f, 11f)
                lineTo(12f, 4f)
                lineTo(20f, 11f)
                lineTo(20f, 20f)
                lineTo(14f, 20f)
                lineTo(14f, 14f)
                lineTo(10f, 14f)
                lineTo(10f, 20f)
                lineTo(4f, 20f)
                close()
            }
        }
    }

    val Discover: ImageVector by lazy {
        icon("Discover") {
            stroke(2f) {
                circle(12f, 12f, 9f)
                moveTo(15.5f, 8.5f)
                lineTo(13.5f, 13.5f)
                lineTo(8.5f, 15.5f)
                lineTo(10.5f, 10.5f)
                close()
            }
        }
    }

    val Schedule: ImageVector by lazy {
        icon("Schedule") {
            stroke(2f) {
                moveTo(6f, 5f)
                lineTo(18f, 5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = 2f)
                lineTo(20f, 18f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = 2f)
                lineTo(6f, 20f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = -2f)
                lineTo(4f, 7f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = -2f)
                close()
                moveTo(4f, 10f)
                lineTo(20f, 10f)
                moveTo(9f, 3f)
                lineTo(9f, 7f)
                moveTo(15f, 3f)
                lineTo(15f, 7f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            stroke(2f) {
                moveTo(4f, 7f)
                lineTo(13f, 7f)
                moveTo(19f, 7f)
                lineTo(20f, 7f)
                circle(16f, 7f, 2.5f)
                moveTo(4f, 17f)
                lineTo(5f, 17f)
                moveTo(11f, 17f)
                lineTo(20f, 17f)
                circle(8f, 17f, 2.5f)
            }
        }
    }

    /** A head and shoulders, for the profile until signing in brings the account's picture. */
    val Profile: ImageVector by lazy {
        icon("Profile") {
            path(fill = SolidColor(Color.Black)) {
                circle(12f, 8.5f, 4f)
                close()
                moveTo(4.5f, 20f)
                curveTo(4.5f, 15.8f, 8f, 13.8f, 12f, 13.8f)
                curveTo(16f, 13.8f, 19.5f, 15.8f, 19.5f, 20f)
                close()
            }
        }
    }

    /** Wi-Fi waves struck through, for Home with no internet. */
    val WifiOff: ImageVector by lazy {
        icon("WifiOff") {
            stroke(2f) {
                moveTo(2f, 8.5f)
                curveTo(7.5f, 3.8f, 16.5f, 3.8f, 22f, 8.5f)
                moveTo(5.5f, 12f)
                curveTo(9.2f, 9f, 14.8f, 9f, 18.5f, 12f)
                moveTo(9f, 15.5f)
                curveTo(10.8f, 14.2f, 13.2f, 14.2f, 15f, 15.5f)
                moveTo(12f, 19f)
                lineTo(12f, 19.5f)
                moveTo(3f, 3f)
                lineTo(21f, 21f)
            }
        }
    }

    /** A jigsaw piece, for Sources with none added. */
    val Puzzle: ImageVector by lazy {
        icon("Puzzle") {
            svgStroke(
                1.8f,
                "M14 4h4a2 2 0 0 1 2 2v4h-1.5a2.5 2.5 0 0 0 0 5H20v4a2 2 0 0 1-2 2h-4v-1.5a2.5 2.5 0 0 0-5 0V21H5" +
                    "a2 2 0 0 1-2-2v-4h1.5a2.5 2.5 0 0 0 0-5H3V6a2 2 0 0 1 2-2h4v1.5a2.5 2.5 0 0 0 5 0z",
            )
        }
    }

    /** A plug, for an episode with no sources to play it. */
    val Plug: ImageVector by lazy {
        icon("Plug") {
            svgStroke(2f, "M9 3v4M15 3v4M6 7h12v4c0 3.5-2.5 6-6 6s-6-2.5-6-6zM12 17v4")
        }
    }

    /** A triangle with an exclamation mark, always next to words that say what's wrong. */
    val Warning: ImageVector by lazy {
        icon("Warning") {
            svgStroke(2f, "M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0zM12 9v4M12 17h.01")
        }
    }

    /** A tick, for a ticked box. */
    val Tick: ImageVector by lazy {
        icon("Tick") {
            svgStroke(3f, "M5 12l5 5L20 7")
        }
    }

    /** A bin, for Remove. */
    val Remove: ImageVector by lazy {
        icon("Remove") {
            svgStroke(2f, "M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2M6 6l1 14a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-14")
        }
    }

    /** Two bars, for Pause. */
    val Pause: ImageVector by lazy {
        icon("Pause") {
            addPath(
                pathData =
                    addPathNodes(
                        "M7 5h2a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z" +
                            "M15 5h2a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1h-2a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z",
                    ),
                fill = SolidColor(Color.Black),
            )
        }
    }

    /** Two stills with lines beside them, for Episodes. */
    val Episodes: ImageVector by lazy {
        icon("Episodes") {
            svgStroke(
                2f,
                "M4.5 5h4A1.5 1.5 0 0 1 10 6.5v3A1.5 1.5 0 0 1 8.5 11h-4A1.5 1.5 0 0 1 3 9.5v-3A1.5 1.5 0 0 1 4.5 5zM13 7h8" +
                    "M4.5 13h4a1.5 1.5 0 0 1 1.5 1.5v3A1.5 1.5 0 0 1 8.5 19h-4A1.5 1.5 0 0 1 3 17.5v-3A1.5 1.5 0 0 1 4.5 13zM13 15h8",
            )
        }
    }

    /** A screen with lines of text, for Audio and subtitles. */
    val Subtitles: ImageVector by lazy {
        icon("Subtitles") {
            svgStroke(2f, "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM7 11h3M13 11h4M7 15h7")
        }
    }
}

/** A circle around ([x], [y]), drawn as two half arcs. */
private fun PathBuilder.circle(
    x: Float,
    y: Float,
    radius: Float,
) {
    moveTo(x - radius, y)
    arcToRelative(radius, radius, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = radius * 2, dy1 = 0f)
    arcToRelative(radius, radius, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -radius * 2, dy1 = 0f)
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

/** A stroked path in SVG path syntax, as the design's boards give it. */
private fun ImageVector.Builder.svgStroke(
    width: Float,
    pathData: String,
) = addPath(
    pathData = addPathNodes(pathData),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = width,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
)
