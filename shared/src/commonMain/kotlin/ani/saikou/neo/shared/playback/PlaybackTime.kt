package ani.saikou.neo.shared.playback

import kotlin.math.abs

/**
 * Formats a playback position as `m:ss`, or `h:mm:ss` from one hour.
 * Negative positions show as `0:00`.
 */
public fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:$seconds"
    } else {
        "$minutes:$seconds"
    }
}

/** Formats a scrub's jump with its sign, such as `−5:00` or `+0:30`. */
public fun formatJump(jumpMs: Long): String = (if (jumpMs < 0) "\u2212" else "+") + formatPlaybackTime(abs(jumpMs))

/** When an episode [durationMs] long ends if it plays on from [positionMs] at [nowMs] and [speed], or null if its length is unknown. */
public fun endsAtMs(
    nowMs: Long,
    positionMs: Long,
    durationMs: Long,
    speed: Float = 1f,
): Long? = if (durationMs <= 0) null else nowMs + ((durationMs - positionMs).coerceAtLeast(0) / speed).toLong()

/** The speeds the player offers. */
public val PLAYBACK_SPEEDS: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** Formats a speed without its point when it's whole, such as `1` or `1.25`. */
public fun formatSpeed(speed: Float): String = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
