package ani.saikou.neo.shared.log

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Writes failed calls to the app log: an answer of 400 or more, or a call that threw. A call the app cancelled is
 * left out, as that is the app giving up, not the network failing. One that timed out is logged. The URL goes in as it is, as [AppLog] cuts it down
 * to the host.
 */
internal class NetworkLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val started = System.nanoTime()
        val response =
            try {
                chain.proceed(request)
            } catch (e: IOException) {
                if (!chain.call().isCanceled() || e is InterruptedIOException ||
                    timedOut(chain, started)
                ) {
                    AppLog.w(TAG, "${request.method} ${request.url} failed", e)
                }
                throw e
            }
        if (response.code >= 400 && !chain.call().isCanceled()) AppLog.w(TAG, "${request.method} ${request.url} -> ${response.code}")
        return response
    }

    // OkHttp ends a call that runs out of time by cancelling it, so a cancelled call that ran as long as its limit timed
    // out, and one that stopped sooner was given up on by the app.
    private fun timedOut(
        chain: Interceptor.Chain,
        started: Long,
    ): Boolean {
        val limit = chain.call().timeout().timeoutNanos()
        return limit > 0 && System.nanoTime() - started >= limit - SLACK_NANOS
    }

    private companion object {
        // The call's clock starts a little before this interceptor runs.
        const val SLACK_NANOS = 100_000_000L

        const val TAG = "Net"
    }
}
