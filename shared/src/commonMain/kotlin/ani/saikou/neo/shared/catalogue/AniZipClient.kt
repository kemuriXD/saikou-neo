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

    /** The show's title logo, a transparent image, or null when AniZip has none or can't be reached. */
    public suspend fun logo(anilistId: Int): String? =
        // The engine blocks while it waits for the network, so keep it off the main thread, as ApolloClient does.
        withContext(io) {
            try {
                val response = engine.execute(HttpRequest.Builder(HttpMethod.Get, "$ANIZIP_URL?anilist_id=$anilistId").build())
                val body = response.body
                if (response.statusCode != 200 || body == null) null else body.use { it.jsonReader().clearLogo() }
            } catch (_: ApolloException) {
                null
            }
        }

    private companion object {
        const val ANIZIP_URL = "https://api.ani.zip/mappings"
        const val TIMEOUT_MILLIS = 15_000L
    }
}

/** Finds `images[coverType = Clearlogo].url` and skips everything else. */
private fun JsonReader.clearLogo(): String? {
    var logo: String? = null
    beginObject()
    while (hasNext()) {
        if (nextName() != "images" || peek() != JsonReader.Token.BEGIN_ARRAY) {
            skipValue()
            continue
        }
        beginArray()
        while (hasNext()) {
            var type: String? = null
            var url: String? = null
            beginObject()
            while (hasNext()) {
                when (nextName()) {
                    "coverType" -> type = nextString()
                    "url" -> url = nextString()
                    else -> skipValue()
                }
            }
            endObject()
            if (type == "Clearlogo" && logo == null) logo = url
        }
        endArray()
    }
    endObject()
    return logo
}
