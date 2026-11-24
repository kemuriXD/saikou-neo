package ani.saikou.neo.shared.sources

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlainThreadCallsTest {
    private val server = MockWebServer()
    private val threads = mutableListOf<String>()
    private val client =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                synchronized(threads) { threads += Thread.currentThread().name }
                chain.proceed(chain.request())
            }.build()
    private val calls = PlainThreadCalls(client)

    @BeforeTest
    fun start() = server.start()

    @AfterTest
    fun stop() = server.close()

    private fun request(path: String = "/watch/one-piece-100") = Request.Builder().url(server.url(path)).build()

    /** Enqueues [call] and waits for its answer: the response, or the failure thrown. */
    private fun answer(call: Call): Response {
        val result = CompletableFuture<Response>()
        call.enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    result.completeExceptionally(e)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    result.complete(response)
                }
            },
        )
        return try {
            result.get(5, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause!!
        }
    }

    @Test
    fun anEnqueuedCallRunsOnAThreadThatNamesNoHost() {
        server.enqueue(MockResponse.Builder().body("#EXTM3U").build())

        answer(calls.newCall(request())).use { assertEquals("#EXTM3U", it.body.string()) }

        val name = threads.single()
        assertEquals("Stream", name)
        assertFalse(server.hostName in name)
    }

    @Test
    fun aFailureReachesTheCallback() {
        val call = calls.newCall(request())
        server.close()

        assertFailsWith<IOException> { answer(call) }
    }

    @Test
    fun cancellingStopsAWaitingCall() {
        server.enqueue(MockResponse.Builder().headersDelay(10, TimeUnit.SECONDS).build())
        val call = calls.newCall(request())
        val result = CompletableFuture<IOException>()
        call.enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    result.complete(e)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    response.close()
                }
            },
        )

        Thread.sleep(200)
        call.cancel()

        result.get(5, TimeUnit.SECONDS)
        assertTrue(call.isCanceled())
    }

    @Test
    fun aCallRunsOnlyOnce() {
        server.enqueue(MockResponse.Builder().build())
        val call = calls.newCall(request())
        answer(call).close()

        assertTrue(call.isExecuted())
        assertFailsWith<IllegalStateException> { answer(call) }
    }

    @Test
    fun executeStillWorks() {
        server.enqueue(MockResponse.Builder().body("ok").build())

        calls.newCall(request()).execute().use { assertEquals("ok", it.body.string()) }
    }
}
