package ani.saikou.neo.shared.log

import ani.saikou.neo.shared.sources.sharedHttpClient
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class NetworkLogInterceptorTest {
    private val server = MockWebServer()
    private val sink = RecordingSink()
    private val http = sharedHttpClient()

    @BeforeTest
    fun start() {
        server.start()
        AppLog.addSink(sink)
    }

    @AfterTest
    fun stop() {
        AppLog.removeSink(sink)
        server.close()
    }

    private fun get(path: String) = http.newCall(Request.Builder().url(server.url(path)).build()).execute().close()

    @Test
    fun errorResponseIsLogged() {
        server.enqueue(MockResponse.Builder().code(503).build())

        get("/x?token=abc")

        val line = sink.lines.single()
        assertEquals(LogLevel.Warn, line.level)
        assertEquals("Net", line.tag)
        assertTrue(line.message.endsWith("-> 503"), line.message)
        assertFalse("abc" in line.message, line.message)
    }

    @Test
    fun failureIsLoggedAndRethrown() {
        // Nothing is listening any more, so the connection is refused.
        server.close()

        assertFailsWith<IOException> { get("/x") }

        val line = sink.lines.single()
        assertEquals("Net", line.tag)
        assertEquals(LogLevel.Warn, line.level)
        assertTrue("failed" in line.message, line.message)
    }

    @Test
    fun callTimeoutIsLogged() {
        server.enqueue(MockResponse.Builder().headersDelay(5, TimeUnit.SECONDS).build())
        val slow = sharedHttpClient(callLimit = 300.milliseconds)

        assertFailsWith<InterruptedIOException> { slow.newCall(Request.Builder().url(server.url("/x")).build()).execute().close() }

        val line = sink.lines.single()
        assertEquals(LogLevel.Warn, line.level)
        assertEquals("Net", line.tag)
        assertTrue("failed" in line.message, line.message)
    }

    @Test
    fun successIsNotLogged() {
        server.enqueue(MockResponse.Builder().code(200).build())

        get("/x")

        assertEquals(emptyList(), sink.lines)
    }
}
