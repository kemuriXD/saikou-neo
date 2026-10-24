package ani.saikou.neo.shared.details

private val lineBreak = Regex("""\s*<br\s*/?>\s*""", RegexOption.IGNORE_CASE)
private val tag = Regex("""<[^>]*>""")
private val entity = Regex("""&(#\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);""")
private val longGap = Regex("""\n{3,}""")

private val namedEntities = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")

/**
 * AniList's synopsis as plain text: `<br>` becomes a new line, other tags are dropped and entities decoded.
 * Null when nothing is left to show.
 */
public fun plainSynopsis(html: String?): String? {
    if (html == null) return null
    val text =
        html
            .replace(lineBreak, "\n")
            .replace(tag, "")
            .replace(entity) { decode(it.groupValues[1]) ?: it.value }
            .replace(longGap, "\n\n")
            .trim()
    return text.ifEmpty { null }
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
