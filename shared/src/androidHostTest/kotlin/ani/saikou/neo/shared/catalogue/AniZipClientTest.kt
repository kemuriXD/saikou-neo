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
    fun findsTheClearLogoAndFanArtInOneRequest() =
        runTest {
            val engine = AniZipEngine("mappings")

            val art = AniZipClient(engine).art(177709)

            assertEquals("https://artworks.thetvdb.com/banners/v4/series/423732/icons/6687c76dcbad1.png", art?.logo)
            assertEquals("https://artworks.thetvdb.com/banners/v4/series/423732/backgrounds/6654700131bab.jpg", art?.fanArt)
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

    @Test
    fun aShowWithoutALogoHasNone() =
        runTest {
            val art = AniZipClient(AniZipEngine("mappings-no-logo")).art(177709)

            assertNull(art?.logo)
        }

    // AniZip's art belongs to the whole series, so a later season shows the same art as the first.
    @Test
    fun aLaterSeasonGetsTheSeriesArt() =
        runTest {
            val art = AniZipClient(AniZipEngine("mappings-second-season")).art(145064)

            assertEquals("https://artworks.thetvdb.com/banners/v4/series/377543/icons/60735d109b705.png", art?.logo)
            assertEquals("https://artworks.thetvdb.com/banners/v4/series/377543/backgrounds/609cba7794bab.jpg", art?.fanArt)
        }

    @Test
    fun aShowAniZipDoesNotKnowHasNoArt() = runTest { assertNull(AniZipClient(AniZipEngine(null, status = 404)).art(1)) }

    @Test
    fun noConnectionMeansNoArt() = runTest { assertNull(AniZipClient(OfflineEngine).art(177709)) }
}

/** Answers with a recorded AniZip response from `resources/anizip`, or an empty body. */
internal class AniZipEngine(
    private val fixture: String?,
    private val status: Int = 200,
    /** Recordings for particular AniList ids, in place of [fixture]. */
    private val byId: Map<Int, String> = emptyMap(),
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
            .body(Buffer().writeUtf8(body ?: "{\"code\":404}"))
            .build()
    }

    override fun close() {}
}
