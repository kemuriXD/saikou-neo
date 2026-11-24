package ani.saikou.neo.shared.crash

import ani.saikou.neo.shared.log.LogRedactor
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Takes what a crash report must not carry out of a log line: URLs, paths, host names, show IDs, episode numbers,
 * server names and source names. The local log keeps them. Safe to use from any thread.
 *
 * Show IDs and episode numbers are caught after a word starting with `show`, `media`, `anilist`, `ep` or `episode`,
 * as in `show 21`, `mediaId: 21` or `episode=3`, and as `S01E03`. New log lines should use one of those shapes.
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
            decodePercents(message)
                .replace(url, "[url]")
                .replace(protocolRelative, "[url]")
                .replace(path, "[path]")
                .replace(seasonEpisode, "S[n]E[n]")
                .replace(numbered) {
                    val marker = if (it.groupValues[1].startsWith("ep", ignoreCase = true)) "[n]" else "[id]"
                    "${it.groupValues[1]}${it.groupValues[2]}$marker"
                }.replace(server) { "${it.groupValues[1]}[server]" }
        for (term in terms.load().patterns) text = text.replace(term, "[source]")
        return LogRedactor.redact(text.replace(host, "[host]"))
    }

    // An encoded `%` (`%25`) is decoded up to twice, so a double-encoded query reaches the redactor as a plain one.
    private fun decodePercents(text: String): String {
        var out = text
        repeat(2) { out = out.replace(encodedPercent, "%") }
        return out
    }

    private companion object {
        const val MIN_TERM = 3

        // `://` and the JSON-escaped `:\/\/`.
        val url = Regex("""\b[a-z][a-z0-9+.\-]*:(?:\\?/){2}\S+""", RegexOption.IGNORE_CASE)
        val protocolRelative = Regex("""(?<![^\s"'(=,\[])//\S+""")
        val host = Regex("""\b(?:[\w\-]+\.)+[a-z]{2,}\b""", RegexOption.IGNORE_CASE)

        // A path like `/watch/one-piece-100` at the start or after a space.
        val path = Regex("""(?<!\S)/[^\s/]\S*""")
        val seasonEpisode = Regex("""\bS\d+E\d+\b""", RegexOption.IGNORE_CASE)

        // The word, then a space or `:`, `=` or `#`. A word ending in a digit, such as Media3, isn't one.
        val numbered =
            Regex(
                """\b((?:ep|episode|show|media|anilist)[a-z_]*)(\s*[:=#]\s*|\s+)\d+(?:\.\d+)?""",
                RegexOption.IGNORE_CASE,
            )

        // A server name can point at its embed host, so the player's "episode <n> on <server>" loses it.
        val server = Regex("""\b(episode \[n] on ).+""", RegexOption.IGNORE_CASE)
        val encodedPercent = Regex("%25")
    }
}
