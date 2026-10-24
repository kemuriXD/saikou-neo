package ani.saikou.neo.shared.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimePage
import ani.saikou.neo.shared.catalogue.AnimeSeason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Home's rows, top to bottom. */
public enum class HomeRow { TRENDING, THIS_SEASON, POPULAR }

public sealed interface RowState {
    public data object Loading : RowState

    public data class Loaded(
        val items: List<AnimeCard>,
    ) : RowState

    /** The row couldn't load. Home shows the rows that did. */
    public data object Failed : RowState
}

public data class HomeState(
    /** Every [HomeRow], in order. */
    val rows: Map<HomeRow, RowState>,
)

/** Loads Home's rows from AniList. This season is [season] of [year]. */
public class HomeViewModel(
    private val client: AniListClient,
    private val season: AnimeSeason,
    private val year: Int,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState(HomeRow.entries.associateWith { RowState.Loading }))
    public val state: StateFlow<HomeState> = mutableState.asStateFlow()

    init {
        HomeRow.entries.forEach { row ->
            viewModelScope.launch {
                val result = load(row)
                val rowState = if (result is AniListResult.Success) RowState.Loaded(result.value.items) else RowState.Failed
                mutableState.update { it.copy(rows = it.rows + (row to rowState)) }
            }
        }
    }

    private suspend fun load(row: HomeRow): AniListResult<AnimePage> =
        when (row) {
            HomeRow.TRENDING -> client.trending()
            HomeRow.THIS_SEASON -> client.season(season, year)
            HomeRow.POPULAR -> client.popular()
        }
}
