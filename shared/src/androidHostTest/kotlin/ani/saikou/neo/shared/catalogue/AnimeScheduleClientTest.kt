package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.http.HttpHeader
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AnimeScheduleClientTest {
    private val now = Instant.parse("2026-11-16T12:00:00Z")

    private fun client(
        engine: HttpEngine,
        token: String? = TOKEN,
    ) = AnimeScheduleClient(token, engine, now = { now })

    @Test
    fun theWeekIsOneCallInTheZoneAskedFor() =
        runTest {
            val engine = ScheduleEngine()

            client(engine).week("Europe/London")

            val request = engine.requests.single()
            assertEquals("https://animeschedule.net/api/v3/timetables/all?tz=Europe%2FLondon", request.url)
            assertEquals("Bearer $TOKEN", request.headers.single { it.name == "Authorization" }.value)
        }

    @Test
    fun eachEpisodeIsRead() =
        runTest {
            val episodes = assertIs<AnimeScheduleResult.Success>(client(ScheduleEngine()).week("Europe/London")).episodes

            assertEquals(6, episodes.size)
            assertEquals(
                TimetableEpisode(
                    route = "mahou-shoujo-ikusei-keikaku-restart",
                    title = "Mahou Shoujo Ikusei Keikaku: Restart",
                    romaji = "Mahou Shoujo Ikusei Keikaku: Restart",
                    english = "Magical Girl Raising Project: Restart",
                    episodeNumber = 7,
                    airType = AirType.SUB,
                    episodeDate = Instant.parse("2026-11-16T17:30:00Z"),
                    delayedFrom = null,
                    delayedUntil = null,
                    airingStatus = AiringStatus.AIRING,
                    donghua = false,
                    format = ShowFormat.TV,
                    lengthMinutes = 24,
                    imageUrl =
                        "https://img.animeschedule.net/production/assets/public/img/anime/jpg/default/" +
                            "mahou-shoujo-ikusei-keikaku-restart-8fd4ef30820c1c8a4d8f327c9d6d1e7e.jpg",
                ),
                episodes[1],
            )
        }

    @Test
    fun rawSubAndDubAreTold() =
        runTest {
            val episodes = assertIs<AnimeScheduleResult.Success>(client(ScheduleEngine()).week("UTC")).episodes

            assertEquals(
                listOf(AirType.RAW, AirType.SUB, AirType.SUB, AirType.DUB, AirType.DUB, AirType.RAW),
                episodes.map { it.airType },
            )
            assertTrue(episodes[0].donghua)
        }

    @Test
    fun eachEpisodeHasItsFormatAndLength() =
        runTest {
            val episodes = assertIs<AnimeScheduleResult.Success>(client(ScheduleEngine()).week("UTC")).episodes

            // "ONA (Chinese)" is an ONA. Chinese animation is told apart by donghua.
            assertEquals(ShowFormat.ONA, episodes[0].format)
            assertEquals(10, episodes[0].lengthMinutes)
            assertEquals(ShowFormat.TV, episodes[3].format)
        }

    @Test
    fun everyFormatIsRead() =
        runTest {
            val names = listOf("TV", "TV Short", "ONA", "ONA (Chinese)", "Movie", "OVA", "Special")
            val body =
                names.joinToString(",", "[", "]") {
                    """{"route":"a","episodeDate":"2026-11-16T02:00:00Z","airType":"sub","mediaTypes":[{"name":"$it","route":"x"}]}"""
                }

            val episodes = assertIs<AnimeScheduleResult.Success>(client(ScheduleEngine(body = body)).week("UTC")).episodes

            assertEquals(
                listOf(
                    ShowFormat.TV,
                    ShowFormat.TV_SHORT,
                    ShowFormat.ONA,
                    ShowFormat.ONA,
                    ShowFormat.MOVIE,
                    ShowFormat.OVA,
                    ShowFormat.SPECIAL,
                ),
                episodes.map { it.format },
            )
        }

    // A few rows have a length of 0 or no media types. Neither is known, so neither is filtered on.
    @Test
    fun anUnknownFormatOrLengthIsNull() =
        runTest {
            val body =
                """[{"route":"a","episodeDate":"2026-11-16T02:00:00Z","airType":"sub","lengthMin":0,"mediaTypes":[]},""" +
                    """{"route":"b","episodeDate":"2026-11-16T02:00:00Z","airType":"sub","mediaTypes":[{"name":"Music"}]},""" +
                    """{"route":"c","episodeDate":"2026-11-16T02:00:00Z","airType":"sub","mediaTypes":null}]"""

            val episodes = assertIs<AnimeScheduleResult.Success>(client(ScheduleEngine(body = body)).week("UTC")).episodes

            assertEquals(listOf(null, null, null), episodes.map { it.format })
            assertEquals(listOf(null, null, null), episodes.map { it.lengthMinutes })
        }

    @Test
    fun aDelayedEpisodeHasItsDates() =
        runTest {
            val psyren = assertIs<AnimeScheduleResult.Success>(client(ScheduleEngine()).week("UTC")).episodes[3]

            assertEquals(AiringStatus.DELAYED, psyren.airingStatus)
            assertEquals(Instant.parse("2026-11-16T00:00:00Z"), psyren.delayedFrom)
            assertEquals(Instant.parse("2026-11-23T00:00:00Z"), psyren.delayedUntil)
        }

    // Some shows have no romaji or English title.
    @Test
    fun missingTitlesAreNull() =
        runTest {
            val unaired = assertIs<AnimeScheduleResult.Success>(client(ScheduleEngine()).week("UTC")).episodes[5]

            assertEquals("Battle Spirits [Re] Zekkai No Ku", unaired.title)
            assertNull(unaired.romaji)
            assertNull(unaired.english)
            assertEquals(AiringStatus.UNAIRED, unaired.airingStatus)
        }

    @Test
    fun withoutATokenNothingIsAsked() =
        runTest {
            val engine = ScheduleEngine()

            assertEquals(AnimeScheduleResult.Failure(AnimeScheduleError.NoToken), client(engine, null).week("UTC"))
            assertEquals(AnimeScheduleResult.Failure(AnimeScheduleError.NoToken), client(engine, " ").week("UTC"))
            assertTrue(engine.requests.isEmpty())
        }

    @Test
    fun aRateLimitSaysHowLongToWait() =
        runTest {
            val reset = now.epochSeconds + 42
            val engine = ScheduleEngine(429, "", listOf(HttpHeader("x-ratelimit-reset", "$reset")))

            assertEquals(AnimeScheduleResult.Failure(AnimeScheduleError.RateLimited(42.seconds)), client(engine).week("UTC"))
        }

    @Test
    fun aRateLimitWithoutAResetWaitsAMinute() =
        runTest {
            assertEquals(
                AnimeScheduleResult.Failure(AnimeScheduleError.RateLimited(1.minutes)),
                client(ScheduleEngine(429, "")).week("UTC"),
            )
        }

    @Test
    fun aBadTokenIsItsOwnError() =
        runTest {
            assertEquals(AnimeScheduleResult.Failure(AnimeScheduleError.BadToken), client(ScheduleEngine(401, "")).week("UTC"))
        }

    @Test
    fun anotherErrorStatusIsKept() =
        runTest {
            assertEquals(AnimeScheduleResult.Failure(AnimeScheduleError.Http(503)), client(ScheduleEngine(503, "")).week("UTC"))
        }

    @Test
    fun noConnectionIsANetworkError() =
        runTest {
            assertIs<AnimeScheduleError.Network>(assertIs<AnimeScheduleResult.Failure>(client(OfflineScheduleEngine).week("UTC")).error)
        }

    @Test
    fun anAnswerThatIsNotATimetableIsUnexpected() =
        runTest {
            val result = client(ScheduleEngine(body = "Couldn't find anime with these parameters.")).week("UTC")

            assertIs<AnimeScheduleError.Unexpected>(assertIs<AnimeScheduleResult.Failure>(result).error)
        }

    @Test
    fun aTimetableThatStopsPartWayIsUnexpected() =
        runTest {
            val result = client(ScheduleEngine(body = """[{"route":"a",""")).week("UTC")

            assertIs<AnimeScheduleError.Unexpected>(assertIs<AnimeScheduleResult.Failure>(result).error)
        }

    // One odd row shouldn't hide the rest of the week.
    @Test
    fun aRowWithoutARouteOrTimeIsSkipped() =
        runTest {
            val body =
                """[{"title":"No route","episodeDate":"2026-11-16T02:00:00Z","airType":"sub"},""" +
                    """{"title":"Kept","route":"kept","episodeDate":"2026-11-16T02:00:00Z","airType":"sub"},""" +
                    """{"title":"No time","route":"no-time","airType":"sub"}]"""

            val episodes = assertIs<AnimeScheduleResult.Success>(client(ScheduleEngine(body = body)).week("UTC")).episodes

            assertEquals(listOf("kept"), episodes.map { it.route })
            assertNull(episodes.single().imageUrl)
        }

    @Test
    fun asksOffTheCallersThread() =
        runTest {
            val engine = ScheduleEngine()

            client(engine).week("UTC")

            assertNotEquals(Thread.currentThread(), engine.threads.single())
        }

    private companion object {
        const val TOKEN = "test-token"
    }
}

/** Answers with [body], or the recorded timetable from `resources/animeschedule`. */
private class ScheduleEngine(
    private val status: Int = 200,
    private val body: String? = null,
    private val headers: List<HttpHeader> = emptyList(),
) : HttpEngine {
    val requests = mutableListOf<HttpRequest>()
    val threads = mutableListOf<Thread>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        threads += Thread.currentThread()
        val text = body ?: checkNotNull(javaClass.getResource("/animeschedule/timetable.json")).readText()
        return HttpResponse
            .Builder(status)
            .headers(headers)
            .body(Buffer().writeUtf8(text))
            .build()
    }

    override fun close() {}
}

private object OfflineScheduleEngine : HttpEngine {
    override suspend fun execute(request: HttpRequest): HttpResponse = throw ApolloNetworkException("No route to host")

    override fun close() {}
}
