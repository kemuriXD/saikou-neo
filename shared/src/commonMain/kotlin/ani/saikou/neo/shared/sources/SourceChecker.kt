package ani.saikou.neo.shared.sources

import ani.saikou.neo.extension.api.Anime
import ani.saikou.neo.extension.api.AnimeIds
import ani.saikou.neo.extension.api.AnimeTitles
import ani.saikou.neo.extension.api.Episode
import ani.saikou.neo.extension.api.HostRequest
import ani.saikou.neo.extension.api.HostServices
import ani.saikou.neo.extension.api.Season
import ani.saikou.neo.extension.api.Server
import ani.saikou.neo.extension.api.SourceException
import ani.saikou.neo.extension.api.SourceExtension
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.playback.SourceFailure
import ani.saikou.neo.shared.playback.failReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The steps of a source check, in the order they run. */
public enum class CheckStep {
    /** Load the source. */
    START,

    /** Request the source's site. Skipped when it doesn't name one. */
    SITE,

    /** Ask for servers for a known episode, which also covers the source's own search. */
    SERVERS,

    /** Get the stream for the first server. */
    STREAM,
}

/** How a step went. */
public sealed interface StepOutcome {
    public data object Passed : StepOutcome

    /** The step didn't apply, such as the site step for a source that names no site. */
    public data object Skipped : StepOutcome

    /** The step failed for [reason], the same reasons the app shows when finding servers. */
    public data class Failed(
        val reason: FailReason,
    ) : StepOutcome
}

/** One step of a check, how it went and how long it [took]. */
public data class StepResult(
    val step: CheckStep,
    val outcome: StepOutcome,
    val took: Duration,
)

// How long one step can take before it counts as no answer.
internal val STEP_LIMIT = 30.seconds

/**
 * Checks an installed source from loading to stream, without playing anything. It walks the path playback takes and
 * stops at the first step that fails. [load] gives the source with [host] already attached, as playback does, and the
 * site is requested through [host] so a Cloudflare check doesn't make a working site look down. Sources run in [work].
 */
public class SourceChecker(
    private val load: suspend (InstalledSource) -> SourceExtension?,
    private val host: HostServices,
    private val work: CoroutineContext = EmptyCoroutineContext,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    /** Checks [source], handing each step to [onStep] as it finishes, and returns them all. */
    public suspend fun check(
        source: InstalledSource,
        onStep: (StepResult) -> Unit = {},
    ): List<StepResult> {
        val steps = mutableListOf<StepResult>()

        // Runs one step and records it. A step whose block gives null was skipped.
        suspend fun <T> run(
            step: CheckStep,
            block: suspend () -> T,
        ): T? {
            val started = timeSource.markNow()
            var value: T? = null
            val outcome =
                try {
                    value = withTimeout(STEP_LIMIT) { withContext(work) { block() } }
                    if (value == null) StepOutcome.Skipped else StepOutcome.Passed
                } catch (e: TimeoutCancellationException) {
                    StepOutcome.Failed(FailReason.NoAnswer)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SourceFailure) {
                    StepOutcome.Failed(e.reason)
                } catch (e: Throwable) {
                    StepOutcome.Failed(e.failReason())
                }
            val result = StepResult(step, outcome, started.elapsedNow())
            steps += result
            onStep(result)
            return value
        }

        val extension = run(CheckStep.START) { start(source) } ?: return steps
        run(CheckStep.SITE) { extension.siteUrl?.let { visit(it) } }
        if (steps.last().outcome is StepOutcome.Failed) return steps
        val servers = run(CheckStep.SERVERS) { findServers(extension) } ?: return steps
        run(CheckStep.STREAM) { extension.stream(servers.first()) }
        return steps
    }

    private suspend fun start(source: InstalledSource): SourceExtension =
        try {
            load(source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw SourceFailure(FailReason.WontStart, e)
        } ?: throw SourceFailure(FailReason.WontStart)

    private suspend fun visit(site: String) {
        // Only a web address is requested, whatever the source names.
        if (!site.startsWith("https://") && !site.startsWith("http://")) throw SourceFailure(FailReason.SourceDown)
        val reason =
            when (host.http(HostRequest(site)).status) {
                in 200..399 -> return
                403 -> FailReason.Blocked
                429 -> FailReason.RateLimited(null)
                else -> FailReason.SourceDown
            }
        throw SourceFailure(reason)
    }

    // A source can miss one show, so it counts as not found only when every show comes back empty.
    private suspend fun findServers(extension: SourceExtension): List<Server> {
        for (show in KNOWN_SHOWS) {
            val servers =
                try {
                    extension.servers(Episode(show, 1))
                } catch (e: SourceException.NotFound) {
                    emptyList()
                }
            if (servers.isNotEmpty()) return servers
        }
        throw SourceFailure(FailReason.NotFound)
    }
}

// Well-known finished shows that most sites carry, tried in order.
private val KNOWN_SHOWS =
    listOf(
        Anime(
            ids = AnimeIds(anilist = 1, mal = 1, anidb = null, kitsu = null),
            titles = AnimeTitles("Cowboy Bebop", "Cowboy Bebop", "カウボーイビバップ", emptyList()),
            season = Season.Spring,
            year = 1998,
        ),
        Anime(
            ids = AnimeIds(anilist = 5114, mal = 5114, anidb = null, kitsu = null),
            titles =
                AnimeTitles(
                    romaji = "Hagane no Renkinjutsushi: FULLMETAL ALCHEMIST",
                    english = "Fullmetal Alchemist: Brotherhood",
                    native = "鋼の錬金術師 FULLMETAL ALCHEMIST",
                    synonyms = emptyList(),
                ),
            season = Season.Spring,
            year = 2009,
        ),
        Anime(
            ids = AnimeIds(anilist = 1535, mal = 1535, anidb = null, kitsu = null),
            titles = AnimeTitles("DEATH NOTE", "Death Note", "デスノート", emptyList()),
            season = Season.Fall,
            year = 2006,
        ),
    )
