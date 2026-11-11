package ani.saikou.neo.shared.sources

import ani.saikou.neo.extension.api.HostRequest
import ani.saikou.neo.extension.api.HostServices
import ani.saikou.neo.extension.api.SourceExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** How long after a site is visited to get it ready it isn't visited again. */
internal val WARM_UP_EVERY: Duration = 10.minutes

/**
 * Gets the installed sources' sites ready before an episode is played. It visits each site through [host] in [scope],
 * so a site with a browser check has it run now, in the background, and the clearance is there when the episode's
 * servers are asked for (#369). A site without one costs a single request. A site visited in the last
 * [WARM_UP_EVERY] is left alone, and a visit that fails is let go: the real request will meet the same problem and say so.
 */
public class SourceWarmUp(
    private val installed: StateFlow<List<InstalledSource>?>,
    private val extension: suspend (InstalledSource) -> SourceExtension?,
    private val host: HostServices,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val lock = Mutex()
    private val visitedAt = mutableMapOf<String, TimeMark>()

    /** Visits the sites of the sources installed now that haven't been visited lately. Returns at once. */
    public fun warm() {
        scope.launch {
            installed.value
                .orEmpty()
                .filter { it.problem == null && claim(it.id) }
                .forEach { source -> launch { visit(source) } }
        }
    }

    /** Whether [id] is due a visit, marking it visited now if so. */
    private suspend fun claim(id: String): Boolean =
        lock.withLock {
            if (visitedAt[id]?.let { it.elapsedNow() < WARM_UP_EVERY } == true) return@withLock false
            visitedAt[id] = timeSource.markNow()
            true
        }

    private suspend fun visit(source: InstalledSource) {
        try {
            val site = extension(source)?.siteUrl ?: return
            // Only a web address is requested, whatever the source names.
            if (!site.startsWith("https://") && !site.startsWith("http://")) return
            host.http(HostRequest(site))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Let go: the episode's own requests will meet the same problem and report it.
        }
    }
}
