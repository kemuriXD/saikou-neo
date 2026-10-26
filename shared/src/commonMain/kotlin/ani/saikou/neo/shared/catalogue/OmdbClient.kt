package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.network.http.DefaultHttpEngine
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** A show's ratings on OMDb, each out of 100. Either can be missing. */
public data class OmdbRatings(
    val imdb: Int?,
    /** The Tomatometer, which OMDb has mostly for films. */
    val rottenTomatoes: Int?,
    /** OMDb lists it as a film rather than a series. */
    val film: Boolean,
)

/** Reads IMDb and Rotten Tomatoes ratings from OMDb. [apiKey] is the app's OMDb key. Without one, it asks nothing. */
public class OmdbClient internal constructor(
    private val apiKey: String?,
    private val engine: HttpEngine,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    public constructor(apiKey: String?) : this(apiKey, DefaultHttpEngine(TIMEOUT_MILLIS))

    /** The ratings of the show with IMDb id [imdbId], or null without a key or when OMDb doesn't know it. */
    public suspend fun ratings(imdbId: String): OmdbRatings? {
        if (apiKey.isNullOrBlank() || !imdbId.matches(imdbIdFormat)) return null
        // OMDb only takes the key in the url. Apollo's engine logs nothing, and nothing else sees it.
        return engine.getJson("$OMDB_URL?i=$imdbId&apikey=$apiKey", io) { ratings() }
    }

    private companion object {
        const val OMDB_URL = "https://www.omdbapi.com/"
        const val TIMEOUT_MILLIS = 15_000L
    }
}

/** Reads `imdbRating`, `Type` and the Rotten Tomatoes entry in `Ratings`. An unknown id answers `"Response":"False"`. */
private fun JsonReader.ratings(): OmdbRatings? {
    val fields = mutableMapOf<String, String>()
    var rottenTomatoes: Int? = null
    beginObject()
    while (hasNext()) {
        val name = nextName()
        when {
            name == "Ratings" && peek() == JsonReader.Token.BEGIN_ARRAY -> {
                beginArray()
                while (hasNext()) {
                    val rating = readFields()
                    if (rating["Source"] == "Rotten Tomatoes") rottenTomatoes = rating["Value"]?.toPercent()
                }
                endArray()
            }

            peek() == JsonReader.Token.STRING -> {
                fields[name] = nextString()!!
            }

            else -> {
                skipValue()
            }
        }
    }
    endObject()
    if (fields["Response"] != "True") return null
    return OmdbRatings(imdb = fields["imdbRating"]?.let(::outOfTen), rottenTomatoes = rottenTomatoes, film = fields["Type"] == "movie")
}

private fun String.toPercent(): Int? = removeSuffix("%").toIntOrNull()?.takeIf { it in 0..100 }
