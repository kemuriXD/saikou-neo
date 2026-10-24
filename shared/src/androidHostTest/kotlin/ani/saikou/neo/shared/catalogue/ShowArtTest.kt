package ani.saikou.neo.shared.catalogue

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShowArtTest {
    private val dragonBallSuperFanArt = "https://artworks.thetvdb.com/banners/fanart/original/295068-1.jpg"

    @Test
    fun aShowWithFanArtNeedsNothingMore() =
        runTest {
            val aniList = QueryEngine("relations", emptyMap())
            val aniZip = AniZipEngine("mappings")

            val art = showArt(clientFor(aniList), AniZipClient(aniZip), 177709)

            assertEquals("https://artworks.thetvdb.com/banners/v4/series/423732/backgrounds/6654700131bab.jpg", art?.fanArt)
            assertTrue(aniList.requestBodies.isEmpty())
            assertEquals(1, aniZip.urls.size)
        }

    // Dragon Ball Super: Beerus is too new for AniZip, but it belongs to Dragon Ball Super, which AniZip knows.
    @Test
    fun withoutFanArtAShowBorrowsItsSeriesFanArt() =
        runTest {
            val aniZip = AniZipEngine("mappings-unknown", byId = mapOf(21175 to "mappings-parent"))

            val art = showArt(clientFor(RecordedEngine("relations")), AniZipClient(aniZip), 206814)

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

            val art = showArt(clientFor(RecordedEngine("relations-none")), AniZipClient(aniZip), 177709)

            assertNull(art?.fanArt)
            assertEquals(1, aniZip.urls.size)
        }

    @Test
    fun noConnectionMeansNoFanArt() = runTest { assertNull(showArt(clientFor(OfflineEngine), AniZipClient(OfflineEngine), 206814)?.fanArt) }
}
