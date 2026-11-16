package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.http.HttpHeader
import com.apollographql.apollo.api.http.HttpMethod
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.api.json.jsonReader
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.network.http.DefaultHttpEngine
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.IOException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Which release of an episode a timetable row is. */
public enum class AirType { RAW, SUB, DUB }

/** Where an episode stands, as AnimeSchedule.net tells it. */
public enum class AiringStatus { AIRED, AIRING, UNAIRED, DELAYED, UNKNOWN }

/** One episode on this week's timetable. */
public data class TimetableEpisode(
    /** AnimeSchedule's id for the show, such as `psyren`. */
    val route: String,
    val title: String?,
    val romaji: String?,
    val english: String?,
    val episodeNumber: Int?,
    val airType: AirType,
    val episodeDate: Instant,
    /** When the delay started, or null when the episode isn't delayed. */
    val delayedFrom: Instant?,
    /** When the delay ends, or null when the episode isn't delayed. */
    val delayedUntil: Instant?,
    val airingStatus: AiringStatus,
    /** A Chinese animation. */
    val donghua: Boolean,
    val imageUrl: String?,
)

/** The outcome of an AnimeSchedule.net call. */
public sealed interface AnimeScheduleResult {
    public data class Success(
        val episodes: List<TimetableEpisode>,
    ) : AnimeScheduleResult

    public data class Failure(
        val error: AnimeScheduleError,
    ) : AnimeScheduleResult
}

/** Why an AnimeSchedule.net call failed. */
public sealed interface AnimeScheduleError {
    /** The build has no token, so nothing was asked. */
    public data object NoToken : AnimeScheduleError

    /** AnimeSchedule turned the token down with a 401. */
    public data object BadToken : AnimeScheduleError

    /** AnimeSchedule answered 429. Wait [retryAfter] before calling again. */
    public data class RateLimited(
        val retryAfter: Duration,
    ) : AnimeScheduleError

    /** Another error status. */
    public data class Http(
        val statusCode: Int,
    ) : AnimeScheduleError

    /** AnimeSchedule couldn't be reached. */
    public data class Network(
        val message: String?,
    ) : AnimeScheduleError

    /** AnimeSchedule answered, but not with a timetable. */
    public data class Unexpected(
        val message: String?,
    ) : AnimeScheduleError
}

/**
 * Reads this week's timetable from AnimeSchedule.net (ADR 0041). [token] is the app's application token. Without one, it
 * asks nothing.
 */
public class AnimeScheduleClient internal constructor(
    private val token: String?,
    private val engine: HttpEngine,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Instant = { Clock.System.now() },
) {
    public constructor(token: String?) : this(token, DefaultHttpEngine(TIMEOUT_MILLIS))

    /** Every raw, sub and dub episode airing this week, Monday to Sunday, with times for the IANA time zone [zone]. */
    public suspend fun week(zone: String): AnimeScheduleResult {
        if (token.isNullOrBlank()) return AnimeScheduleResult.Failure(AnimeScheduleError.NoToken)
        val request =
            HttpRequest
                .Builder(HttpMethod.Get, "$API_URL/timetables/all?tz=${encode(zone)}")
                // Apollo's engine logs nothing, so the token goes nowhere else.
                .addHeader("Authorization", "Bearer $token")
                .build()
        // The engine blocks while it waits for the network, so keep it off the main thread, as ApolloClient does.
        return withContext(io) {
            try {
                val response = engine.execute(request)
                response.body.use { body ->
                    when (response.statusCode) {
                        200 -> read { checkNotNull(body) { "No body" }.jsonReader().timetable() }
                        401 -> AnimeScheduleResult.Failure(AnimeScheduleError.BadToken)
                        429 -> AnimeScheduleResult.Failure(AnimeScheduleError.RateLimited(retryAfter(response.headers)))
                        else -> AnimeScheduleResult.Failure(AnimeScheduleError.Http(response.statusCode))
                    }
                }
            } catch (e: ApolloException) {
                AnimeScheduleResult.Failure(AnimeScheduleError.Network(e.message))
            } catch (e: IOException) {
                // The connection dropped while the answer was read.
                AnimeScheduleResult.Failure(AnimeScheduleError.Network(e.message))
            }
        }
    }

    /** X-RateLimit-Reset is when the limit resets, in Unix seconds. Without it, wait a minute. */
    private fun retryAfter(headers: List<HttpHeader>): Duration {
        val reset =
            headers
                .firstOrNull { it.name.equals("X-RateLimit-Reset", ignoreCase = true) }
                ?.value
                ?.trim()
                ?.toLongOrNull() ?: return 1.minutes
        val wait = (reset - now().epochSeconds).seconds
        return if (wait > Duration.ZERO) wait else 1.minutes
    }

    private companion object {
        const val API_URL = "https://animeschedule.net/api/v3"
        const val TIMEOUT_MILLIS = 15_000L
    }
}

