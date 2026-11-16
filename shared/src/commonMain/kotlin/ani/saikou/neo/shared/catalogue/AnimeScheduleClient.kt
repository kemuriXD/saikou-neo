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
import okio.Buffer
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

/** What kind of show it is, as AnimeSchedule.net lists it. A Chinese ONA is an [ONA]; see [TimetableEpisode.donghua]. */
public enum class ShowFormat { TV, TV_SHORT, ONA, MOVIE, OVA, SPECIAL }

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
    /** The show's format, or null when AnimeSchedule lists none it knows. */
    val format: ShowFormat?,
    /** How long an episode runs, or null when it isn't known. */
    val lengthMinutes: Int?,
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

/** The outcome of matching one show between AniList and AnimeSchedule.net. */
public sealed interface ShowLookup<out T : Any> {
    public data class Found<out T : Any>(
        val value: T,
    ) : ShowLookup<T>

    /** AnimeSchedule doesn't know the show, or has no AniList link for it. */
    public data object NoMatch : ShowLookup<Nothing>

    public data class Failure(
        val error: AnimeScheduleError,
    ) : ShowLookup<Nothing>
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
    public suspend fun week(zone: String): AnimeScheduleResult =
        when (val reply = ask("/timetables/all?tz=${encode(zone)}")) {
            is Reply.Answered -> read { Buffer().writeUtf8(reply.body).jsonReader().timetable() }
            Reply.Missing -> AnimeScheduleResult.Failure(AnimeScheduleError.Http(404))
            is Reply.Failed -> AnimeScheduleResult.Failure(reply.error)
        }

    /** AnimeSchedule's route for the AniList show [aniListId]. One show per call: repeating the id returns nothing. */
    public suspend fun routeFor(aniListId: Int): ShowLookup<String> =
        lookUp("/anime?anilist-ids=$aniListId") { body ->
            body.jsonReader().routeOfFirstShow()
        }

    /** The AniList id of AnimeSchedule's show [route], read from its AniList link. */
    public suspend fun aniListIdFor(route: String): ShowLookup<Int> {
        if (route.isBlank()) return ShowLookup.NoMatch
        return lookUp("/anime/${encode(route)}") { body ->
            body.jsonReader().aniListLink()?.let(::aniListIdFromLink)
        }
    }

    /** A 404, an empty answer or AnimeSchedule's plain-text "Couldn't find" is no match. Bad JSON is unexpected. */
    private suspend fun <T : Any> lookUp(
        path: String,
        found: (Buffer) -> T?,
    ): ShowLookup<T> =
        when (val reply = ask(path)) {
            is Reply.Answered -> {
                if (!reply.body.trimStart().startsWith("{")) {
                    ShowLookup.NoMatch
                } else {
                    try {
                        found(Buffer().writeUtf8(reply.body))?.let { ShowLookup.Found(it) } ?: ShowLookup.NoMatch
                    } catch (e: ApolloException) {
                        ShowLookup.Failure(AnimeScheduleError.Unexpected(e.message))
                    } catch (e: IllegalStateException) {
                        ShowLookup.Failure(AnimeScheduleError.Unexpected(e.message))
                    } catch (e: IOException) {
                        // The JSON stopped part way.
                        ShowLookup.Failure(AnimeScheduleError.Unexpected(e.message))
                    }
                }
            }

            Reply.Missing -> {
                ShowLookup.NoMatch
            }

            is Reply.Failed -> {
                ShowLookup.Failure(reply.error)
            }
        }

