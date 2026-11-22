package ani.saikou.neo.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import ani.saikou.neo.shared.account.ListStatus
import ani.saikou.neo.shared.account.NoticeChange
import ani.saikou.neo.shared.account.ShownNotice
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.home.displayTitle
import ani.saikou.neo.tv.theme.NeoTheme
import android.view.KeyEvent as AndroidKeyEvent

internal const val NOTICE_TAG = "anilist-notice"
internal const val NOTICE_UNDO_TAG = "anilist-notice-undo"

private const val UNDO_GLOW_ALPHA = 0.28f

/** What the notice's Undo does, and how it tells the notice it has focus, so it stays. */
internal data class NoticeActions(
    val onUndo: () -> Unit = {},
    val onUndoFocused: (Boolean) -> Unit = {},
)

/**
 * Undo over every screen but the player. It never takes real focus, so focus can't get lost in the screen under it on
 * the way back. While Undo shows focused, the app hands every key here first: OK presses Undo, and any D-pad key or Back
 * only takes the focus off Undo, leaving focus where it was on the screen.
 */
@Stable
internal class NoticeKeys {
    var focused by mutableStateOf(false)
        internal set

    internal var onUndo: () -> Unit = {}

    /** True when the key was Undo's. */
    fun onKey(event: KeyEvent): Boolean {
        if (!focused) return false
        val down = event.type == KeyEventType.KeyDown
        when (event.nativeKeyEvent.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (!down) {
                    focused = false
                    onUndo()
                }
            }

            // Back acts as it lifts, so the press doesn't go on to the screen.
            AndroidKeyEvent.KEYCODE_BACK -> {
                if (!down) focused = false
            }

            AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_DPAD_LEFT,
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
            -> {
                if (down) focused = false
            }

            else -> {
                return false
            }
        }
        return true
    }
}

/**
 * Says what the TV just changed on AniList, with Undo (TV/Notice, 196:589). Once Undo has failed there's no Undo, just
 * the warning. It sits bottom left, 48 dp in. Up next gives Undo real focus through [undo]. Elsewhere [undoFocused] only
 * draws it focused, for [NoticeKeys].
 */
