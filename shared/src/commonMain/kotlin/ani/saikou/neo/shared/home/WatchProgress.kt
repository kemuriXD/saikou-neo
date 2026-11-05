package ani.saikou.neo.shared.home

import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Less than this in, the episode starts from the beginning without asking.
private val RESUME_FROM = 10.seconds

// From this far through, the episode was watched to the credits, so it starts over.
private const val WATCHED_TO = 0.95f

private const val SECONDS_PER_DAY = 86_400L

/**
 * How far into an episode this TV got. [still] is a frame from the episode, where one is known. [savedAt] is when, in
 * seconds since 1970.
 */
public data class WatchProgress(
    val episode: Int,
    val watched: Duration,
    val length: Duration,
    val still: String?,
    val savedAt: Long? = null,
) {
    /** Whole minutes left, rounded up, so an episode that isn't over never shows 0. */
    val minutesLeft: Int get() = ceil((length - watched).inWholeSeconds / 60.0).toInt().coerceAtLeast(1)

    /** How much was watched, from 0 to 1. */
    val fraction: Float get() = (watched / length).toFloat().coerceIn(0f, 1f)

    /** True once [episode] was watched to the credits. */
    val watchedToEnd: Boolean get() = fraction >= WATCHED_TO

    /** Where [episode] picks up, if this is that episode and it's worth asking: not just begun, not watched to the end. */
    public fun resumeAtMs(episode: Int): Long? =
        watched.inWholeMilliseconds.takeIf { episode == this.episode && watched >= RESUME_FROM && !watchedToEnd }
}

/** How many days back [savedAt] was from [now], both in seconds, by the calendar where the viewer is. */
public fun daysSince(
    savedAt: Long,
    now: Long,
    zoneOffsetSeconds: Int,
): Int = ((now + zoneOffsetSeconds).floorDiv(SECONDS_PER_DAY) - (savedAt + zoneOffsetSeconds).floorDiv(SECONDS_PER_DAY)).toInt()
