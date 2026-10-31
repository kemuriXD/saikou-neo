package ani.saikou.neo.shared.sources

import com.apollographql.apollo.api.http.HttpMethod
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSource
import okio.IOException

/** An engine that doesn't follow redirects, so [SourceDownloads] can check each one. */
internal expect fun noRedirectsEngine(timeoutMillis: Long): HttpEngine

/**
 * Downloads a source's APK. Every link on the way, redirects included, must pass [isAllowedSourceLink], and once a
 * link is https, the rest must be too.
 */
public class SourceDownloads internal constructor(
    private val engine: HttpEngine,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val maxBytes: Long = MAX_BYTES,
) {
    public constructor() : this(noRedirectsEngine(TIMEOUT_MILLIS))

    /** The file at [url], or null when it isn't allowed, can't be fetched, or is too big to be a source. */
    public suspend fun fetch(url: String): ByteArray? =
        withContext(io) {
            try {
                var next = url
                var secure = false
                repeat(MAX_REDIRECTS + 1) {
                    if (!isAllowedSourceLink(next)) return@withContext null
                    if (next.startsWith("https://", ignoreCase = true)) {
                        secure = true
                    } else if (secure) {
                        return@withContext null
                    }
                    val response = engine.execute(HttpRequest.Builder(HttpMethod.Get, next).build())
                    val body = response.body
                    val location = response.headers.firstOrNull { it.name.equals("Location", ignoreCase = true) }?.value
                    when {
                        response.statusCode in REDIRECTS && location != null -> {
                            body?.close()
                            next = resolve(next, location) ?: return@withContext null
                        }

                        response.statusCode == 200 && body != null -> {
                            return@withContext body.use { it.readAtMost(maxBytes) }
                        }

                        else -> {
                            body?.close()
                            return@withContext null
                        }
                    }
                }
                null
            } catch (_: ApolloException) {
                null
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                // The network library refused the link.
                null
            }
        }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val MAX_REDIRECTS = 5

        // Sources are a few megabytes at most, and the download is held in memory on TVs with little of it.
        const val MAX_BYTES = 16L * 1024 * 1024
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}

/** Everything left to read, or null when it's more than [max] bytes. */
private fun BufferedSource.readAtMost(max: Long): ByteArray? {
    val buffer = Buffer()
    while (buffer.size <= max) {
        if (read(buffer, 8_192) == -1L) return buffer.readByteArray()
    }
    return null
}

/** Where a redirect from [from] to [location] goes: a full link, or a path on the same site. */
private fun resolve(
    from: String,
    location: String,
): String? {
    val scheme = from.substringBefore("://")
    return when {
        "://" in location -> location
        location.startsWith("//") -> "$scheme:$location"
        location.startsWith("/") -> "$scheme://" + from.substringAfter("://").substringBefore('/') + location
        else -> null
    }
}
