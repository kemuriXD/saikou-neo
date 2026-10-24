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

    // AniZip's art belongs to the whole series, so it would show the first season's art on the second.
    @Test
    fun aLaterSeasonKeepsItsLogoButHasNoFanArt() =
        runTest {
            val art = AniZipClient(AniZipEngine("mappings-second-season")).art(145064)

            assertEquals("https://artworks.thetvdb.com/banners/v4/series/377543/icons/60735d109b705.png", art?.logo)
            assertNull(art?.fanArt)
        }

    // TheTVDB files movies under specials, season 0.
    @Test
    fun aMovieHasNoFanArt() = runTest { assertNull(AniZipClient(AniZipEngine("mappings-movie")).art(131573)?.fanArt) }

    // Without episodes, there's no telling which season the show is.
    @Test
    fun aShowWithoutEpisodesHasNoFanArt() = runTest { assertNull(AniZipClient(AniZipEngine("mappings-no-logo")).art(177709)?.fanArt) }

    @Test
    fun aShowAniZipDoesNotKnowHasNoArt() = runTest { assertNull(AniZipClient(AniZipEngine(null, status = 404)).art(1)) }

    @Test
    fun noConnectionMeansNoArt() = runTest { assertNull(AniZipClient(OfflineEngine).art(177709)) }
}

/** Answers with a recorded AniZip response from `resources/anizip`, or an empty body. */
internal class AniZipEngine(
    private val fixture: String?,
    private val status: Int = 200,
) : HttpEngine {
    val urls = mutableListOf<String>()
    val threads = mutableListOf<Thread>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        urls += request.url
        threads += Thread.currentThread()
        val body = fixture?.let { checkNotNull(javaClass.getResource("/anizip/$it.json")) { "No fixture $it" }.readText() }
        return HttpResponse
            .Builder(status)
            .body(Buffer().writeUtf8(body ?: "{\"code\":404}"))
            .build()
    }

    override fun close() {}
}
