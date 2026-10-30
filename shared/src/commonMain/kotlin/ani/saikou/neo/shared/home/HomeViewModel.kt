package ani.saikou.neo.shared.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimePage
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.ArtLibrary
import ani.saikou.neo.shared.catalogue.art
import ani.saikou.neo.shared.details.parseSynopsis
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private const val FEATURED_SHOWS = 6
private val paragraphBreak = Regex("""\s*\n\s*""")
private val ROTATE_EVERY = 10.seconds
internal val RETRY_EVERY = 30.seconds

/** Home's rows, top to bottom. Continue watching comes from this TV, the rest from AniList. */
public enum class HomeRow { CONTINUE_WATCHING, TRENDING, THIS_SEASON, POPULAR }

private val aniListRows = HomeRow.entries - HomeRow.CONTINUE_WATCHING

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
    /** The logo of the card focus rests on, once found, for the screen to start loading. */
    val restingLogo: Logo.Image? = null,
    /** How far this TV got into each show in Continue watching, by anime id. */
    val progress: Map<Int, WatchProgress> = emptyMap(),
    /** True when every AniList row failed because AniList couldn't be reached. */
    val offline: Boolean = false,
)

/** The shows featured at the top of Home, and which one is showing. */
public data class Spotlight(
    val shows: List<AnimeCard>,
    /** The row the shows come from. */
    val row: HomeRow,
    val index: Int = 0,
    /** Each show's logo, by anime id, once its art has been found. */
    val logos: Map<Int, Logo> = emptyMap(),
    /** Each show's backdrop, or its series' when it has none, by anime id. Shows without any are left out. */
    val fanArt: Map<Int, String> = emptyMap(),
    /**
     * Each show's story as one paragraph, by anime id, once AniList has sent it. The spotlight shows only a few lines,
     * so paragraph breaks would leave them half empty. Shows without a story are left out.
     */
    val synopses: Map<Int, String> = emptyMap(),
    /** Why each show is featured, by anime id, once known. Shows with nothing to say are left out. */
    val reasons: Map<Int, FeaturedReason> = emptyMap(),
    /** True once AniList has answered for [synopses] and [reasons], or failed to. */
    val detailsLoaded: Boolean = false,
    /** True once the viewer picked a show, which stops the spotlight moving on. */
    val picked: Boolean = false,
) {
    val show: AnimeCard get() = shows[index]

    public fun logo(animeId: Int): Logo = logos[animeId] ?: Logo.Loading

    /** The art behind [card]: its backdrop, or AniList's art without one. Null until its art is found, so it fades in once. */
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
 * first row that loads, with logos and backdrops from [library]. While it's in view it moves on every 10 seconds,
 * until the viewer picks a show. [now] is the time in seconds, for how soon a show's next episode is out.
 * When the rows fail for the network, Home is offline and tries again every 30 seconds while that's on screen.
 */
public class HomeViewModel(
    private val client: AniListClient,
    private val library: ArtLibrary,
    private val season: AnimeSeason,
    private val year: Int,
    private val now: () -> Long = { Clock.System.now().epochSeconds },
) : ViewModel() {
    // Nothing saves progress yet (#115), so Continue watching is empty from the start.
    private val mutableState =
        MutableStateFlow(
            HomeState(
                HomeRow.entries.associateWith { row ->
                    if (row == HomeRow.CONTINUE_WATCHING) RowState.Loaded(emptyList()) else RowState.Loading
                },
            ),
        )
    public val state: StateFlow<HomeState> = mutableState.asStateFlow()
    private var spotlightInView = false
    private var rotation: Job? = null
    private var offlineInView = false
    private var retrying: Job? = null

    private val resting = Resting(viewModelScope, library) { logo -> mutableState.update { it.copy(restingLogo = logo) } }

    init {
        aniListRows.forEach(::load)
    }

    /**
     * Loads the rows that failed again. They wait in their places as when Home starts, so one that answers first can't
     * push the others off the top of the list. Home stays offline until one loads.
     */
    public fun retry() {
        val failed = aniListRows.filter { state.value.rows[it] == RowState.Failed }
        if (failed.isEmpty()) return
        mutableState.update { state -> state.copy(rows = state.rows + failed.associateWith { RowState.Loading }) }
        failed.forEach(::load)
    }

    /** Call with true while the offline screen is shown, and false once it isn't, so Home only tries again while seen. */
    public fun watchOffline(inView: Boolean) {
        offlineInView = inView
        retryLater()
    }

    private fun load(row: HomeRow) {
        viewModelScope.launch {
            val result = client.row(row, season, year)
            val rowState = if (result is AniListResult.Success) RowState.Loaded(result.value.items) else RowState.Failed
            val network = (result as? AniListResult.Failure)?.error is AniListError.Network
            val hadSpotlight = state.value.spotlight != null
            // The spotlight arrives with the row it comes from, so the screen never sees one without the other.
            mutableState.update { state ->
                val next = state.copy(rows = state.rows + (row to rowState))
                next
                    .copy(
                        spotlight = next.spotlight ?: next.featured()?.let { (row, shows) -> Spotlight(shows, row) },
                        // Once offline, Home stays so while it tries again, until a row loads.
                        offline =
                            rowState !is RowState.Loaded &&
                                (state.offline || (network && aniListRows.all { next.rows[it] == RowState.Failed })),
                    )
            }
            if (!hadSpotlight) state.value.spotlight?.let(::startSpotlight)
            retryLater()
        }
    }

    /** Starts the 30 second timer over, if Home is offline and that's on screen. */
    private fun retryLater() {
        retrying?.cancel()
        if (!offlineInView || !state.value.offline) return
        retrying =
            viewModelScope.launch {
                delay(RETRY_EVERY)
                retry()
            }
    }

    /** Shows the show at [index] and stops the spotlight moving on. */
    public fun pick(index: Int) {
        updateSpotlight { it.copy(index = index, picked = true) }
        rotate()
    }

    /** Call with the card focus moves to, or null once it leaves the rows. Resting on one finds its art. */
    public fun rest(card: AnimeCard?) {
        resting.on(card)
    }

    /** Call with true while the spotlight is on screen, and false once it isn't, so it only moves on while seen. */
    public fun watchSpotlight(inView: Boolean) {
        spotlightInView = inView
        rotate()
    }

    private fun startSpotlight(spotlight: Spotlight) {
        val shows = spotlight.shows
        viewModelScope.launch {
            val facts = (client.spotlightFacts(shows.map { it.id }) as? AniListResult.Success)?.value.orEmpty()
            val stories =
                facts.mapNotNull { (id, show) ->
                    show.description
                        ?.let(::parseSynopsis)
                        ?.story
                        ?.let { id to it.replace(paragraphBreak, " ") }
                }
            // Trending's own order still says something when the rest doesn't load.
            val reasons =
                shows.withIndex().mapNotNull { (index, show) ->
                    val known = facts[show.id]
                    val trending = if (spotlight.row == HomeRow.TRENDING) index + 1 else null
                    featuredReason(known?.nextEpisode, known?.ratedRank, trending, now())?.let { show.id to it }
                }
            updateSpotlight { it.copy(synopses = stories.toMap(), reasons = reasons.toMap(), detailsLoaded = true) }
        }
        shows.forEach { show ->
            viewModelScope.launch {
                val art = library.art(show)
                updateSpotlight { spotlight ->
                    spotlight.copy(
                        logos = spotlight.logos + (show.id to art.logo),
                        fanArt = art.backdrop?.let { spotlight.fanArt + (show.id to it) } ?: spotlight.fanArt,
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
        HomeRow.CONTINUE_WATCHING -> error("Continue watching doesn't come from AniList")
        HomeRow.TRENDING -> trending(page)
        HomeRow.THIS_SEASON -> season(season, year, page)
        HomeRow.POPULAR -> popular(page)
    }

/** The first shows of the first row that loaded, and that row, waiting for the rows above it. Null while waiting or if none did. */
private fun HomeState.featured(): Pair<HomeRow, List<AnimeCard>>? {
    for (row in aniListRows) {
        when (val rowState = rows[row]) {
            RowState.Loading -> return null
            is RowState.Loaded -> if (rowState.items.isNotEmpty()) return row to rowState.items.take(FEATURED_SHOWS)
            else -> continue
        }
    }
    return null
}
