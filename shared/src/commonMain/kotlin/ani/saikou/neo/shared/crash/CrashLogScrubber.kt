package ani.saikou.neo.shared.crash

import ani.saikou.neo.shared.log.LogRedactor
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Takes what a crash report must not carry out of a log line: URLs, host names, show IDs, episode numbers and source
 * names. The local log keeps them. Safe to use from any thread.
 *
 * Show IDs and episode numbers are only caught when written as `show <n>` or `episode <n>`, so new log lines should
 * use that wording.
 */
@OptIn(ExperimentalAtomicApi::class)
public class CrashLogScrubber {
    private class Terms(
        val names: Set<String>,
        val patterns: List<Regex>,
    )

    // One value, so a reader never sees the names of one update and the patterns of another.
    private val terms = AtomicReference(Terms(emptySet(), emptyList()))

    /** Names and IDs of the installed sources. Terms under [MIN_TERM] characters are ignored. */
    public var sourceTerms: Set<String>
        get() = terms.load().names
        set(value) {
            terms.store(
                Terms(
                    value,
                    value
                        .filter { it.length >= MIN_TERM }
                        .sortedByDescending { it.length }
                        .map { Regex(Regex.escape(it), RegexOption.IGNORE_CASE) },
                ),
            )
        }

    public fun scrub(message: String): String {
        var text =
            message
                .replace(url, "[url]")
                .replace(protocolRelative, "[url]")
                .replace(show) { "${it.groupValues[1]} [id]" }
                .replace(episode) { "${it.groupValues[1]} [n]" }
        for (term in terms.load().patterns) text = text.replace(term, "[source]")
        return LogRedactor.redact(text.replace(host, "[host]"))
    }

    private companion object {
        const val MIN_TERM = 3

        // `://` and the JSON-escaped `:\/\/`.
        val url = Regex("""\b[a-z][a-z0-9+.\-]*:(?:\\?/){2}\S+""", RegexOption.IGNORE_CASE)
        val protocolRelative = Regex("""(?<![^\s"'(=,\[])//\S+""")
        val host = Regex("""\b(?:[a-z0-9\-]+\.)+[a-z]{2,}\b""", RegexOption.IGNORE_CASE)
        val show = Regex("""\b(show)\s+\d+""", RegexOption.IGNORE_CASE)
        val episode = Regex("""\b(episode)\s+\d+(\.\d+)?""", RegexOption.IGNORE_CASE)
    }
}