    /** One GET of [path] with the token. Without a token, nothing is asked. */
    private suspend fun ask(path: String): Reply {
        if (token.isNullOrBlank()) return Reply.Failed(AnimeScheduleError.NoToken)
        val request =
            HttpRequest
                .Builder(HttpMethod.Get, API_URL + path)
                // Apollo's engine logs nothing, so the token goes nowhere else.
                .addHeader("Authorization", "Bearer $token")
                .build()
        // The engine blocks while it waits for the network, so keep it off the main thread, as ApolloClient does.
        return withContext(io) {
            try {
                val response = engine.execute(request)
                response.body.use { body ->
                    when (response.statusCode) {
                        200 -> Reply.Answered(body?.readUtf8().orEmpty())
                        401 -> Reply.Failed(AnimeScheduleError.BadToken)
                        404 -> Reply.Missing
                        429 -> Reply.Failed(AnimeScheduleError.RateLimited(retryAfter(response.headers)))
                        else -> Reply.Failed(AnimeScheduleError.Http(response.statusCode))
                    }
                }
            } catch (e: ApolloException) {
                Reply.Failed(AnimeScheduleError.Network(e.message))
            } catch (e: IOException) {
                // The connection dropped while the answer was read.
                Reply.Failed(AnimeScheduleError.Network(e.message))
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

private sealed interface Reply {
    data class Answered(
        val body: String,
    ) : Reply

    data object Missing : Reply

    data class Failed(
        val error: AnimeScheduleError,
    ) : Reply
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
    } catch (e: IOException) {
        // The JSON stopped part way.
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
    var format: ShowFormat? = null
    beginObject()
    while (hasNext()) {
        val name = nextName()
        if (name == "mediaTypes" && peek() == JsonReader.Token.BEGIN_ARRAY) {
            format = mediaTypes()
            continue
        }
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
        format = format,
        lengthMinutes = fields["lengthMin"]?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 },
        imageUrl = fields["imageVersionRoute"].orNullIfBlank()?.let { IMAGE_URL + it },
    )
}

/** The first media type that's a [ShowFormat], or null when none is. */
private fun JsonReader.mediaTypes(): ShowFormat? {
    var format: ShowFormat? = null
    beginArray()
    while (hasNext()) {
        val name =
            if (peek() == JsonReader.Token.BEGIN_OBJECT) {
                stringField("name")
            } else {
                skipValue()
                null
            }
        if (format == null) format = showFormat(name)
    }
    endArray()
    return format
}

private fun showFormat(name: String?): ShowFormat? =
    when (name?.substringBefore('(')?.trim()?.lowercase()) {
        "tv" -> ShowFormat.TV
        "tv short" -> ShowFormat.TV_SHORT
        "ona" -> ShowFormat.ONA
        "movie" -> ShowFormat.MOVIE
        "ova" -> ShowFormat.OVA
        "special" -> ShowFormat.SPECIAL
        else -> null
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

/** The route of the first show in an `/anime` search, or null when there's none. */
private fun JsonReader.routeOfFirstShow(): String? {
    var route: String? = null
    beginObject()
    while (hasNext()) {
        if (nextName() == "anime" && peek() == JsonReader.Token.BEGIN_ARRAY) {
            beginArray()
            while (hasNext()) {
                if (route == null && peek() == JsonReader.Token.BEGIN_OBJECT) route = stringField("route") else skipValue()
            }
            endArray()
        } else {
            skipValue()
        }
    }
    endObject()
    return route.orNullIfBlank()
}

/** `websites.aniList` of one show, or null when it has none. */
private fun JsonReader.aniListLink(): String? {
    var link: String? = null
    beginObject()
    while (hasNext()) {
        if (nextName() == "websites" && peek() == JsonReader.Token.BEGIN_OBJECT) link = stringField("aniList") else skipValue()
    }
    endObject()
    return link
}

/** Reads an object, keeping only the string [name]. */
private fun JsonReader.stringField(name: String): String? {
    var value: String? = null
    beginObject()
    while (hasNext()) {
        if (nextName() == name && peek() == JsonReader.Token.STRING) value = nextString() else skipValue()
    }
    endObject()
    return value
}

private val ANILIST_LINK = Regex("""^(?:https?://)?(?:www\.)?anilist\.co/anime/(\d+)(?:[/?#].*)?$""")

/** The id in an AniList show link such as `anilist.co/anime/154587/Sousou-no-Frieren/`, or null for any other link. */
internal fun aniListIdFromLink(link: String): Int? =
    ANILIST_LINK
        .matchEntire(link.trim())
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
