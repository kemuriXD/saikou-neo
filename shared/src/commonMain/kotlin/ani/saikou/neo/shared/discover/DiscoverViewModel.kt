package ani.saikou.neo.shared.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.home.RETRY_EVERY
import ani.saikou.neo.shared.home.RowState
import ani.saikou.neo.shared.search.SearchGenre
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// The genres Discover has a row for, after this season.
private val genres = listOf(SearchGenre.ACTION, SearchGenre.ROMANCE, SearchGenre.COMEDY, SearchGenre.FANTASY)

/** One theme on Discover. */
public sealed interface DiscoverRow {
    /** What's airing in [season] of [year], most popular first. */
    public data class Season(
        val season: AnimeSeason,
        val year: Int,
    ) : DiscoverRow

    /** The most popular anime of [genre]. */
    public data class Genre(
        val genre: SearchGenre,
    ) : DiscoverRow
}

public data class DiscoverState(
    /** Every row, in order. */
    val rows: Map<DiscoverRow, RowState>,
    /** True when every row failed because AniList couldn't be reached. */
    val offline: Boolean = false,
)

/**
 * Loads Discover's rows from AniList: [season] of [year], then a few genres. As on Home, a row that fails is left out,
 * and when they all fail for the network Discover is offline and tries again every 30 seconds while that's on screen.
 */
public class DiscoverViewModel(
    private val client: AniListClient,
    season: AnimeSeason,
    year: Int,
) : ViewModel() {
    private val rows = listOf(DiscoverRow.Season(season, year)) + genres.map { DiscoverRow.Genre(it) }
    private val mutableState = MutableStateFlow(DiscoverState(rows.associateWith { RowState.Loading }))
    public val state: StateFlow<DiscoverState> = mutableState.asStateFlow()
    private var offlineInView = false
    private var retrying: Job? = null

    init {
        rows.forEach(::load)
    }

    /** Loads the rows that failed again, in their places. Discover stays offline until one loads. */
    public fun retry() {
        val failed = rows.filter { state.value.rows[it] == RowState.Failed }
        if (failed.isEmpty()) return
        mutableState.update { state -> state.copy(rows = state.rows + failed.associateWith { RowState.Loading }) }
        failed.forEach(::load)
    }

    /** Call with true while the offline screen is shown, and false once it isn't, so Discover only tries again while seen. */
    public fun watchOffline(inView: Boolean) {
        offlineInView = inView
        retryLater()
    }

    private fun load(row: DiscoverRow) {
        viewModelScope.launch {
            val result =
                when (row) {
                    is DiscoverRow.Season -> client.season(row.season, row.year)
                    is DiscoverRow.Genre -> client.genre(row.genre.aniListName)
                }
            val rowState = if (result is AniListResult.Success) RowState.Loaded(result.value.items) else RowState.Failed
            val network = (result as? AniListResult.Failure)?.error is AniListError.Network
            mutableState.update { state ->
                val next = state.rows + (row to rowState)
                DiscoverState(
                    rows = next,
                    offline =
                        rowState !is RowState.Loaded &&
                            (state.offline || (network && next.values.all { it == RowState.Failed })),
                )
            }
            retryLater()
        }
    }

    /** Starts the 30 second timer over, if Discover is offline and that's on screen. */
    private fun retryLater() {
        retrying?.cancel()
        if (!offlineInView || !state.value.offline) return
        retrying =
            viewModelScope.launch {
                delay(RETRY_EVERY)
                retry()
            }
    }
}
