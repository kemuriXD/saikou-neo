package ani.saikou.neo.shared.sources

import ani.saikou.neo.extension.api.ExtractionRequest
import ani.saikou.neo.extension.api.ExtractionResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class PollForResultTest {
    private suspend fun poll(
        request: ExtractionRequest,
        captured: CapturedRequest,
        read: suspend (String) -> String?,
    ): ExtractionResult =
        // Far under a request's time limit, so a stuck read fails here rather than ending as a timeout.
        withTimeout(5.seconds) { pollForResult(request, captured, read) }

    @Test
    fun aLostAnswerIsAskedForAgain() =
        runTest {
            val request = ExtractionRequest(embedUrl = "https://embed.example/", script = "window.link")
            var reads = 0

            val result =
                poll(request, CapturedRequest(null)) {
                    if (++reads == 1) awaitCancellation()
                    "\"https://cdn.example/a.m3u8\""
                }

            assertEquals("https://cdn.example/a.m3u8", result.value)
            assertEquals(2, reads)
        }

    @Test
    fun aCapturedRequestComesBackWithTheAnswerAfterALostOne() =
        runTest {
            val request = ExtractionRequest(embedUrl = "https://embed.example/", urlPattern = "*.m3u8", script = "window.key")
            val captured = CapturedRequest(request.urlPattern)
            captured.offer("https://cdn.example/a.m3u8", mapOf("Referer" to "https://embed.example/"))
            var reads = 0

            val result =
                poll(request, captured) {
                    if (++reads == 1) awaitCancellation()
                    "\"key\""
                }

            assertEquals("https://cdn.example/a.m3u8", result.url)
            assertEquals("key", result.value)
            assertEquals(mapOf("Referer" to "https://embed.example/"), result.requestHeaders)
        }
}
