package ani.saikou.neo.shared.details

/** AniList's description as plain text, with the story apart from editors' notes and the source credit. */
public data class Synopsis(
    /** What the anime is about. Null when AniList has nothing more than notes. */
    val story: String?,
    /** Editors' notes, such as when episodes streamed early, without their "Note:" label. */
    val notes: List<String>,
    /** Where the story text came from, such as `Crunchyroll`. */
    val source: String?,
)

private val spoiler = Regex("""~!.*?!~""", RegexOption.DOT_MATCHES_ALL)
private val lineBreak = Regex("""\s*<br\s*/?>\s*""", RegexOption.IGNORE_CASE)
private val tag = Regex("""<[^>]*>""")
private val entity = Regex("""&(#\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);""")
private val longGap = Regex("""\n{3,}""")

// A note is a line of its own that starts with its label.
private val note = Regex("""^notes?:\s*(.+)$""", RegexOption.IGNORE_CASE)

// Credits come last on a line: "(Source: Crunchyroll)" or "[Written by MAL Rewrite]".
private val sourceCredit = Regex("""\s*(?:\(source:\s*([^)]*)\)|\[written by\s*([^\]]*)])\s*$""", RegexOption.IGNORE_CASE)

private val namedEntities = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")

/** Reads AniList's description: `<br>` becomes a new line, other tags and spoilers are dropped and entities decoded. */
public fun parseSynopsis(html: String?): Synopsis {
    if (html == null) return Synopsis(story = null, notes = emptyList(), source = null)
    val text =
        html
            .replace(spoiler, "")
            .replace(lineBreak, "\n")
            .replace(tag, "")
            .replace(entity) { decode(it.groupValues[1]) ?: it.value }
    val notes = mutableListOf<String>()
    var source: String? = null
    val story =
        text.lines().mapNotNull { raw ->
            var line = raw.trim()
            sourceCredit.find(line)?.let { credit ->
                source =
                    credit.groupValues
                        .drop(1)
                        .first { it.isNotEmpty() }
                        .trim()
                line = line.removeRange(credit.range)
            }
            val noteText = note.find(line)?.groupValues?.get(1)
            when {
                noteText != null -> null.also { notes += noteText.trim() }
                raw.isNotBlank() && line.isEmpty() -> null
                else -> line
            }
        }
    return Synopsis(
        story =
            story
                .joinToString("\n")
                .replace(longGap, "\n\n")
                .trim()
                .ifEmpty { null },
        notes = notes,
        source = source,
    )
}

private fun decode(name: String): String? {
    val code =
        when {
            name.startsWith("#x", ignoreCase = true) -> name.drop(2).toIntOrNull(16)
            name.startsWith("#") -> name.drop(1).toIntOrNull()
            else -> return namedEntities[name]
        }
    return code?.takeIf { it in 1..0xFFFF }?.toChar()?.toString()
}

/** AniList's average score out of 100 as a score out of ten, such as `8.5`. */
public fun scoreOutOfTen(averageScore: Int?): String? = averageScore?.let { "${it / 10}.${it % 10}" }
