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
    fun findsTheClearLogo() =
        runTest {
            val engine = AniZipEngine("mappings")

            val logo = AniZipClient(engine).logo(177709)

            assertEquals("https://artworks.thetvdb.com/banners/v4/series/423732/icons/6687c76dcbad1.png", logo)
            assertEquals("https://api.ani.zip/mappings?anilist_id=177709", engine.urls.single())
        }

    // The engine blocks, and Android stops network calls on the main thread.
    @Test
    fun asksOffTheCallersThread() =
        runTest {
            val engine = AniZipEngine("mappings")

            AniZipClient(engine).logo(177709)

            assertNotEquals(Thread.currentThread(), engine.threads.single())
        }

    @Test
    fun aShowWithoutALogoHasNone() = runTest { assertNull(AniZipClient(AniZipEngine("mappings-no-logo")).logo(177709)) }

    @Test
    fun aShowAniZipDoesNotKnowHasNone() = runTest { assertNull(AniZipClient(AniZipEngine(null, status = 404)).logo(1)) }

    @Test
    fun noConnectionMeansNoLogo() = runTest { assertNull(AniZipClient(OfflineEngine).logo(177709)) }
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
