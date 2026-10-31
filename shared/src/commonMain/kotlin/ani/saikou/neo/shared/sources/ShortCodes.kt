package ani.saikou.neo.shared.sources

import ani.saikou.neo.shared.catalogue.getJson
import ani.saikou.neo.shared.catalogue.readFields
import com.apollographql.apollo.network.http.DefaultHttpEngine
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** Turns a short code into the link it stands for, with v.gd's lookup API. The app runs no code service of its own. */
public class ShortCodes internal constructor(
    private val engine: HttpEngine,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    public constructor() : this(DefaultHttpEngine(TIMEOUT_MILLIS))

    /** The link v.gd has for [code], or null when it has none, disabled it, or can't answer. */
    public suspend fun link(code: String): String? = engine.getJson("$LOOKUP_URL?format=json&shorturl=$code", io) { readFields()["url"] }

    private companion object {
        const val LOOKUP_URL = "https://v.gd/forward.php"
        const val TIMEOUT_MILLIS = 15_000L
    }
}
