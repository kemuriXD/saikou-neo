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

class FanartTvClientTest {
    private val spiritedAway = AniZipArt(logo = null, fanArt = null, movieDbId = 129)
    private val sakamotoDays = AniZipArt(logo = null, fanArt = null, movieDbId = null, tvDbId = 423732)

    @Test
    fun aFilmIsAskedByItsTmdbId() =
        runTest {
            val engine = FanartTvEngine("movie")

            val background = FanartTvClient(KEY, engine).background(spiritedAway)

            // The most liked background comes first.
            assertEquals("https://assets.fanart.tv/fanart/spirited-away-50f0c9c556cad.jpg", background)
            assertEquals("https://webservice.fanart.tv/v3.2/movies/129", engine.requests.single().url)
            // The key goes in a header, so it never shows up in a url.
            assertEquals(
                KEY,
                engine.requests
                    .single()
                    .headers
                    .single { it.name == "api-key" }
                    .value,
            )
        }

    @Test
    fun aSeriesIsAskedByItsTheTvdbId() =
        runTest {
            val engine = FanartTvEngine("tv")

            val background = FanartTvClient(KEY, engine).background(sakamotoDays)

            assertEquals("https://assets.fanart.tv/fanart/sakamoto-days-66866dfcc9fd5.jpg", background)
            assertEquals("https://webservice.fanart.tv/v3.2/tv/423732", engine.requests.single().url)
        }

    // The engine blocks, and Android stops network calls on the main thread.
    @Test
    fun asksOffTheCallersThread() =
        runTest {
            val engine = FanartTvEngine("movie")

            FanartTvClient(KEY, engine).background(spiritedAway)

            assertNotEquals(Thread.currentThread(), engine.threads.single())
        }

    // Builds without a key work as before.
    @Test
    fun withoutAKeyNothingIsAsked() =
        runTest {
            val engine = FanartTvEngine("movie")

            assertNull(FanartTvClient(null, engine).background(spiritedAway))
            assertNull(FanartTvClient("", engine).background(spiritedAway))
            assertTrue(engine.requests.isEmpty())
        }

    @Test
    fun aShowWithoutIdsIsNotAsked() =
        runTest {
            val engine = FanartTvEngine("movie")

            assertNull(FanartTvClient(KEY, engine).background(AniZipArt(logo = null, fanArt = null)))
            assertTrue(engine.requests.isEmpty())
        }

    @Test
    fun aFilmFanartTvDoesNotKnowHasNoBackground() =
        runTest { assertNull(FanartTvClient(KEY, FanartTvEngine("not-found", status = 404)).background(spiritedAway)) }

    // Android 7 and 8 still allow plain http, so only https art is used.
    @Test
    fun onlyHttpsBackgroundsAreUsed() =
        runTest {
            val engine =
                FanartTvEngine("movie") {
                    it.replace("https://assets.fanart.tv/fanart/spirited-away-50f0", "http://assets.fanart.tv/fanart/spirited-away-50f0")
                }

            assertEquals(
                "https://assets.fanart.tv/fanart/spirited-away-5cfb4c72ccd34.jpg",
                FanartTvClient(KEY, engine).background(spiritedAway),
            )
        }

    @Test
    fun aDroppedConnectionMeansNoBackground() = runTest { assertNull(FanartTvClient(KEY, DroppedEngine).background(spiritedAway)) }

    @Test
    fun noConnectionMeansNoBackground() = runTest { assertNull(FanartTvClient(KEY, OfflineEngine).background(spiritedAway)) }

    private companion object {
        const val KEY = "test-project-key"
    }
}

/** Answers every request with a recorded fanart.tv response from `resources/fanarttv`. */
internal class FanartTvEngine(
    private val fixture: String,
    private val status: Int = 200,
    private val change: (String) -> String = { it },
) : HttpEngine {
    val requests = mutableListOf<HttpRequest>()
    val threads = mutableListOf<Thread>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        threads += Thread.currentThread()
        val body = checkNotNull(javaClass.getResource("/fanarttv/$fixture.json")) { "No fixture $fixture" }.readText()
        return HttpResponse
            .Builder(status)
            .body(Buffer().writeUtf8(change(body)))
            .build()
    }

    override fun close() {}
}
