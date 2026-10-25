package ani.saikou.neo.shared.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListResult
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

public sealed interface DetailsState {
    public data object Loading : DetailsState

    public data class Loaded(
        val details: AnimeDetails,
        val synopsis: Synopsis = parseSynopsis(details.description),
        /** The show's backdrop, or AniList's art without one. Null until its art is found, so it fades in once. */
        val backdrop: String? = null,
        /** The title logo, found with the backdrop. */
        val logo: Logo = Logo.Loading,
    ) : DetailsState

    public data object Failed : DetailsState
}

/**
 * Loads one anime's details from AniList, and its backdrop and logo from [library] at the same time. The title is the
 * logo only if the art arrives with the details, as art found while the viewer rested on its card does, so the title
 * never swaps in front of the viewer. A backdrop found later still fades in.
 */
public class DetailsViewModel(
    private val client: AniListClient,
    private val library: ArtLibrary,
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
            val loaded = DetailsState.Loaded(result.value)
            if (art.isCompleted) {
                mutableState.value = loaded.withArt(art.await())
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
