package ani.saikou.neo.shared.catalogue

import ani.saikou.neo.shared.home.Logo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Everywhere a show's art comes from. */
internal class ArtSources(
    val aniList: AniListClient,
    val aniZip: AniZipClient,
    val fanartTv: FanartTvClient,
    val tmdb: TmdbClient,
)

/** A show's backdrop, null when no source has one, and its title logo. */
public data class ShowArt(
    val backdrop: String?,
    val logo: Logo,
)

/** How TMDB and fanart.tv know a show. Either can be missing. */
private class ShowIds(
    val tmdb: TmdbId?,
    val fanartTv: FanartTvId?,
) {
    val none get() = tmdb == null && fanartTv == null
}

/** One show's own picks, before any from its series. */
private class Picks(
    val logo: ArtImage?,
    val backdrop: String?,
)

internal suspend fun showArt(
    sources: ArtSources,
    card: AnimeCard,
): ShowArt = showArt(sources, card.id) { card }

/**
 * [animeId]'s art. Its logo is TMDB's, else fanart.tv's, in English or no language, else in the show's own language.
 * Its backdrop is TMDB's, fanart.tv's or AniZip's, full HD or more. AniZip tells TMDB and fanart.tv which show it is,
 * and a film is looked up on TMDB by its IMDb id, or by the title and year on its [card], which is only asked for when
 * needed. A series AniZip has no TMDB id for is looked up by its title and year too. A show without ids of its own,
 * such as a season too new for AniZip, takes the logo of the series AniList says it belongs to, follows or comes from.
 * A show without a backdrop of its own borrows the series' too. Callers fall back to AniList's banner or cover.
 */
internal suspend fun showArt(
    sources: ArtSources,
    animeId: Int,
    card: suspend () -> AnimeCard?,
): ShowArt {
    val aniZip = sources.aniZip.art(animeId)
    val ids = sources.ids(aniZip, card)
    val own = sources.picks(aniZip, ids, card)
    if (own.backdrop != null && (own.logo != null || !ids.none)) return ShowArt(own.backdrop, own.logo.asLogo())
    val series = sources.seriesPicks(animeId, card)
    val logo = own.logo ?: series?.logo?.takeIf { ids.none }
    return ShowArt(own.backdrop ?: series?.backdrop, logo.asLogo())
}

private fun ArtImage?.asLogo() = this?.let { Logo.Image(it.url) } ?: Logo.None

// The series is from the same country as the show, so the show's card tells its language.
private suspend fun ArtSources.seriesPicks(
    animeId: Int,
    card: suspend () -> AnimeCard?,
): Picks? {
    val series = (aniList.relatedSeries(animeId) as? AniListResult.Success)?.value?.firstOrNull() ?: return null
    val aniZip = aniZip.art(series)
    return picks(aniZip, ids(aniZip) { null }, card)
}

private suspend fun ArtSources.ids(
    aniZip: AniZipArt?,
    card: suspend () -> AnimeCard?,
): ShowIds {
    val film = aniZip?.film ?: (card()?.format == "MOVIE")
    if (!film) {
        // AniZip has no TMDB id for some series, such as Chinese ONAs, so TMDB is searched for them.
        val tmdbSeries = aniZip?.tmdbId ?: card()?.let { searchSeries(it) }
        return ShowIds(tmdbSeries?.let { TmdbId(it, film = false) }, aniZip?.tvDbId?.let(FanartTvId::Series))
    }
    // AniZip's TMDB id for a film is sometimes the id of its TV series, so the film is found again.
    val tmdbFilm = aniZip?.imdbId?.let { tmdb.findFilm(it) } ?: card()?.let { searchFilm(it) }
    val fanartTvFilm = tmdbFilm?.toString() ?: aniZip?.imdbId
    return ShowIds(tmdbFilm?.let { TmdbId(it, film = true) }, fanartTvFilm?.let(FanartTvId::Film))
}

private suspend fun ArtSources.searchFilm(card: AnimeCard): Int? {
    val title = card.title.english ?: card.title.romaji ?: return null
    return tmdb.searchFilm(title, card.seasonYear)
}

private suspend fun ArtSources.searchSeries(card: AnimeCard): Int? {
    val title = card.title.english ?: card.title.romaji ?: return null
    return tmdb.searchSeries(title, card.seasonYear)
}

/** Asks TMDB and fanart.tv at once, so a slow answer from one doesn't hold up the other. */
private suspend fun ArtSources.picks(
    aniZip: AniZipArt?,
    ids: ShowIds,
    card: suspend () -> AnimeCard?,
): Picks =
    coroutineScope {
        val tmdbImages = async { ids.tmdb?.let { tmdb.images(it) } }
        val fanartTvArt = async { ids.fanartTv?.let { fanartTv.art(it) } }
        val tmdbLogos = tmdbImages.await()?.logos.orEmpty()
        val fanartTvLogos = fanartTvArt.await()?.logos.orEmpty()
        // An English logo from either comes before one in the show's own language, which needs its card.
        val logo =
            pickLogo(tmdbLogos) ?: pickLogo(fanartTvLogos)
                ?: card()?.language?.let { pickLogo(tmdbLogos, it) ?: pickLogo(fanartTvLogos, it) }
        val backdrop =
            pickBackdrop(tmdbImages.await()?.backdrops.orEmpty())?.url
                ?: pickBackdrop(fanartTvArt.await()?.backgrounds.orEmpty())?.url
                ?: fullHdFanArt(aniZip?.fanArt)
        Picks(logo, backdrop)
    }
