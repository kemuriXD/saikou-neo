package ani.saikou.neo.shared.playback

import ani.saikou.neo.shared.settings.StillWatching

/**
 * How many episodes in a row Up next's countdown has played on with no button pressed, kept for as long as the app runs
 * because each episode opens a new player. Up next asks if you're still watching once that run, with the episode on
 * screen, reaches what [limit] reads from Settings (ADR 0055).
 */
public class WatchingRun(
    private val limit: suspend () -> StillWatching,
) {
    private var playedOn = 0

    /** A button was pressed somewhere in the app, so someone's there. */
    public fun pressed() {
        playedOn = 0
    }

    /** The countdown played the next episode on its own. */
    public fun playedOn() {
        playedOn++
    }

    /** How many episodes have played in a row when Up next should ask now, or null when it counts down. */
    public suspend fun asks(): Int? {
        val episodes = limit().episodes ?: return null
        return (playedOn + 1).takeIf { it >= episodes }
    }
}
