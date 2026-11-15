package ani.saikou.neo.tv.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ani.saikou.neo.tv.R

// Static files, since variable font settings need API 26. Neither has CJK, so Japanese titles fall
// back to the system font.
internal object NeoFonts {
    val bricolageGrotesque =
        FontFamily(
            Font(R.font.bricolage_grotesque_medium, FontWeight.Medium),
            Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
        )

    val atkinsonHyperlegibleNext =
        FontFamily(
            Font(R.font.atkinson_hyperlegible_next_regular, FontWeight.Normal),
            Font(R.font.atkinson_hyperlegible_next_medium, FontWeight.Medium),
            Font(R.font.atkinson_hyperlegible_next_bold, FontWeight.Bold),
        )
}

private fun display(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = NeoFonts.bricolageGrotesque,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

private fun text(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = NeoFonts.atkinsonHyperlegibleNext,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

/**
 * The type styles from the Saikou Neo design system, in sp on a 960 × 540 dp screen. Text styles are
 * 1 sp above the boards, on the boards' line heights, as Atkinson Hyperlegible Next has a smaller x-height than
 * the Inter they were sized for.
 */
@Immutable
data class NeoTypography(
    val display: TextStyle = display(44, 50, FontWeight.Bold, -0.75f),
    val headline: TextStyle = display(30, 38, FontWeight.Bold, -0.5f),
    val titleLarge: TextStyle = display(24, 32, FontWeight.Bold, -0.25f),
    val rowTitle: TextStyle = display(18, 24, FontWeight.Bold),
    val title: TextStyle = display(18, 24, FontWeight.Medium),
    val bodyLarge: TextStyle = text(16, 22, FontWeight.Normal),
    val body: TextStyle = text(15, 20, FontWeight.Normal),
    val labelLarge: TextStyle = text(17, 22, FontWeight.Medium),
    val label: TextStyle = text(15, 20, FontWeight.Medium),
    val caption: TextStyle = text(13, 18, FontWeight.Normal),
    val overline: TextStyle = text(13, 18, FontWeight.Medium, 0.6f),
    // Tabular figures so the player clock doesn't jitter.
    val timecode: TextStyle = text(14, 18, FontWeight.Medium).copy(fontFeatureSettings = "tnum"),
)
