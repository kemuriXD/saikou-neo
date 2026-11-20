package ani.saikou.neo.tv.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.ChoiceRow
import ani.saikou.neo.tv.ui.NeoIcons
import ani.saikou.neo.tv.ui.keepsArrowsIn

internal const val EPISODE_MENU_TAG = "episode-menu"
internal const val MARK_WATCHED_TAG = "mark-watched"
internal const val MARK_UNWATCHED_TAG = "mark-unwatched"

private val MenuWidth = 344.dp
private val MenuShape = RoundedCornerShape(16.dp)
private val ScreenRoom = 24.dp
private const val SCRIM_ALPHA = 0.5f

/**
 * The menu holding OK on an episode opens beside its [row] (ADR 0052): Mark watched up to here on an unwatched
 * episode, Mark unwatched on a [watched] one. [onMark] gets which was picked; Back calls [onClose].
 */
@Composable
internal fun EpisodeMenu(
    show: String,
    title: String,
    episode: Int,
    watched: Boolean,
    row: Rect,
    onMark: (watched: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val colors = NeoTheme.colors
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    // Holding OK opens the menu, and letting go of it mustn't pick the row it opens on. Only an OK pressed here counts.
    val pressedHere = remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.scrimPlayer.copy(alpha = SCRIM_ALPHA))
            .onPreviewKeyEvent { event ->
                if (!event.isOk()) return@onPreviewKeyEvent false
                // The held key keeps repeating once the menu is open; a fresh press starts at 0.
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) pressedHere.value = true
                !pressedHere.value
            },
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .besideRow(row)
                .padding(end = NeoTheme.dimensions.gridMargin)
                .width(MenuWidth)
                .shadow(24.dp, MenuShape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(colors.surface, MenuShape)
                .border(1.dp, colors.line, MenuShape)
                .padding(16.dp)
                .keepsArrowsIn()
                .testTag(EPISODE_MENU_TAG),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.padding(start = 4.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    show.uppercase(),
                    style = NeoTheme.typography.overline.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = colors.pink,
                    maxLines = 1,
                )
                Text(title, style = NeoTheme.typography.title, color = colors.paper, maxLines = 1)
            }
            if (watched) {
                ChoiceRow(
                    name = stringResource(R.string.details_mark_unwatched),
                    help =
                        if (episode > 1) {
                            stringResource(R.string.details_mark_unwatched_help, episode - 1)
                        } else {
                            stringResource(R.string.details_mark_unwatched_help_none)
                        },
                    icon = NeoIcons.Cross,
                    onClick = { onMark(false) },
                    modifier = Modifier.focusRequester(first).testTag(MARK_UNWATCHED_TAG),
                )
            } else {
                ChoiceRow(
                    name = stringResource(R.string.details_mark_watched),
                    help = stringResource(R.string.details_mark_watched_help, episode),
                    icon = NeoIcons.Tick,
                    onClick = { onMark(true) },
                    modifier = Modifier.focusRequester(first).testTag(MARK_WATCHED_TAG),
                )
            }
        }
    }
}

private fun KeyEvent.isOk() = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

/** Sets the menu level with the middle of [row], kept on the screen. */
private fun Modifier.besideRow(row: Rect) =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        val room = ScreenRoom.roundToPx()
        val top =
            (row.center.y - placeable.height / 2f).toInt().coerceIn(
                room,
                (constraints.maxHeight - placeable.height - room).coerceAtLeast(room),
            )
        layout(placeable.width, constraints.maxHeight) { placeable.place(0, top) }
    }

/** "Hold OK to mark watched", over the episodes when they can be marked. */
@Composable
internal fun MarkHint(modifier: Modifier = Modifier) {
    val colors = NeoTheme.colors
    val caption = NeoTheme.typography.caption
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.details_mark_hint_hold), style = caption, color = colors.textMuted)
        Text(
            stringResource(R.string.details_mark_hint_key),
            style = caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = colors.textMuted,
            modifier = Modifier.border(1.dp, colors.outline, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
        )
        Text(stringResource(R.string.details_mark_hint_after), style = caption, color = colors.textMuted)
    }
}
