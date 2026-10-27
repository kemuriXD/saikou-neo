package ani.saikou.neo.shared.details

import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AniZipEpisode
import ani.saikou.neo.shared.catalogue.AniZipEpisodes
import ani.saikou.neo.shared.catalogue.AniZipExtra
import ani.saikou.neo.shared.catalogue.AnimeDetails

private const val RANGE = 50

/** A show's episodes for its Episodes tab. They come from the catalogue, never from extensions. */
public data class EpisodeList(
    val count: Int,
    /** AniList doesn't say how many there will be, so [count] is how many are out so far. */
    val countSoFar: Boolean,
    val film: Boolean,
    /**
     * Every episode from 1 to [count], or only those out when upcoming ones are left out. One AniZip doesn't know has
     * only its number.
     */
    val episodes: List<AniZipEpisode>,
    /** A film's extras, such as its making-of. */
    val extras: List<AniZipExtra>,
    /** No episode has a title or an air date, so the list says they're unavailable. */
    val detailsMissing: Boolean,
    /** How many of [episodes] are out by today. One without an air date counts as out. */
    val aired: Int = count,
)

/**
 * How many episodes [details] has, from AniList: its total, or the episodes out before the next one airs. When AniList
 * knows neither, AniZip's episodes count.
 */
public fun episodeCount(
    details: AnimeDetails,
    aniZip: AniZipEpisodes?,
): Int =
    details.card.episodes
        ?: details.nextEpisode?.number?.minus(1)
        ?: aniZip?.episodes?.size
        ?: 0

/**
 * The Episodes tab's list: AniList's count, with whatever [aniZip] knows of each episode. AniList's next episode and
 * those after it haven't aired. Without one, an episode dated after [today] hasn't. Unless [showUpcoming], the list
 * leaves out the episodes that haven't aired, though its count and how many have aired stay the same.
 */
public fun episodeList(
    details: AnimeDetails,
    aniZip: AniZipEpisodes?,
    today: AirDate,
    showUpcoming: Boolean = true,
): EpisodeList {
    val count = episodeCount(details, aniZip)
    val known = aniZip?.episodes.orEmpty().associateBy { it.number }
    val episodes = (1..count).map { known[it] ?: AniZipEpisode(it, null, null, null, null, null) }
    val film = details.card.format == "MOVIE"
    val aired = episodes.filter { it.hasAired(today, details.nextEpisode?.number) }
    return EpisodeList(
        count = count,
        countSoFar = details.card.episodes == null,
        film = film,
        episodes = if (showUpcoming) episodes else aired,
        extras = if (film) aniZip?.extras.orEmpty() else emptyList(),
        detailsMissing = episodes.none { it.title != null || it.airDate != null },
        aired = aired.size,
    )
}

// AniList knows which episode is next. AniZip's dates can be a day out, so they only count without it.
private fun AniZipEpisode.hasAired(
    today: AirDate,
    next: Int?,
): Boolean = if (next != null) number < next else airDate == null || airDate <= today

/**
 * Ranges of 50 to jump between, such as 1–50 and 51–100, for a show with more than 50 episodes. A few left over join
 * the last range, as in 151–201, rather than making a range of their own.
 */
public fun episodeRanges(count: Int): List<IntRange> {
    if (count <= RANGE) return emptyList()
    val ranges = (1..count step RANGE).map { it..minOf(it + RANGE - 1, count) }
    val last = ranges.last()
    if (last.count() >= RANGE / 2) return ranges
    return ranges.dropLast(2) + listOf(ranges[ranges.size - 2].first..last.last)
}
