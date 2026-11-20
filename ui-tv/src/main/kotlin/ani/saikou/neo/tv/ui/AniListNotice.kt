package ani.saikou.neo.tv.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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

internal const val NOTICE_TAG = "anilist-notice"
internal const val NOTICE_UNDO_TAG = "anilist-notice-undo"

/** What the notice's Undo does, and how it tells the notice it has focus, so it stays. */
internal data class NoticeActions(
    val onUndo: () -> Unit = {},
    val onUndoFocused: (Boolean) -> Unit = {},
)

/**
 * Says what the TV just changed on AniList, with Undo (TV/Notice, 196:589). Once Undo has failed there's no Undo, just
 * the warning. It sits bottom left, 48 dp in.
 */
@Composable
internal fun BoxScope.AniListNotice(
    shown: ShownNotice,
    actions: NoticeActions,
    undo: FocusRequester,
    modifier: Modifier = Modifier,
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
            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
            .testTag(NOTICE_TAG),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val change = shown.notice.change
        val icon =
            when {
                shown.failed -> NeoIcons.Warning
                change is NoticeChange.SavedForLater -> NeoIcons.WifiOff
                else -> NeoIcons.Passed
            }
        Icon(
            icon,
            contentDescription = null,
            tint =
                if (icon ==
                    NeoIcons.WifiOff
                ) {
                    colors.textMuted
                } else {
                    colors.pink
                },
            modifier = Modifier.size(22.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = colors.paper,
                maxLines = 1,
            )
            Text(detail, style = typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.textMuted, maxLines = 1)
        }
        if (!shown.failed) {
            Box(Modifier.width(4.dp))
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
                    stringResource(R.string.notice_undo),
                    style = typography.label.copy(fontSize = 14.sp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/**
 * The notice over every screen but the player, where Up next shows it. It takes focus to Undo, and any D-pad move or
 * Back sends focus back to where it was on [screen], as does the notice closing while Undo has it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BoxScope.NoticeOverScreens(
    shown: ShownNotice?,
    actions: NoticeActions,
    screen: FocusRequester,
) {
    val undo = remember { FocusRequester() }
    var undoFocused by remember { mutableStateOf(false) }
    val canUndo = shown != null && !shown.failed
    // Read as the notice changes, before a closing Undo loses focus. A newer notice keeps where focus came from.
    val hadFocus = undoFocused
    LaunchedEffect(shown?.notice, canUndo) {
        if (canUndo) {
            if (!hadFocus) screen.saveFocusedChild()
            undo.requestFocus()
        } else if (hadFocus) {
            if (!screen.restoreFocusedChild()) screen.requestFocus()
        }
    }
    shown ?: return
    val back = {
        if (!screen.restoreFocusedChild()) screen.requestFocus()
        true
    }
    AniListNotice(
        shown,
        actions.copy(
            onUndoFocused = {
                undoFocused = it
                actions.onUndoFocused(it)
            },
        ),
        undo,
        Modifier.onPreviewKeyEvent {
            when (it.nativeKeyEvent.keyCode) {
                // Back acts as it lifts, so the press doesn't go on to the screen under.
                KeyEvent.KEYCODE_BACK -> {
                    if (it.type == KeyEventType.KeyUp) back() else true
                }

                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (it.type == KeyEventType.KeyDown) back() else true
                }

                else -> {
                    false
                }
            }
        },
    )
}

@Composable
private fun words(shown: ShownNotice): Pair<String, String> {
    val notice = shown.notice
    val show = notice.title?.displayTitle()?.ifBlank { null } ?: stringResource(R.string.notice_this_show)
    if (shown.failed) return stringResource(R.string.notice_couldnt_undo, show) to stringResource(R.string.notice_couldnt_undo_why)
    val before = notice.before
    return when (val change = notice.change) {
        is NoticeChange.SavedForLater -> {
            stringResource(R.string.notice_saved_for_later, show, change.pending.episode) to
                stringResource(R.string.notice_sent_when_online)
        }

        NoticeChange.Removed -> {
            stringResource(R.string.notice_off_your_list, show) to
                (before?.let { stringResource(R.string.notice_it_was_status, statusName(it.status), it.progress) } ?: "")
        }

        is NoticeChange.Updated -> {
            val title =
                if (change.byEpisode && change.status != ListStatus.COMPLETED) {
                    stringResource(R.string.notice_episode_on_anilist, show, change.progress)
                } else {
                    stringResource(R.string.notice_status_on_anilist, show, statusName(change.status))
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
