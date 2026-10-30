package ani.saikou.neo.shared.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeCard
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

    /** What AniList found for [query], which can be nothing. */
    public data class Found(
        val query: String,
        val items: List<AnimeCard>,
    ) : SearchResults

    public data object Failed : SearchResults
}

public data class SearchState(
    val query: String = "",
    /** Results for an earlier query stay until the ones for [query] come in. */
    val results: SearchResults = SearchResults.Idle,
)

// Long enough to wait out a run of key presses on a remote.
private const val PAUSE_MILLIS = 300L
private const val RESULTS = 24

/** Search as you type: AniList is asked once typing pauses. */
public class SearchViewModel(
    private val client: AniListClient,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchState())
    public val state: StateFlow<SearchState> = mutableState.asStateFlow()
    private var search: Job? = null

    public fun type(letter: Char) {
        change(state.value.query + letter)
    }

    public fun delete() {
        change(state.value.query.dropLast(1))
    }

    public fun clear() {
        change("")
    }

    /** Searches again after a search failed. */
    public fun retry() {
        if (state.value.results == SearchResults.Failed) find(state.value.query, pause = false)
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
                val result = client.search(query.trim(), perPage = RESULTS)
                val results = if (result is AniListResult.Success) SearchResults.Found(query, result.value.items) else SearchResults.Failed
                mutableState.update { it.copy(results = results) }
            }
    }
}
