package ani.saikou.neo.shared.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.FanartTvClient
import ani.saikou.neo.shared.catalogue.ShowArt
import ani.saikou.neo.shared.catalogue.art
import ani.saikou.neo.shared.catalogue.showArt
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
        /** The show's fan art, or AniList's art without any. Null until AniZip answers, so it fades in once. */
        val backdrop: String? = null,
        /** The title logo, from the same AniZip and fanart.tv answers as the backdrop. */
        val logo: Logo = Logo.Loading,
    ) : DetailsState

    public data object Failed : DetailsState
}

/** Loads one anime's details from AniList, and its fan art and logo from [aniZip] or [fanartTv] at the same time. */
public class DetailsViewModel(
    private val client: AniListClient,
    private val aniZip: AniZipClient,
    private val fanartTv: FanartTvClient,
    private val animeId: Int,
) : ViewModel() {
    private val mutableState = MutableStateFlow<DetailsState>(DetailsState.Loading)
    public val state: StateFlow<DetailsState> = mutableState.asStateFlow()

    init {
        val art = viewModelScope.async { showArt(client, aniZip, fanartTv, animeId) }
        viewModelScope.launch {
            val result = client.details(animeId)
            if (result !is AniListResult.Success) {
                art.cancel()
                mutableState.value = DetailsState.Failed
                return@launch
            }
            val details = result.value
            if (art.isCompleted) {
                mutableState.value = DetailsState.Loaded(details).withArt(art.await())
                return@launch
            }
            mutableState.value = DetailsState.Loaded(details)
            val shown = art.await()
            mutableState.update { (it as DetailsState.Loaded).withArt(shown) }
        }
    }
}

private fun DetailsState.Loaded.withArt(art: ShowArt) = copy(backdrop = art.fanArt ?: details.card.art, logo = art.logo)
