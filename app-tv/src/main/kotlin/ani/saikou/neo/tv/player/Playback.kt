package ani.saikou.neo.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

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
}

/** The screen's ExoPlayer, as the controls see it. */
internal class ExoPlayback(
    val player: ExoPlayer,
) : Playback,
    Player.Listener {
    override val positionMs get() = player.currentPosition
    override val durationMs get() = player.duration.takeIf { it != C.TIME_UNSET } ?: 0
    override var playing by mutableStateOf(player.playWhenReady)
        private set

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        playing = playWhenReady
    }
}

/** An ExoPlayer that lives as long as the screen and is released when it leaves. */
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
            ExoPlayback(ExoPlayer.Builder(context).setAudioAttributes(audio, true).build())
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
