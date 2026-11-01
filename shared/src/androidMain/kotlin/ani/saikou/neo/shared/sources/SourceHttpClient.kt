package ani.saikou.neo.shared.sources

import ani.saikou.neo.extension.api.HostMethod
import ani.saikou.neo.extension.api.HostRequest
import ani.saikou.neo.extension.api.HostResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The most of a response body to read into memory, so a hostile server can't run the app out of it. */
internal const val MAX_BODY_BYTES: Long = 16L * 1024 * 1024

/**
 * The app's one HTTP client for sources and the player. It holds the shared
 * cookie jar and sends the pinned User-Agent on every request, so a cookie a
 * source picks up is there when the player fetches, and Cloudflare sees one
 * browser. Backs `HostServices.http`; Cloudflare clearance and Media3 reuse the
 * same [client].
 */
internal class SourceHttpClient(
    val client: OkHttpClient = sharedHttpClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val maxBodyBytes: Long = MAX_BODY_BYTES,
) {
    suspend fun http(request: HostRequest): HostResponse =
        withContext(io) {
            client.newCall(request.toOkHttpRequest()).execute().use { it.toHostResponse(maxBodyBytes) }
        }
}

/**
 * The shared client. The pinned User-Agent is forced on every request, so a source can't change it. With a [clearer] it
 * also clears Cloudflare and keeps the clearance in [cookieJar] for later requests and the player.
 */
internal fun sharedHttpClient(
    cookieJar: CookieJar = InMemoryCookieJar(),
    clearer: CloudflareClearer? = null,
): OkHttpClient =
    OkHttpClient
        .Builder()
        .cookieJar(cookieJar)
        // A whole call, including a slow drip, can't hold a connection and a thread forever.
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain
                    .request()
                    .newBuilder()
                    .header("User-Agent", SOURCE_USER_AGENT)
                    .build(),
            )
        }.apply { if (clearer != null) addInterceptor(CloudflareInterceptor(clearer)) }
        .build()

private fun HostRequest.toOkHttpRequest(): Request {
    val builder = Request.Builder().url(url)
    when (method) {
        HostMethod.Get -> builder.get()

        // As bytes, so OkHttp sends the source's content type as given instead of adding a charset.
        HostMethod.Post -> builder.post((body ?: "").encodeToByteArray().toRequestBody(contentType?.toMediaTypeOrNull()))
    }
    headers.forEach { (name, value) -> builder.header(name, value) }
    return builder.build()
}

private fun Response.toHostResponse(maxBodyBytes: Long): HostResponse {
    // Buffer one byte past the cap first, so an endless or oversized body fails instead of filling the heap.
    val source = body.source()
    source.request(maxBodyBytes + 1)
    if (source.buffer.size > maxBodyBytes) throw IOException("Response body is larger than $maxBodyBytes bytes")
    return HostResponse(
        status = code,
        headers = headers.toSingleValueMap(),
        body = body.string(),
    )
}

/** Keeps each header name as the server sent it, with its last value, which is enough for a source reading a response. */
private fun Headers.toSingleValueMap(): Map<String, String> = names().associateWith { name -> this[name].orEmpty() }
