package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.http.HttpHeader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AniListErrorTest {
    @Test
    fun rateLimitCarriesTheRetryAfterTime() =
        runTest {
            val engine = RecordedEngine("rate-limited", status = 429, headers = listOf(HttpHeader("Retry-After", "30")))

            assertEquals(AniListError.RateLimited(retryAfter = 30.seconds), clientFor(engine).trending().failure())
        }

    @Test
    fun rateLimitWithoutRetryAfterWaitsAMinute() =
        runTest {
            val engine = RecordedEngine("rate-limited", status = 429)

            assertEquals(AniListError.RateLimited(retryAfter = 1.minutes), clientFor(engine).search("frieren").failure())
        }

    @Test
    fun missingAnimeIsAnHttpError() =
        runTest {
            val engine = RecordedEngine("details-not-found", status = 404)

            assertEquals(AniListError.Http(statusCode = 404), clientFor(engine).details(999999999).failure())
        }

    @Test
    fun droppedConnectionIsANetworkError() =
        runTest {
            assertIs<AniListError.Network>(clientFor(OfflineEngine).popular().failure())
        }

    @Test
    fun answerWithoutTheAnimeIsUnexpected() =
        runTest {
            val engine = RecordedEngine("details-not-found", status = 200)

            assertIs<AniListError.Unexpected>(clientFor(engine).details(999999999).failure())
        }
}

internal fun <T> AniListResult<T>.failure(): AniListError = assertIs<AniListResult.Failure>(this).error
