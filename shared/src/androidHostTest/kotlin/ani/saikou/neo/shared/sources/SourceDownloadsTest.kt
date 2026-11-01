package ani.saikou.neo.shared.sources

import ani.saikou.neo.shared.catalogue.OfflineEngine
import com.apollographql.apollo.api.http.HttpHeader
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Answers each url from [pages]: a body, or a redirect when the text starts with `->`, or else from [files]. Keeps the
 * urls asked for.
 */
internal class PagesEngine(
    private val pages: Map<String, String>,
    private val files: Map<String, ByteArray> = emptyMap(),
) : HttpEngine {
    val urls = mutableListOf<String>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        urls += request.url
        files[request.url]?.let { return HttpResponse.Builder(200).body(Buffer().write(it)).build() }
        val page = pages[request.url] ?: return HttpResponse.Builder(404).body(Buffer()).build()
        return if (page.startsWith("->")) {
            HttpResponse
                .Builder(302)
                .addHeaders(listOf(HttpHeader("Location", page.removePrefix("->"))))
                .body(Buffer())
                .build()
        } else {
            HttpResponse.Builder(200).body(Buffer().writeUtf8(page)).build()
        }
    }

    override fun close() {}
}

class SourceDownloadsTest {
    @Test
    fun aLinkGivesItsFile() =
        runTest {
            val apk = SourceDownloads(PagesEngine(mapOf("https://example.com/a.apk" to "APK"))).fetch("https://example.com/a.apk")

            assertContentEquals("APK".encodeToByteArray(), apk)
        }

    @Test
    fun redirectsAreFollowed() =
        runTest {
            val engine =
                PagesEngine(
                    mapOf(
                        "https://example.com/latest" to "->https://files.example.com/a.apk",
                        "https://files.example.com/a.apk" to "->/b.apk",
                        "https://files.example.com/b.apk" to "APK",
                    ),
                )

            assertContentEquals("APK".encodeToByteArray(), SourceDownloads(engine).fetch("https://example.com/latest"))
        }

    @Test
    fun aRedirectToPlainHttpOutsideHomeIsRefused() =
        runTest {
            val engine =
                PagesEngine(
                    mapOf(
                        "https://example.com/latest" to "->http://example.com/a.apk",
                        "http://example.com/a.apk" to "APK",
                    ),
                )

            assertNull(SourceDownloads(engine).fetch("https://example.com/latest"))
            assertEquals(listOf("https://example.com/latest"), engine.urls)
        }

    @Test
    fun anHttpsLinkCannotRedirectToPlainHttpEvenAtHome() =
        runTest {
            val engine =
                PagesEngine(
                    mapOf(
                        "https://example.com/latest" to "->http://192.168.1.20/a.apk",
                        "http://192.168.1.20/a.apk" to "APK",
                    ),
                )

            assertNull(SourceDownloads(engine).fetch("https://example.com/latest"))
            assertEquals(listOf("https://example.com/latest"), engine.urls)
        }

    @Test
    fun aLinkTheEngineRejectsGivesNothing() =
        runTest {
            val engine =
                object : HttpEngine {
                    override suspend fun execute(request: HttpRequest): HttpResponse = throw IllegalArgumentException("Invalid URL")

                    override fun close() {}
                }

            assertNull(SourceDownloads(engine).fetch("https://example.com/a.apk"))
        }

    @Test
    fun aLinkThatIsNotAllowedIsNeverAsked() =
        runTest {
            val engine = PagesEngine(mapOf("http://example.com/a.apk" to "APK"))

            assertNull(SourceDownloads(engine).fetch("http://example.com/a.apk"))
            assertEquals(emptyList(), engine.urls)
        }

    @Test
    fun endlessRedirectsGiveUp() =
        runTest {
            val engine = PagesEngine(mapOf("https://example.com/a" to "->https://example.com/a"))

            assertNull(SourceDownloads(engine).fetch("https://example.com/a"))
            assertEquals(6, engine.urls.size)
        }

    @Test
    fun aFileOverTheLimitIsRefused() =
        runTest {
            val engine = PagesEngine(mapOf("https://example.com/a.apk" to "12345"))

            assertNull(SourceDownloads(engine, maxBytes = 4).fetch("https://example.com/a.apk"))
            assertContentEquals("12345".encodeToByteArray(), SourceDownloads(engine, maxBytes = 5).fetch("https://example.com/a.apk"))
        }

    @Test
    fun anIconIsAPngUnderItsOwnLimit() =
        runTest {
            val icon = png(96, 96)
            val engine =
                PagesEngine(
                    mapOf("https://example.com/text.png" to "not a picture", "https://example.com/latest.png" to "->/a.png"),
                    mapOf("https://example.com/a.png" to icon, "https://example.com/big.png" to icon + ByteArray(40)),
                )
            val downloads = SourceDownloads(engine, maxIconBytes = icon.size + 39L)

            assertContentEquals(icon, downloads.fetchIcon("https://example.com/latest.png"))
            assertNull(downloads.fetchIcon("https://example.com/big.png"))
            assertNull(downloads.fetchIcon("https://example.com/text.png"))
            // An APK is held to its own, larger limit.
            assertContentEquals(icon + ByteArray(40), downloads.fetch("https://example.com/big.png"))
        }

    @Test
    fun anIconLinkIsCheckedLikeAnApkLink() =
        runTest {
            val engine = PagesEngine(emptyMap(), mapOf("http://example.com/a.png" to png(96, 96)))

            assertNull(SourceDownloads(engine).fetchIcon("http://example.com/a.png"))
            assertEquals(emptyList(), engine.urls)
        }

    @Test
    fun aMissingFileOrNoConnectionGivesNothing() =
        runTest {
            assertNull(SourceDownloads(PagesEngine(emptyMap())).fetch("https://example.com/a.apk"))
            assertNull(SourceDownloads(OfflineEngine).fetch("https://example.com/a.apk"))
        }
}
