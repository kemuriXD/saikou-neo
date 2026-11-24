package ani.saikou.neo.shared.sources

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Makes calls through [delegate], but runs enqueued ones on threads named "Stream". OkHttp names its own threads after
 * the request's host while a call runs, and a crash report lists every thread's name, so a stream's host would go with
 * it. Calls made with `execute()` run where they are, as OkHttp doesn't rename that thread.
 */
public class PlainThreadCalls(
    private val delegate: Call.Factory,
) : Call.Factory {
    override fun newCall(request: Request): Call = PlainThreadCall(delegate.newCall(request))
}

private class PlainThreadCall(
    private val call: Call,
) : Call by call {
    private val enqueued = AtomicBoolean()

    override fun enqueue(responseCallback: Callback) {
        check(enqueued.compareAndSet(false, true)) { "Already Executed" }
        threads.execute {
            val response =
                try {
                    call.execute()
                } catch (e: IOException) {
                    responseCallback.onFailure(this, e)
                    return@execute
                } catch (e: Throwable) {
                    // As OkHttp does: the caller hears of it, then the thread fails.
                    call.cancel()
                    responseCallback.onFailure(this, IOException("canceled due to $e", e))
                    throw e
                }
            responseCallback.onResponse(this, response)
        }
    }

    override fun isExecuted(): Boolean = enqueued.get() || call.isExecuted()

    override fun clone(): Call = PlainThreadCall(call.clone())

    private companion object {
        // Grows as calls need it and lets idle threads go, like OkHttp's own pool.
        val threads: Executor =
            Executors.newCachedThreadPool { task -> Thread(task, "Stream").apply { isDaemon = true } }
    }
}
