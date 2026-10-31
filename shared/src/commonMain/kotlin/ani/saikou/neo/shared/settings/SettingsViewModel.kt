package ani.saikou.neo.shared.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val OffOn = listOf(false, true)

/** The settings the Settings screen lists, each with the values it can take, in the order they're offered. */
public enum class Setting(
    public val choices: List<Any>,
) {
    UNAIRED_ANIME(OffOn),
    UPCOMING_EPISODES(OffOn),
    SLOW_TV(OffOn),
    SUB_OR_DUB(SubOrDub.entries),
}

/** The value [setting] has now, one of its [Setting.choices]. */
public operator fun Settings.get(setting: Setting): Any =
    when (setting) {
        Setting.UNAIRED_ANIME -> showUnairedAnime
        Setting.UPCOMING_EPISODES -> showUpcomingEpisodes
        Setting.SLOW_TV -> slowTv
        Setting.SUB_OR_DUB -> subOrDub
    }

private fun Settings.with(
    setting: Setting,
    value: Any,
): Settings =
    when (setting) {
        Setting.UNAIRED_ANIME -> copy(showUnairedAnime = value as Boolean)
        Setting.UPCOMING_EPISODES -> copy(showUpcomingEpisodes = value as Boolean)
        Setting.SLOW_TV -> copy(slowTv = value as Boolean)
        Setting.SUB_OR_DUB -> copy(subOrDub = value as SubOrDub)
    }

/** The Settings screen's state: the saved settings, or null until they're read. */
public class SettingsViewModel(
    private val store: SettingsStore,
) : ViewModel() {
    public val state: StateFlow<Settings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Sets [setting] to [value], one of its [Setting.choices], as the viewer chose, and saves it. */
    public fun choose(
        setting: Setting,
        value: Any,
    ) {
        require(value in setting.choices) { "$value isn't a choice for $setting" }
        viewModelScope.launch { store.update { it.with(setting, value) } }
    }
}
