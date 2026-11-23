package ani.saikou.neo.tv.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ani.saikou.neo.tv.BuildConfig

/** The colour tokens from the Saikou Neo design system. TV has one theme, on ink. */
@Immutable
data class NeoColors(
    val ink: Color = Color(0xFF11121C),
    val paper: Color = Color(0xFFF6F5F2),
    /** The accent: the pinks of the original Saikou logo (ADR 0057), green in alpha and beta. See [Accent]. */
    val pink: Color = Accent.current.pink,
    /** Pink text on surfaceRaised, or on a light tint, where [pink] is too dark to read. */
    val pinkLight: Color = Accent.current.pinkLight,
    val pinkDeep: Color = Accent.current.pinkDeep,
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
    /** A step that's done, such as the player's list while it starts. */
    val done: Color = Color(0xFF7BD389),
    val scrimPlayer: Color = Color(0xFF05060A),
)

/**
 * The accent colours. Stable uses the Saikou logo's pinks. Alpha and beta have a green icon, so they use greens. Each
 * green has the luminance of its pink, so every pairing keeps stable's contrast. The light green is brighter, since it
 * only sits on dark surfaces.
 */
@Immutable
data class Accent(
    val pink: Color,
    val pinkLight: Color,
    val pinkDeep: Color,
) {
    companion object {
        val Stable = Accent(pink = Color(0xFFFF0080), pinkLight = Color(0xFFFF4BA8), pinkDeep = Color(0xFFC50053))
        val Preview = Accent(pink = Color(0xFF00984C), pinkLight = Color(0xFF4BFFA2), pinkDeep = Color(0xFF007239))

        /** This build's accent. */
        val current: Accent = if (BuildConfig.FLAVOR == "stable") Stable else Preview
    }
}
