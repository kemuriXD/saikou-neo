package ani.saikou.neo.extension.api

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HostServicesTest {
    @Test
    fun aSourceThatIgnoresTheHostStillWorks() =
        runTest {
            val source = FakeExtension()

            // The default attach does nothing, so a source that doesn't need the host still runs.
            source.attach(FakeHost())

            val stream = source.stream(Server(id = "sub-1", name = "Main", edition = Edition.Sub, runtime = null))
            assertEquals(3, stream.qualities.size)
        }

    @Test
    fun theHostIsHandedToASourceThroughAttach() {
        val source = RecordingSource()
        val host = FakeHost()

        source.attach(host)

        assertSame(host, source.received)
    }

    @Test
    fun printingAnHttpRequestHidesHeaderValuesAndTheBody() {
        val request =
            HostRequest(
                url = "https://example.com/api",
                method = HostMethod.Post,
                headers = mapOf("Cookie" to "sid=abc123"),
                body = "password=xyz789",
            )

        val text = request.toString()
        assertFalse("abc123" in text)
        assertFalse("xyz789" in text)
        assertTrue("Cookie" in text)
        assertTrue("https://example.com/api" in text)
    }

    @Test
    fun printingAnHttpResponseHidesHeaderValuesAndTheBody() {
        val response =
            HostResponse(
                status = 200,
                headers = mapOf("Set-Cookie" to "cf_clearance=abc123"),
                body = "{\"enc\":\"xyz789\"}",
            )

        val text = response.toString()
        assertFalse("abc123" in text)
        assertFalse("xyz789" in text)
        assertTrue("Set-Cookie" in text)
        assertTrue("200" in text)
    }

    @Test
    fun aRecipeNeedsAUrlPatternOrAScript() {
        assertFailsWith<IllegalArgumentException> { ExtractionRequest(embedUrl = "https://embed.example.com/e/1") }
    }

    @Test
    fun aRecipeCanMatchAUrlReadAScriptOrBoth() {
        ExtractionRequest(embedUrl = "https://embed.example.com/e/1", urlPattern = "*.m3u8")
        ExtractionRequest(embedUrl = "https://embed.example.com/e/1", script = "window.sources")
        ExtractionRequest(embedUrl = "https://embed.example.com/e/1", urlPattern = "*.m3u8", script = "window.sources")
    }

    @Test
    fun printingAnExtractionRequestHidesHeaderValues() {
        val request =
            ExtractionRequest(
                embedUrl = "https://embed.example.com/e/1",
                headers = mapOf("Cookie" to "sid=abc123"),
                urlPattern = "*.m3u8",
            )

        val text = request.toString()
        assertFalse("abc123" in text)
        assertTrue("Cookie" in text)
    }

    @Test
    fun printingAnExtractionResultHidesHeaderValuesAndTheReadBackValue() {
        val result =
            ExtractionResult(
                url = "https://cdn.example.com/x.m3u8",
                requestHeaders = mapOf("Cookie" to "sid=abc123"),
                value = "token=xyz789",
            )

        val text = result.toString()
        assertFalse("abc123" in text)
        assertFalse("xyz789" in text)
        assertTrue("Cookie" in text)
    }

    @Test
    fun anExtractionResultIsEmptyByDefault() {
        val result = ExtractionResult()

        assertNull(result.url)
        assertNull(result.value)
        assertTrue(result.requestHeaders.isEmpty())
    }
}

/** A host that answers with canned values, enough to hand to a source in a test. */
private class FakeHost : HostServices {
    override suspend fun http(request: HostRequest): HostResponse = HostResponse(status = 200)

    override suspend fun extract(request: ExtractionRequest): ExtractionResult = ExtractionResult()
}

/** A source that keeps the host it was handed, to show the hook reaches it. */
private class RecordingSource : SourceExtension {
    var received: HostServices? = null

    override fun attach(host: HostServices) {
        received = host
    }

    override suspend fun servers(episode: Episode): List<Server> = emptyList()

    override suspend fun stream(server: Server): Stream =
        Stream(qualities = listOf(Quality(720, "https://example.com/720.m3u8", StreamFormat.Hls)))
}
