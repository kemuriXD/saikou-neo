package ani.saikou.neo.shared.details

import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.MalClient
import ani.saikou.neo.shared.catalogue.OmdbClient
import ani.saikou.neo.shared.catalogue.OmdbRatings

/** A show's ratings, each out of 100 and shown out of 10. A site without a rating for it is null. */
public data class Ratings(
    val aniList: Int?,
    val mal: Int? = null,
    val imdb: Int? = null,
    /** Only for films. */
    val rottenTomatoes: Int? = null,
) {
    /** The average of the ratings there are, rounded, or null without any. */
    val mean: Int?
        get() {
            val all = listOfNotNull(aniList, mal, imdb, rottenTomatoes)
            return if (all.isEmpty()) null else (all.sum() * 2 + all.size) / (all.size * 2)
        }
}

/** The sites Details asks for ratings beyond AniList's: MyAnimeList, and OMDb for IMDb and Rotten Tomatoes. */
public class Raters(
    private val mal: MalClient,
    private val omdb: OmdbClient,
) {
    internal suspend fun mal(details: AnimeDetails): Int? = details.malId?.let { mal.score(it) }

    /**
     * IMDb's rating, and Rotten Tomatoes' for a film, for the show with IMDb id [imdbId]. AniZip sometimes gives a film its
     * series' id or a series a film's, so an entry OMDb lists as the other kind is left out.
     */
    internal suspend fun omdb(
        details: AnimeDetails,
        imdbId: String,
    ): OmdbRatings? {
        val film = details.card.format == "MOVIE"
        return omdb.ratings(imdbId)?.takeIf { it.film == film }?.let { if (film) it else it.copy(rottenTomatoes = null) }
    }
}
