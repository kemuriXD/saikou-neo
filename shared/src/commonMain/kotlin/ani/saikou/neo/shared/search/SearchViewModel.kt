package ani.saikou.neo.shared.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.KitsuClient
import ani.saikou.neo.shared.catalogue.LoadProblem
import ani.saikou.neo.shared.catalogue.SearchFilter
import ani.saikou.neo.shared.catalogue.problem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The genres Search offers before you type and when nothing matches, each as AniList names it. */
public enum class SearchGenre(
    public val aniListName: String,
) {
    ACTION("Action"),
    COMEDY("Comedy"),
    FANTASY("Fantasy"),
    ROMANCE("Romance"),
    SCI_FI("Sci-Fi"),
    SLICE_OF_LIFE("Slice of Life"),
}

public sealed interface SearchResults {
    /** Nothing typed yet. */
    public data object Idle : SearchResults

    /** The first search for what's typed is on its way. */
    public data object Loading : SearchResults

    /**
     * What AniList found for [query]. Nothing only when nothing was found before it, and then perhaps a [suggestion] of
     * the show the query meant.
     */
    public data class Found(
        val query: String,
        val items: List<AnimeCard>,
        val suggestion: AnimeCard? = null,
    ) : SearchResults

    /** AniList couldn't search, for [problem]. */
    public data class Failed(
        val problem: LoadProblem,
    ) : SearchResults
}

public data class SearchState(
    val query: String = "",
    /** Results for an earlier query stay until the ones for [query] come in. */
    val results: SearchResults = SearchResults.Idle,
    val filter: SearchFilter = SearchFilter.ALL,
    /** The last searches a result was opened from, newest first. */
    val recent: List<String> = emptyList(),
)

// Long enough to wait out a run of key presses on a remote.
private const val PAUSE_MILLIS = 300L
private const val RESULTS = 24

/**
 * Search as you type: AniList is asked once typing pauses. When it finds nothing, [kitsu], which forgives typos, says
 * which show was meant. A search a result is opened from goes into [recent].
 */
public class SearchViewModel(
    private val client: AniListClient,
    private val kitsu: KitsuClient,
    private val recent: RecentSearches,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchState())
    public val state: StateFlow<SearchState> = mutableState.asStateFlow()
    private var search: Job? = null

    init {
        viewModelScope.launch { recent.searches.collect { searches -> mutableState.update { it.copy(recent = searches) } } }
    }

    /** Call when a result is opened, so the search that found it is kept with the recent ones. */
    public fun openedResult() {
        val found = state.value.results as? SearchResults.Found ?: return
        viewModelScope.launch { recent.add(found.query) }
    }

    /** Fills in [query], one of the recent searches, and searches for it straight away. */
    public fun searchAgain(query: String) {
        search?.cancel()
        mutableState.update { it.copy(query = query) }
        find(query, pause = false)
    }

    public fun type(letter: Char) {
        change(state.value.query + letter)
    }

    public fun delete() {
        change(state.value.query.dropLast(1))
    }

    public fun clear() {
        change("")
    }

    /** Narrows the search to [filter] and searches again for what's typed. */
    public fun filter(filter: SearchFilter) {
        if (filter == state.value.filter) return
        // Results that ignore the new filter would be wrong, so they go while the filtered ones load.
        mutableState.update { it.copy(filter = filter, results = if (it.query.isEmpty()) it.results else SearchResults.Loading) }
        search?.cancel()
        if (state.value.query.isNotEmpty()) find(state.value.query, pause = false)
    }

    /** Searches again after a search failed. */
    public fun retry() {
        if (state.value.results is SearchResults.Failed) find(state.value.query, pause = false)
    }

    private fun change(query: String) {
        // Spaces alone don't make a query.
        val text = if (query.isBlank()) "" else query
        mutableState.update { it.copy(query = text) }
        search?.cancel()
        if (text.isEmpty()) {
            mutableState.update { it.copy(results = SearchResults.Idle) }
        } else {
            find(text, pause = true)
        }
    }

    private fun find(
        query: String,
        pause: Boolean,
    ) {
        mutableState.update { if (it.results is SearchResults.Found) it else it.copy(results = SearchResults.Loading) }
        search =
            viewModelScope.launch {
                if (pause) delay(PAUSE_MILLIS)
                val text = query.trim()
                val items =
                    when (val result = client.search(text, perPage = RESULTS, filter = state.value.filter)) {
                        is AniListResult.Success -> {
                            result.value.items
                        }

                        is AniListResult.Failure -> {
                            mutableState.update { it.copy(results = SearchResults.Failed(result.error.problem)) }
                            return@launch
                        }
                    }
                // AniList only matches whole words, so a word half typed finds nothing. What it found before stays.
                if (items.isEmpty() && (state.value.results as? SearchResults.Found)?.items?.isNotEmpty() == true) return@launch
                // A filter that found nothing isn't a spelling mistake, and Kitsu's show might not pass the filter.
                val suggestion =
                    if (items.isEmpty() && state.value.filter == SearchFilter.ALL) kitsu.aniListId(text)?.let { client.card(it) } else null
                mutableState.update { it.copy(results = SearchResults.Found(text, items, suggestion)) }
            }
    }
}
