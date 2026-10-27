package ani.saikou.neo.shared.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The settings the Settings screen lists, each switched on or off. */
public enum class Setting { UNAIRED_ANIME, UPCOMING_EPISODES, SLOW_TV }

public fun Settings.isOn(setting: Setting): Boolean =
    when (setting) {
        Setting.UNAIRED_ANIME -> showUnairedAnime
        Setting.UPCOMING_EPISODES -> showUpcomingEpisodes
        Setting.SLOW_TV -> slowTv
    }

private fun Settings.toggled(setting: Setting): Settings =
    when (setting) {
        Setting.UNAIRED_ANIME -> copy(showUnairedAnime = !showUnairedAnime)
        Setting.UPCOMING_EPISODES -> copy(showUpcomingEpisodes = !showUpcomingEpisodes)
        Setting.SLOW_TV -> copy(slowTv = !slowTv)
    }

/** The Settings screen's state: the saved settings, or null until they're read. */
public class SettingsViewModel(
    private val store: SettingsStore,
) : ViewModel() {
    public val state: StateFlow<Settings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Switches [setting] on or off and saves it. */
    public fun toggle(setting: Setting) {
        viewModelScope.launch { store.update { it.toggled(setting) } }
    }
}
