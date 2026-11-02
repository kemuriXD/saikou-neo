package ani.saikou.neo.tv.player

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale

/** What the controls do to the video, and what they read from it. */
internal interface Playback {
    val positionMs: Long

    /** The episode's length, or 0 or less while it isn't known. */
    val durationMs: Long

    /** Whether the video plays, or would once it has buffered. Compose reads it as state. */
    val playing: Boolean

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    /** The audio tracks the video offers. Compose reads it as state, as with the rest below. */
    val audio: List<TrackOption>

    val subtitles: List<TrackOption>

    val speed: Float

    fun selectTrack(option: TrackOption)

    fun subtitlesOff()

    fun changeSpeed(speed: Float)

    /** How far subtitles run behind the video, or ahead when negative. */
    val subtitleDelayMs: Int

    fun changeSubtitleDelay(delayMs: Int)
}

/** A track to offer: where it is in the video, its name if it has one, and whether it's the one playing. */
internal data class TrackOption(
    val group: Int,
    val track: Int,
    val label: String?,
    val selected: Boolean,
)

/** The tracks of [type] this device can play. The same name twice, such as a language at two bitrates, is one choice. */
@OptIn(UnstableApi::class)
internal fun Tracks.options(type: Int): List<TrackOption> {
    val all =
        groups.flatMapIndexed { index, group ->
            if (group.type != type) return@flatMapIndexed emptyList()
            (0 until group.length).filter { group.isTrackSupported(it) && !group.getTrackFormat(it).isCaptionGuess() }.map {
                TrackOption(index, it, group.getTrackFormat(it).name(), group.isTrackSelected(it))
            }
        }
    val named =
        all.filter { it.label != null }.groupBy { it.label }.mapValues { (_, same) ->
            same.firstOrNull { it.selected }
                ?: same.first()
        }
    return all.filter { it.label == null || named[it.label] == it }
}

// A playlist that doesn't rule captions out gets this nameless track whether or not the video has any.
private fun Format.isCaptionGuess() =
    (sampleMimeType == MimeTypes.APPLICATION_CEA608 || sampleMimeType == MimeTypes.APPLICATION_CEA708) && name() == null

private fun Format.name() =
    label?.takeIf { it.isNotBlank() }
        ?: language
            ?.takeIf { it != C.LANGUAGE_UNDETERMINED }
            ?.let { Locale.forLanguageTag(it).displayName }
            ?.takeIf { it.isNotBlank() }

/** The track named [wanted], if it isn't playing. A reload forgets what was picked, so it's found again by name. */
internal fun List<TrackOption>.toRestore(wanted: String?) = firstOrNull { wanted != null && it.label == wanted }?.takeIf { !it.selected }

/** Plays [track] of [group], with its type turned on if it was off. */
@OptIn(UnstableApi::class)
internal fun TrackSelectionParameters.picking(
    group: TrackGroup,
    track: Int,
): TrackSelectionParameters =
    buildUpon()
        .setTrackTypeDisabled(group.type, false)
        .setOverrideForType(TrackSelectionOverride(group, track))
        .build()

internal fun TrackSelectionParameters.withoutSubtitles(): TrackSelectionParameters =
    buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()

/** The screen's ExoPlayer, as the controls see it. */
internal class ExoPlayback(
    val player: ExoPlayer,
    private val delay: SubtitleDelay,
) : Playback,
    Player.Listener {
    override val positionMs get() = player.currentPosition
    override val durationMs get() = player.duration.takeIf { it != C.TIME_UNSET } ?: 0
    override var playing by mutableStateOf(player.playWhenReady)
        private set
    override var audio by mutableStateOf(player.currentTracks.options(C.TRACK_TYPE_AUDIO))
        private set
    override var subtitles by mutableStateOf(player.currentTracks.options(C.TRACK_TYPE_TEXT))
        private set
    override var speed by mutableFloatStateOf(player.playbackParameters.speed)
        private set

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    // What was picked, by track type.
    private val wanted = mutableMapOf<Int, String?>()

    override fun selectTrack(option: TrackOption) {
        val group = player.currentTracks.groups.getOrNull(option.group) ?: return
        wanted[group.type] = option.label
        player.trackSelectionParameters = player.trackSelectionParameters.picking(group.mediaTrackGroup, option.track)
    }

    override fun subtitlesOff() {
        wanted.remove(C.TRACK_TYPE_TEXT)
        player.trackSelectionParameters = player.trackSelectionParameters.withoutSubtitles()
    }

    override fun changeSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    override var subtitleDelayMs by mutableIntStateOf(0)
        private set

    override fun changeSubtitleDelay(delayMs: Int) {
        subtitleDelayMs = delayMs
        delay.ms = delayMs
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        playing = playWhenReady
    }

    override fun onTracksChanged(tracks: Tracks) {
        audio = tracks.options(C.TRACK_TYPE_AUDIO)
        subtitles = tracks.options(C.TRACK_TYPE_TEXT)
        audio.toRestore(wanted[C.TRACK_TYPE_AUDIO])?.let(::selectTrack)
        subtitles.toRestore(wanted[C.TRACK_TYPE_TEXT])?.let(::selectTrack)
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        speed = playbackParameters.speed
    }
}

/** An ExoPlayer that lives as long as the screen and is released when it leaves. */
@OptIn(UnstableApi::class)
@Composable
internal fun rememberExoPlayback(): ExoPlayback {
    val context = LocalContext.current
    val playback =
        remember {
            // Takes audio focus, so other apps go quiet while it plays and it pauses for a call.
            val audio =
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
            val delay = SubtitleDelay()
            ExoPlayback(ExoPlayer.Builder(context, DelayedSubtitlesFactory(context, delay)).setAudioAttributes(audio, true).build(), delay)
        }
    DisposableEffect(playback) {
        playback.player.addListener(playback)
        onDispose {
            playback.player.removeListener(playback)
            playback.player.release()
        }
    }
    return playback
}
