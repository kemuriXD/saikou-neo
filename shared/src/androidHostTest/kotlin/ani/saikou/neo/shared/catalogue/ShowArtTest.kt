package ani.saikou.neo.shared.catalogue

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShowArtTest {
    private val dragonBallSuperFanArt = "https://artworks.thetvdb.com/banners/fanart/original/295068-1.jpg"
    private val noFanartTv = FanartTvClient(null, OfflineEngine)

    @Test
    fun aShowWithFanArtNeedsNothingMore() =
        runTest {
            val aniList = QueryEngine("relations", emptyMap())
            val aniZip = AniZipEngine("mappings")
            val fanartTv = FanartTvEngine("tv")

            val art = showArt(clientFor(aniList), AniZipClient(aniZip), FanartTvClient(KEY, fanartTv), 177709)

            assertEquals("https://artworks.thetvdb.com/banners/v4/series/423732/backgrounds/6654700131bab.jpg", art?.fanArt)
            assertTrue(aniList.requestBodies.isEmpty())
            assertEquals(1, aniZip.urls.size)
            assertTrue(fanartTv.requests.isEmpty())
        }

    // Spirited Away: AniZip has no fan art for films, so fanart.tv's background comes before any related series'.
    @Test
    fun withoutFanArtAShowGetsFanartTvsBackground() =
        runTest {
            val aniList = QueryEngine("relations", emptyMap())
            val fanartTv = FanartTvEngine("movie")

            val art = showArt(clientFor(aniList), AniZipClient(AniZipEngine("mappings-movie")), FanartTvClient(KEY, fanartTv), 199)

            assertEquals("https://assets.fanart.tv/fanart/spirited-away-50f0c9c556cad.jpg", art?.fanArt)
            assertEquals("https://webservice.fanart.tv/v3.2/movies/129", fanartTv.requests.single().url)
            assertTrue(aniList.requestBodies.isEmpty())
        }

    @Test
    fun withoutFanartTvArtAShowBorrowsItsSeriesFanArt() =
        runTest {
            val aniZip = AniZipClient(AniZipEngine("mappings-movie", byId = mapOf(21175 to "mappings-parent")))
            val fanartTv = FanartTvClient(KEY, FanartTvEngine("not-found", status = 404))

            val art = showArt(clientFor(RecordedEngine("relations")), aniZip, fanartTv, 199)

            assertEquals(dragonBallSuperFanArt, art?.fanArt)
        }

    // Dragon Ball Super: Beerus is too new for AniZip, but it belongs to Dragon Ball Super, which AniZip knows.
    @Test
    fun withoutFanArtAShowBorrowsItsSeriesFanArt() =
        runTest {
            val aniZip = AniZipEngine("mappings-unknown", byId = mapOf(21175 to "mappings-parent"))

            val art = showArt(clientFor(RecordedEngine("relations")), AniZipClient(aniZip), noFanartTv, 206814)

            assertEquals(dragonBallSuperFanArt, art?.fanArt)
            // The logo stays the show's own. Beerus has none.
            assertNull(art?.logo)
            assertEquals(
                listOf("https://api.ani.zip/mappings?anilist_id=206814", "https://api.ani.zip/mappings?anilist_id=21175"),
                aniZip.urls,
            )
        }

    @Test
    fun aShowWithoutFanArtOrASeriesHasNone() =
        runTest {
            val aniZip = AniZipEngine("mappings-unknown")

            val art = showArt(clientFor(RecordedEngine("relations-none")), AniZipClient(aniZip), noFanartTv, 21519)

            assertNull(art?.fanArt)
            assertEquals(1, aniZip.urls.size)
        }

    @Test
    fun noConnectionMeansNoFanArt() =
        runTest {
            val fanartTv = FanartTvClient(KEY, OfflineEngine)

            assertNull(showArt(clientFor(OfflineEngine), AniZipClient(OfflineEngine), fanartTv, 206814)?.fanArt)
        }

    private companion object {
        const val KEY = "test-project-key"
    }
}
