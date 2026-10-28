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

private fun Settings.with(
    setting: Setting,
    on: Boolean,
): Settings =
    when (setting) {
        Setting.UNAIRED_ANIME -> copy(showUnairedAnime = on)
        Setting.UPCOMING_EPISODES -> copy(showUpcomingEpisodes = on)
        Setting.SLOW_TV -> copy(slowTv = on)
    }

/** The Settings screen's state: the saved settings, or null until they're read. */
public class SettingsViewModel(
    private val store: SettingsStore,
) : ViewModel() {
    public val state: StateFlow<Settings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Sets [setting] [on] or off, as the viewer chose, and saves it. */
    public fun choose(
        setting: Setting,
        on: Boolean,
    ) {
        viewModelScope.launch { store.update { it.with(setting, on) } }
    }
}
