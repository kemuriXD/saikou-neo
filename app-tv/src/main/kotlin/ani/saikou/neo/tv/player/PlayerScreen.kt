package ani.saikou.neo.tv.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.shared.catalogue.art
import ani.saikou.neo.shared.playback.PlayRequest
import ani.saikou.neo.shared.playback.PlayServer
import ani.saikou.neo.shared.playback.PlayerError
import ani.saikou.neo.shared.playback.PlayerPhase
import ani.saikou.neo.shared.playback.PlayerState
import ani.saikou.neo.shared.playback.formatPlaybackTime
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.details.editionName
import ani.saikou.neo.tv.details.failReasonText
import ani.saikou.neo.tv.home.displayTitle
import ani.saikou.neo.tv.home.parseColour
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoDialogButton
import ani.saikou.neo.tv.ui.NeoDialogButtons
import ani.saikou.neo.tv.ui.NeoIcons
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

internal const val PLAYER_STARTING_TAG = "player-starting"
internal const val PLAYER_STOPPED_TAG = "player-stopped"
internal const val PLAYER_CHOOSE_TAG = "player-choose"
internal const val PLAYER_TRY_NEXT_TAG = "player-try-next"
internal const val PLAYER_TRY_AGAIN_TAG = "player-try-again"

/** What the player tells its ViewModel, and Choose another server, which goes back to the streams page. */
internal data class PlayerActions(
    val onStarted: () -> Unit = {},
    val onFailed: (PlayerError, positionMs: Long) -> Unit = { _, _ -> },
    val onTryNext: () -> Unit = {},
    val onTryAgain: () -> Unit = {},
    val onChooseAnother: () -> Unit = {},
)

// From the design.
private val StartingLeft = 58.dp
private val StartingTop = 128.dp
private val StartingWidth = 470.dp
private val GhostLeft = 30.dp
private val GhostTop = 40.dp
private val StoppedWidth = 420.dp
private val StoppedPadding = 28.dp
private val StoppedRadius = 20.dp
private val StepMark = 20.dp
private const val GHOST_ALPHA = 0.07f
private const val BACKDROP_ALPHA = 0.6f
private const val STOPPED_SCRIM = 0.6f
private const val STARTING_SCRIM_LEFT = 0.85f
private const val STARTING_SCRIM_RIGHT = 0.5f
private const val STOPPED_FILL = 0.96f
private const val BODY_ALPHA = 0.8f

// The art is drawn this small and stretched, which blurs it on every Android version. Blur effects need Android 12.
private const val BACKDROP_WIDTH = 48
private const val BACKDROP_HEIGHT = 27

/**
 * The player for [request]: the video full screen, with the show's blurred art and what's happening while it starts,
 * and a dialog when the server stops answering. Once it plays, nothing sits over the video. [video] draws the player.
 */
@Composable
internal fun PlayerScreen(
    state: PlayerState<Stream>,
    request: PlayRequest,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
    video: @Composable () -> Unit = {
        StreamVideo(state, state.server.server.edition, actions.onStarted, actions.onFailed)
    },
) {
    val scrim = NeoTheme.colors.scrimPlayer
    Box(modifier.fillMaxSize().background(scrim)) {
        video()
        when (val phase = state.phase) {
            is PlayerPhase.Starting -> {
                if (!phase.quiet) {
                    Backdrop(
                        request,
                        Brush.horizontalGradient(listOf(scrim.copy(alpha = STARTING_SCRIM_LEFT), scrim.copy(alpha = STARTING_SCRIM_RIGHT))),
                    )
                    Starting(state, phase, request, actions.onChooseAnother)
                }
            }

            is PlayerPhase.Stopped -> {
                Backdrop(request, SolidColor(scrim.copy(alpha = STOPPED_SCRIM)))
                Stopped(state.server, phase, request, actions)
            }

            // Nothing over the video.
            is PlayerPhase.Playing -> {}
        }
    }
}

/** The show's art, blurred, under [scrim]. */
@Composable
private fun BoxScope.Backdrop(
    request: PlayRequest,
    scrim: Brush,
) {
    val art = request.show.card.art
    if (art != null) {
        val context = LocalPlatformContext.current
        AsyncImage(
            model =
                remember(art) {
                    ImageRequest
                        .Builder(context)
                        .data(art)
                        .size(BACKDROP_WIDTH, BACKDROP_HEIGHT)
                        .build()
                },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().alpha(BACKDROP_ALPHA),
        )
    }
    Box(Modifier.matchParentSize().background(scrim))
}

