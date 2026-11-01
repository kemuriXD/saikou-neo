package ani.saikou.neo.shared.sources

import android.os.Looper
import ani.saikou.neo.extension.api.SourceException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Clears Cloudflare for the shared client. When a response is a challenge it
 * hands the URL to [clearer] on the main thread, blocking this request (not the
 * dispatcher), then retries once with the clearance now in the jar. A challenge
 * that needs a person stops as [SourceException.Blocked]; a page that won't load
 * stops as `SourceException.SourceDown`, thrown by [clearer].
 */
internal class CloudflareInterceptor(
    private val clearer: CloudflareClearer,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // clear() needs the main thread, so this must run off it, or runBlocking would deadlock.
        requireOffMainThread()
        val response = chain.proceed(chain.request())
        if (!response.isCloudflareChallenge()) return response
        response.close()

        runBlocking { clearer.clear(chain.request().url) }
            ?: throw SourceException.Blocked("Cloudflare needs a browser check a TV can't do")

        val retry = chain.proceed(chain.request())
        if (!retry.isCloudflareChallenge()) return retry
        retry.close()
        throw SourceException.Blocked("The Cloudflare challenge did not clear")
    }
}

/** Reads enough of the response to tell a Cloudflare challenge from a plain page. */
private fun Response.isCloudflareChallenge(): Boolean {
    val body = peekBody(CHALLENGE_PEEK_BYTES).string()
    val headerMap = headers.names().associateWith { headers[it].orEmpty() }
    return cloudflareChallenge(code, headerMap, body)
}

private const val CHALLENGE_PEEK_BYTES = 64L * 1024

/** Fails loudly instead of deadlocking if a request is ever made on the main thread. Off in a unit test, where there is no Looper. */
private fun requireOffMainThread() {
    val mainLooper = runCatching { Looper.getMainLooper() }.getOrNull() ?: return
    check(Looper.myLooper() !== mainLooper) { "The source HTTP client must not run on the main thread" }
}
