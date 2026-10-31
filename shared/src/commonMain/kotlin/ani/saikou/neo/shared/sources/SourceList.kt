package ani.saikou.neo.shared.sources

import ani.saikou.neo.shared.catalogue.readFields
import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.api.json.jsonReader
import com.apollographql.apollo.exception.ApolloException
import okio.Buffer
import okio.IOException

/** A source as a list names it (ADR 0025): its id, name, version, the API version it was built for, and its APK. */
public data class ListedSource(
    val id: String,
    val name: String,
    val version: String,
    val api: Int,
    val apk: String,
)

/** A source's id: its package name. */
public val sourceIdFormat: Regex = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

private const val MAX_ENTRIES = 50
private const val MAX_TEXT = 60
private val BYTE_ORDER_MARK = Char(0xFEFF)

/** Whether [file] is a list rather than an APK: its first character, after spaces, is `{`. An APK starts with `PK`. */
internal fun isSourceList(file: ByteArray): Boolean =
    file.decodeToString(endIndex = minOf(file.size, 64)).trimStart(' ', '\t', '\r', '\n', BYTE_ORDER_MARK).startsWith('{')

/**
 * The sources a list names, in its order, or null when it can't be read. An entry with a missing or wrong field, an id
 * seen before, or an APK link the app wouldn't take when typed is left out. At most [MAX_ENTRIES] are read.
 */
internal fun readSourceList(file: ByteArray): List<ListedSource>? =
    try {
        Buffer().write(file).jsonReader().use { it.sources() }
    } catch (_: ApolloException) {
        null
    } catch (_: IOException) {
        null
    }

private fun JsonReader.sources(): List<ListedSource>? {
    if (peek() != JsonReader.Token.BEGIN_OBJECT) return null
    var sources: List<ListedSource>? = null
    beginObject()
    while (hasNext()) {
        if (nextName() != "sources") {
            skipValue()
        } else if (peek() != JsonReader.Token.BEGIN_ARRAY) {
            return null
        } else {
            val read = mutableListOf<ListedSource>()
            beginArray()
            while (hasNext()) {
                // Past the limit, the rest are skipped without being read.
                if (read.size == MAX_ENTRIES) {
                    skipValue()
                    continue
                }
                val source = readFields().toSource()
                if (source != null && read.none { it.id == source.id }) read += source
            }
            endArray()
            sources = read
        }
    }
    endObject()
    return sources
}

private fun Map<String, String>.toSource(): ListedSource? {
    val id = get("id")?.takeIf { it.matches(sourceIdFormat) } ?: return null
    val name = get("name")?.shown() ?: return null
    val version = get("version")?.shown() ?: return null
    val api = get("api")?.toIntOrNull() ?: return null
    val apk = get("apk")?.takeIf { isAllowedSourceLink(it) } ?: return null
    return ListedSource(id, name, version, api, apk)
}

/**
 * Text as the app shows it: without control or formatting characters, which could hide or reorder what's shown,
 * trimmed and shortened. Null when nothing is left.
 */
private fun String.shown(): String? =
    filter { it.category !in hidden }
        .trim()
        .take(MAX_TEXT)
        .takeIf { it.isNotEmpty() }

private val hidden = setOf(CharCategory.CONTROL, CharCategory.FORMAT, CharCategory.LINE_SEPARATOR, CharCategory.PARAGRAPH_SEPARATOR)
