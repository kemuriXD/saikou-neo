package ani.saikou.neo.shared.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.account.ListedProgress
import ani.saikou.neo.shared.account.WatchedEpisodes
import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.ArtLibrary
import ani.saikou.neo.shared.catalogue.EpisodeKind
import ani.saikou.neo.shared.catalogue.ShowArt
import ani.saikou.neo.shared.catalogue.art
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.shared.home.USUAL_EPISODE
import ani.saikou.neo.shared.home.PickUp
import ani.saikou.neo.shared.home.PickUpSides
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.shared.home.airedEpisodes
import ani.saikou.neo.shared.home.pickUp
import ani.saikou.neo.shared.home.picksUpAt
import ani.saikou.neo.shared.home.upNext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
        /** AniList's score at first. The other sites' join as they answer. */
        val ratings: Ratings = Ratings(details.card.averageScore),
        /** How far this TV got in the show, or null if it hasn't been watched here. */
        val progress: WatchProgress? = null,
        /** Where AniList has the signed-in user in the show, if it's on their Watching or Rewatching list. */
        val listed: ListedProgress? = null,
        /** The signed-in user's AniList progress, whatever the show's status. Null signed out or when it can't be read. */
        val aniList: AniListWatched? = null,
    ) : DetailsState {
        /** Whether [episode] is watched, or how much of it is (ADR 0052). */
        public fun markFor(episode: Int): EpisodeMark? = episodeMark(episode, place, aniList?.progress)

        /** Where to pick up from this TV's place and AniList's, and whether to ask (ADR 0050). */
        val pickUp: PickUp? get() = pickUp(progress, listed)

        /** The place Details carries on from until the viewer picks another. */
        val place: WatchProgress? get() = pickUp?.place

        /** Where to carry on: [place], or the next episode once it's watched to the end. Null with nothing left that's out. */
        val upNext: WatchProgress? get() = place?.upNext(airedEpisodes(details.card.episodes, details.nextEpisode?.number))
    }

    public data object Failed : DetailsState
}

/** How many episodes of the show [userId] has watched on AniList: 0 when it isn't on their list. */
public data class AniListWatched(
    val userId: Int,
    val progress: Int,
)

/**
 * Loads one anime's details from AniList, and its backdrop and logo from [library] at the same time. Art found while the
 * viewer rested on the show's card is there at once; otherwise Details waits up to [ART_WAIT] for it. The title is the
 * logo only if the art came by then, so the title never swaps in front of the viewer. A backdrop found later still fades
 * in. The episodes come from AniZip once AniList's details are in, with AniList's dates where AniZip has none, read on
 * a clock [zoneOffsetSeconds] ahead of UTC. Those after [today] haven't aired, and they're left out of the list unless
 * [showUpcomingEpisodes]. The [raters] are asked once AniList's details are in, and for IMDb once AniZip gives its id.
 * MAL's filler and recap episodes join the list when MAL answers.
 * [progress] is how far this TV got in the show, if anywhere, and again each time the player saves more.
 * Opening it calls [warmSources], so the sources' sites are ready by the time an episode is played.
 */
