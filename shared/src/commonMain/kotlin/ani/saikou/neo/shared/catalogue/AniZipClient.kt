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

/** Reads extra artwork from AniZip, which maps AniList ids to TheTVDB's art. */
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
            }
        }

    private companion object {
        const val ANIZIP_URL = "https://api.ani.zip/mappings"
        const val TIMEOUT_MILLIS = 15_000L
    }
}

/** A show's art from AniZip. Either can be missing. */
public data class AniZipArt(
    /** The title logo, a transparent image. */
    val logo: String?,
    /**
     * 1920 × 1080 art for the backdrop. AniZip's art belongs to the whole series, so only a series' first season
     * gets it. Later seasons, movies and specials would show another season's art.
     */
    val fanArt: String?,
)

/** Reads the `Clearlogo` and `Fanart` urls from `images`, and which seasons the `episodes` are in. */
private fun JsonReader.art(): AniZipArt {
    var logo: String? = null
    var fanArt: String? = null
    val seasons = mutableSetOf<Int>()
    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "images" -> {
                forEachObject { fields ->
                    if (fields["coverType"] == "Clearlogo" && logo == null) logo = fields["url"]
                    if (fields["coverType"] == "Fanart" && fanArt == null) fanArt = fields["url"]
                }
            }

            "episodes" -> {
                if (peek() != JsonReader.Token.BEGIN_OBJECT) {
                    skipValue()
                    continue
                }
                beginObject()
                while (hasNext()) {
                    nextName()
                    readFields()["seasonNumber"]?.toIntOrNull()?.let(seasons::add)
                }
                endObject()
            }

            else -> {
                skipValue()
            }
        }
    }
    endObject()
    return AniZipArt(logo, fanArt.takeIf { seasons == setOf(1) })
}

/** Calls [block] with the plain fields of each object in an array, and skips anything that isn't one. */
private fun JsonReader.forEachObject(block: (Map<String, String>) -> Unit) {
    if (peek() != JsonReader.Token.BEGIN_ARRAY) return skipValue()
    beginArray()
    while (hasNext()) block(readFields())
    endArray()
}

/** An object's string and number fields. Nested values are skipped. */
private fun JsonReader.readFields(): Map<String, String> {
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
