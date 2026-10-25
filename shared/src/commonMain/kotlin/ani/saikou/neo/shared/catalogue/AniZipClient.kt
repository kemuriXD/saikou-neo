package ani.saikou.neo.shared.catalogue

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

/** Reads fan art from AniZip, which maps AniList ids to TheTVDB's art and to other sites' ids. */
public class AniZipClient internal constructor(
    private val engine: HttpEngine,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    public constructor() : this(DefaultHttpEngine(TIMEOUT_MILLIS))

    /** The show's art, or null when AniZip doesn't know the show or can't be reached. */
    public suspend fun art(anilistId: Int): AniZipArt? =
        // The engine blocks while it waits for the network, so keep it off the main thread, as ApolloClient does.
        withContext(io) {
            try {
                val response = engine.execute(HttpRequest.Builder(HttpMethod.Get, "$ANIZIP_URL?anilist_id=$anilistId").build())
                val body = response.body
                if (response.statusCode != 200 || body == null) null else body.use { it.jsonReader().art() }
            } catch (_: ApolloException) {
                null
            } catch (_: IOException) {
                // The connection dropped while the answer was read.
                null
            }
        }

    private companion object {
        const val ANIZIP_URL = "https://api.ani.zip/mappings"
        const val TIMEOUT_MILLIS = 15_000L
    }
}

/** A show's fan art from AniZip, and the ids other sites know it by. Any of them can be missing. */
public data class AniZipArt(
    /** 1920 × 1080 art for the backdrop. It belongs to the whole series, so every season shows the same art. */
    val fanArt: String?,
    /** A film rather than a series. TMDB numbers the two apart. */
    val film: Boolean,
    /** The series' TheTVDB id. */
    val tvDbId: Int? = null,
    /** The show's TMDB id. A film's is sometimes its TV series', so films are looked up again by [imdbId]. */
    val tmdbId: Int? = null,
    val imdbId: String? = null,
)

/** Reads the `Fanart` url from `images` and the ids from `mappings`, and skips everything else. */
private fun JsonReader.art(): AniZipArt {
    var fanArt: String? = null
    var mappings = emptyMap<String, String>()
    beginObject()
    while (hasNext()) {
        val name = nextName()
        if (name == "mappings") {
            mappings = readFields()
            continue
        }
        if (name != "images" || peek() != JsonReader.Token.BEGIN_ARRAY) {
            skipValue()
            continue
        }
        beginArray()
        while (hasNext()) {
            val fields = readFields()
            if (fields["coverType"] == "Fanart" && fanArt == null) fanArt = fields["url"]
        }
        endArray()
    }
    endObject()
    return AniZipArt(
        fanArt = fanArt,
        film = mappings["type"] == "MOVIE",
        tvDbId = mappings["thetvdb_id"]?.toIntOrNull()?.takeIf { it > 0 },
        tmdbId = mappings["themoviedb_id"]?.toIntOrNull()?.takeIf { it > 0 },
        // It goes into a fanart.tv path, so it must be an IMDb id and nothing else.
        imdbId = mappings["imdb_id"]?.takeIf { it.matches(imdbId) },
    )
}

private val imdbId = Regex("""tt\d{1,10}""")

/** An object's string and number fields. Nested values are skipped. */
internal fun JsonReader.readFields(): Map<String, String> {
    if (peek() != JsonReader.Token.BEGIN_OBJECT) {
        skipValue()
        return emptyMap()
    }
    val fields = mutableMapOf<String, String>()
    beginObject()
    while (hasNext()) {
        val name = nextName()
        when (peek()) {
            JsonReader.Token.STRING, JsonReader.Token.NUMBER, JsonReader.Token.LONG -> fields[name] = nextString()!!
            else -> skipValue()
        }
    }
    endObject()
    return fields
}
