package ani.saikou.neo.shared.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

public sealed interface DetailsState {
    public data object Loading : DetailsState

    public data class Loaded(
        val details: AnimeDetails,
        val synopsis: Synopsis = parseSynopsis(details.description),
    ) : DetailsState

    public data object Failed : DetailsState
}

/** Loads one anime's details from AniList. */
public class DetailsViewModel(
    private val client: AniListClient,
    private val animeId: Int,
) : ViewModel() {
    private val mutableState = MutableStateFlow<DetailsState>(DetailsState.Loading)
    public val state: StateFlow<DetailsState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val result = client.details(animeId)
            mutableState.value = if (result is AniListResult.Success) DetailsState.Loaded(result.value) else DetailsState.Failed
        }
    }
}
