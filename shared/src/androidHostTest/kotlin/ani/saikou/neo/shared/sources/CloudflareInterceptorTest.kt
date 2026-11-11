package ani.saikou.neo.shared.sources

import ani.saikou.neo.extension.api.HostRequest
import ani.saikou.neo.extension.api.SourceException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CloudflareInterceptorTest {
    private val server = MockWebServer()
    private val jar = InMemoryCookieJar()

    @BeforeTest
    fun start() {
        server.start()
    }

    @AfterTest
    fun stop() {
        server.close()
    }

    private fun challenge() =
        MockResponse(code = 503, headers = headers("Server" to "cloudflare"), body = "<title>Just a moment...</title>")

    private fun headers(vararg pairs: Pair<String, String>): Headers {
        val builder = Headers.Builder()
        pairs.forEach { (name, value) -> builder.add(name, value) }
        return builder.build()
    }

    private fun get(clearer: CloudflareClearer) =
        sharedHttpClient(cookieJar = jar, clearer = clearer)
            .newCall(Request.Builder().url(server.url("/")).build())
            .execute()

    private fun hostHttp(clearer: CloudflareClearer) =
        runBlocking {
            SourceHttpClient(sharedHttpClient(cookieJar = jar, clearer = clearer)).http(HostRequest(url = server.url("/").toString()))
        }

    /** Makes the call as Media3 does, on OkHttp's dispatcher, and reports how the call failed and what killed its thread. */
    private fun failEnqueued(clearer: CloudflareClearer): EnqueuedFailure {
        val uncaught = AtomicReference<Throwable?>()
        val threads = CopyOnWriteArrayList<Thread>()
        val executor =
            Executors.newSingleThreadExecutor { task ->
                Thread(task).apply {
                    setUncaughtExceptionHandler { _, error -> uncaught.set(error) }
                    threads += this
                }
            }
        val failure = CompletableFuture<IOException>()
        sharedHttpClient(cookieJar = jar, clearer = clearer)
            .newBuilder()
            .dispatcher(Dispatcher(executor))
            .build()
            .newCall(Request.Builder().url(server.url("/")).build())
            .enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        failure.complete(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        response.close()
                        failure.completeExceptionally(AssertionError("The call should have failed"))
                    }
                },
            )
        val error = failure.get(TEN_SECONDS, TimeUnit.SECONDS)
        executor.shutdown()
        // The executor reports terminated before a dying thread's handler runs, so wait for the threads themselves.
        threads.forEach { it.join(TimeUnit.SECONDS.toMillis(TEN_SECONDS)) }
        return EnqueuedFailure(
            generateSequence<Throwable>(error) { it.cause }.firstNotNullOfOrNull { it as? SourceException },
            uncaught.get(),
        )
    }

    @Test
    fun aChallengeIsClearedThenTheRequestSucceeds() {
        val clearer = FakeClearer(jar, FakeClearer.Outcome.Clear)
        server.enqueue(challenge())
        server.enqueue(MockResponse(code = 200, body = "the page"))

        val response = get(clearer)

        assertEquals("the page", response.body.string())
        assertEquals(1, clearer.calls)
        server.takeRequest()
        assertEquals("cf_clearance=cleared", server.takeRequest().headers["Cookie"])
    }

    @Test
    fun theClearerIsToldWhenTheChallengedRequestWentOut() {
        // So it doesn't hand back the clearance the site just refused, such as one kept from before a restart.
        val clearer = FakeClearer(jar, FakeClearer.Outcome.Clear)
        server.enqueue(challenge())
        server.enqueue(MockResponse(code = 200, body = "the page"))
        val before = System.currentTimeMillis()

        get(clearer).close()

        val sentAt = assertNotNull(clearer.sentAt)
        assertTrue(sentAt in before..System.currentTimeMillis())
    }

    @Test
    fun aCallWaitingOnASiteCheckGetsTheLongLimit() {
        val clearer = FakeClearer(jar, FakeClearer.Outcome.Clear, delayMillis = 1_500)
        server.enqueue(challenge())
        server.enqueue(MockResponse(code = 200, body = "the page"))
        val client = sharedHttpClient(cookieJar = jar, clearer = clearer, callLimit = 500.milliseconds, siteCheckCallLimit = 10.seconds)

        val response = client.newCall(Request.Builder().url(server.url("/")).build()).execute()

        assertEquals("the page", response.body.string())
    }

    @Test
    fun anOrdinaryCallThatStallsFailsAtTheNormalLimit() {
        server.enqueue(
            MockResponse
                .Builder()
                .body("the page")
                .headersDelay(3, TimeUnit.SECONDS)
                .build(),
        )
        val client =
            sharedHttpClient(
                cookieJar = jar,
                clearer = FakeClearer(jar, FakeClearer.Outcome.Clear),
                callLimit = 500.milliseconds,
                siteCheckCallLimit = 10.seconds,
            )

        assertFailsWith<InterruptedIOException> { client.newCall(Request.Builder().url(server.url("/")).build()).execute() }
    }

    @Test
    fun aNormalResponseIsNotTouched() {
        val clearer = FakeClearer(jar, FakeClearer.Outcome.Clear)
        server.enqueue(MockResponse(code = 200, body = "the page"))

        assertEquals("the page", get(clearer).body.string())
        assertEquals(0, clearer.calls)
    }

    @Test
    fun aChallengeThatNeedsAPersonFailsAsBlocked() {
        server.enqueue(challenge())

        assertFailsWith<SourceException.Blocked> { hostHttp(FakeClearer(jar, FakeClearer.Outcome.NeedsPerson)) }
    }

    @Test
    fun aClearanceThatCantBeMintedFailsAsTheSiteBeingDown() {
        server.enqueue(challenge())

        assertFailsWith<SourceException.SourceDown> { hostHttp(FakeClearer(jar, FakeClearer.Outcome.Error)) }
    }

    @Test
    fun aChallengeThatStaysAfterClearingFailsAsBlocked() {
        server.enqueue(challenge())
        server.enqueue(challenge())

        assertFailsWith<SourceException.Blocked> { hostHttp(FakeClearer(jar, FakeClearer.Outcome.Clear)) }
    }

    @Test
    fun aBlockedEnqueuedCallFailsWithoutKillingItsThread() {
        server.enqueue(challenge())

        val failed = failEnqueued(FakeClearer(jar, FakeClearer.Outcome.NeedsPerson))

        assertNull(failed.threadDeath)
        assertIs<SourceException.Blocked>(failed.reason)
    }

    @Test
    fun aChallengeThatStaysFailsAnEnqueuedCallWithoutKillingItsThread() {
        server.enqueue(challenge())
        server.enqueue(challenge())

        val failed = failEnqueued(FakeClearer(jar, FakeClearer.Outcome.Clear))

        assertNull(failed.threadDeath)
        assertIs<SourceException.Blocked>(failed.reason)
    }

    @Test
    fun aPageThatWontLoadFailsAnEnqueuedCallWithoutKillingItsThread() {
        server.enqueue(challenge())

        val failed = failEnqueued(FakeClearer(jar, FakeClearer.Outcome.Error))

        assertNull(failed.threadDeath)
        assertIs<SourceException.SourceDown>(failed.reason)
    }

    @Test
    fun aClearerThatCrashesFailsAnEnqueuedCallWithoutKillingItsThread() {
        server.enqueue(challenge())

        val failed = failEnqueued(FakeClearer(jar, FakeClearer.Outcome.Crash))

        assertNull(failed.threadDeath)
        assertIs<SourceException.SourceDown>(failed.reason)
    }

    @Test
    fun aClearerThatCrashesFailsHostHttpAsTheSiteBeingDown() {
        server.enqueue(challenge())

        assertFailsWith<SourceException.SourceDown> { hostHttp(FakeClearer(jar, FakeClearer.Outcome.Crash)) }
    }
}

