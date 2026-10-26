package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.http.HttpMethod
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.api.json.jsonReader
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.IOException
import kotlin.math.roundToInt

/** Asks for [url] and reads its JSON, or null when the site can't answer. Nothing here logs [url]. */
internal suspend fun <T> HttpEngine.getJson(
    url: String,
    io: CoroutineDispatcher,
    read: JsonReader.() -> T?,
): T? =
    // The engine blocks while it waits for the network, so keep it off the main thread, as ApolloClient does.
    withContext(io) {
        try {
            val response = execute(HttpRequest.Builder(HttpMethod.Get, url).build())
            val body = response.body
            if (response.statusCode != 200 || body == null) null else body.use { it.jsonReader().read() }
        } catch (_: ApolloException) {
            null
        } catch (_: IOException) {
            // The connection dropped while the answer was read.
            null
        }
    }

/** A score such as `8.65` or `"8.6"`, out of 10, as a whole number out of 100. Halves round up. */
internal fun outOfTen(score: String): Int? = score.toDoubleOrNull()?.takeIf { it in 0.0..10.0 }?.let { (it * 10).roundToInt() }
