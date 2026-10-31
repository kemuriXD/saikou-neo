package ani.saikou.neo.shared.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath

private fun sourceKey(animeId: Int) = stringPreferencesKey("show-$animeId-source")

private fun serverKey(animeId: Int) = stringPreferencesKey("show-$animeId-server")

/**
 * Keeps [LastServers] in a file, so they're there after the app restarts. Only one store may have a file open at a time,
 * so the app makes one and shares it.
 */
public class LastServersStore private constructor(
    private val dataStore: DataStore<Preferences>,
) : LastServers {
    override suspend fun get(animeId: Int): LastServer? {
        val saved = dataStore.data.first()
        return LastServer(saved[sourceKey(animeId)] ?: return null, saved[serverKey(animeId)] ?: return null)
    }

    override suspend fun set(
        animeId: Int,
        server: LastServer,
    ) {
        dataStore.edit { saved ->
            saved[sourceKey(animeId)] = server.sourceId
            saved[serverKey(animeId)] = server.serverName
        }
    }

    public companion object {
        /** A store kept in the file at [path], which reads and writes in [scope] until that ends. */
        public fun at(
            path: String,
            scope: CoroutineScope,
        ): LastServersStore = LastServersStore(PreferenceDataStoreFactory.createWithPath(scope = scope) { path.toPath() })
    }
}
