package ani.saikou.neo.shared.sources

import ani.saikou.neo.extension.api.SourceException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CloudflareInterceptorTest {
    private val server = MockWebServer()
    private val jar = InMemoryCookieJar()

    @BeforeTest
    fun start() {
        server.start()
    }

    @AfterTest
    fun stop() {
        server.close()
    }

    private fun challenge() =
        MockResponse(code = 503, headers = headers("Server" to "cloudflare"), body = "<title>Just a moment...</title>")

    private fun headers(vararg pairs: Pair<String, String>): Headers {
        val builder = Headers.Builder()
        pairs.forEach { (name, value) -> builder.add(name, value) }
        return builder.build()
    }

    private fun get(clearer: CloudflareClearer) =
        sharedHttpClient(cookieJar = jar, clearer = clearer)
            .newCall(Request.Builder().url(server.url("/")).build())
            .execute()

    @Test
    fun aChallengeIsClearedThenTheRequestSucceeds() {
        val clearer = FakeClearer(jar, FakeClearer.Outcome.Clear)
        server.enqueue(challenge())
        server.enqueue(MockResponse(code = 200, body = "the page"))

        val response = get(clearer)

        assertEquals("the page", response.body.string())
        assertEquals(1, clearer.calls)
        server.takeRequest()
        assertEquals("cf_clearance=cleared", server.takeRequest().headers["Cookie"])
    }

    @Test
    fun aNormalResponseIsNotTouched() {
        val clearer = FakeClearer(jar, FakeClearer.Outcome.Clear)
        server.enqueue(MockResponse(code = 200, body = "the page"))

        assertEquals("the page", get(clearer).body.string())
        assertEquals(0, clearer.calls)
    }

    @Test
    fun aChallengeThatNeedsAPersonFailsAsBlocked() {
        server.enqueue(challenge())

        assertFailsWith<SourceException.Blocked> { get(FakeClearer(jar, FakeClearer.Outcome.NeedsPerson)) }
    }

    @Test
    fun aClearanceThatCantBeMintedFailsAsTheSiteBeingDown() {
        server.enqueue(challenge())

        assertFailsWith<SourceException.SourceDown> { get(FakeClearer(jar, FakeClearer.Outcome.Error)) }
    }

    @Test
    fun aChallengeThatStaysAfterClearingFailsAsBlocked() {
        server.enqueue(challenge())
        server.enqueue(challenge())

        assertFailsWith<SourceException.Blocked> { get(FakeClearer(jar, FakeClearer.Outcome.Clear)) }
    }
}

/** Stands in for the WebView clearer: it clears, needs a person, or errors, as the test asks. */
private class FakeClearer(
    private val jar: CookieJar,
    private val outcome: Outcome,
) : CloudflareClearer {
    enum class Outcome { Clear, NeedsPerson, Error }

    var calls = 0

    override suspend fun clear(url: HttpUrl): Clearance? {
        calls++
        return when (outcome) {
            Outcome.Clear -> {
                jar.saveFromResponse(
                    url,
                    listOf(
                        Cookie
                            .Builder()
                            .name("cf_clearance")
                            .value("cleared")
                            .domain(url.host)
                            .build(),
                    ),
                )
                Clearance("cf_clearance=cleared", SOURCE_USER_AGENT, Long.MAX_VALUE)
            }

            Outcome.NeedsPerson -> {
                null
            }

            Outcome.Error -> {
                throw SourceException.SourceDown("the WebView could not load the page")
            }
        }
    }
}
