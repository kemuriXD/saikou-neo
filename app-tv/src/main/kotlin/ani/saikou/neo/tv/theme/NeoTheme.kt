package ani.saikou.neo.tv.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

private val LocalNeoColors = staticCompositionLocalOf { NeoColors() }
private val LocalNeoTypography = staticCompositionLocalOf { NeoTypography() }
private val LocalNeoDimensions = staticCompositionLocalOf { NeoDimensions() }

/**
 * Applies the Saikou Neo design system, and maps it onto TV Material so its components match. The [light] look, for
 * slow TVs, has no glow around what's focused, and swaps art and screens without fading them.
 */
@Composable
fun NeoTheme(
    light: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = NeoColors()
    val typography = NeoTypography()
    val dimensions = if (light) NeoDimensions(glowFocus = 0.dp, durationBackdropMillis = 0) else NeoDimensions()
    CompositionLocalProvider(
        LocalNeoColors provides colors,
        LocalNeoTypography provides typography,
        LocalNeoDimensions provides dimensions,
    ) {
        MaterialTheme(
            colorScheme =
                darkColorScheme(
                    primary = colors.pink,
                    onPrimary = colors.ink,
                    background = colors.ink,
                    onBackground = colors.paper,
                    surface = colors.ink,
                    onSurface = colors.paper,
                    surfaceVariant = colors.surface,
                    onSurfaceVariant = colors.textMuted,
                    // TV Material fills focused controls with the inverse surface.
                    inverseSurface = colors.focus,
                    inverseOnSurface = colors.onFocus,
                    border = colors.focus,
                    borderVariant = colors.line,
                ),
            typography =
                Typography(
                    displaySmall = typography.display,
                    headlineMedium = typography.headline,
                    headlineSmall = typography.titleLarge,
                    titleMedium = typography.title,
                    bodyLarge = typography.bodyLarge,
                    bodyMedium = typography.body,
                    labelLarge = typography.label,
                    bodySmall = typography.caption,
                ),
            content = content,
        )
    }
}

/** Reads the current Saikou Neo tokens. */
object NeoTheme {
    val colors: NeoColors
        @Composable @ReadOnlyComposable
        get() = LocalNeoColors.current

    val typography: NeoTypography
        @Composable @ReadOnlyComposable
        get() = LocalNeoTypography.current

    val dimensions: NeoDimensions
        @Composable @ReadOnlyComposable
        get() = LocalNeoDimensions.current
}
