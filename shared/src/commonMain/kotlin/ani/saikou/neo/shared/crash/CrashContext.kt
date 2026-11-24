package ani.saikou.neo.shared.crash

import ani.saikou.neo.shared.log.AppLog
import ani.saikou.neo.shared.sources.InstalledSource
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * What the app was doing when a report is made: the screen, what the player is playing, and the errors it recovered
 * from. Does nothing until [install], so tests and builds without a reporter can call it freely. Safe from any thread.
 */
@OptIn(ExperimentalAtomicApi::class)
public object CrashContext {
    private class Installed(
        val reporter: CrashReporter,
        val scrubber: CrashLogScrubber,
    )

    private const val RAW_LIMIT = 8_000
    private const val LINE_LIMIT = 1_000

    private val installed = AtomicReference<Installed?>(null)

    // The player whose keys are set, so an old one closing after the next has opened leaves them.
    private val player = AtomicReference<Any?>(null)

    /** Whether crash reports are on. Off, recovered errors aren't recorded, as they'd only wait on the device. */
    @Volatile
    public var enabled: Boolean = true

    public fun install(
        reporter: CrashReporter,
        scrubber: CrashLogScrubber,
    ) {
        installed.store(Installed(reporter, scrubber))
    }

    /** For tests. */
    public fun reset() {
        installed.store(null)
        player.store(null)
        enabled = true
    }

    /** The screen on show, named without any IDs, such as `details` or `player`. */
    public fun screen(name: String) {
        setKey(SCREEN, name)
    }

    /** [owner], a player, is playing [episode] of [animeId] from [source]. */
    public fun playing(
        owner: Any,
        animeId: Int,
        episode: Int,
        source: InstalledSource,
    ) {
        player.store(owner)
        setKey(ANIME, animeId.toString())
        setKey(EPISODE, episode.toString())
        setKey(EXTENSION, source.id)
        setKey(EXTENSION_VERSION, source.version)
    }

    /** [owner] closed. Its keys are blanked, as Crashlytics can't remove one, unless another player has taken over. */
    public fun stoppedPlaying(owner: Any) {
        if (!player.compareAndSet(owner, null)) return
        listOf(ANIME, EPISODE, EXTENSION, EXTENSION_VERSION).forEach { setKey(it, "") }
    }

    /**
     * Records an error the app recovered from, as [tag] would log [message] and [error], then cleaned as log lines are
     * for reports. The error's own text only goes in cleaned.
     */
    public fun recovered(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) {
        val now = installed.load() ?: return
        if (!enabled) return
        try {
            val raw = "$tag: ${message.take(RAW_LIMIT)}" + error?.let { ": ${AppLog.describe(it)}" }.orEmpty()
            val clean = now.scrubber.scrub(raw.take(RAW_LIMIT))
            now.reporter.recordNonFatal(if (clean.length > LINE_LIMIT) clean.take(LINE_LIMIT) + "…" else clean, error)
        } catch (_: Throwable) {
            // A broken reporter must not stop the caller.
        }
    }

    private fun setKey(
        key: String,
        value: String,
    ) {
        try {
            installed.load()?.reporter?.setKey(key, value)
        } catch (_: Throwable) {
            // A broken reporter must not stop the caller.
        }
    }

    private const val SCREEN = "screen"
    private const val ANIME = "anime_id"
    private const val EPISODE = "episode"
    private const val EXTENSION = "extension"
    private const val EXTENSION_VERSION = "extension_version"
}
