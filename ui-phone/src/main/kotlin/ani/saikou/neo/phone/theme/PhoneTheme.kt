package ani.saikou.neo.phone.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ani.saikou.neo.phone.R

/** The design's colour tokens (Foundations), the same as the TV's. The phone has one theme, on ink. */
internal object PhoneColors {
    val ink = Color(0xFF11121C)
    val paper = Color(0xFFF6F5F2)
    val pink = Color(0xFFFF4F91)
    val surface = Color(0xFF1A1B27)
    val surfaceRaised = Color(0xFF242535)
    val line = Color(0xFF33344A)
    val textMuted = paper.copy(alpha = 0.72f)
    val fillTonal = paper.copy(alpha = 0.2f)
    val outline = paper.copy(alpha = 0.52f)
    val error = Color(0xFFFF8A80)
}

// Static font files, since variable font settings need API 26. The TV uses the same files from brand/res.
private val bricolageGrotesque =
    FontFamily(
        Font(R.font.bricolage_grotesque_medium, FontWeight.Medium),
        Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
    )

private val atkinsonHyperlegibleNext =
    FontFamily(
        Font(R.font.atkinson_hyperlegible_next_regular, FontWeight.Normal),
        Font(R.font.atkinson_hyperlegible_next_medium, FontWeight.Medium),
        Font(R.font.atkinson_hyperlegible_next_bold, FontWeight.Bold),
    )

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    tracking: Float = 0f,
) = TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp, letterSpacing = tracking.sp)

/** Material's type roles in our fonts (the design's Phone text styles): Bricolage for display to title large. */
private val phoneTypography =
    Typography(
        displaySmall = style(bricolageGrotesque, FontWeight.Bold, 36, 44),
        headlineMedium = style(bricolageGrotesque, FontWeight.Bold, 28, 36),
        headlineSmall = style(bricolageGrotesque, FontWeight.Bold, 24, 32),
        titleLarge = style(bricolageGrotesque, FontWeight.Bold, 22, 28),
        titleMedium = style(atkinsonHyperlegibleNext, FontWeight.Bold, 16, 24, 0.15f),
        titleSmall = style(atkinsonHyperlegibleNext, FontWeight.Bold, 14, 20, 0.1f),
        bodyLarge = style(atkinsonHyperlegibleNext, FontWeight.Normal, 16, 24, 0.5f),
        bodyMedium = style(atkinsonHyperlegibleNext, FontWeight.Normal, 14, 20, 0.25f),
        bodySmall = style(atkinsonHyperlegibleNext, FontWeight.Normal, 12, 16, 0.4f),
        labelLarge = style(atkinsonHyperlegibleNext, FontWeight.Bold, 14, 20, 0.1f),
        labelMedium = style(atkinsonHyperlegibleNext, FontWeight.Medium, 12, 16, 0.5f),
    )

/**
 * Material 3 with every colour role bound to our tokens, as the design's kit frames are: surface is ink, primary is
 * paper on ink, tertiary is pink. No dynamic colour.
 */
private val phoneColorScheme =
    darkColorScheme(
        primary = PhoneColors.paper,
        onPrimary = PhoneColors.ink,
        secondaryContainer = PhoneColors.fillTonal,
        onSecondaryContainer = PhoneColors.paper,
        tertiary = PhoneColors.pink,
        onTertiary = PhoneColors.ink,
        background = PhoneColors.ink,
        onBackground = PhoneColors.paper,
        surface = PhoneColors.ink,
        onSurface = PhoneColors.paper,
        onSurfaceVariant = PhoneColors.textMuted,
        surfaceContainerLow = PhoneColors.surface,
        surfaceContainer = PhoneColors.surface,
        surfaceContainerHigh = PhoneColors.surfaceRaised,
        surfaceContainerHighest = PhoneColors.surfaceRaised,
        outline = PhoneColors.outline,
        outlineVariant = PhoneColors.line,
        error = PhoneColors.error,
    )

@Composable
internal fun PhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = phoneColorScheme, typography = phoneTypography, content = content)
}
