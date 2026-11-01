package ani.saikou.neo.shared.playback

/** The parts of an episode the bar names. */
public enum class SegmentKind { Recap, Opening, Ending, Preview }

/** Where a [kind] of part runs in the episode. */
public data class Segment(
    val kind: SegmentKind,
    val startMs: Long,
    val endMs: Long,
)

/** One stretch of the bar: a named part, or the episode itself when [kind] is null. */
public data class Stretch(
    val kind: SegmentKind?,
    val startMs: Long,
    val endMs: Long,
)

/**
 * The bar for an episode [durationMs] long: [segments] in order, cut to the episode, with the episode filling the gaps.
 * A segment that overlaps the one before starts where that one ends. An unknown length has no bar.
 */
public fun stretches(
    segments: List<Segment>,
    durationMs: Long,
): List<Stretch> {
    if (durationMs <= 0) return emptyList()
    val bar = mutableListOf<Stretch>()
    var at = 0L
    for (segment in segments.sortedBy { it.startMs }) {
        val start = segment.startMs.coerceIn(at, durationMs)
        val end = segment.endMs.coerceAtMost(durationMs)
        if (end <= start) continue
        if (start > at) bar += Stretch(null, at, start)
        bar += Stretch(segment.kind, start, end)
        at = end
    }
    if (at < durationMs) bar += Stretch(null, at, durationMs)
    return bar
}

/** The stretch playing at [positionMs]. A stretch's end belongs to the next one, except at the very end. */
public fun List<Stretch>.at(positionMs: Long): Stretch? =
    firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }
        ?: lastOrNull()?.takeIf { positionMs == it.endMs }
