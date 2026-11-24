package ani.saikou.neo.shared.log

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Writes failed calls to the app log: an answer of 400 or more, or a call that threw. A call that was cancelled is
 * left out, as that is the app giving up, not the network failing. The URL goes in as it is, as [AppLog] cuts it down
 * to the host.
 */
internal class NetworkLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response =
            try {
                chain.proceed(request)
            } catch (e: IOException) {
                if (!chain.call().isCanceled()) AppLog.w(TAG, "${request.method} ${request.url} failed", e)
                throw e
            }
        if (response.code >= 400 && !chain.call().isCanceled()) AppLog.w(TAG, "${request.method} ${request.url} -> ${response.code}")
        return response
    }

    private companion object {
        const val TAG = "Net"
    }
}
