package ani.saikou.neo.tv.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.shared.playback.PlayerError
import ani.saikou.neo.shared.playback.PlayerPhase
import ani.saikou.neo.shared.playback.PlayerState
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.shared.settings.SubtitleDrawing
import kotlinx.coroutines.delay
import okhttp3.Call
import kotlin.time.Duration.Companion.seconds

// How often where the video got to is saved while it plays.
private val SAVE_EVERY = 10.seconds

/**
 * Plays each stream [state] hands over, from its start position, and says when the video [onStarted] or the player
 * [onFailed], on [playback]'s player, and where it [onPlayed] to every few seconds and on leaving. The view draws the video,
 * and subtitles as [subtitles] says, with a [sample] line while the looks are open, and never takes focus; the controls
 * sit over it, and subtitles stay above [subtitlesAbove], a line in the window, while they're open.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun StreamVideo(
    state: PlayerState<Stream>,
    edition: ServerEdition,
    playback: ExoPlayback,
    onStarted: () -> Unit,
    onFailed: (PlayerError, positionMs: Long) -> Unit,
    callFactory: Call.Factory,
    subtitles: SubtitleDrawing,
    modifier: Modifier = Modifier,
    sample: Sample? = null,
    subtitlesAbove: Float? = null,
    online: () -> Boolean = { true },
    onPlayed: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val player = playback.player
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = PauseInBackground({ player.playWhenReady }, player::pause, player::play)
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val started by rememberUpdatedState(onStarted)
    val failed by rememberUpdatedState(onFailed)
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) started()
                }

                override fun onPlayerError(error: PlaybackException) {
                    failed(error.toPlayerError(online()), player.currentPosition)
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val played by rememberUpdatedState(onPlayed)
    val report = { played(player.currentPosition, player.duration.coerceAtLeast(0)) }
    DisposableEffect(player) { onDispose { report() } }
    val playing = state.phase is PlayerPhase.Playing
    LaunchedEffect(playing) {
        while (playing) {
            delay(SAVE_EVERY)
            report()
        }
    }
    // A new stream: a new server, the same one again, or a fresh link.
    LaunchedEffect(state.load) {
        val answered = (state.phase as? PlayerPhase.Starting)?.answered ?: return@LaunchedEffect
        player.setMediaSource(
            answered.stream.mediaSource(context, edition, callFactory, state.pickedHeight, state.pickedAudio),
            state.startAtMs,
        )
        player.prepare()
        player.playWhenReady = true
    }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                useController = false
                // The last frame stays up while a quality or link is swapped, not a black screen.
                setKeepContentOnPlayerReset(true)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                this.player = player
                tag = SubtitleLayer(this).also { layer -> layer.attach(player) }
            }
        },
        update = {
            it.keepScreenOn = state.phase.keepsScreenOn()
            (it.tag as SubtitleLayer).apply {
                draw(subtitles)
                sample(sample)
                keepAbove(subtitlesAbove)
            }
        },
        onRelease = { (it.tag as SubtitleLayer).detach(player) },
        modifier = modifier.fillMaxSize(),
    )
}

/** Whether the TV stays awake: while the video starts or plays, but not on a dialog it could be left on. */
internal fun PlayerPhase<*>.keepsScreenOn() = this !is PlayerPhase.Stopped

/** Pauses while the app is in the background, such as after Home, and plays again on coming back if it was [playing]. */
internal class PauseInBackground(
    private val playing: () -> Boolean,
    private val pause: () -> Unit,
    private val play: () -> Unit,
) : DefaultLifecycleObserver {
    private var resume = false

    override fun onStop(owner: LifecycleOwner) {
        resume = playing()
        if (resume) pause()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (resume) play()
        resume = false
    }
}
