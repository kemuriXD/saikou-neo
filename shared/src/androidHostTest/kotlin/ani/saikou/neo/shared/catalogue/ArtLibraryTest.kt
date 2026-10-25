package ani.saikou.neo.shared.catalogue

import ani.saikou.neo.shared.home.Logo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ArtLibraryTest {
    private var aniZip = AniZipEngine("mappings-one-piece")
    private val tmdb = TmdbEngine { "tv-images" }
    private val onePiece = card(21, "One Piece")

    /** Everything on the test's dispatcher, so waiting for it to go idle waits for every answer. */
    private fun TestScope.library(): ArtLibrary {
        val io = StandardTestDispatcher(testScheduler)
        return ArtLibrary(
            ArtSources(
                clientFor(RecordedEngine("relations-none"), io),
                AniZipClient(aniZip, io),
                FanartTvClient("test-key", FanartTvEngine("tv"), io),
                TmdbClient("test-key", tmdb, io),
            ),
            // The test itself, since waiting for it to go idle skips background work.
            this,
        )
    }

    @Test
    fun eachShowIsAskedForOnce() =
        runTest {
            val library = library()

            val first = library.art(onePiece)
            val second = library.art(onePiece)

            assertEquals(first, second)
            assertEquals(Logo.Image("https://image.tmdb.org/t/p/original/9F7daAmibx8ZHTE17CdM5FAwiHE.png"), first.logo)
            assertEquals(1, aniZip.urls.size)
            assertEquals(1, tmdb.urls.size)
        }

    // Details hands over its card once AniList answers, which a film without ids needs for TMDB's search. If Details
    // closes before then, the next ask starts over.
    @Test
    fun aSearchThatWasCancelledIsAskedAgain() =
        runTest {
            aniZip = AniZipEngine("mappings-no-ids")
            val library = library()
            val details = CompletableDeferred<AnimeCard?>()
            launch { runCatching { library.art(9260) { details.await() } } }
            advanceUntilIdle()

            details.cancel()
            advanceUntilIdle()
            library.art(card(9260, "Kizumonogatari Part 1: Tekketsu", format = "MOVIE", year = 2016))

            assertEquals(2, aniZip.urls.size)
        }
}
