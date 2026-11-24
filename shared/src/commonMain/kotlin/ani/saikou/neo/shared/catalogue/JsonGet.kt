package ani.saikou.neo.shared.catalogue

import ani.saikou.neo.shared.log.AppLog
import com.apollographql.apollo.api.http.HttpMethod
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.api.json.jsonReader
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.IOException
import kotlin.math.roundToInt

/**
 * Asks for [url] and reads its JSON, or null when the site can't answer. With a [logTag], a failed call is written to the
 * app log under it, as the status or error only.
 */
internal suspend fun <T> HttpEngine.getJson(
    url: String,
    io: CoroutineDispatcher,
    logTag: String? = null,
    read: JsonReader.() -> T?,
): T? =
    // The engine blocks while it waits for the network, so keep it off the main thread, as ApolloClient does.
    withContext(io) {
        try {
            val response = execute(HttpRequest.Builder(HttpMethod.Get, url).build())
            if (response.statusCode != 200 && logTag != null) AppLog.w(logTag, "Request answered ${response.statusCode}")
            response.readIfOk { it.jsonReader().read() }
        } catch (e: ApolloException) {
            logTag?.let { AppLog.w(it, "Request failed", e) }
            null
        } catch (e: IOException) {
            // The connection dropped while the answer was read.
            logTag?.let { AppLog.w(it, "Request failed", e) }
            null
        }
    }

/**
 * Reads the body of a 200 with [read], or gives null for any other answer. The body is closed either way: one left open
 * leaks its connection, which piles up on TVs with little memory (#362).
 */
internal inline fun <T> HttpResponse.readIfOk(read: (BufferedSource) -> T?): T? = body?.use { if (statusCode == 200) read(it) else null }

/** A score such as `8.65` or `"8.6"`, out of 10, as a whole number out of 100. Halves round up. */
internal fun outOfTen(score: String): Int? = score.toDoubleOrNull()?.takeIf { it in 0.0..10.0 }?.let { (it * 10).roundToInt() }
