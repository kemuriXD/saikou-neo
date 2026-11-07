package ani.saikou.neo.shared.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** How the player draws subtitles for the show with AniList id [showId], following every change to [settings]. */
public class SubtitleDrawingViewModel(
    settings: Flow<SubtitleSettings>,
    showId: Int,
) : ViewModel() {
    public val drawing: StateFlow<SubtitleDrawing> =
        settings
            .map { it.drawingFor(showId) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleSettings().drawingFor(showId))
}
