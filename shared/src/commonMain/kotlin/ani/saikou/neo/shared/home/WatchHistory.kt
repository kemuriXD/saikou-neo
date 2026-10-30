package ani.saikou.neo.shared.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How far this TV got in each show, keyed by anime id. Kept in memory and shared by Home and Details. For now it only
 * holds the alpha samples, until the player saves real progress (#115).
 */
public class WatchHistory {
    private val mutableShows = MutableStateFlow(emptyMap<Int, WatchProgress>())
    public val shows: StateFlow<Map<Int, WatchProgress>> = mutableShows.asStateFlow()

    /** Adds [progress], replacing what was kept for the same shows. */
    public fun put(progress: Map<Int, WatchProgress>) {
        mutableShows.value += progress
    }
}
