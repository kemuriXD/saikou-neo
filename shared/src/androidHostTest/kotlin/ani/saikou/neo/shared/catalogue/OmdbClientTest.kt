package ani.saikou.neo.shared.catalogue

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OmdbClientTest {
    @Test
    fun aSeriesHasItsImdbRating() =
        runTest {
            val engine = SiteEngine("omdb") { "series" }

            val ratings = OmdbClient(KEY, engine).ratings("tt26743760")

            assertEquals("https://www.omdbapi.com/?i=tt26743760&apikey=$KEY", engine.urls.single())
            assertEquals(OmdbRatings(imdb = 86, rottenTomatoes = null, film = false), ratings)
        }

    @Test
    fun aFilmAlsoHasRottenTomatoesOutOfAHundred() =
        runTest {
            val ratings = OmdbClient(KEY, SiteEngine("omdb") { "movie" }).ratings("tt0245429")

            assertEquals(OmdbRatings(imdb = 86, rottenTomatoes = 96, film = true), ratings)
        }

    // OMDb writes "N/A" for a rating IMDb doesn't have yet.
    @Test
    fun aShowWithoutRatingsHasNone() =
        runTest {
            val ratings = OmdbClient(KEY, SiteEngine("omdb") { "series-unrated" }).ratings("tt26743760")

            assertEquals(OmdbRatings(imdb = null, rottenTomatoes = null, film = false), ratings)
        }

    @Test
    fun anIdOmdbDoesNotKnowHasNoRatings() =
        runTest {
            assertNull(OmdbClient(KEY, SiteEngine("omdb") { "not-found" }).ratings("tt9999999999"))
        }

    @Test
    fun anErrorFromOmdbHasNoRatings() =
        runTest {
            assertNull(OmdbClient(KEY, SiteEngine("omdb", status = 401) { "not-found" }).ratings("tt0245429"))
        }

    @Test
    fun withoutAKeyNothingIsAsked() =
        runTest {
            val engine = SiteEngine("omdb") { "movie" }

            assertNull(OmdbClient("", engine).ratings("tt0245429"))
            assertTrue(engine.urls.isEmpty())
        }

    private companion object {
        const val KEY = "test-api-key"
    }
}