private const val TEN_SECONDS = 10L

/** How an enqueued call ended: the [reason] its error carries, and what, if anything, killed the dispatcher's thread. */
private class EnqueuedFailure(
    val reason: SourceException?,
    val threadDeath: Throwable?,
)

/** Stands in for the WebView clearer: it clears, needs a person, errors or crashes, as the test asks. */
private class FakeClearer(
    private val jar: CookieJar,
    private val outcome: Outcome,
    private val delayMillis: Long = 0,
) : CloudflareClearer {
    enum class Outcome { Clear, NeedsPerson, Error, Crash }

    var calls = 0
    var sentAt: Long? = null

    override suspend fun clear(
        url: HttpUrl,
        sentAtMillis: Long,
    ): Clearance? {
        calls++
        sentAt = sentAtMillis
        delay(delayMillis)
        return when (outcome) {
            Outcome.Clear -> {
                jar.saveFromResponse(
                    url,
                    listOf(
                        Cookie
                            .Builder()
                            .name("cf_clearance")
                            .value("cleared")
                            .domain(url.host)
                            .build(),
                    ),
                )
                Clearance("cf_clearance=cleared", SOURCE_USER_AGENT, sentAtMillis, Long.MAX_VALUE)
            }

            Outcome.NeedsPerson -> {
                null
            }

            Outcome.Error -> {
                throw SourceException.SourceDown("the WebView could not load the page")
            }

            Outcome.Crash -> {
                throw IllegalStateException("no WebView on this device")
            }
        }
    }
}
