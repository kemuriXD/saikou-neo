package ani.saikou.neo.shared.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimePage
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.FanartTvClient
import ani.saikou.neo.shared.catalogue.art
import ani.saikou.neo.shared.catalogue.showArt
import ani.saikou.neo.shared.details.parseSynopsis
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private const val FEATURED_SHOWS = 6
private val paragraphBreak = Regex("""\s*\n\s*""")
private val ROTATE_EVERY = 10.seconds

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
    /** Null until the rows it comes from have loaded, and if none did. */
    val spotlight: Spotlight? = null,
)

/** The shows featured at the top of Home, and which one is showing. */
public data class Spotlight(
    val shows: List<AnimeCard>,
    val index: Int = 0,
    /** Each show's logo, by anime id, once AniZip has answered. */
    val logos: Map<Int, Logo> = emptyMap(),
    /** Each show's fan art, or its series' when it has none, by anime id. Shows without any are left out. */
    val fanArt: Map<Int, String> = emptyMap(),
    /**
     * Each show's story as one paragraph, by anime id, once AniList has sent it. The spotlight shows only a few lines,
     * so paragraph breaks would leave them half empty. Shows without a story are left out.
     */
    val synopses: Map<Int, String> = emptyMap(),
    /** True once the viewer picked a show, which stops the spotlight moving on. */
    val picked: Boolean = false,
) {
    val show: AnimeCard get() = shows[index]

    public fun logo(animeId: Int): Logo = logos[animeId] ?: Logo.Loading

    /** The art behind [card]: its fan art, or AniList's art without any. Null until AniZip answers, so it fades in once. */
    public fun backdrop(card: AnimeCard): String? = if (card.id in logos) fanArt[card.id] ?: card.art else null
}

/** A show's title logo. Without one, the title is shown as text. */
public sealed interface Logo {
    public data object Loading : Logo

    public data object None : Logo

    public data class Image(
        val url: String,
    ) : Logo
}

/**
 * Loads Home's rows from AniList. This season is [season] of [year]. The spotlight features the first shows of the
 * first row that loads, with logos and fan art from [aniZip], or backgrounds from [fanartTv] without fan art. While it's
 * in view it moves on every 10 seconds, until the viewer picks a show.
 */
public class HomeViewModel(
    private val client: AniListClient,
    private val aniZip: AniZipClient,
    private val fanartTv: FanartTvClient,
    private val season: AnimeSeason,
    private val year: Int,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState(HomeRow.entries.associateWith { RowState.Loading }))
    public val state: StateFlow<HomeState> = mutableState.asStateFlow()
    private var spotlightInView = false
    private var rotation: Job? = null

    init {
        HomeRow.entries.forEach { row ->
            viewModelScope.launch {
                val result = client.row(row, season, year)
                val rowState = if (result is AniListResult.Success) RowState.Loaded(result.value.items) else RowState.Failed
                val hadSpotlight = state.value.spotlight != null
                // The spotlight arrives with the row it comes from, so the screen never sees one without the other.
                mutableState.update { state ->
                    val next = state.copy(rows = state.rows + (row to rowState))
                    next.copy(spotlight = next.spotlight ?: next.featured()?.let(::Spotlight))
                }
                if (!hadSpotlight) state.value.spotlight?.let { startSpotlight(it.shows) }
            }
        }
    }

    /** Shows the show at [index] and stops the spotlight moving on. */
    public fun pick(index: Int) {
        updateSpotlight { it.copy(index = index, picked = true) }
        rotate()
    }

    /** Call with true while the spotlight is on screen, and false once it isn't, so it only moves on while seen. */
    public fun watchSpotlight(inView: Boolean) {
        spotlightInView = inView
        rotate()
    }

    private fun startSpotlight(shows: List<AnimeCard>) {
        viewModelScope.launch {
            val result = client.synopses(shows.map { it.id }) as? AniListResult.Success ?: return@launch
            val stories =
                result.value.mapNotNull { (id, description) ->
                    parseSynopsis(description).story?.let {
                        id to
                            it.replace(paragraphBreak, " ")
                    }
                }
            updateSpotlight { it.copy(synopses = stories.toMap()) }
        }
        shows.forEach { show ->
            viewModelScope.launch {
                val art = showArt(client, aniZip, fanartTv, show.id)
                val logo = art?.logo?.let(Logo::Image) ?: Logo.None
                updateSpotlight { spotlight ->
                    spotlight.copy(
                        logos = spotlight.logos + (show.id to logo),
                        fanArt = art?.fanArt?.let { spotlight.fanArt + (show.id to it) } ?: spotlight.fanArt,
                    )
                }
            }
        }
        rotate()
    }

    /** Starts the 10 second timer over, if the spotlight should be moving on. */
    private fun rotate() {
        rotation?.cancel()
        val spotlight = state.value.spotlight
        if (!spotlightInView || spotlight == null || spotlight.picked) return
        rotation =
            viewModelScope.launch {
                while (true) {
                    delay(ROTATE_EVERY)
                    updateSpotlight { it.copy(index = (it.index + 1) % it.shows.size) }
                }
            }
    }

    private fun updateSpotlight(change: (Spotlight) -> Spotlight) {
        mutableState.update { state -> state.copy(spotlight = state.spotlight?.let(change)) }
    }
}

/** One page of [row]'s list. This season is [season] of [year]. */
internal suspend fun AniListClient.row(
    row: HomeRow,
    season: AnimeSeason,
    year: Int,
    page: Int = 1,
): AniListResult<AnimePage> =
    when (row) {
        HomeRow.TRENDING -> trending(page)
        HomeRow.THIS_SEASON -> season(season, year, page)
        HomeRow.POPULAR -> popular(page)
    }

/** The first shows of the first row that loaded, waiting for the rows above it. Null while waiting or if none did. */
private fun HomeState.featured(): List<AnimeCard>? {
    for (row in HomeRow.entries) {
        when (val rowState = rows[row]) {
            RowState.Loading -> return null
            is RowState.Loaded -> if (rowState.items.isNotEmpty()) return rowState.items.take(FEATURED_SHOWS)
            else -> continue
        }
    }
    return null
}
