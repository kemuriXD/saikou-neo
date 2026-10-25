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

/**
 * Reads backgrounds and title logos from fanart.tv. [apiKey] is the app's fanart.tv project key.
 * Without one, it asks nothing.
 */
public class FanartTvClient internal constructor(
    private val apiKey: String?,
    private val engine: HttpEngine,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    public constructor(apiKey: String?) : this(apiKey, DefaultHttpEngine(TIMEOUT_MILLIS))

    /**
     * The art fanart.tv has for the show AniZip described in [art]: a film by its TMDB id, a series by its TheTVDB id.
     * Null without a key or an id, or when fanart.tv doesn't know the show or can't be reached.
     */
    public suspend fun art(art: AniZipArt): FanartTvArt? {
        if (apiKey.isNullOrBlank()) return null
        val (path, fields) =
            when {
                art.movieDbId != null -> "movies/${art.movieDbId}" to MOVIE_FIELDS
                art.tvDbId != null -> "tv/${art.tvDbId}" to SERIES_FIELDS
                else -> return null
            }
        // The engine blocks while it waits for the network, so keep it off the main thread, as ApolloClient does.
        return withContext(io) {
            try {
                // The key goes in a header, not the url, so it stays out of logs.
                val request = HttpRequest.Builder(HttpMethod.Get, "$FANART_TV_URL/$path").addHeader("api-key", apiKey).build()
                val response = engine.execute(request)
                val body = response.body
                if (response.statusCode != 200 || body == null) null else body.use { it.jsonReader().art(fields) }
            } catch (_: ApolloException) {
                null
            } catch (_: IOException) {
                // The connection dropped while the answer was read.
                null
            }
        }
    }

    private companion object {
        const val FANART_TV_URL = "https://webservice.fanart.tv/v3.2"
        const val TIMEOUT_MILLIS = 15_000L
        val MOVIE_FIELDS = ArtFields(background = "moviebackground", logo = "hdmovielogo")
        val SERIES_FIELDS = ArtFields(background = "showbackground", logo = "hdtvlogo")
    }
}

/** A show's background and title logo on fanart.tv. Either may be missing. */
public data class FanartTvArt(
    /** The most liked background. */
    val background: String?,
    /** A title logo with clear edges, chosen by people as the show's own. */
    val logo: String?,
)

private class ArtFields(
    val background: String,
    val logo: String,
)

/** One image from a fanart.tv list. Lists come sorted by likes. */
private class ArtImage(
    val url: String,
    val language: String?,
)

private fun JsonReader.art(fields: ArtFields): FanartTvArt {
    var backgrounds = emptyList<ArtImage>()
    var logos = emptyList<ArtImage>()
    beginObject()
    while (hasNext()) {
        when (nextName()) {
            fields.background -> backgrounds = images()
            fields.logo -> logos = images()
            else -> skipValue()
        }
    }
    endObject()
    // An English logo first, then one with no words in any one language, which fanart.tv marks "00", then the most liked.
    val logo = logos.firstOrNull { it.language == "en" } ?: logos.firstOrNull { it.language.isNullOrEmpty() || it.language == "00" }
    return FanartTvArt(background = backgrounds.firstOrNull()?.url, logo = (logo ?: logos.firstOrNull())?.url)
}

/** The https images in a list, in fanart.tv's order. Anything that isn't a list reads as none. */
private fun JsonReader.images(): List<ArtImage> {
    if (peek() != JsonReader.Token.BEGIN_ARRAY) {
        skipValue()
        return emptyList()
    }
    val images = mutableListOf<ArtImage>()
    beginArray()
    while (hasNext()) {
        val fields = readFields()
        // Android 7 and 8 still allow plain http, so only https art is used.
        val url = fields["url"]?.takeIf { it.startsWith("https://") } ?: continue
        images += ArtImage(url, fields["lang"])
    }
    endArray()
    return images
}
