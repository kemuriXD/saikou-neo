package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.http.HttpHeader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
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

    // A TV with no connection says so at once, instead of waiting for a request that can't get out.
    @Test
    fun withNoConnectionNothingIsAskedAndItIsANetworkError() =
        runTest {
            val engine = RecordedEngine("search")

            val error = clientFor(engine, isOnline = { false }).search("frieren").failure()

            assertIs<AniListError.Network>(error)
            assertTrue(engine.requestBodies.isEmpty())
        }

    @Test
    fun answerWithoutTheAnimeIsUnexpected() =
        runTest {
            val engine = RecordedEngine("details-not-found", status = 200)

            assertIs<AniListError.Unexpected>(clientFor(engine).details(999999999).failure())
        }

    @Test
    fun eachErrorIsOneOfThreeProblemsForTheViewer() {
        assertEquals(LoadProblem.NO_NETWORK, AniListError.Network("Unable to resolve host").problem)
        assertEquals(LoadProblem.RATE_LIMITED, AniListError.RateLimited(1.minutes).problem)
        assertEquals(LoadProblem.OTHER, AniListError.Http(500).problem)
        assertEquals(LoadProblem.OTHER, AniListError.Unexpected(null).problem)
    }
}

internal fun <T> AniListResult<T>.failure(): AniListError = assertIs<AniListResult.Failure>(this).error
