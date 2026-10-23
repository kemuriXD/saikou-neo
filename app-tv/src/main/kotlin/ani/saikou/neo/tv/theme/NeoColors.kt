package ani.saikou.neo.tv.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The colour tokens from the Saikou Neo design system. TV has one theme, on ink. */
@Immutable
data class NeoColors(
    val ink: Color = Color(0xFF11121C),
    val paper: Color = Color(0xFFF6F5F2),
    val pink: Color = Color(0xFFFF4F91),
    val pinkDeep: Color = Color(0xFFB3245C),
    val surface: Color = Color(0xFF1A1B27),
    val surfaceScrim: Color = Color(0xD91A1B27),
    val surfaceRaised: Color = Color(0xFF242535),
    val line: Color = Color(0xFF33344A),
    val textMuted: Color = paper.copy(alpha = 0.72f),
    val textFaint: Color = paper.copy(alpha = 0.52f),
    val fillTonal: Color = paper.copy(alpha = 0.2f),
    val outline: Color = paper.copy(alpha = 0.52f),
    val onFocus: Color = ink,
    val onFocusMuted: Color = ink.copy(alpha = 0.72f),
    val onFocusTonal: Color = ink.copy(alpha = 0.12f),
    val focus: Color = paper,
    val warning: Color = Color(0xFFFFC15E),
    val scrimPlayer: Color = Color(0xFF05060A),
)
