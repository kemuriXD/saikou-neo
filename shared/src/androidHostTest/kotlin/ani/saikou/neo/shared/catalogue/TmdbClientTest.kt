package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TmdbClientTest {
    private val onePiece = TmdbId(37854, film = false)
    private val spiritedAway = TmdbId(129, film = true)

    @Test
    fun aSeriesLogosAndBackdropsComeInOneRequest() =
        runTest {
            val engine = TmdbEngine { "tv-images" }

            val images = TmdbClient(KEY, engine).images(onePiece)

            assertEquals("https://api.themoviedb.org/3/tv/37854/images?api_key=$KEY", engine.urls.single())
            // In TMDB's order, the best voted first.
            assertEquals(
                ArtImage("https://image.tmdb.org/t/p/original/9F7daAmibx8ZHTE17CdM5FAwiHE.png", 1277, 443, "en"),
                images?.logos?.first(),
            )
            assertEquals(9, images?.logos?.size)
            assertEquals(
                ArtImage("https://image.tmdb.org/t/p/original/v38qp4bySLTXYu3MF8r5GD51FN3.jpg", 3840, 2160, null),
                images?.backdrops?.first(),
            )
            assertEquals(7, images?.backdrops?.size)
        }

    @Test
    fun aFilmIsAskedForAsAMovie() =
        runTest {
            val engine = TmdbEngine { "movie-images" }

            val images = TmdbClient(KEY, engine).images(spiritedAway)

            assertEquals("https://api.themoviedb.org/3/movie/129/images?api_key=$KEY", engine.urls.single())
            assertEquals("pt", images?.logos?.first()?.language)
        }

    // TMDB serves every SVG logo as a PNG at the same path, which Coil can show without an SVG decoder.
    @Test
    fun svgLogosAreFetchedAsPngs() =
        runTest {
            val logo = TmdbClient(KEY, TmdbEngine { "tv-images" }).images(onePiece)?.logos?.get(3)

            assertEquals(
                ArtImage("https://image.tmdb.org/t/p/original/fOtSIjkHW22CBj61tQqGdmSbVWL.png", 255, 93, "ja", vector = true),
                logo,
            )
        }

    // AniZip gives The End of Evangelion the Evangelion series' TMDB id, but its IMDb id is the film's own.
    @Test
    fun aFilmIsFoundByItsImdbId() =
        runTest {
            val engine = TmdbEngine { "find-movie" }

            assertEquals(18491, TmdbClient(KEY, engine).findFilm("tt0169858"))
            assertEquals("https://api.themoviedb.org/3/find/tt0169858?external_source=imdb_id&api_key=$KEY", engine.urls.single())
        }

    @Test
    fun anImdbIdTmdbDoesNotKnowFindsNoFilm() = runTest { assertNull(TmdbClient(KEY, TmdbEngine { "find-none" }).findFilm("tt0000000")) }

    // Kizumonogatari has no ids on AniZip at all.
    @Test
    fun aFilmIsFoundByItsTitleAndYear() =
        runTest {
            val engine = TmdbEngine { "search-movie" }

            assertEquals(357786, TmdbClient(KEY, engine).searchFilm("Kizumonogatari Part 1: Tekketsu", 2016))
            assertEquals(
                "https://api.themoviedb.org/3/search/movie?query=Kizumonogatari%20Part%201%3A%20Tekketsu&year=2016&api_key=$KEY",
                engine.urls.single(),
            )
        }

    @Test
    fun aSearchWithoutAYearLeavesItOut() =
        runTest {
            val engine = TmdbEngine { "search-movie" }

            TmdbClient(KEY, engine).searchFilm("Tekketsu", null)

            assertEquals("https://api.themoviedb.org/3/search/movie?query=Tekketsu&api_key=$KEY", engine.urls.single())
        }

    @Test
    fun aSearchWithNoResultsFindsNoFilm() =
        runTest { assertNull(TmdbClient(KEY, TmdbEngine { "find-none" }).searchFilm("Nothing like it", 2016)) }

    // Psychic Princess has no ids on AniZip.
    @Test
    fun aSeriesIsFoundByItsTitle() =
        runTest {
            val engine = TmdbEngine { "search-tv" }

            assertEquals(84737, TmdbClient(KEY, engine).searchSeries("Psychic Princess", null))
            assertEquals("https://api.themoviedb.org/3/search/tv?query=Psychic%20Princess&api_key=$KEY", engine.urls.single())
        }

    @Test
    fun aSeriesSearchNarrowsByTheFirstYear() =
        runTest {
            val engine = TmdbEngine { "search-tv" }

            TmdbClient(KEY, engine).searchSeries("Psychic Princess", 2018)

            assertEquals(
                "https://api.themoviedb.org/3/search/tv?query=Psychic%20Princess&first_air_date_year=2018&api_key=$KEY",
                engine.urls.single(),
            )
        }

    // A series search matches loosely, and a wrong show's art is worse than none.
    @Test
    fun aSeriesWithAnotherNameIsNotTaken() =
        runTest { assertNull(TmdbClient(KEY, TmdbEngine { "search-tv" }).searchSeries("Princess", null)) }

    // TMDB's first True Beauty is the live-action drama. The animated one comes second.
    @Test
    fun aSeriesThatIsNotAnimatedIsNotTaken() =
        runTest { assertEquals(239779, TmdbClient(KEY, TmdbEngine { "search-tv-live-action" }).searchSeries("True Beauty", null)) }

    @Test
    fun aSeriesNameMatchesWhateverItsCase() =
        runTest { assertEquals(84737, TmdbClient(KEY, TmdbEngine { "search-tv" }).searchSeries("psychic princess", null)) }

    // The engine blocks, and Android stops network calls on the main thread.
    @Test
    fun asksOffTheCallersThread() =
        runTest {
            val engine = TmdbEngine { "tv-images" }

            TmdbClient(KEY, engine).images(onePiece)

            assertNotEquals(Thread.currentThread(), engine.threads.single())
        }

    // Builds without a key still get art from fanart.tv and AniZip.
    @Test
    fun withoutAKeyNothingIsAsked() =
        runTest {
            val engine = TmdbEngine { "tv-images" }

            assertNull(TmdbClient(null, engine).images(onePiece))
            assertNull(TmdbClient(" ", engine).findFilm("tt0169858"))
            assertNull(TmdbClient("", engine).searchFilm("Tekketsu", 2016))
            assertTrue(engine.urls.isEmpty())
        }

    @Test
    fun aShowTmdbDoesNotKnowHasNoImages() =
        runTest { assertNull(TmdbClient(KEY, TmdbEngine(status = 404) { "not-found" }).images(TmdbId(99999999, film = true))) }

    @Test
    fun aDroppedConnectionMeansNoImages() = runTest { assertNull(TmdbClient(KEY, DroppedEngine).images(onePiece)) }

    @Test
    fun noConnectionMeansNoFilm() = runTest { assertNull(TmdbClient(KEY, OfflineEngine).findFilm("tt0169858")) }

    private companion object {
        const val KEY = "test-api-key"
    }
}

/** Answers each request with the recorded TMDB response from `resources/tmdb` that [fixture] names for its url. */
internal class TmdbEngine(
    private val status: Int = 200,
    private val fixture: (url: String) -> String,
) : HttpEngine {
    val urls = mutableListOf<String>()
    val threads = mutableListOf<Thread>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        urls += request.url
        threads += Thread.currentThread()
        val name = fixture(request.url)
        val body = checkNotNull(javaClass.getResource("/tmdb/$name.json")) { "No fixture $name" }.readText()
        return HttpResponse
            .Builder(status)
            .body(Buffer().writeUtf8(body))
            .build()
    }

    override fun close() {}
}
