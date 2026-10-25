package ani.saikou.neo.shared.catalogue

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Every show's art, found once and remembered while the app runs, so Home, See more and Details share one answer per
 * show. The search runs in the library's own [scope], so art fetched ahead for a card survives the screen that asked.
 * Use it from the main thread.
 */
public class ArtLibrary internal constructor(
    private val sources: ArtSources,
    private val scope: CoroutineScope,
) {
    public constructor(
        aniList: AniListClient,
        aniZip: AniZipClient,
        fanartTv: FanartTvClient,
        tmdb: TmdbClient,
    ) : this(ArtSources(aniList, aniZip, fanartTv, tmdb), CoroutineScope(SupervisorJob() + Dispatchers.Main))

    private val found = mutableMapOf<Int, Deferred<ShowArt>>()

    public suspend fun art(card: AnimeCard): ShowArt = art(card.id) { card }

    /** [animeId]'s art. Its [card] is only asked for if TMDB needs the show's title and year. */
    internal suspend fun art(
        animeId: Int,
        card: suspend () -> AnimeCard?,
    ): ShowArt = find(animeId, card).await()

    private fun find(
        animeId: Int,
        card: suspend () -> AnimeCard?,
    ): Deferred<ShowArt> {
        // A search is cancelled when the screen that handed over the card closes first. The next ask starts over.
        found[animeId]?.takeUnless { it.isCancelled }?.let { return it }
        return scope.async { showArt(sources, animeId, card) }.also { found[animeId] = it }
    }
}
