package ani.saikou.neo.shared.details

import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AniZipEpisode
import ani.saikou.neo.shared.catalogue.AniZipEpisodes
import ani.saikou.neo.shared.catalogue.AniZipExtra
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.EpisodeKind
import ani.saikou.neo.shared.home.WatchProgress

private const val RANGE = 50

// A show of up to 100 episodes jumps a dozen at a time, about a cour each.
private const val SHORT_RANGE = 12
private const val SHORT_SERIES = 100

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
    /** How many of [episodes] are out by today. One without an air date counts as out. */
    val aired: Int = count,
    /** MAL's filler and recap episodes by number. Empty until MAL answers, and for a show it doesn't know. */
    val kinds: Map<Int, EpisodeKind> = emptyMap(),
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
 * The Episodes tab's list: AniList's count, with whatever [aniZip] knows of each episode. Where AniZip has no date or
 * length, AniList's airing schedule and episode length fill in, its dates read on a clock [zoneOffsetSeconds] ahead of
 * UTC. A film takes AniList's start date instead. AniList's next episode and those after it haven't aired.
 * Without one, an episode dated after [today] hasn't. Unless [showUpcoming], the list leaves out the episodes that
 * haven't aired, though its count and how many have aired stay the same.
 */
public fun episodeList(
    details: AnimeDetails,
    aniZip: AniZipEpisodes?,
    today: AirDate,
    showUpcoming: Boolean = true,
    zoneOffsetSeconds: Int = 0,
): EpisodeList {
    val count = episodeCount(details, aniZip)
    val known = aniZip?.episodes.orEmpty().associateBy { it.number }
    val film = details.card.format == "MOVIE"
    val episodes =
        (1..count).map { number ->
            val episode = known[number] ?: AniZipEpisode(number, null, null, null, null, null)
            // A film's schedule has its first showing in Japan, which can fall the day before the release AniList lists.
            val scheduled = details.airingAt[number]?.let { AirDate.at(it, zoneOffsetSeconds) }
            val aired = if (film) details.startDate ?: scheduled else scheduled
            episode.copy(airDate = episode.airDate ?: aired, minutes = episode.minutes ?: details.episodeMinutes)
        }
    val aired = episodes.filter { it.hasAired(today, details.nextEpisode?.number) }
    return EpisodeList(
        count = count,
        countSoFar = details.card.episodes == null,
        film = film,
        episodes = if (showUpcoming) episodes else aired,
        extras = if (film) aniZip?.extras.orEmpty() else emptyList(),
        aired = aired.size,
    )
}

// AniList knows which episode is next. AniZip's dates can be a day out, so they only count without it.
private fun AniZipEpisode.hasAired(
    today: AirDate,
    next: Int?,
): Boolean = if (next != null) number < next else airDate == null || airDate <= today

/**
 * Ranges to jump between: of 12, such as 1–12 and 13–22, for a show of up to 100 episodes, and of 50, such as 1–50 and
 * 51–100, for a longer one. A show that fits in one range has none. A few left over join the last range, as in
 * 151–201, rather than making a range of their own.
 */
public fun episodeRanges(count: Int): List<IntRange> {
    val size = if (count <= SHORT_SERIES) SHORT_RANGE else RANGE
    if (count <= size) return emptyList()
    val ranges = (1..count step size).map { it..minOf(it + size - 1, count) }
    val last = ranges.last()
    if (last.count() >= size / 2) return ranges
    return ranges.dropLast(2) + listOf(ranges[ranges.size - 2].first..last.last)
}

/** How far this TV got in one episode. */
public sealed interface EpisodeMark {
    public data object Watched : EpisodeMark

    public data class InProgress(
        val progress: WatchProgress,
    ) : EpisodeMark
}

/** Episodes before the one in progress count as watched. Those after it aren't started. */
public fun WatchProgress.markFor(episode: Int): EpisodeMark? =
    when {
        episode < this.episode -> EpisodeMark.Watched
        episode == this.episode -> EpisodeMark.InProgress(this)
        else -> null
    }

/** The episode to watch next: the one in [progress] if it's listed, or else the first. */
public fun EpisodeList.nextToWatch(progress: WatchProgress?): Int? =
    episodes.firstOrNull { it.number == progress?.episode }?.number ?: episodes.firstOrNull()?.number
