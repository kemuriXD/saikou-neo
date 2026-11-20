package ani.saikou.neo.shared.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath

private const val PREFIX = "pending_"
private const val SEPARATOR = ";"

/**
 * Keeps the [SyncQueue] in a file, so updates still wait after the app restarts. It holds AniList user and show IDs,
 * never the token. Only one store may have a file open at a time, so the app makes one and shares it.
 */
public class SyncQueueStore private constructor(
    private val dataStore: DataStore<Preferences>,
) : SyncQueue {
    override suspend fun all(): List<PendingProgress> =
        dataStore.data.first().asMap().mapNotNull { (key, value) ->
            val animeId = key.name.removePrefix(PREFIX).toIntOrNull() ?: return@mapNotNull null
            val fields = (value as? String)?.split(SEPARATOR) ?: return@mapNotNull null
            val userId = fields.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val episode = fields.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            PendingProgress(animeId, episode, userId)
        }

    override suspend fun put(pending: PendingProgress) {
        dataStore.edit { it[key(pending.animeId)] = "${pending.userId}$SEPARATOR${pending.episode}" }
    }

    override suspend fun remove(animeId: Int) {
        dataStore.edit { it.remove(key(animeId)) }
    }

    private fun key(animeId: Int) = stringPreferencesKey("$PREFIX$animeId")

    public companion object {
        /** A store kept in the file at [path], which reads and writes in [scope] until that ends. */
        public fun at(
            path: String,
            scope: CoroutineScope,
        ): SyncQueueStore = SyncQueueStore(PreferenceDataStoreFactory.createWithPath(scope = scope) { path.toPath() })
    }
}
