package ani.saikou.neo.shared.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.ArtLibrary
import ani.saikou.neo.shared.catalogue.ShowArt
import ani.saikou.neo.shared.catalogue.art
import ani.saikou.neo.shared.home.Logo
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

// On a first visit the art takes about twice as long as AniList's details, so Details waits this long for it.
internal val ART_WAIT = 3.seconds

public sealed interface DetailsState {
    public data object Loading : DetailsState

    public data class Loaded(
        val details: AnimeDetails,
        val synopsis: Synopsis = parseSynopsis(details.description),
        /** The show's backdrop, or AniList's art without one. Null until its art is found, so it fades in once. */
        val backdrop: String? = null,
        /** The title logo, found with the backdrop. */
        val logo: Logo = Logo.Loading,
        /** Null until AniZip has answered, or couldn't be reached. */
        val episodes: EpisodeList? = null,
    ) : DetailsState

    public data object Failed : DetailsState
}

/**
 * Loads one anime's details from AniList, and its backdrop and logo from [library] at the same time. Art found while the
 * viewer rested on the show's card is there at once; otherwise Details waits up to [ART_WAIT] for it. The title is the
 * logo only if the art came by then, so the title never swaps in front of the viewer. A backdrop found later still fades
 * in. The episodes come from AniZip once AniList's details are in, or as plain numbers without it.
 */
public class DetailsViewModel(
    private val client: AniListClient,
    private val library: ArtLibrary,
    private val aniZip: AniZipClient,
    private val animeId: Int,
) : ViewModel() {
    private val mutableState = MutableStateFlow<DetailsState>(DetailsState.Loading)
    public val state: StateFlow<DetailsState> = mutableState.asStateFlow()

    init {
        val details = viewModelScope.async { client.details(animeId) }
        // A film's title is only needed when TMDB can't find it by its IMDb id.
        val art =
            viewModelScope.async {
                library.art(animeId) { (details.await() as? AniListResult.Success)?.value?.card }
            }
        viewModelScope.launch {
            val result = details.await()
            if (result !is AniListResult.Success) {
                art.cancel()
                mutableState.value = DetailsState.Failed
                return@launch
            }
            // The episodes can come before or after Details shows, so they're kept here as well as in the state.
            var episodes: EpisodeList? = null
            launch {
                val found = episodeList(result.value, aniZip.episodes(animeId))
                episodes = found
                mutableState.update { (it as? DetailsState.Loaded)?.copy(episodes = found) ?: it }
            }
            val found = withTimeoutOrNull(ART_WAIT) { art.await() }
            val loaded = DetailsState.Loaded(result.value, episodes = episodes)
            if (found != null) {
                mutableState.value = loaded.withArt(found)
                return@launch
            }
            mutableState.value = loaded.copy(logo = Logo.None)
            val shown = art.await()
            mutableState.update { (it as DetailsState.Loaded).withArt(shown, logo = Logo.None) }
        }
    }
}

private fun DetailsState.Loaded.withArt(
    art: ShowArt,
    logo: Logo = art.logo,
) = copy(backdrop = art.backdrop ?: details.card.art, logo = logo)
