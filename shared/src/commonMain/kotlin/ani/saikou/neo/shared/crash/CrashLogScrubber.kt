package ani.saikou.neo.shared.crash

import ani.saikou.neo.shared.log.LogRedactor
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Takes what a crash report must not carry out of a log line: URLs, show IDs, episode numbers and source names.
 * The local log keeps them. Safe to use from any thread.
 */
@OptIn(ExperimentalAtomicApi::class)
public class CrashLogScrubber {
    private val terms = AtomicReference<List<Regex>>(emptyList())

    /** Names and IDs of the installed sources. Terms under [MIN_TERM] characters are ignored. */
    public var sourceTerms: Set<String>
        get() = names.load()
        set(value) {
            names.store(value)
            terms.store(
                value
                    .filter { it.length >= MIN_TERM }
                    .sortedByDescending { it.length }
                    .map { Regex(Regex.escape(it), RegexOption.IGNORE_CASE) },
            )
        }

    private val names = AtomicReference<Set<String>>(emptySet())

    public fun scrub(message: String): String {
        var text =
            message
                .replace(url, "[url]")
                .replace(show) { "${it.groupValues[1]} [id]" }
                .replace(episode) { "${it.groupValues[1]} [n]" }
        for (term in terms.load()) text = text.replace(term, "[source]")
        return LogRedactor.redact(text)
    }

    private companion object {
        const val MIN_TERM = 3
        val url = Regex("""\b[a-z][a-z0-9+.\-]*://\S+""", RegexOption.IGNORE_CASE)
        val show = Regex("""\b(show)\s+\d+""", RegexOption.IGNORE_CASE)
        val episode = Regex("""\b(episode)\s+\d+(\.\d+)?""", RegexOption.IGNORE_CASE)
    }
}
