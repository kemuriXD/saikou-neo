package ani.saikou.neo.extension.api

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ErrorsTest {
    private val server = Server(id = "sub-1", name = "Main", edition = Edition.Sub, runtime = null)

    @Test
    fun eachKindOfFailureHasItsOwnType() {
        val failures =
            listOf(
                SourceException.Blocked(),
                SourceException.RateLimited(retryAfter = 30.seconds),
                SourceException.SourceDown(),
                SourceException.LinkExpired(),
                SourceException.NotFound(),
            )

        val kinds =
            failures.map {
                when (it) {
                    is SourceException.Blocked -> "blocked"
                    is SourceException.RateLimited -> "rate-limited"
                    is SourceException.SourceDown -> "down"
                    is SourceException.LinkExpired -> "expired"
                    is SourceException.NotFound -> "not found"
                }
            }

        assertEquals(listOf("blocked", "rate-limited", "down", "expired", "not found"), kinds)
    }

    @Test
    fun aRateLimitCanSayWhenToTryAgain() {
        assertEquals(30.seconds, SourceException.RateLimited(retryAfter = 30.seconds).retryAfter)
        assertEquals(null, SourceException.RateLimited().retryAfter)
    }

    @Test
    fun aFailureKeepsTheSourcesMessageAndCause() {
        val cause = IllegalStateException("HTTP 403")
        val failure = SourceException.Blocked("Cloudflare challenge", cause)

        assertEquals("Cloudflare challenge", failure.message)
        assertEquals(cause, failure.cause)
    }

    @Test
    fun anExpiredLinkCanBeRefreshedForTheSameServer() =
        runTest {
            val extension = ExpiringExtension()
            val first = extension.stream(server)

            val fresh = extension.refresh(server, expired = first)

            assertEquals(first.qualities.map { it.height }, fresh.qualities.map { it.height })
            assertNotEquals(first.qualities.first().url, fresh.qualities.first().url)
        }

    @Test
    fun aSourceThatDoesntOverrideRefreshFetchesTheStreamAgain() =
        runTest {
            val extension = ExpiringExtension()
            val first = extension.stream(server)

            extension.refresh(server, expired = first)

            assertEquals(2, extension.streamCalls)
        }

    @Test
    fun aRateLimitCantAskForANegativeWait() {
        assertFailsWith<IllegalArgumentException> { SourceException.RateLimited(retryAfter = Duration.ZERO - 1.seconds) }
    }

    /** Hands out a new signed link each time, like sites whose links expire. */
    private class ExpiringExtension : SourceExtension {
        var streamCalls = 0

        override suspend fun servers(episode: Episode): List<Server> = emptyList()

        override suspend fun stream(server: Server): Stream {
            streamCalls++
            return Stream(
                qualities = listOf(Quality(720, "https://example.com/720.m3u8?token=$streamCalls", StreamFormat.Hls)),
            )
        }
    }
}
