package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.network.http.DefaultHttpEngine
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Reads MyAnimeList's scores through Tenrai, a free mirror of MAL's catalogue that answers in Jikan's shape. It needs no
 * key, and allows 120 requests a minute from each TV.
 */
public class MalClient internal constructor(
    private val engine: HttpEngine,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    public constructor() : this(DefaultHttpEngine(TIMEOUT_MILLIS))

    /** The show's MAL score out of 100, or null when nobody has scored it or Tenrai can't answer. */
    public suspend fun score(malId: Int): Int? = engine.getJson("$TENRAI_URL/anime/$malId", io) { score() }

    private companion object {
        const val TENRAI_URL = "https://api.tenrai.org/v1"
        const val TIMEOUT_MILLIS = 15_000L
    }
}

/** Reads `data.score`, and skips everything else. */
private fun JsonReader.score(): Int? {
    var score: Int? = null
    beginObject()
    while (hasNext()) {
        if (nextName() == "data" && peek() == JsonReader.Token.BEGIN_OBJECT) {
            score = readFields()["score"]?.let(::outOfTen)
        } else {
            skipValue()
        }
    }
    endObject()
    return score
}
