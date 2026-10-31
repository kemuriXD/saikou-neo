package ani.saikou.neo.tv

import android.app.Application
import ani.saikou.neo.shared.home.WatchHistory
import ani.saikou.neo.shared.search.RecentSearchesStore
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.settings.SettingsStore
import ani.saikou.neo.tv.sources.ApkSourceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File

/**
 * Holds what lives as long as the app: the settings, recent searches and sources, each of which only one store may have
 * open, and the watch history.
 */
class NeoApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsStore by lazy { SettingsStore.at(File(filesDir, "settings.preferences_pb").absolutePath, scope) }

    /** The settings now, or null until they've been read. */
    val settings: StateFlow<Settings?> by lazy { settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, null) }

    val recentSearches by lazy { RecentSearchesStore.at(File(filesDir, "recent_searches.preferences_pb").absolutePath, scope) }

    /** The sources added on this TV, read from its files at start. */
    val sources by lazy { ApkSourceStore(this, scope) }

    /** How far this TV got in each show. */
    val history = WatchHistory()
}
