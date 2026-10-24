package ani.saikou.neo.shared.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.art
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
    ) : DetailsState

    public data object Failed : DetailsState
}

/** Loads one anime's details from AniList, and its fan art from [aniZip] at the same time. */
public class DetailsViewModel(
    private val client: AniListClient,
    private val aniZip: AniZipClient,
    private val animeId: Int,
) : ViewModel() {
    private val mutableState = MutableStateFlow<DetailsState>(DetailsState.Loading)
    public val state: StateFlow<DetailsState> = mutableState.asStateFlow()

    init {
        val fanArt = viewModelScope.async { aniZip.art(animeId)?.fanArt }
        viewModelScope.launch {
            val result = client.details(animeId)
            if (result !is AniListResult.Success) {
                fanArt.cancel()
                mutableState.value = DetailsState.Failed
                return@launch
            }
            val details = result.value
            if (fanArt.isCompleted) {
                mutableState.value = DetailsState.Loaded(details, backdrop = fanArt.await() ?: details.card.art)
                return@launch
            }
            mutableState.value = DetailsState.Loaded(details)
            val backdrop = fanArt.await() ?: details.card.art
            mutableState.update { (it as DetailsState.Loaded).copy(backdrop = backdrop) }
        }
    }
}
