package ani.saikou.neo.shared.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings > Subtitles: the saved [settings], or null until they're read, and every change, saved through [save]. */
public class SubtitleSettingsViewModel(
    settings: Flow<SubtitleSettings>,
    private val save: suspend ((SubtitleSettings) -> SubtitleSettings) -> Unit,
) : ViewModel() {
    public val state: StateFlow<SubtitleSettings?> = settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    public fun step(
        option: SubtitleOption,
        by: Int,
    ): Unit = change { it.stepped(option, by) }

    /** Uses [look] for every show without a look of its own. */
    public fun use(look: LookId): Unit = change { it.withLook(look) }

    /** Adds a look of your own, copied from the one in use, and uses it. Returns it, or null once there are three. */
    public fun addLook(): LookId? {
        val now = state.value ?: return null
        if (!now.canAddLook) return null
        val number = (1..SubtitleSettings.MAX_MY_LOOKS).first { it !in now.myLooks }
        change { it.addLook() }
        return LookId.Mine(number)
    }

    public fun edit(
        look: LookId,
        part: LookPart,
        by: Int,
    ): Unit = change { settings -> settings.style(look)?.let { settings.editLook(look, it.stepped(part, by)) } ?: settings }

    public fun reset(look: BuiltInLook): Unit = change { it.resetLook(look) }

    public fun delete(number: Int): Unit = change { it.deleteLook(number) }

    public fun clearShow(showId: Int): Unit = change { it.clearShow(showId) }

    /** Every subtitle setting back to how a new install has it. */
    public fun resetAll(): Unit = change { SubtitleSettings() }

    private fun change(change: (SubtitleSettings) -> SubtitleSettings) {
        viewModelScope.launch { save(change) }
    }
}
