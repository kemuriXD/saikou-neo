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
 * Reads backgrounds from fanart.tv, for shows AniZip has no fan art for. [apiKey] is the app's fanart.tv project key.
 * Without one, it asks nothing.
 */
public class FanartTvClient internal constructor(
    private val apiKey: String?,
    private val engine: HttpEngine,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    public constructor(apiKey: String?) : this(apiKey, DefaultHttpEngine(TIMEOUT_MILLIS))

    /**
     * The most liked background for the show AniZip described in [art]: a film by its TMDB id, a series by its
     * TheTVDB id. Null without a key or an id, or when fanart.tv has none or can't be reached.
     */
    public suspend fun background(art: AniZipArt): String? {
        if (apiKey.isNullOrBlank()) return null
        val (path, field) =
            when {
                art.movieDbId != null -> "movies/${art.movieDbId}" to "moviebackground"
                art.tvDbId != null -> "tv/${art.tvDbId}" to "showbackground"
                else -> return null
            }
        // The engine blocks while it waits for the network, so keep it off the main thread, as ApolloClient does.
        return withContext(io) {
            try {
                // The key goes in a header, not the url, so it stays out of logs.
                val request = HttpRequest.Builder(HttpMethod.Get, "$FANART_TV_URL/$path").addHeader("api-key", apiKey).build()
                val response = engine.execute(request)
                val body = response.body
                if (response.statusCode != 200 || body == null) null else body.use { it.jsonReader().firstUrl(field) }
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
    }
}

/** The first https image url in the [field] list, which fanart.tv sorts by likes. */
private fun JsonReader.firstUrl(field: String): String? {
    var url: String? = null
    beginObject()
    while (hasNext()) {
        if (nextName() != field || peek() != JsonReader.Token.BEGIN_ARRAY) {
            skipValue()
            continue
        }
        beginArray()
        while (hasNext()) {
            // Android 7 and 8 still allow plain http, so only https art is used.
            val image = readFields()["url"]?.takeIf { it.startsWith("https://") }
            if (url == null) url = image
        }
        endArray()
    }
    endObject()
    return url
}
