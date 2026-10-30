package ani.saikou.neo.shared.home

import kotlin.math.ceil
import kotlin.time.Duration

/** How far into an episode this TV got. [still] is a frame from the episode, where one is known. */
public data class WatchProgress(
    val episode: Int,
    val watched: Duration,
    val length: Duration,
    val still: String?,
) {
    /** Whole minutes left, rounded up, so an episode that isn't over never shows 0. */
    val minutesLeft: Int get() = ceil((length - watched).inWholeSeconds / 60.0).toInt().coerceAtLeast(1)

    /** How much was watched, from 0 to 1. */
    val fraction: Float get() = (watched / length).toFloat().coerceIn(0f, 1f)
}
