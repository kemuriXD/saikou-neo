package ani.saikou.neo.shared.sources

import android.os.Looper
import ani.saikou.neo.extension.api.SourceException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okio.AsyncTimeout
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Clears Cloudflare for the shared client. When a response is a challenge it
 * hands the URL to [clearer] on the main thread, blocking this request (not the
 * dispatcher), then retries once with the clearance now in the jar. A challenge
 * that needs a person stops as [SourceException.Blocked]; a page that won't load
 * or a clearer that breaks stops as [SourceException.SourceDown]. Both leave here
 * inside a [SourceFailure], because the player's calls run on OkHttp's dispatcher,
 * where anything but an [IOException] is rethrown and takes the app down.
 */
internal class CloudflareInterceptor(
    private val clearer: CloudflareClearer,
    private val siteCheckCallLimit: Duration = SITE_CHECK_CALL_LIMIT,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // clear() needs the main thread, so this must run off it, or runBlocking would deadlock.
        requireOffMainThread()
        val response = chain.proceed(chain.request())
        if (!response.isCloudflareChallenge()) return response
        response.close()

        // Only now does this call wait on a browser check, so only now does it get the long limit.
        allowSiteCheck(chain)
        clear(chain.request().url)
            ?: throw SourceFailure(SourceException.Blocked("Cloudflare needs a browser check a TV can't do"))

        val retry = chain.proceed(chain.request())
        if (!retry.isCloudflareChallenge()) return retry
        retry.close()
        throw SourceFailure(SourceException.Blocked("The Cloudflare challenge did not clear"))
    }

    /**
     * Restarts the call's whole-call timer at [siteCheckCallLimit]. OkHttp runs that timer as an okio [AsyncTimeout]
     * entered when the call starts; leaving it and entering again keeps it balanced for the exit OkHttp makes when
     * the call ends. A call already timed out stays failed.
     */
    private fun allowSiteCheck(chain: Interceptor.Chain) {
        val timer = chain.call().timeout() as? AsyncTimeout ?: return
        if (timer.exit()) throw InterruptedIOException("timeout")
        timer.timeout(siteCheckCallLimit.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        timer.enter()
    }

    private fun clear(url: HttpUrl): Clearance? =
        try {
            runBlocking { clearer.clear(url) }
        } catch (e: SourceException) {
            throw SourceFailure(e)
        } catch (e: Exception) {
            // A device with no working WebView lands here, and must fail this call like any other.
            throw SourceFailure(SourceException.SourceDown("The Cloudflare check could not run", e))
        }
}

/** Carries [failure] through OkHttp as the [IOException] it needs. `host.http` throws [failure]; the player finds it as a cause. */
internal class SourceFailure(
    val failure: SourceException,
) : IOException(failure.message, failure)

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
