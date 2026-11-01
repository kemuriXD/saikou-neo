package ani.saikou.neo.tv

import android.app.Application
import ani.saikou.neo.shared.home.WatchHistory
import ani.saikou.neo.shared.playback.LastServersStore
import ani.saikou.neo.shared.search.RecentSearchesStore
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.settings.SettingsStore
import ani.saikou.neo.shared.sources.SourceHost
import ani.saikou.neo.tv.sources.ApkSourceStore
import ani.saikou.neo.tv.sources.ExtensionServerFinder
import ani.saikou.neo.tv.sources.ExtensionStreamFinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File

/**
 * Holds what lives as long as the app: the settings, recent searches, sources and last servers, each of which only one
 * store may have open, and the watch history.
 */
class NeoApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsStore by lazy { SettingsStore.at(File(filesDir, "settings.preferences_pb").absolutePath, scope) }

    /** The settings now, or null until they've been read. */
    val settings: StateFlow<Settings?> by lazy { settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, null) }

    val recentSearches by lazy { RecentSearchesStore.at(File(filesDir, "recent_searches.preferences_pb").absolutePath, scope) }

    /** The shared HTTP client, Cloudflare clearance and WebView extraction every source and the player use. */
    val sourceHost by lazy { SourceHost(this) }

    /** The sources added on this TV, read from its files at start. */
    val sources by lazy { ApkSourceStore(this, scope, sourceHost.services) }

    /** Asks the installed sources for an episode's servers. */
    val serverFinder by lazy { ExtensionServerFinder(sources::extension) }

    /** Asks the installed sources for a server's stream. */
    val streamFinder by lazy { ExtensionStreamFinder(sources::extension) }

    /** The server last picked for each show. */
    val lastServers by lazy { LastServersStore.at(File(filesDir, "last_servers.preferences_pb").absolutePath, scope) }

    /** How far this TV got in each show. */
    val history = WatchHistory()
}
