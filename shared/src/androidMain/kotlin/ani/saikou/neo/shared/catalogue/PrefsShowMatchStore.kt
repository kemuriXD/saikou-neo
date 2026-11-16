package ani.saikou.neo.shared.catalogue

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import kotlin.time.Instant

/** Keeps show matches in the app's private storage. Public ids only; nothing secret. */
public class PrefsShowMatchStore(
    context: Context,
) : ShowMatchStore {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun route(aniListId: Int): SavedMatch<String>? = read(ANILIST + aniListId) { getString(VALUE) }

    override fun aniListId(route: String): SavedMatch<Int>? = read(ROUTE + route) { getInt(VALUE) }

    override fun saveRoute(
        aniListId: Int,
        match: SavedMatch<String>,
    ): Unit = save(ANILIST + aniListId, match)

    override fun saveAniListId(
        route: String,
        match: SavedMatch<Int>,
    ): Unit = save(ROUTE + route, match)

    private fun <T : Any> read(
        key: String,
        value: JSONObject.() -> T,
    ): SavedMatch<T>? {
        val saved = preferences.getString(key, null) ?: return null
        return try {
            val json = JSONObject(saved)
            SavedMatch(if (json.has(VALUE)) json.value() else null, Instant.fromEpochSeconds(json.getLong(CHECKED_AT)))
        } catch (_: JSONException) {
            null
        }
    }

    private fun save(
        key: String,
        match: SavedMatch<Any>,
    ) {
        val json = JSONObject().put(CHECKED_AT, match.checkedAt.epochSeconds)
        match.value?.let { json.put(VALUE, it) }
        preferences.edit().putString(key, json.toString()).apply()
    }

    internal companion object {
        const val FILE = "show_matches"
        private const val ANILIST = "anilist:"
        private const val ROUTE = "route:"
        private const val VALUE = "value"
        private const val CHECKED_AT = "checkedAt"
    }
}