@Composable
private fun BoxScope.Starting(
    state: PlayerState<Stream>,
    phase: PlayerPhase.Starting<Stream>,
    request: PlayRequest,
    onChooseAnother: () -> Unit,
) {
    val colors = NeoTheme.colors
    val typography = NeoTheme.typography
    val choose = remember { FocusRequester() }
    LaunchedEffect(Unit) { choose.requestFocus() }
    val server = state.server
    Text(
        request.episode.toString().padStart(2, '0'),
        style = typography.display.copy(fontSize = 300.sp, lineHeight = 300.sp, letterSpacing = (-14).sp),
        color = colors.paper.copy(alpha = GHOST_ALPHA),
        modifier = Modifier.padding(start = GhostLeft, top = GhostTop),
    )
    Column(
        Modifier.padding(start = StartingLeft, top = StartingTop).width(StartingWidth).testTag(PLAYER_STARTING_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            request.show.card
                .displayTitle()
                .uppercase(),
            style = typography.overline.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
            color = parseColour(request.show.card.coverColor) ?: colors.pink,
        )
        Text(episodeTitle(request), style = typography.headline.copy(fontSize = 34.sp, lineHeight = 40.sp), color = colors.paper)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Step(done = true, pluralStringResource(R.plurals.player_found, server.sourceServers, server.source.name, server.sourceServers))
            val answered = phase.answered
            if (answered == null) {
                Step(done = false, stringResource(R.string.player_asking, server.server.name))
            } else {
                Step(
                    done = true,
                    stringResource(R.string.player_answered, server.server.name, answered.height, editionName(server.server.edition)),
                )
                Step(done = false, stringResource(R.string.player_buffering))
            }
        }
        NeoButton(
            stringResource(R.string.player_choose_another),
            onChooseAnother,
            Modifier.padding(top = 14.dp).focusRequester(choose).testTag(PLAYER_CHOOSE_TAG),
        )
        if (state.willTryNext) {
            Text(
                stringResource(R.string.player_auto_next, server.server.name),
                style = typography.caption,
                color = colors.textFaint,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** One line of what's happening: ticked when [done], or a still ring while it waits. Nothing spins, as on the splash. */
@Composable
private fun Step(
    done: Boolean,
    text: String,
) {
    val colors = NeoTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (done) {
            Box(Modifier.size(StepMark).background(colors.done, CircleShape), contentAlignment = Alignment.Center) {
                Icon(NeoIcons.Tick, contentDescription = null, tint = colors.ink, modifier = Modifier.size(12.dp))
            }
        } else {
            val ring = colors.paper
            Canvas(Modifier.size(StepMark).padding(2.dp)) {
                drawArc(ring, startAngle = 0f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
        Text(text, style = NeoTheme.typography.body, color = colors.paper)
    }
}

@Composable
private fun BoxScope.Stopped(
    server: PlayServer,
    phase: PlayerPhase.Stopped,
    request: PlayRequest,
    actions: PlayerActions,
) {
    val colors = NeoTheme.colors
    val typography = NeoTheme.typography
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    val title = request.show.card.displayTitle()
    val why = failReasonText(phase.reason, title)
    val next = phase.next
    Column(
        Modifier
            .align(Alignment.Center)
            .width(StoppedWidth)
            .background(colors.ink.copy(alpha = STOPPED_FILL), RoundedCornerShape(StoppedRadius))
            .padding(StoppedPadding)
            .testTag(PLAYER_STOPPED_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(NeoIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(28.dp))
        Text(
            stringResource(R.string.player_stopped_title, server.server.name),
            style = typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp),
            color = colors.paper,
        )
        Text(
            phase.atMs?.let { stringResource(R.string.player_stopped_at, why, request.episode, formatPlaybackTime(it)) }
                ?: stringResource(R.string.player_stopped_why, why),
            style = typography.body.copy(lineHeight = 22.sp),
            color = colors.paper.copy(alpha = BODY_ALPHA),
        )
        NeoDialogButtons(Modifier.padding(top = 8.dp)) {
            if (next != null) {
                NeoDialogButton(
                    stringResource(R.string.player_try_server, serverName(next, server)),
                    actions.onTryNext,
                    Modifier.focusRequester(first).testTag(PLAYER_TRY_NEXT_TAG),
                )
            }
            NeoDialogButton(
                stringResource(R.string.player_choose_another),
                actions.onChooseAnother,
                (if (next == null) Modifier.focusRequester(first) else Modifier).testTag(PLAYER_CHOOSE_TAG),
            )
            NeoDialogButton(
                stringResource(R.string.player_try_again, server.server.name),
                actions.onTryAgain,
                Modifier.testTag(PLAYER_TRY_AGAIN_TAG),
            )
        }
        Text(
            phase.httpCode?.let { stringResource(R.string.player_footer_code, server.source.name, server.server.name, it) }
                ?: stringResource(R.string.player_footer, server.source.name, server.server.name),
            style = typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = colors.textFaint,
        )
    }
}

@Composable
private fun episodeTitle(request: PlayRequest) =
    request.episodeTitle?.let { stringResource(R.string.streams_episode_titled, request.episode, it) }
        ?: stringResource(R.string.streams_episode, request.episode)

/** [server]'s name, with its edition when [other] has the same name, as a source's Sub and Dub servers can. */
@Composable
private fun serverName(
    server: PlayServer,
    other: PlayServer,
) = if (server.server.name == other.server.name) {
    stringResource(R.string.player_server_edition, server.server.name, editionName(server.server.edition))
} else {
    server.server.name
}
