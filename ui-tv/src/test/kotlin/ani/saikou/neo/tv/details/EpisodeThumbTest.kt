package ani.saikou.neo.tv.details

import ani.saikou.neo.shared.catalogue.AniZipEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeThumbTest {
    private val backdrop = "https://image.tmdb.org/t/p/original/backdrop.jpg"

    @Test
    fun anEpisodeWithAStillShowsIt() =
        assertEquals(
            EpisodeThumb("https://artworks.thetvdb.com/1.jpg", dimmed = false),
            episode("https://artworks.thetvdb.com/1.jpg").thumb(backdrop),
        )

    @Test
    fun anEpisodeWithoutAStillShowsTheBackdropDimmed() = assertEquals(EpisodeThumb(backdrop, dimmed = true), episode(null).thumb(backdrop))

    @Test
    fun withoutABackdropEitherTheTileStaysEmpty() = assertNull(episode(null).thumb(null))

    private fun episode(thumbnail: String?) = AniZipEpisode(1, "Rebirth", null, thumbnail, null, null)
}
