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

/** How fanart.tv knows a show: a film by its TMDB or IMDb id, a series by its TheTVDB id. */
public sealed interface FanartTvId {
    public data class Film(
        val id: String,
    ) : FanartTvId

    public data class Series(
        val tvDbId: Int,
    ) : FanartTvId
}

/** A show's backgrounds and HD title logos on fanart.tv, most liked first. */
public data class FanartTvArt(
    val logos: List<ArtImage>,
    val backgrounds: List<ArtImage>,
)

/** Reads backgrounds and title logos from fanart.tv. [apiKey] is the app's fanart.tv project key. Without one, it asks nothing. */
public class FanartTvClient internal constructor(
    private val apiKey: String?,
    private val engine: HttpEngine,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    public constructor(apiKey: String?) : this(apiKey, DefaultHttpEngine(TIMEOUT_MILLIS))

    /** The art fanart.tv has for [show]. Null without a key, or when fanart.tv doesn't know the show or can't be reached. */
    public suspend fun art(show: FanartTvId): FanartTvArt? {
        if (apiKey.isNullOrBlank()) return null
        val (path, fields) =
            when (show) {
                is FanartTvId.Film -> "movies/${show.id}" to MOVIE_FIELDS
                is FanartTvId.Series -> "tv/${show.tvDbId}" to SERIES_FIELDS
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

        // Only the HD logos: the plain `movielogo` and `clearlogo` lists hold the old 400 px ones.
        val MOVIE_FIELDS = ArtFields(background = "moviebackground", logo = "hdmovielogo")
        val SERIES_FIELDS = ArtFields(background = "showbackground", logo = "hdtvlogo")
    }
}

private class ArtFields(
    val background: String,
    val logo: String,
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
    return FanartTvArt(logos = logos, backgrounds = backgrounds)
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
        images +=
            ArtImage(
                url = url,
                width = fields["width"]?.toIntOrNull() ?: 0,
                height = fields["height"]?.toIntOrNull() ?: 0,
                // Backgrounds have no words, which fanart.tv marks with an empty language.
                language = fields["lang"]?.takeIf { it.isNotEmpty() },
            )
    }
    endArray()
    return images
}