/** Reads a 200's timetable. A body that isn't one, such as AnimeSchedule's plain-text "Couldn't find", is unexpected. */
private inline fun read(timetable: () -> List<TimetableEpisode>): AnimeScheduleResult =
    try {
        AnimeScheduleResult.Success(timetable())
    } catch (e: ApolloException) {
        // Apollo's JSON reader reports bad JSON this way.
        AnimeScheduleResult.Failure(AnimeScheduleError.Unexpected(e.message))
    } catch (e: IllegalStateException) {
        AnimeScheduleResult.Failure(AnimeScheduleError.Unexpected(e.message))
    } catch (e: IllegalArgumentException) {
        AnimeScheduleResult.Failure(AnimeScheduleError.Unexpected(e.message))
    }

private const val IMAGE_URL = "https://img.animeschedule.net/production/assets/public/img/"

/** AnimeSchedule's zero date, which it sends for "no delay". */
private const val NO_DATE = "0001-01-01T00:00:00Z"

private fun JsonReader.timetable(): List<TimetableEpisode> {
    val episodes = mutableListOf<TimetableEpisode>()
    beginArray()
    while (hasNext()) readEpisode()?.let { episodes += it }
    endArray()
    return episodes
}

/** One row, or null for a row without a route, time or air type. Streams and other nested values are skipped. */
private fun JsonReader.readEpisode(): TimetableEpisode? {
    if (peek() != JsonReader.Token.BEGIN_OBJECT) {
        skipValue()
        return null
    }
    val fields = mutableMapOf<String, String>()
    var donghua = false
    beginObject()
    while (hasNext()) {
        val name = nextName()
        when (peek()) {
            JsonReader.Token.STRING, JsonReader.Token.NUMBER, JsonReader.Token.LONG -> fields[name] = nextString()!!
            JsonReader.Token.BOOLEAN -> if (name == "donghua") donghua = nextBoolean() else skipValue()
            else -> skipValue()
        }
    }
    endObject()
    val route = fields["route"]?.takeIf { it.isNotBlank() } ?: return null
    val episodeDate = fields["episodeDate"]?.let(::instantOrNull) ?: return null
    val airType = AirType.entries.firstOrNull { it.name.equals(fields["airType"], ignoreCase = true) } ?: return null
    return TimetableEpisode(
        route = route,
        title = fields["title"].orNullIfBlank(),
        romaji = fields["romaji"].orNullIfBlank(),
        english = fields["english"].orNullIfBlank(),
        episodeNumber = fields["episodeNumber"]?.toIntOrNull(),
        airType = airType,
        episodeDate = episodeDate,
        delayedFrom = fields["delayedFrom"]?.takeIf { it != NO_DATE }?.let(::instantOrNull),
        delayedUntil = fields["delayedUntil"]?.takeIf { it != NO_DATE }?.let(::instantOrNull),
        airingStatus = airingStatus(fields["airingStatus"]),
        donghua = donghua,
        imageUrl = fields["imageVersionRoute"].orNullIfBlank()?.let { IMAGE_URL + it },
    )
}

private fun airingStatus(value: String?): AiringStatus =
    when (value) {
        "aired" -> AiringStatus.AIRED
        "airing" -> AiringStatus.AIRING
        "unaired" -> AiringStatus.UNAIRED
        "delayed-air" -> AiringStatus.DELAYED
        else -> AiringStatus.UNKNOWN
    }

private fun instantOrNull(text: String): Instant? = runCatching { Instant.parse(text) }.getOrNull()

private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
