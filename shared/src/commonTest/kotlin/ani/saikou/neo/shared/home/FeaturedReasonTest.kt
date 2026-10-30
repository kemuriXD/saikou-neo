package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.catalogue.NextEpisode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FeaturedReasonTest {
    private val now = 1_800_000_000L
    private val day = 24 * 60 * 60L

    private fun episodeIn(seconds: Long) = NextEpisode(5, now + seconds)

    @Test
    fun anEpisodeThisWeekComesFirst() {
        assertEquals(
            FeaturedReason.NewEpisode(now + 2 * day),
            featuredReason(episodeIn(2 * day), ratedRank = 1, trendingRank = 1, nowEpochSeconds = now),
        )
    }

    @Test
    fun anEpisodeMoreThanAWeekAwayOrAlreadyOutIsLeftOut() {
        assertNull(featuredReason(episodeIn(8 * day), ratedRank = null, trendingRank = null, nowEpochSeconds = now))
        assertNull(featuredReason(episodeIn(-60), ratedRank = null, trendingRank = null, nowEpochSeconds = now))
    }

    @Test
    fun aTopTenRatedShowSaysSoBeforeTrending() {
        assertEquals(FeaturedReason.HighestRated(3), featuredReason(null, ratedRank = 3, trendingRank = 1, nowEpochSeconds = now))
        assertEquals(FeaturedReason.Trending(1), featuredReason(null, ratedRank = 11, trendingRank = 1, nowEpochSeconds = now))
    }

    @Test
    fun nothingToSayWithoutAnyOfThem() {
        assertNull(featuredReason(null, ratedRank = null, trendingRank = null, nowEpochSeconds = now))
    }
}
