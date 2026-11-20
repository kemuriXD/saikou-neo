package ani.saikou.neo.tv.home

import ani.saikou.neo.shared.home.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class ContinueCardTest {
    private fun progress(still: String?) = WatchProgress(4, 0.minutes, 24.minutes, still)

    @Test
    fun theEpisodesStillComesFirst() {
        val show = card(1).copy(bannerImageUrl = "banner.jpg")

        assertEquals("still.jpg", continueArt(show, progress("still.jpg")))
    }

    // A show from your AniList list has no still until it's played on this TV.
    @Test
    fun withoutAStillTheShowsBannerShows() {
        val show = card(1).copy(bannerImageUrl = "banner.jpg", coverImageUrl = "cover.jpg")

        assertEquals("banner.jpg", continueArt(show, progress(null)))
    }

    @Test
    fun withoutABannerTheCoverShows() {
        val show = card(1).copy(coverImageUrl = "cover.jpg")

        assertEquals("cover.jpg", continueArt(show, progress(null)))
    }
}
