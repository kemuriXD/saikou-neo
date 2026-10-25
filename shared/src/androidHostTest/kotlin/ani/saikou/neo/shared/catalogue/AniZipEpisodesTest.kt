package ani.saikou.neo.shared.catalogue

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AniZipEpisodesTest {
    // Death Note: a finished show, where AniZip knows every episode.
    @Test
    fun readsEachEpisodesTitleSynopsisThumbnailDateAndLength() =
        runTest {
            val episodes = AniZipClient(AniZipEngine("episodes-death-note")).episodes(1535)

            val first = episodes?.episodes?.first()
            assertEquals(1, first?.number)
            assertEquals("Rebirth", first?.title)
            assertTrue(first?.synopsis.orEmpty().startsWith("Light Yagami is a high school genius"))
            assertEquals("https://artworks.thetvdb.com/banners/episodes/79481/311257.jpg", first?.thumbnail)
            assertEquals(AirDate(2006, 10, 4), first?.airDate)
            assertEquals(23, first?.minutes)
            assertEquals(listOf(1, 2, 3), episodes?.episodes?.map { it.number })
        }

    @Test
    fun specialsAreExtras() =
        runTest {
            val episodes = AniZipClient(AniZipEngine("episodes-death-note")).episodes(1535)

            assertEquals(
                listOf(AniZipExtra("Death Note Special Compilation", 25), AniZipExtra("Ending of Episode 37", 2)),
                episodes?.extras,
            )
        }

    // Spirited Away: AniDB's entries without a date or synopsis, and an "O" entry that is only its subtitles.
    @Test
    fun aFilmsEntriesKeepTheirTitleAndLength() =
        runTest {
            val episodes = AniZipClient(AniZipEngine("episodes-film")).episodes(199)

            val film = episodes?.episodes?.first()
            assertEquals("Complete Movie", film?.title)
            assertEquals(125, film?.minutes)
            assertEquals(AirDate(2001, 7, 20), film?.airDate)
            assertNull(film?.synopsis)
            assertEquals(
                listOf(
                    "Spirited Away Extra - Behind the microphone",
                    "Spirited Away Extra - Storyboard-To-Scene-Selection",
                ),
                episodes?.extras?.map {
                    it.title
                },
            )
        }

    // A show still airing: AniZip lists the next episodes before it knows their titles.
    @Test
    fun anEpisodeNotOutYetHasOnlyItsDate() =
        runTest {
            val episodes = AniZipClient(AniZipEngine("episodes-new")).episodes(195604)

            val next = episodes?.episodes?.single { it.number == 5 }
            assertNull(next?.title)
            assertNull(next?.synopsis)
            assertNull(next?.thumbnail)
            assertEquals(AirDate(2026, 10, 31), next?.airDate)
        }

    @Test
    fun aShowAniZipDoesNotKnowHasNoEpisodes() = runTest { assertNull(AniZipClient(AniZipEngine(null, status = 404)).episodes(1)) }

    @Test
    fun noConnectionMeansNoEpisodes() = runTest { assertNull(AniZipClient(OfflineEngine).episodes(1535)) }
}
