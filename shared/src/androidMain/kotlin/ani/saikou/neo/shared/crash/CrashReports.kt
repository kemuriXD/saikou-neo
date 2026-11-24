package ani.saikou.neo.shared.crash

import ani.saikou.neo.shared.account.SignedInAccount
import ani.saikou.neo.shared.log.AppLog
import ani.saikou.neo.shared.log.LogLine
import ani.saikou.neo.shared.log.LogSink
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.sources.sourceIdFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * What goes with a crash report: the device's details, the signed-in AniList user and a trail of scrubbed log lines,
 * plus what [CrashContext] is told. Sending follows the Crash reports setting.
 */
public class CrashReports(
    private val reporter: CrashReporter,
    private val scope: CoroutineScope,
) {
    public val scrubber: CrashLogScrubber = CrashLogScrubber()

    private val sink = HoldingSink(reporter, scrubber)

    /**
     * Starts passing log lines on at once, so call it before anything is logged. [facts] are read and [settings],
     * [account] and [network] followed in [scope], so nothing here blocks. A crash before the settings load follows
     * the choice the reporter saved on the last run.
     */
    public fun start(
        facts: suspend () -> Map<String, String>,
        settings: Flow<Settings>,
        account: Flow<SignedInAccount?>,
        network: Flow<String> = emptyFlow(),
    ) {
        AppLog.addSink(sink)
        // Off until the setting is read, as it could be off.
        CrashContext.enabled = false
        CrashContext.install(reporter, scrubber)
        // Only changes, the first value included. The reporter keeps the choice for the next run, where it fully
        // applies, and deletes the reports kept while it was off.
        scope.launch(failed) {
            settings.map { it.crashReports }.distinctUntilChanged().collect {
                CrashContext.enabled = it
                safely { reporter.setEnabled(it) }
            }
        }
        scope.launch(failed) {
            facts().forEach { (key, value) -> safely { reporter.setKey(key, value) } }
            network.collect { safely { reporter.setKey(NETWORK, it) } }
        }
        scope.launch(failed) {
            account.collect { safely { reporter.setUser(it?.profile?.id?.toString(), it?.profile?.name) } }
        }
    }

    // A flow or the facts failing stops that one part, never the app.
    private val failed = CoroutineExceptionHandler { _, error -> AppLog.w(TAG, "Crash report details failed", error) }

    // A broken reporter must not stop the next call.
    private inline fun safely(call: () -> Unit) {
        try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Nothing to do: the reporter is the place errors would go.
        }
    }

    /**
     * Adds the names and IDs of installed sources to what is scrubbed. The first call also sends the source lines held
     * until now. Terms are never taken away, as a removed source can still be named later in the run.
     */
    public fun addSourceTerms(terms: Set<String>) {
        sink.addTerms(terms)
    }

    private companion object {
        const val NETWORK = "network"
        const val TAG = "App"
    }
}

/**
 * Holds the source store's lines until the sources' names are known, since its first lines name them, then sends them
 * scrubbed. Other lines go straight on. Only [LIMIT] lines are held, and the rest counted.
 */
private class HoldingSink(
    private val reporter: CrashReporter,
    private val scrubber: CrashLogScrubber,
) : LogSink {
    private val out = CrashLogSink(reporter, scrubber)
    private val lock = Any()

    // Null once the terms are known.
    private var held: MutableList<LogLine>? = mutableListOf()
    private var dropped = 0

    override fun write(line: LogLine) {
        if (line.tag == SOURCES) {
            synchronized(lock) {
                val waiting = held
                if (waiting != null) {
                    if (waiting.size < LIMIT) waiting += line else dropped++
                    return
                }
                // Inside the lock, so a line can't overtake the held ones.
                out.write(withoutIds(line))
            }
        } else {
            out.write(line)
        }
    }

    fun addTerms(terms: Set<String>) {
        synchronized(lock) {
            scrubber.sourceTerms += terms
            val waiting = held ?: return
            held = null
            waiting.forEach { out.write(withoutIds(it)) }
            if (dropped > 0) runCatching { reporter.log("I $SOURCES: $dropped earlier lines dropped") }
        }
    }

    /**
     * The store names a source by its ID in a few places, sometimes one that is being installed and so isn't a term
     * yet. Those places, and anything shaped like a source ID, are taken out whatever the terms are.
     */
    private fun withoutIds(line: LogLine): LogLine =
        line.copy(
            message =
                line.message
                    .replace(namedId) { "${it.groupValues[1]}[source]" }
                    .replace(sourceIdFormat, "[source]"),
        )

    private companion object {
        const val SOURCES = "Sources"
        const val LIMIT = 50

        // The word after these phrases is a source ID or class name. "Install rejected: the file" isn't one.
        val namedId = Regex("""(Found source |Install rejected: (?!the file\b)|icon of |load )\S+""")
    }
}
