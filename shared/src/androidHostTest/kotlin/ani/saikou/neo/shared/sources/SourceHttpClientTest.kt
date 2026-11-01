package ani.saikou.neo.shared.sources

import ani.saikou.neo.extension.api.HostMethod
import ani.saikou.neo.extension.api.HostRequest
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceHttpClientTest {
    private val server = MockWebServer()
    private val http = SourceHttpClient()

    @BeforeTest
    fun start() {
        server.start()
    }

    @AfterTest
    fun stop() {
        server.close()
    }

    private fun headers(vararg pairs: Pair<String, String>): Headers {
        val builder = Headers.Builder()
        pairs.forEach { (name, value) -> builder.add(name, value) }
        return builder.build()
    }

    @Test
    fun aBodyLargerThanTheCapFails() =
        runTest {
            val capped = SourceHttpClient(maxBodyBytes = 8)
            server.enqueue(MockResponse(body = "123456789"))

            assertFailsWith<IOException> { capped.http(HostRequest(url = server.url("/x").toString())) }
        }

    @Test
    fun aBodyWithinTheCapIsReturned() =
        runTest {
            val capped = SourceHttpClient(maxBodyBytes = 8)
            server.enqueue(MockResponse(body = "12345"))

            assertEquals("12345", capped.http(HostRequest(url = server.url("/x").toString())).body)
        }

    @Test
    fun httpReturnsTheStatusHeadersAndBody() =
        runTest {
            server.enqueue(MockResponse(code = 201, headers = headers("X-Note" to "hello"), body = "the body"))

            val response = http.http(HostRequest(url = server.url("/x").toString()))

            assertEquals(201, response.status)
            assertEquals("hello", response.headers["X-Note"])
            assertEquals("the body", response.body)
        }

    @Test
    fun everyRequestCarriesThePinnedUserAgent() =
        runTest {
            server.enqueue(MockResponse(body = "ok"))

            // The source's own User-Agent is ignored; the pinned one wins, so Cloudflare sees one browser.
            http.http(HostRequest(url = server.url("/x").toString(), headers = mapOf("User-Agent" to "something-else")))

            assertEquals(SOURCE_USER_AGENT, server.takeRequest().headers["User-Agent"])
        }

    @Test
    fun aCookieSetByOneCallIsSentOnTheNext() =
        runTest {
            server.enqueue(MockResponse(headers = headers("Set-Cookie" to "sid=abc123"), body = "one"))
            server.enqueue(MockResponse(body = "two"))

            http.http(HostRequest(url = server.url("/first").toString()))
            http.http(HostRequest(url = server.url("/second").toString()))

            server.takeRequest()
            assertEquals("sid=abc123", server.takeRequest().headers["Cookie"])
        }

    @Test
    fun aPostSendsItsBodyAndContentType() =
        runTest {
            server.enqueue(MockResponse(body = "ok"))

            http.http(
                HostRequest(
                    url = server.url("/x").toString(),
                    method = HostMethod.Post,
                    body = "name=value",
                    contentType = "application/x-www-form-urlencoded",
                ),
            )

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("name=value", recorded.body?.utf8())
            assertEquals("application/x-www-form-urlencoded", recorded.headers["Content-Type"])
        }

    @Test
    fun aRequestsHeadersAreSent() =
        runTest {
            server.enqueue(MockResponse(body = "ok"))

            http.http(HostRequest(url = server.url("/x").toString(), headers = mapOf("Referer" to "https://ref.example/")))

            assertEquals("https://ref.example/", server.takeRequest().headers["Referer"])
        }
}
