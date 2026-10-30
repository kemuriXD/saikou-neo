package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.network.http.DefaultHttpEngine
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Asks Kitsu, whose search forgives typos, which show a search that found nothing on AniList meant. Only its top
 * match's AniList id is read; nothing else from Kitsu is shown or kept. It needs no key.
 */
public class KitsuClient internal constructor(
    private val engine: HttpEngine,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    public constructor() : this(DefaultHttpEngine(TIMEOUT_MILLIS))

    /** The AniList id of the show Kitsu best matches to [text], or null when it has none or can't answer. */
    public suspend fun aniListId(text: String): Int? =
        engine.getJson("$KITSU_URL/anime?filter%5Btext%5D=${encode(text)}&page%5Blimit%5D=1$FIELDS", io) { aniListId() }

    private companion object {
        const val KITSU_URL = "https://kitsu.app/api/edge"

        // Only the top match's ids on other sites.
        const val FIELDS = "&fields%5Banime%5D=mappings&include=mappings&fields%5Bmappings%5D=externalSite,externalId"
        const val TIMEOUT_MILLIS = 15_000L
    }
}

/** Reads the AniList id from the `included` mappings, and skips everything else. */
private fun JsonReader.aniListId(): Int? {
    var id: Int? = null
    beginObject()
    while (hasNext()) {
        if (nextName() != "included" || peek() != JsonReader.Token.BEGIN_ARRAY) {
            skipValue()
            continue
        }
        beginArray()
        while (hasNext()) {
            val mapping = mappingFields()
            if (mapping["externalSite"] == "anilist/anime") id = id ?: mapping["externalId"]?.toIntOrNull()
        }
        endArray()
    }
    endObject()
    return id
}

/** One mapping's `attributes`. */
private fun JsonReader.mappingFields(): Map<String, String> {
    var fields = emptyMap<String, String>()
    beginObject()
    while (hasNext()) {
        if (nextName() == "attributes") fields = readFields() else skipValue()
    }
    endObject()
    return fields
}