@Composable
internal fun BoxScope.AniListNotice(
    shown: ShownNotice,
    actions: NoticeActions,
    undo: FocusRequester?,
    modifier: Modifier = Modifier,
    undoFocused: Boolean = false,
) {
    val colors = NeoTheme.colors
    val typography = NeoTheme.typography
    val shape = RoundedCornerShape(16.dp)
    val (title, detail) = words(shown)
    Row(
        modifier
            .align(Alignment.BottomStart)
            .padding(start = 48.dp, bottom = 27.dp)
            .shadow(12.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.line, shape)
            .testTag(NOTICE_TAG)
            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val change = shown.notice.change
        val icon =
            when {
                shown.failed || change is NoticeChange.NotSaved -> NeoIcons.Warning
                change is NoticeChange.SavedForLater -> NeoIcons.WifiOff
                else -> NeoIcons.Passed
            }
        val tint = if (icon == NeoIcons.WifiOff) colors.textMuted else colors.pink
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = colors.paper,
                maxLines = 1,
            )
            Text(detail, style = typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.textMuted, maxLines = 1)
        }
        if (!shown.canUndo) return@Row
        Box(Modifier.width(4.dp))
        val label = stringResource(R.string.notice_undo)
        if (undo != null) {
            NeoSurface(
                actions.onUndo,
                CircleShape,
                NeoTheme.dimensions.focusScaleButton,
                Modifier
                    .focusRequester(undo)
                    .onFocusChanged { actions.onUndoFocused(it.isFocused) }
                    .testTag(NOTICE_UNDO_TAG),
            ) {
                Text(
                    label,
                    style = typography.label.copy(fontSize = 14.sp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        } else {
            // Drawn as the focused button looks (196:561), without taking focus.
            Box(
                Modifier
                    .semantics { selected = undoFocused }
                    .testTag(NOTICE_UNDO_TAG)
                    .scale(if (undoFocused) NeoTheme.dimensions.focusScaleButton else 1f)
                    .then(
                        if (undoFocused) {
                            Modifier.shadow(
                                18.dp,
                                CircleShape,
                                ambientColor = Color.White,
                                spotColor = Color.White.copy(alpha = UNDO_GLOW_ALPHA),
                            )
                        } else {
                            Modifier
                        },
                    ).background(if (undoFocused) colors.focus else colors.fillTonal, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(label, style = typography.label.copy(fontSize = 14.sp), color = if (undoFocused) colors.onFocus else colors.paper)
            }
        }
    }
}

/**
 * The notice over every screen but the player, where Up next shows it. Undo shows focused as it comes, and [keys] takes
 * the remote's keys while it is. It has a box of its own, so a parent that stretches its children, like the app's
 * Surface, doesn't stretch the notice.
 */
@Composable
internal fun NoticeOverScreens(
    shown: ShownNotice?,
    actions: NoticeActions,
    keys: NoticeKeys,
) {
    keys.onUndo = actions.onUndo
    val canUndo = shown?.canUndo == true
    LaunchedEffect(shown?.notice, canUndo) { keys.focused = canUndo }
    // Undo showing focused keeps the notice up, as real focus would.
    LaunchedEffect(keys) { snapshotFlow { keys.focused }.collect { actions.onUndoFocused(it) } }
    DisposableEffect(keys) { onDispose { keys.focused = false } }
    shown ?: return
    Box(Modifier.fillMaxSize()) { AniListNotice(shown, actions, undo = null, undoFocused = keys.focused) }
}

@Composable
private fun words(shown: ShownNotice): Pair<String, String> {
    val notice = shown.notice
    val show = notice.title?.displayTitle()?.ifBlank { null } ?: stringResource(R.string.notice_this_show)
    if (shown.failed) return stringResource(R.string.notice_couldnt_undo, show) to stringResource(R.string.notice_couldnt_undo_why)
    val before = notice.before
    return when (val change = notice.change) {
        // A mark from Details that went back (#512).
        is NoticeChange.NotSaved -> {
            val title = if (change.watched) R.string.notice_not_marked_watched else R.string.notice_not_marked_unwatched
            stringResource(title, show, change.episode) to stringResource(R.string.notice_not_saved_why)
        }

        is NoticeChange.SavedForLater -> {
            stringResource(R.string.notice_saved_for_later, show, change.pending.episode) to
                stringResource(R.string.notice_sent_when_online)
        }

        NoticeChange.Removed -> {
            stringResource(R.string.notice_off_your_list, show) to
                (before?.let { stringResource(R.string.notice_it_was_status, statusName(it.status), it.progress) } ?: "")
        }

        is NoticeChange.Updated -> {
            // An episode that changed the status, such as one starting a rewatch, names the new status too.
            val title =
                when {
                    !change.byEpisode || change.status == ListStatus.COMPLETED -> {
                        stringResource(R.string.notice_status_on_anilist, show, statusName(change.status))
                    }

                    before?.status != change.status -> {
                        stringResource(R.string.notice_status_episode_on_anilist, show, statusName(change.status), change.progress)
                    }

                    else -> {
                        stringResource(R.string.notice_episode_on_anilist, show, change.progress)
                    }
                }
            val detail =
                when {
                    before == null -> {
                        stringResource(R.string.notice_wasnt_on_your_list)
                    }

                    !change.byEpisode -> {
                        stringResource(R.string.notice_it_was, statusName(before.status))
                    }

                    before.status != change.status -> {
                        stringResource(
                            R.string.notice_it_was_status,
                            statusName(before.status),
                            before.progress,
                        )
                    }

                    else -> {
                        stringResource(R.string.notice_it_was_episode, before.progress)
                    }
                }
            title to detail
        }
    }
}
