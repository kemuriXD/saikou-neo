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

/** A film or series on TMDB, which numbers the two apart. */
public data class TmdbId(
    val id: Int,
    val film: Boolean,
)

/** A show's title logos and backdrops on TMDB, best voted first. */
public data class TmdbImages(
    val logos: List<ArtImage>,
    val backdrops: List<ArtImage>,
)

/**
 * Reads logos and backdrops from TMDB, and finds films AniZip has no right id for. [apiKey] is the app's TMDB API key.
 * Without one, it asks nothing.
 */
public class TmdbClient internal constructor(
    private val apiKey: String?,
    private val engine: HttpEngine,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    public constructor(apiKey: String?) : this(apiKey, DefaultHttpEngine(TIMEOUT_MILLIS))

    /** Every logo and backdrop TMDB has for [show], in one request. Null without a key, or when TMDB can't answer. */
    public suspend fun images(show: TmdbId): TmdbImages? = get("/${if (show.film) "movie" else "tv"}/${show.id}/images") { images() }

    /** The film with IMDb id [imdbId], or null when TMDB doesn't know it. */
    public suspend fun findFilm(imdbId: String): Int? = get("/find/${encode(imdbId)}?external_source=imdb_id") { firstId("movie_results") }

    /** The best match for a film called [title] released in [year], or null when nothing matches. */
    public suspend fun searchFilm(
        title: String,
        year: Int?,
    ): Int? = get("/search/movie?query=${encode(title)}${year?.let { "&year=$it" }.orEmpty()}") { firstId("results") }

    /**
     * The series called [title] that first aired in [year], or null when none has that name. TV search matches loosely,
     * so only a series with that exact name, in any case, is taken.
     */
    public suspend fun searchSeries(
        title: String,
        year: Int?,
    ): Int? = get("/search/tv?query=${encode(title)}${year?.let { "&first_air_date_year=$it" }.orEmpty()}") { firstNamed(title) }

    private suspend fun <T> get(
        path: String,
        read: JsonReader.() -> T?,
    ): T? {
        if (apiKey.isNullOrBlank()) return null
        // A v3 key only works in the url. Apollo's engine logs nothing, and nothing else sees these urls.
        val url = "$TMDB_URL$path${if ('?' in path) '&' else '?'}api_key=$apiKey"
        // The engine blocks while it waits for the network, so keep it off the main thread, as ApolloClient does.
        return withContext(io) {
            try {
                val response = engine.execute(HttpRequest.Builder(HttpMethod.Get, url).build())
                val body = response.body
                if (response.statusCode != 200 || body == null) null else body.use { it.jsonReader().read() }
            } catch (_: ApolloException) {
                null
            } catch (_: IOException) {
                // The connection dropped while the answer was read.
                null
            }
        }
    }

    private companion object {
        const val TMDB_URL = "https://api.themoviedb.org/3"
        const val TIMEOUT_MILLIS = 15_000L
    }
}

private const val IMAGE_URL = "https://image.tmdb.org/t/p/original"

private fun JsonReader.images(): TmdbImages {
    var logos = emptyList<ArtImage>()
    var backdrops = emptyList<ArtImage>()
    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "logos" -> logos = imageList()
            "backdrops" -> backdrops = imageList()
            else -> skipValue()
        }
    }
    endObject()
    return TmdbImages(logos, backdrops)
}

private fun JsonReader.imageList(): List<ArtImage> {
    if (peek() != JsonReader.Token.BEGIN_ARRAY) {
        skipValue()
        return emptyList()
    }
    val images = mutableListOf<ArtImage>()
    beginArray()
    while (hasNext()) {
        val fields = readFields()
        val path = fields["file_path"] ?: continue
        val vector = path.endsWith(".svg")
        images +=
            ArtImage(
                // TMDB serves each SVG as a PNG at the same path.
                url = IMAGE_URL + if (vector) path.removeSuffix(".svg") + ".png" else path,
                width = fields["width"]?.toIntOrNull() ?: 0,
                height = fields["height"]?.toIntOrNull() ?: 0,
                language = fields["iso_639_1"],
                vector = vector,
            )
    }
    endArray()
    return images
}

/** The id of the first object in the [field] list. */
private fun JsonReader.firstId(field: String): Int? {
    var id: Int? = null
    beginObject()
    while (hasNext()) {
        if (nextName() != field || peek() != JsonReader.Token.BEGIN_ARRAY) {
            skipValue()
            continue
        }
        beginArray()
        while (hasNext()) {
            val found = readFields()["id"]?.toIntOrNull()
            if (id == null) id = found
        }
        endArray()
    }
    endObject()
    return id
}

/**
 * The id of the first animated series in `results` whose name or original name is [title], ignoring case. Some anime
 * share their name with a live-action drama, such as True Beauty.
 */
private fun JsonReader.firstNamed(title: String): Int? {
    var id: Int? = null
    beginObject()
    while (hasNext()) {
        if (nextName() != "results" || peek() != JsonReader.Token.BEGIN_ARRAY) {
            skipValue()
            continue
        }
        beginArray()
        while (hasNext()) {
            val series = readSeries()
            val named = listOfNotNull(series.name, series.originalName).any { it.equals(title, ignoreCase = true) }
            if (id == null && named && series.animated) id = series.id
        }
        endArray()
    }
    endObject()
    return id
}

private const val ANIMATION_GENRE = 16

private class FoundSeries(
    val id: Int?,
    val name: String?,
    val originalName: String?,
    val animated: Boolean,
)

/** One series from a TV search, with its genres, which [readFields] would skip. */
private fun JsonReader.readSeries(): FoundSeries {
    if (peek() != JsonReader.Token.BEGIN_OBJECT) {
        skipValue()
        return FoundSeries(null, null, null, animated = false)
    }
    val fields = mutableMapOf<String, String>()
    var animated = false
    beginObject()
    while (hasNext()) {
        val name = nextName()
        when {
            name == "genre_ids" && peek() == JsonReader.Token.BEGIN_ARRAY -> {
                beginArray()
                while (hasNext()) if (nextString()?.toIntOrNull() == ANIMATION_GENRE) animated = true
                endArray()
            }

            peek() == JsonReader.Token.STRING || peek() == JsonReader.Token.NUMBER || peek() == JsonReader.Token.LONG -> {
                fields[name] = nextString()!!
            }

            else -> {
                skipValue()
            }
        }
    }
    endObject()
    return FoundSeries(fields["id"]?.toIntOrNull(), fields["name"], fields["original_name"], animated)
}

/** Percent-encodes everything but the characters a url never needs encoded. */
private fun encode(text: String): String =
    buildString {
        for (byte in text.encodeToByteArray()) {
            val char = byte.toInt().toChar()
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-._~") {
                append(char)
            } else {
                append('%')
                append(HEX[(byte.toInt() shr 4) and 0xF])
                append(HEX[byte.toInt() and 0xF])
            }
        }
    }

private const val HEX = "0123456789ABCDEF"
