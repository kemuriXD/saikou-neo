package ani.saikou.neo.shared.pairing

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Keeps Your TVs in the app's private storage. Names and dates only; nothing secret. */
public class PrefsPairedTvStore(
    context: Context,
) : PairedTvStore {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun readTvs(): List<PairedTv> {
        val saved = preferences.getString(TVS, null) ?: return emptyList()
        return try {
            val array = JSONArray(saved)
            List(array.length()) { index ->
                val tv = array.getJSONObject(index)
                PairedTv(tv.getString("id"), tv.getString("name"), tv.getLong("pairedAt"))
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    override fun saveTvs(tvs: List<PairedTv>) {
        val array = JSONArray()
        tvs.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("pairedAt", it.pairedAtEpochSeconds)) }
        preferences.edit().putString(TVS, array.toString()).commit()
    }

    internal companion object {
        const val FILE = "paired_tvs"
        private const val TVS = "tvs"
    }
}
