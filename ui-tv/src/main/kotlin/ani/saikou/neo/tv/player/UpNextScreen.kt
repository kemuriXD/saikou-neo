package ani.saikou.neo.tv.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import ani.saikou.neo.shared.account.ShownNotice
import ani.saikou.neo.shared.playback.PlayRequest
import ani.saikou.neo.shared.playback.UP_NEXT_SECONDS
import ani.saikou.neo.shared.playback.UpNext
import ani.saikou.neo.shared.playback.UpNextEpisode
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.home.parseColour
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.AniListNotice
import ani.saikou.neo.tv.ui.NeoSurface
import ani.saikou.neo.tv.ui.NoticeActions
import coil3.compose.AsyncImage

internal const val UP_NEXT_TAG = "player-up-next"
internal const val UP_NEXT_PLAY_TAG = "player-up-next-play"
internal const val UP_NEXT_CREDITS_TAG = "player-up-next-credits"
internal const val UP_NEXT_STOP_TAG = "player-up-next-stop"

// From the design (3:4515): the video shrinks to 520 × 293 at the top left, with Up next beside it.
internal val UpNextVideoLeft = 48.dp
internal val UpNextVideoTop = 40.dp
internal val UpNextVideoWidth = 520.dp
internal val UpNextVideoHeight = 293.dp
internal val UpNextVideoRadius = 14.dp
private val UpNextLeft = 600.dp
private val UpNextWidth = 312.dp
private val ArtHeight = 176.dp
private val Ring = 30.dp
private const val SYNOPSIS_ALPHA = 0.78f
private const val GHOST_ALPHA = 0.3f
private const val RING_TRACK_ALPHA = 0.2f

/**
 * Up next over the shrunk video: [next]'s still, title and synopsis, with Play counting down [upNext]'s seconds and Watch
 * credits. Play has focus. [ending] says the ending is what's playing. After a run of episodes it asks if you're still
 * watching instead (204:172): Play has no countdown, and Stop calls [onStop] in place of Watch credits. The AniList
 * [notice] shows bottom left (199:2495). It doesn't take focus: Down from the buttons reaches its Undo.
 */
@Composable
internal fun BoxScope.UpNextScreen(
    request: PlayRequest,
    next: UpNextEpisode,
    upNext: UpNext,
    ending: Boolean,
    onPlay: () -> Unit,
    onCredits: () -> Unit,
    onStop: () -> Unit = {},
    notice: ShownNotice? = null,
    noticeActions: NoticeActions = NoticeActions(),
) {
    val run = upNext.stillWatching
    val colors = NeoTheme.colors
    val typography = NeoTheme.typography
    val play = remember { FocusRequester() }
    val undo = remember { FocusRequester() }
    var undoFocused by remember { mutableStateOf(false) }
    val canUndo = notice?.canUndo == true
    LaunchedEffect(Unit) { play.requestFocus() }
    // Undo gone while it had focus, focus goes back to Play. Read as Undo goes, before it loses focus.
    val hadFocus = undoFocused
    LaunchedEffect(canUndo) {
        if (!canUndo && hadFocus) play.requestFocus()
    }
    Text(
        stringResource(if (ending) R.string.player_ending_plays_on_here else R.string.player_plays_on_here, request.episode),
        style = typography.caption.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
        color = colors.textMuted,
        modifier = Modifier.padding(start = UpNextVideoLeft, top = UpNextVideoTop + UpNextVideoHeight + 29.dp),
    )
    Column(
        Modifier.padding(start = UpNextLeft, top = UpNextVideoTop).width(UpNextWidth).testTag(UP_NEXT_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (run == null) R.string.player_up_next else R.string.player_still_watching).uppercase(),
            style = typography.overline.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
            color = colors.pink,
        )
        Art(request, next, Modifier.size(UpNextWidth, ArtHeight))
        Text(
            next.title?.let { stringResource(R.string.streams_episode_titled, next.number, it) }
                ?: stringResource(R.string.streams_episode, next.number),
            style = typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
            color = colors.paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val note = run?.let { pluralStringResource(R.plurals.player_still_watching_run, it, it, next.number) }
        (note ?: next.synopsis)?.let {
            Text(
                it,
                style = typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = colors.paper.copy(alpha = SYNOPSIS_ALPHA),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.padding(top = 4.dp).focusProperties { if (canUndo) down = undo },
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            NeoSurface(onPlay, CircleShape, NeoTheme.dimensions.focusScaleButton, Modifier.focusRequester(play).testTag(UP_NEXT_PLAY_TAG)) {
                Row(
                    Modifier.height(44.dp).padding(start = if (upNext.secondsLeft != null) 8.dp else 18.dp, end = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    upNext.secondsLeft?.let { Countdown(it) }
                    Text(
                        stringResource(R.string.player_play_episode, next.number),
                        style = typography.label.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
            val second = if (run == null) onCredits else onStop
            val tag = if (run == null) UP_NEXT_CREDITS_TAG else UP_NEXT_STOP_TAG
            NeoSurface(second, CircleShape, NeoTheme.dimensions.focusScaleButton, Modifier.testTag(tag)) {
                Box(Modifier.height(44.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (run == null) R.string.player_watch_credits else R.string.player_stop), style = typography.label)
                }
            }
        }
    }
    if (notice != null) {
        val focused = { it: Boolean ->
            undoFocused = it
            noticeActions.onUndoFocused(it)
        }
        AniListNotice(notice, noticeActions.copy(onUndoFocused = focused), undo, Modifier.focusProperties { up = play })
    }
}

/** The next episode's still, or the show's colour with its number when there's none. */
@Composable
internal fun Art(
    request: PlayRequest,
    next: UpNextEpisode,
    modifier: Modifier = Modifier,
    radius: Int = 12,
    ghost: Boolean = true,
) {
    val colors = NeoTheme.colors
    val tint = parseColour(request.show.card.coverColor) ?: colors.pink
    Box(
        modifier
            .clip(RoundedCornerShape(radius.dp))
            .background(Brush.linearGradient(listOf(colors.surface, tint))),
        contentAlignment = Alignment.BottomEnd,
    ) {
        if (next.still != null) {
            AsyncImage(next.still, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else if (ghost) {
            Text(
                next.number.toString().padStart(2, '0'),
                style = NeoTheme.typography.display.copy(fontSize = 110.sp, lineHeight = 110.sp, letterSpacing = (-4).sp),
                color = colors.paper.copy(alpha = GHOST_ALPHA),
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

/** The seconds left, in a ring that empties as they run out. */
@Composable
private fun Countdown(secondsLeft: Int) {
    val content = LocalContentColor.current
    val pink = NeoTheme.colors.pink
    Box(Modifier.size(Ring), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(Ring).padding(2.dp)) {
            val stroke = Stroke(width = 3.dp.toPx())
            drawArc(content.copy(alpha = RING_TRACK_ALPHA), 0f, 360f, useCenter = false, style = stroke)
            drawArc(pink, -90f, 360f * secondsLeft / UP_NEXT_SECONDS, useCenter = false, style = stroke)
        }
        Text(
            secondsLeft.toString(),
            style = NeoTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
        )
    }
}
