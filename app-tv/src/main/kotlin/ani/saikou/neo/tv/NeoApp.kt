package ani.saikou.neo.tv

import android.app.Application
import ani.saikou.neo.shared.home.WatchHistory
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File

/** Holds what lives as long as the app: the settings, which only one store may have open, and the watch history. */
class NeoApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsStore by lazy { SettingsStore.at(File(filesDir, "settings.preferences_pb").absolutePath, scope) }

    /** The settings now, or null until they've been read. */
    val settings: StateFlow<Settings?> by lazy { settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, null) }

    /** How far this TV got in each show. */
    val history = WatchHistory()
}
