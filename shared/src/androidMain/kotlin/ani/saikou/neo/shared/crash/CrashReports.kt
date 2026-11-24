package ani.saikou.neo.shared.crash

import ani.saikou.neo.shared.account.SignedInAccount
import ani.saikou.neo.shared.log.AppLog
import ani.saikou.neo.shared.log.LogLine
import ani.saikou.neo.shared.log.LogSink
import ani.saikou.neo.shared.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * What goes with a crash report: the device's details, the signed-in AniList user and a trail of scrubbed log lines.
 * Sending follows the Crash reports setting.
 */
public class CrashReports(
    private val reporter: CrashReporter,
    private val scope: CoroutineScope,
) {
    public val scrubber: CrashLogScrubber = CrashLogScrubber()

    private val sink = HoldingSink(reporter, scrubber)

    /**
     * Starts passing log lines on at once, so call it before anything is logged. [facts] are read and [settings],
     * [account] and [network] followed in [scope], so nothing here blocks.
     */
    public fun start(
        facts: suspend () -> Map<String, String>,
        settings: Flow<Settings>,
        account: Flow<SignedInAccount?>,
        network: Flow<String> = emptyFlow(),
    ) {
        AppLog.addSink(sink)
        // Every emission, so turning it off stops sending now. Crashlytics keeps the choice for the next run too.
        scope.launch { settings.collect { reporter.setEnabled(it.crashReports) } }
        scope.launch {
            facts().forEach { (key, value) -> reporter.setKey(key, value) }
            network.collect { reporter.setKey(NETWORK, it) }
        }
        scope.launch { account.collect { reporter.setUser(it?.profile?.id?.toString(), it?.profile?.name) } }
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
                out.write(line)
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
            waiting.forEach(out::write)
            if (dropped > 0) runCatching { reporter.log("I $SOURCES: $dropped earlier lines dropped") }
        }
    }

    private companion object {
        const val SOURCES = "Sources"
        const val LIMIT = 50
    }
}
