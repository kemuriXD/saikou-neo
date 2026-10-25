package ani.saikou.neo.shared.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeSeason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether more of the list can load. */
public enum class More {
    Loading,
    Ready,

    /** The last page couldn't load. Shows already loaded stay. */
    Failed,

    /** AniList has no more pages. */
    Done,
}

public data class SeeMoreState(
    val items: List<AnimeCard> = emptyList(),
    val more: More = More.Loading,
)

/** All of one Home row, a page at a time. This season is [season] of [year]. */
public class SeeMoreViewModel(
    private val client: AniListClient,
    private val row: HomeRow,
    private val season: AnimeSeason,
    private val year: Int,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SeeMoreState())
    public val state: StateFlow<SeeMoreState> = mutableState.asStateFlow()
    private var loadedPages = 0

    init {
        load()
    }

    /** Loads the next page, unless one is on its way, the last one failed or there are none left. */
    public fun loadMore() {
        if (state.value.more == More.Ready) load()
    }

    /** Loads the page that failed again. */
    public fun retry() {
        if (state.value.more == More.Failed) load()
    }

    private fun load() {
        mutableState.update { it.copy(more = More.Loading) }
        viewModelScope.launch {
            val result = client.row(row, season, year, page = loadedPages + 1)
            if (result !is AniListResult.Success) {
                mutableState.update { it.copy(more = More.Failed) }
                return@launch
            }
            loadedPages++
            val page = result.value
            mutableState.update { state ->
                // AniList's order moves between requests, so a page can repeat a show an earlier one had.
                val shown = state.items.mapTo(HashSet()) { it.id }
                state.copy(
                    items = state.items + page.items.filter { it.id !in shown },
                    more = if (page.hasNextPage) More.Ready else More.Done,
                )
            }
        }
    }
}
