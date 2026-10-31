package ani.saikou.neo.tv.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.shared.playback.PlayerError
import ani.saikou.neo.shared.playback.PlayerPhase
import ani.saikou.neo.shared.playback.PlayerState
import ani.saikou.neo.shared.playback.ServerEdition

/**
 * Plays each stream [state] hands over, from its start position, and says when the video [onStarted] or the player
 * [onFailed]. The player lives as long as the screen and is released when it leaves. The view draws the video and
 * subtitles and never takes focus; the remote's controls come with #114.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun StreamVideo(
    state: PlayerState<Stream>,
    edition: ServerEdition,
    onStarted: () -> Unit,
    onFailed: (PlayerError, positionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    val started by rememberUpdatedState(onStarted)
    val failed by rememberUpdatedState(onFailed)
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) started()
                }

                override fun onPlayerError(error: PlaybackException) {
                    failed(error.toPlayerError(), player.currentPosition)
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    // A new stream: a new server, the same one again, or a fresh link.
    LaunchedEffect(state.load) {
        val answered = (state.phase as? PlayerPhase.Starting)?.answered ?: return@LaunchedEffect
        player.setMediaSource(answered.stream.mediaSource(context, edition), state.startAtMs)
        player.prepare()
        player.playWhenReady = true
    }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                this.player = player
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
