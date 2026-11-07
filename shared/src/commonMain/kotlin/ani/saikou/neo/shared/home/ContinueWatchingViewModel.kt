package ani.saikou.neo.shared.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.ArtLibrary
import ani.saikou.neo.shared.catalogue.problem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Every show in [history] that isn't finished, most recently watched first, with AniList's cards: all of Continue
 * watching. It follows the history, so a show watched again moves to the front.
 */
public class ContinueWatchingViewModel(
    client: AniListClient,
    library: ArtLibrary,
    history: WatchHistory,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SeeMoreState())
    public val state: StateFlow<SeeMoreState> = mutableState.asStateFlow()
    private val watchedCards = WatchedCards(client)
    private var saved: Map<Int, WatchProgress> = emptyMap()
    private var loading: Job? = null
    private val resting = Resting(viewModelScope, library) { logo -> mutableState.update { it.copy(restingLogo = logo) } }

    init {
        viewModelScope.launch {
            history.shows.collect { shows ->
                saved = shows
                load()
            }
        }
    }

    /** Call with the card focus moves to. Resting on one finds its art. */
    public fun rest(card: AnimeCard?) {
        resting.on(card)
    }

    /** Loads the list again after it failed. */
    public fun retry() {
        if (state.value.more == More.Failed) load()
    }

    private fun load() {
        val shows = saved
        loading?.cancel()
        mutableState.update { if (it.more == More.Failed) it.copy(more = More.Loading, problem = null) else it }
        loading =
            viewModelScope.launch {
                when (val sent = watchedCards.unfinished(shows)) {
                    is AniListResult.Success -> {
                        mutableState.update { state ->
                            state.copy(
                                items = sent.value,
                                more = More.Done,
                                progress = sent.value.associate { it.id to shows.getValue(it.id) },
                            )
                        }
                    }

                    is AniListResult.Failure -> {
                        mutableState.update { it.copy(more = More.Failed, problem = sent.error.problem) }
                    }
                }
            }
    }
}
