package ani.saikou.neo.shared.playback

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How close to its length a video that stopped moving counts as at its end. */
private val NEAR_END = 2.seconds

/** How long a video near its end can sit still, meant to play, before it counts as ended. */
private val STILL_FOR = 3.seconds

/**
 * Says once when a video ends, so the end always does what it should (ADR 0054). The player usually says so itself, but
 * after a seek near the end some streams stop on their last frame without it: the video sits at its length, meant to
 * play, and doesn't move. That counts as the end once it has sat [stillFor] within [nearEnd] of the length. Going back
 * from the end lets it end again.
 */
public class VideoEnd(
    private val nearEnd: Duration = NEAR_END,
    private val stillFor: Duration = STILL_FOR,
) {
    private var told = false
    private var lastPositionMs: Long? = null
    private var stillSinceMs: Long? = null

    /** The player says the video ended. True unless the end was already told. */
    public fun ended(): Boolean {
        if (told) return false
        told = true
        return true
    }

    /**
     * The video is at [positionMs] of [durationMs] at [nowMs], and [playing] when it's meant to play rather than paused.
     * True once, when it has stopped at its end without the player saying so.
     */
    public fun check(
        positionMs: Long,
        durationMs: Long,
        playing: Boolean,
        nowMs: Long,
    ): Boolean {
        val atEnd = durationMs > 0 && positionMs >= durationMs - nearEnd.inWholeMilliseconds
        val moved = positionMs != lastPositionMs
        lastPositionMs = positionMs
        if (!atEnd) told = false
        if (!atEnd || !playing || moved) {
            stillSinceMs = nowMs.takeIf { atEnd && playing }
            return false
        }
        val since = stillSinceMs ?: nowMs.also { stillSinceMs = it }
        if (nowMs - since < stillFor.inWholeMilliseconds) return false
        return ended()
    }
}
