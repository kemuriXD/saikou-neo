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
    private val spiritedAway = FanartTvId.Film("129")
    private val sakamotoDays = FanartTvId.Series(423732)

    @Test
    fun aFilmIsAskedByItsTmdbId() =
        runTest {
            val engine = FanartTvEngine("movie")

            val art = FanartTvClient(KEY, engine).art(spiritedAway)

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
            // In fanart.tv's order, the most liked first.
            assertEquals(
                ArtImage("https://assets.fanart.tv/fanart/spirited-away-50f0c9c556cad.jpg", 1920, 1080, null),
                art?.backgrounds?.first(),
            )
            assertEquals(9, art?.backgrounds?.size)
            assertEquals(ArtImage("https://assets.fanart.tv/fanart/spirited-away-581cdc4963a71.png", 800, 310, "es"), art?.logos?.first())
        }

    // The old 400 px logos are in their own list, which is never read.
    @Test
    fun onlyHdLogosAreRead() =
        runTest {
            val logos = FanartTvClient(KEY, FanartTvEngine("movie")).art(spiritedAway)?.logos

            assertEquals(12, logos?.size)
            assertTrue(logos!!.none { it.url.endsWith("4fe0b92b5b8ef.png") })
        }

    @Test
    fun aSeriesIsAskedByItsTheTvdbId() =
        runTest {
            val engine = FanartTvEngine("tv")

            val art = FanartTvClient(KEY, engine).art(sakamotoDays)

            assertEquals("https://webservice.fanart.tv/v3.2/tv/423732", engine.requests.single().url)
            assertEquals("https://assets.fanart.tv/fanart/sakamoto-days-66866dfcc9fd5.jpg", art?.backgrounds?.first()?.url)
            assertEquals(ArtImage("https://assets.fanart.tv/fanart/sakamoto-days-66866dc54d0e7.png", 800, 310, "en"), art?.logos?.first())
        }

    // The engine blocks, and Android stops network calls on the main thread.
    @Test
    fun asksOffTheCallersThread() =
        runTest {
            val engine = FanartTvEngine("movie")

            FanartTvClient(KEY, engine).art(spiritedAway)

            assertNotEquals(Thread.currentThread(), engine.threads.single())
        }

    // Builds without a key work as before.
    @Test
    fun withoutAKeyNothingIsAsked() =
        runTest {
            val engine = FanartTvEngine("movie")

            assertNull(FanartTvClient(null, engine).art(spiritedAway))
            assertNull(FanartTvClient("", engine).art(spiritedAway))
            assertTrue(engine.requests.isEmpty())
        }

    @Test
    fun aFilmFanartTvDoesNotKnowHasNoArt() =
        runTest { assertNull(FanartTvClient(KEY, FanartTvEngine("not-found", status = 404)).art(spiritedAway)) }

    // Android 7 and 8 still allow plain http, so only https art is used.
    @Test
    fun onlyHttpsArtIsUsed() =
        runTest {
            val engine =
                FanartTvEngine("movie") {
                    it.replace("https://assets.fanart.tv/fanart/spirited-away-50f0", "http://assets.fanart.tv/fanart/spirited-away-50f0")
                }

            assertEquals(
                "https://assets.fanart.tv/fanart/spirited-away-5cfb4c72ccd34.jpg",
                FanartTvClient(KEY, engine)
                    .art(spiritedAway)
                    ?.backgrounds
                    ?.first()
                    ?.url,
            )
        }

    @Test
    fun aDroppedConnectionMeansNoArt() = runTest { assertNull(FanartTvClient(KEY, DroppedEngine).art(spiritedAway)) }

    @Test
    fun noConnectionMeansNoArt() = runTest { assertNull(FanartTvClient(KEY, OfflineEngine).art(spiritedAway)) }

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
