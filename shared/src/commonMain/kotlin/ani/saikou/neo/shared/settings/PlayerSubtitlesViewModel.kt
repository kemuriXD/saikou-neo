package ani.saikou.neo.shared.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the player knows of the subtitle track that's on: its [key], or null with none, and whether it's [styled]. */
private data class Track(
    val key: String? = null,
    val styled: Boolean = false,
)

private data class Choices(
    val preview: LookId? = null,
    val track: Track = Track(),
    val myLookTracks: Set<String> = emptySet(),
)

/** The subtitles for one show in the player: how they draw now, and what the Subtitle look page shows. */
public data class PlayerSubtitles(
    val settings: SubtitleSettings,
    /** The saved look, size and position for this show. */
    val pick: SubtitlePick,
    /** The look focused on the page, drawn on the video until it's kept or Back puts the saved one back. */
    val preview: LookId?,
    /** The track on is styled, and the setting leaves styled tracks their look. */
    val styledTrack: Boolean,
    /** My look was turned on for the track that's on. */
    val myLookOnTrack: Boolean,
    val drawing: SubtitleDrawing,
) {
    /** The look on the video: the focused one, or else the saved one. */
    val showing: LookId get() = preview ?: pick.look

    /** The track draws in its own styling, so the looks can't change it. */
    val keepsItsLook: Boolean get() = styledTrack && !myLookOnTrack
}

/**
 * The player's subtitles for the show with AniList id [showId], named [title] in a list of shows with their own look.
 * Every change to [settings] redraws them, and changes made here go through [save].
 */
public class PlayerSubtitlesViewModel(
    settings: Flow<SubtitleSettings>,
    private val save: suspend ((SubtitleSettings) -> SubtitleSettings) -> Unit,
    private val showId: Int,
    private val title: String,
) : ViewModel() {
    private val choices = MutableStateFlow(Choices())

    public val state: StateFlow<PlayerSubtitles> =
        combine(settings, choices, ::stateOf).stateIn(viewModelScope, SharingStarted.Eagerly, stateOf(SubtitleSettings(), Choices()))

    private fun stateOf(
        settings: SubtitleSettings,
        choices: Choices,
    ): PlayerSubtitles {
        val styled = choices.track.styled && settings.styledSubtitles == StyledSubtitles.KEEP_THEIR_LOOK
        val mine = choices.track.key != null && choices.track.key in choices.myLookTracks
        val drawing = settings.drawingFor(showId, choices.preview)
        return PlayerSubtitles(
            settings = settings,
            pick = settings.pickFor(showId),
            preview = choices.preview,
            styledTrack = styled,
            myLookOnTrack = mine,
            drawing = if (mine) drawing.copy(styled = StyledSubtitles.USE_MY_LOOK) else drawing,
        )
    }

    /** Draws [look] on the video without saving it, or the saved look again when null. */
    public fun preview(look: LookId?) {
        choices.update { it.copy(preview = look) }
    }

    /** Saves the focused look. The preview stays until the save is read back, so the video never flicks to the old one. */
    public fun keep() {
        val look = choices.value.preview ?: return
        viewModelScope.launch {
            change { it.copy(look = look) }
            choices.update { if (it.preview == look) it.copy(preview = null) else it }
        }
    }

    public fun changeSize(by: Int) {
        viewModelScope.launch { change { it.copy(size = it.size.stepped(by)) } }
    }

    public fun changePosition(by: Int) {
        viewModelScope.launch { change { it.copy(position = it.position.stepped(by)) } }
    }

    /** The subtitle track that's on: its [key], or null with none, and whether it carries its own styling. */
    public fun onTrack(
        key: String?,
        styled: Boolean,
    ) {
        choices.update { it.copy(track = Track(key, styled)) }
    }

    /** Draws the styled track that's on in my look, signs too, or leaves it its own. Only that track changes, until the player closes. */
    public fun useMyLook(on: Boolean) {
        choices.update {
            val key = it.track.key ?: return@update it
            it.copy(myLookTracks = if (on) it.myLookTracks + key else it.myLookTracks - key)
        }
    }

    private suspend fun change(pick: (SubtitlePick) -> SubtitlePick) = save { it.changeWhileWatching(showId, title, pick) }
}
