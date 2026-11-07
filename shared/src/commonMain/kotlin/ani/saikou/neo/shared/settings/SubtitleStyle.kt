package ani.saikou.neo.shared.settings

import ani.saikou.neo.shared.playback.ServerEdition

/** How big text subtitles are, as a [scale] of the usual size. TV keeps the size from the TV's caption settings. */
public enum class SubtitleSize(
    public val scale: Float?,
) {
    TV(null),
    SMALL(0.75f),
    MEDIUM(1f),
    LARGE(1.5f),
    EXTRA_LARGE(2f),
}

/** The colour of the text, as [argb]. TV keeps the colour from the TV's caption settings. */
public enum class SubtitleColour(
    public val argb: Int?,
) {
    TV(null),
    WHITE(0xFFFFFFFF.toInt()),
    YELLOW(0xFFFFEB3B.toInt()),
    CYAN(0xFF4DD0E1.toInt()),
}

/** What sets the text apart from the video. TV keeps the TV's caption settings. */
public enum class SubtitleEdge { TV, OUTLINE, SHADOW, BACKGROUND }

/** How high the subtitles sit, as the [bottomFraction] of the screen left below them. */
public enum class SubtitlePosition(
    public val bottomFraction: Float,
) {
    BOTTOM(0.08f),
    RAISED(0.15f),
    HIGH(0.25f),
}

/** The look of text subtitles. A new install starts from the TV's caption settings, at the bottom. */
public data class SubtitleStyle(
    val size: SubtitleSize = SubtitleSize.TV,
    val colour: SubtitleColour = SubtitleColour.TV,
    val edge: SubtitleEdge = SubtitleEdge.TV,
    val position: SubtitlePosition = SubtitlePosition.BOTTOM,
)

/** The parts of a [SubtitleStyle], in the order they're offered. */
public enum class SubtitleStylePart { SIZE, COLOUR, EDGE, POSITION }

/** The style with [part] moved to its next choice, or its previous one when not [forward]. It stops at either end. */
public fun SubtitleStyle.step(
    part: SubtitleStylePart,
    forward: Boolean,
): SubtitleStyle =
    when (part) {
        SubtitleStylePart.SIZE -> copy(size = size.step(SubtitleSize.entries, forward))
        SubtitleStylePart.COLOUR -> copy(colour = colour.step(SubtitleColour.entries, forward))
        SubtitleStylePart.EDGE -> copy(edge = edge.step(SubtitleEdge.entries, forward))
        SubtitleStylePart.POSITION -> copy(position = position.step(SubtitlePosition.entries, forward))
    }

private fun <T : Enum<T>> T.step(
    all: List<T>,
    forward: Boolean,
): T = all[(ordinal + if (forward) 1 else -1).coerceIn(0, all.lastIndex)]

/** Why subtitles can't be styled now. */
public enum class StyleBlock {
    /** No text subtitles are playing: they're off, or the stream has none. */
    SUBTITLES_OFF,

    /** The server's subtitles are burned into the video. */
    BURNED_IN,
}

/** Why the subtitles playing from a server of [edition] can't be styled, or null when they can. */
public fun styleBlock(
    edition: ServerEdition,
    subtitlesOn: Boolean,
): StyleBlock? =
    when {
        subtitlesOn -> null
        edition == ServerEdition.HARD_SUB -> StyleBlock.BURNED_IN
        else -> StyleBlock.SUBTITLES_OFF
    }
