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

class AniZipClientTest {
    @Test
    fun findsTheFanArtAndIdsInOneRequest() =
        runTest {
            val engine = AniZipEngine("mappings")

            val art = AniZipClient(engine).art(177709)

            assertEquals("https://artworks.thetvdb.com/banners/v4/series/423732/backgrounds/6654700131bab.jpg", art?.fanArt)
            assertEquals(423732, art?.tvDbId)
            assertEquals(false, art?.film)
            assertEquals("https://api.ani.zip/mappings?anilist_id=177709", engine.urls.single())
        }

    // The engine blocks, and Android stops network calls on the main thread.
    @Test
    fun asksOffTheCallersThread() =
        runTest {
            val engine = AniZipEngine("mappings")

            AniZipClient(engine).art(177709)

            assertNotEquals(Thread.currentThread(), engine.threads.single())
        }

    // AniZip's art belongs to the whole series, so a later season shows the same art as the first.
    @Test
    fun aLaterSeasonGetsTheSeriesArt() =
        runTest {
            val art = AniZipClient(AniZipEngine("mappings-second-season")).art(145064)

            assertEquals("https://artworks.thetvdb.com/banners/v4/series/377543/backgrounds/609cba7794bab.jpg", art?.fanArt)
        }

    // One Piece: a series' TMDB id is TMDB's series.
    @Test
    fun aSeriesHasItsTmdbAndImdbIds() =
        runTest {
            val art = AniZipClient(AniZipEngine("mappings-one-piece")).art(21)

            assertEquals(AniZipArt(fanArt = ONE_PIECE_FAN_ART, film = false, tvDbId = 81797, tmdbId = 37854, imdbId = "tt0388629"), art)
        }

    // Spirited Away: a film, so its ids are the film's.
    @Test
    fun aFilmHasItsIds() =
        runTest {
            val art = AniZipClient(AniZipEngine("mappings-movie")).art(199)

            assertEquals(true, art?.film)
            assertEquals(129, art?.tmdbId)
            assertEquals("tt0245429", art?.imdbId)
            assertNull(art?.tvDbId)
            assertNull(art?.fanArt)
        }

    // Kizumonogatari: AniZip knows the film but has no ids for it.
    @Test
    fun aFilmWithoutIdsHasNone() =
        runTest {
            assertEquals(AniZipArt(fanArt = null, film = true), AniZipClient(AniZipEngine("mappings-no-ids")).art(9260))
        }

    // The IMDb id goes into a fanart.tv path, so anything but an IMDb id is ignored.
    @Test
    fun anImdbIdThatIsNotOneIsLeftOut() =
        runTest {
            val engine = AniZipEngine("mappings-movie", change = { it.replace("tt0245429", "tt1/../../other?x=") })

            assertNull(AniZipClient(engine).art(199)?.imdbId)
        }

    @Test
    fun aShowAniZipDoesNotKnowHasNoArt() = runTest { assertNull(AniZipClient(AniZipEngine(null, status = 404)).art(1)) }

    @Test
    fun aDroppedConnectionMeansNoArt() = runTest { assertNull(AniZipClient(DroppedEngine).art(177709)) }

    @Test
    fun noConnectionMeansNoArt() = runTest { assertNull(AniZipClient(OfflineEngine).art(177709)) }

    private companion object {
        const val ONE_PIECE_FAN_ART = "https://artworks.thetvdb.com/banners/v4/series/81797/backgrounds/616009a8bd688.jpg"
    }
}

/** Answers with a recorded AniZip response from `resources/anizip`, or an empty body. */
internal class AniZipEngine(
    private val fixture: String?,
    private val status: Int = 200,
    /** Recordings for particular AniList ids, in place of [fixture]. */
    private val byId: Map<Int, String> = emptyMap(),
    private val change: (String) -> String = { it },
) : HttpEngine {
    val urls = mutableListOf<String>()
    val threads = mutableListOf<Thread>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        urls += request.url
        threads += Thread.currentThread()
        val name = byId[request.url.substringAfter("anilist_id=").toInt()] ?: fixture
        val body = name?.let { checkNotNull(javaClass.getResource("/anizip/$it.json")) { "No fixture $it" }.readText() }
        return HttpResponse
            .Builder(status)
            .body(Buffer().writeUtf8(body?.let(change) ?: "{\"code\":404}"))
            .build()
    }

    override fun close() {}
}
