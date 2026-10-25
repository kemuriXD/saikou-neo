package ani.saikou.neo.shared.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimePage
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.catalogue.ArtLibrary
import ani.saikou.neo.shared.catalogue.map
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
    /** The logo of the poster focus rests on, once found, for the screen to start loading. */
    val restingLogo: Logo.Image? = null,
    /** The show a list of recommendations is for, once its first page is in. */
    val show: AnimeTitle? = null,
)

/** One page of a list, and the show it belongs to, if any. */
private class SeeMorePage(
    val page: AnimePage,
    val show: AnimeTitle? = null,
)

/** All of a list, a page at a time: one Home row, or a show's recommendations. */
public class SeeMoreViewModel private constructor(
    library: ArtLibrary,
    private val loadPage: suspend (page: Int) -> AniListResult<SeeMorePage>,
) : ViewModel() {
    /** All of one Home row. This season is [season] of [year]. */
    public constructor(
        client: AniListClient,
        library: ArtLibrary,
        row: HomeRow,
        season: AnimeSeason,
        year: Int,
    ) : this(library, { page -> client.row(row, season, year, page).map { SeeMorePage(it) } })

    public companion object {
        /** Every anime AniList's users recommend for [animeId], best rated first. */
        public fun recommendations(
            client: AniListClient,
            library: ArtLibrary,
            animeId: Int,
        ): SeeMoreViewModel =
            SeeMoreViewModel(library, { page -> client.recommendations(animeId, page).map { SeeMorePage(it.page, it.show) } })
    }

    private val mutableState = MutableStateFlow(SeeMoreState())
    public val state: StateFlow<SeeMoreState> = mutableState.asStateFlow()
    private var loadedPages = 0
    private val resting = Resting(viewModelScope, library) { logo -> mutableState.update { it.copy(restingLogo = logo) } }

    init {
        load()
    }

    /** Loads the next page, unless one is on its way, the last one failed or there are none left. */
    public fun loadMore() {
        if (state.value.more == More.Ready) load()
    }

    /** Call with the poster focus moves to. Resting on one finds its art. */
    public fun rest(card: AnimeCard?) {
        resting.on(card)
    }

    /** Loads the page that failed again. */
    public fun retry() {
        if (state.value.more == More.Failed) load()
    }

    private fun load() {
        mutableState.update { it.copy(more = More.Loading) }
        viewModelScope.launch {
            val result = loadPage(loadedPages + 1)
            if (result !is AniListResult.Success) {
                mutableState.update { it.copy(more = More.Failed) }
                return@launch
            }
            loadedPages++
            val page = result.value.page
            val show = result.value.show
            mutableState.update { state ->
                // AniList's order moves between requests, so a page can repeat a show an earlier one had.
                val shown = state.items.mapTo(HashSet()) { it.id }
                state.copy(
                    items = state.items + page.items.filter { it.id !in shown },
                    more = if (page.hasNextPage) More.Ready else More.Done,
                    show = state.show ?: show,
                )
            }
        }
    }
}
