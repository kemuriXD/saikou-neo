package ani.saikou.neo.shared.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

private val UnairedAnime = booleanPreferencesKey("show_unaired_anime")
private val UpcomingEpisodes = booleanPreferencesKey("show_upcoming_episodes")
private val SlowTv = booleanPreferencesKey("slow_tv")

/**
 * Keeps [Settings] in a file, so they're there after the app restarts. Only one store may have a file open at a time,
 * so the app makes one and shares it.
 */
public class SettingsStore private constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** The settings now, and again each time one changes. A setting never changed reads as its default. */
    public val settings: Flow<Settings> =
        dataStore.data.map { it.toSettings() }

    /** Saves the settings [change] makes to the ones now. */
    public suspend fun update(change: (Settings) -> Settings) {
        dataStore.edit { saved ->
            val changed = change(saved.toSettings())
            saved[UnairedAnime] = changed.showUnairedAnime
            saved[UpcomingEpisodes] = changed.showUpcomingEpisodes
            saved[SlowTv] = changed.slowTv
        }
    }

    public companion object {
        /** A store kept in the file at [path], which reads and writes in [scope] until that ends. */
        public fun at(
            path: String,
            scope: CoroutineScope,
        ): SettingsStore = SettingsStore(PreferenceDataStoreFactory.createWithPath(scope = scope) { path.toPath() })
    }
}

private fun Preferences.toSettings(): Settings {
    val defaults = Settings()
    return Settings(
        showUnairedAnime = this[UnairedAnime] ?: defaults.showUnairedAnime,
        showUpcomingEpisodes = this[UpcomingEpisodes] ?: defaults.showUpcomingEpisodes,
        slowTv = this[SlowTv] ?: defaults.slowTv,
    )
}
