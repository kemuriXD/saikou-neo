package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.catalogue.NextEpisode

private const val WEEK_SECONDS = 7 * 24 * 60 * 60L
private const val TOP_RATED = 10

/** Why the spotlight features a show, shown above its title. */
public sealed interface FeaturedReason {
    public data class NewEpisode(
        val airingAtEpochSeconds: Long,
    ) : FeaturedReason

    /** Its place among AniList's highest rated shows of all time. */
    public data class HighestRated(
        val rank: Int,
    ) : FeaturedReason

    /** Its place in Home's Trending row. */
    public data class Trending(
        val rank: Int,
    ) : FeaturedReason
}

/**
 * The best thing to say about a show: an episode out within a week, then a place in the all-time top 10 rated, then
 * its place in Trending. Null when none of them apply.
 */
internal fun featuredReason(
    nextEpisode: NextEpisode?,
    ratedRank: Int?,
    trendingRank: Int?,
    nowEpochSeconds: Long,
): FeaturedReason? =
    when {
        nextEpisode != null && nextEpisode.airingAtEpochSeconds - nowEpochSeconds in 0..WEEK_SECONDS -> {
            FeaturedReason.NewEpisode(nextEpisode.airingAtEpochSeconds)
        }

        ratedRank != null && ratedRank <= TOP_RATED -> {
            FeaturedReason.HighestRated(ratedRank)
        }

        trendingRank != null -> {
            FeaturedReason.Trending(trendingRank)
        }

        else -> {
            null
        }
    }
