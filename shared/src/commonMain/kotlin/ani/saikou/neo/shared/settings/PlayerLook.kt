package ani.saikou.neo.shared.settings

import ani.saikou.neo.shared.playback.ServerEdition

/** The next size [by] steps up, or down below zero, stopping at the smallest and largest. */
public fun SubtitleSize.stepped(by: Int): SubtitleSize = SubtitleSize.entries[(ordinal + by).coerceIn(SubtitleSize.entries.indices)]

/** The next position [by] steps up the screen, or down below zero, stopping at Bottom and Top. */
public fun SubtitlePosition.stepped(by: Int): SubtitlePosition =
    SubtitlePosition.entries[(ordinal + by).coerceIn(SubtitlePosition.entries.indices)]

/** What the player's Subtitle look row can do. */
public enum class LookRow {
    /** It opens the looks. */
    READY,

    /** Greyed out until a subtitle track is on. */
    OFF,

    /** Greyed out, with subtitle timing: the subtitles are part of the video. */
    BURNED,
}

/** The Subtitle look row for a server of [edition], with a subtitle track on or not. */
public fun lookRow(
    subtitlesOn: Boolean,
    edition: ServerEdition,
): LookRow =
    when {
        subtitlesOn -> LookRow.READY
        edition == ServerEdition.HARD_SUB -> LookRow.BURNED
        else -> LookRow.OFF
    }
