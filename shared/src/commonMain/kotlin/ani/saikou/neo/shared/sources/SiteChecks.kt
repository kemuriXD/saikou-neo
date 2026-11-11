package ani.saikou.neo.shared.sources

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * How long a site's browser check gets. A Fire TV on Fire OS 7 took 35 to 100 s to pass one that a newer TV passes in
 * under 10 s (#356).
 */
internal val SITE_CHECK_LIMIT: Duration = 120.seconds

/** How long a site whose check failed isn't checked again, so those waiting behind it don't each run one. */
internal val SITE_CHECK_RETRY: Duration = 60.seconds

/**
 * Runs sites' browser checks one at a time, since each is a WebView running the site's script and a TV with little
 * memory can't run many. Asking while one runs waits for it. A site whose check gave nothing isn't checked again for
 * [SITE_CHECK_RETRY]: those waiting get nothing at once. [checking] says while a check runs.
 */
internal class SiteChecks(
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val lock = Mutex()
    private val failedAt = mutableMapOf<String, TimeMark>()
    private val mutableChecking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = mutableChecking.asStateFlow()

    /** Runs [check] for [host] once no other check is running. Null or a failure counts as the check failing. */
    suspend fun <T : Any> run(
        host: String,
        check: suspend () -> T?,
    ): T? =
        lock.withLock {
            failedAt[host]?.let { if (it.elapsedNow() < SITE_CHECK_RETRY) return@withLock null }
            failedAt.remove(host)
            mutableChecking.value = true
            var answer: T? = null
            try {
                answer = check()
                answer
            } finally {
                if (answer == null) failedAt[host] = timeSource.markNow()
                mutableChecking.value = false
            }
        }
}

/** An answer, which may itself be null, as opposed to none in time. */
internal class Answer<T>(
    val value: T,
)

/**
 * Waits up to [limit] for this call's answer. If that runs out while a site's browser check is running ([siteChecking]),
 * it waits for the check to end, up to [SITE_CHECK_LIMIT], and then gives the call [limit] again to use the site it got
 * into. Null when no answer comes in that time.
 */
internal suspend fun <T> Deferred<T>.awaitAllowingSiteChecks(
    limit: Duration,
    siteChecking: StateFlow<Boolean>,
): Answer<T>? {
    withTimeoutOrNull(limit) { Answer(await()) }?.let { return it }
    if (!siteChecking.value) return null
    withTimeoutOrNull(SITE_CHECK_LIMIT) { siteChecking.first { !it } } ?: return null
    return withTimeoutOrNull(limit) { Answer(await()) }
}