public class DetailsViewModel(
    private val client: AniListClient,
    private val library: ArtLibrary,
    private val aniZip: AniZipClient,
    private val raters: Raters,
    private val animeId: Int,
    private val today: AirDate,
    private val showUpcomingEpisodes: Boolean = true,
    private val progress: Flow<WatchProgress?> = flowOf(null),
    private val listed: suspend () -> ListedProgress? = { null },
    private val sides: PickUpSides? = null,
    private val marks: WatchedEpisodes? = null,
    private val marked: suspend () -> Unit = {},
    private val zoneOffsetSeconds: Int = 0,
    warmSources: () -> Unit = {},
) : ViewModel() {
    private val mutableState = MutableStateFlow<DetailsState>(DetailsState.Loading)
    public val state: StateFlow<DetailsState> = mutableState.asStateFlow()

    init {
        warmSources()
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
            // The episodes and ratings can come before or after Details shows, so they're kept here as well as in the state.
            var episodes: EpisodeList? = null
            var kinds = emptyMap<Int, EpisodeKind>()
            var ratings = Ratings(result.value.card.averageScore)

            fun rate(change: (Ratings) -> Ratings) {
                ratings = change(ratings)
                mutableState.update { (it as? DetailsState.Loaded)?.copy(ratings = ratings) ?: it }
            }

            fun show(list: EpisodeList) {
                val shown = list.copy(kinds = kinds)
                episodes = shown
                mutableState.update { (it as? DetailsState.Loaded)?.copy(episodes = shown) ?: it }
            }
            launch { raters.mal(result.value)?.let { score -> rate { it.copy(mal = score) } } }
            // MAL can take a few seconds for a long show, so its filler and recaps join the list once it answers.
            launch {
                kinds = raters.episodeKinds(result.value) ?: return@launch
                episodes?.let(::show)
            }
            launch {
                val aniZipEpisodes = aniZip.episodes(animeId)
                show(episodeList(result.value, aniZipEpisodes, today, showUpcomingEpisodes, zoneOffsetSeconds))
                val omdb = aniZipEpisodes?.imdbId?.let { raters.omdb(result.value, it) } ?: return@launch
                rate { it.copy(imdb = omdb.imdb, rottenTomatoes = omdb.rottenTomatoes) }
            }
            // The list is read while the art is found. It can come before or after Details shows.
            var onList: ListedProgress? = null
            launch {
                onList = listed() ?: return@launch
                mutableState.update { (it as? DetailsState.Loaded)?.copy(listed = onList) ?: it }
            }
            // So is the entry the marks come from, which then follows each change the TV makes to it (ADR 0052).
            var watched: AniListWatched? = null
            launch {
                val read = marks?.entry(animeId) ?: return@launch
                watched = AniListWatched(read.userId, read.show.entry?.progress ?: 0)
                mutableState.update { (it as? DetailsState.Loaded)?.copy(aniList = watched) ?: it }
                marks.changes.filter { it.animeId == animeId && it.userId == read.userId }.collect { change ->
                    val progress = if (change.status == null) 0 else change.progress ?: return@collect
                    watched = watched?.copy(progress = progress)
                    mutableState.update { (it as? DetailsState.Loaded)?.withAniList(progress) ?: it }
                }
            }
            val found = withTimeoutOrNull(ART_WAIT) { art.await() }
            val saved = progress.stateIn(this)
            launch { saved.collect { now -> mutableState.update { (it as? DetailsState.Loaded)?.copy(progress = now) ?: it } } }
            val loaded = DetailsState.Loaded(result.value, episodes = episodes, ratings = ratings, progress = saved.value, listed = onList, aniList = watched)
            if (found != null) {
                mutableState.value = loaded.withArt(found)
                return@launch
            }
            mutableState.value = loaded.copy(logo = Logo.None)
            val shown = art.await()
            mutableState.update { (it as DetailsState.Loaded).withArt(shown, logo = Logo.None) }
        }
    }

    /** AniList's place was picked: this TV carries on there. */
    public fun keepAniList() {
        val asked = (state.value as? DetailsState.Loaded)?.pickUp as? PickUp.Ask ?: return
        mutableState.update { (it as? DetailsState.Loaded)?.copy(progress = asked.aniList) ?: it }
        sides?.saveHere(animeId, asked.aniList)
    }

    /** This TV's place was picked: AniList moves to the episodes watched here. */
    public fun keepThisTv() {
        val loaded = state.value as? DetailsState.Loaded ?: return
        val asked = loaded.pickUp as? PickUp.Ask ?: return
        val listed = loaded.listed ?: return
        val watched = asked.thisTv.picksUpAt - 1
        mutableState.update { (it as? DetailsState.Loaded)?.withAniList(watched)?.copy(listed = listed.copy(progress = watched)) ?: it }
        viewModelScope.launch { sides?.moveAniList(animeId, watched, listed) }
    }

    /** Mark watched up to here: AniList, and this TV's place, move to [episode] (ADR 0052). */
    public fun markWatched(episode: Int): Unit = mark(episode, watched = true)

    /** Mark unwatched: AniList goes back to the episode before [episode], and so does a place here past it. */
    public fun markUnwatched(episode: Int): Unit = mark(episode, watched = false)

    // Shows straight away, and goes back if AniList doesn't take it.
    private fun mark(
        episode: Int,
        watched: Boolean,
    ) {
        val marks = marks ?: return
        val before = (state.value as? DetailsState.Loaded)?.aniList ?: return
        if (watched == (episode <= before.progress)) return
        val to = if (watched) episode else episode - 1
        mutableState.update { (it as? DetailsState.Loaded)?.withAniList(to) ?: it }
        viewModelScope.launch {
            val sent =
                if (watched) marks.markWatched(animeId, episode, before.userId) else marks.markUnwatched(animeId, episode, before.userId)
            if (!sent) {
                // Unless another mark has shown since.
                mutableState.update { (it as? DetailsState.Loaded)?.takeIf { it.aniList?.progress == to }?.withAniList(before.progress) ?: it }
                return@launch
            }
            moveHere(episode, watched)
            marked()
        }
    }

    // This TV's place moves with a mark, so the two agree and Details doesn't ask where to pick up.
    private fun moveHere(
        episode: Int,
        watched: Boolean,
    ) {
        val loaded = state.value as? DetailsState.Loaded ?: return
        val here = loaded.progress
        val length = here?.length ?: loaded.details.episodeMinutes?.minutes ?: USUAL_EPISODE
        val moved =
            when {
                watched && (here == null || here.picksUpAt <= episode) -> WatchProgress(episode, length, length, null)
                !watched && here != null && here.picksUpAt > episode -> WatchProgress(episode, Duration.ZERO, length, null)
                else -> return
            }
        mutableState.update { (it as? DetailsState.Loaded)?.copy(progress = moved) ?: it }
        sides?.saveHere(animeId, moved)
    }
}

private fun DetailsState.Loaded.withAniList(progress: Int) =
    copy(aniList = aniList?.copy(progress = progress), listed = listed?.copy(progress = progress))

private fun DetailsState.Loaded.withArt(
    art: ShowArt,
    logo: Logo = art.logo,
) = copy(backdrop = art.backdrop ?: details.card.art, logo = logo)
