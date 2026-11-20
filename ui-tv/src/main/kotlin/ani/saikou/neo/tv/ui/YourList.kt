package ani.saikou.neo.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.account.ListButtonState
import ani.saikou.neo.shared.account.ListStatus
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme

const val LIST_BUTTON_TAG = "list-button"
const val LIST_PANEL_TAG = "list-panel"
const val LIST_REMOVE_TAG = "list-remove"

/** The test tag of [status]'s row in the list panel. */
fun listStatusTag(status: ListStatus): String = "list-${status.name.lowercase()}"

private val PanelWidth = 360.dp
private val RowShape = RoundedCornerShape(14.dp)
private const val SCRIM_ALPHA = 0.72f
private const val ROW_GLOW_ALPHA = 0.28f

// The order AniList's own list uses.
private val PanelStatuses =
    listOf(ListStatus.WATCHING, ListStatus.PLANNING, ListStatus.COMPLETED, ListStatus.REWATCHING, ListStatus.PAUSED, ListStatus.DROPPED)

/** The show's status on your list with a tick, or Add to list. OK opens the list panel (ADR 0051). */
@Composable
fun ListButton(
    state: ListButtonState.Shown,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NeoSurface(onClick, CircleShape, NeoTheme.dimensions.focusScaleButton, modifier.height(44.dp).testTag(LIST_BUTTON_TAG)) {
        Row(
            Modifier.fillMaxHeight().padding(start = 16.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val status = state.status
            Icon(
                if (status != null) NeoIcons.Tick else NeoIcons.Add,
                contentDescription = null,
                tint = if (status != null) NeoTheme.colors.pink else LocalContentColor.current,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = status?.let { statusName(it) } ?: stringResource(R.string.home_add_to_list),
                style = NeoTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
    }
}

/**
 * The side panel for picking [show]'s status on your list, with focus on the one it has. [onChange] gets the status
 * picked, or null for Remove, which shows only when the show is on the list. Back calls [onClose].
 */
@Composable
fun YourListPanel(
    show: String,
    state: ListButtonState.Shown,
    onChange: (ListStatus?) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val colors = NeoTheme.colors
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Box(Modifier.fillMaxSize().background(colors.scrimPlayer.copy(alpha = SCRIM_ALPHA)).testTag(LIST_PANEL_TAG)) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .width(PanelWidth)
                .fillMaxHeight()
                .background(colors.surface)
                .padding(horizontal = 24.dp, vertical = 27.dp)
                .keepsArrowsIn(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    show.uppercase(),
                    style = NeoTheme.typography.overline.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = colors.pink,
                    maxLines = 1,
                )
                Text(stringResource(R.string.list_title), style = NeoTheme.typography.titleLarge, color = colors.paper)
                Text(stringResource(R.string.list_body), style = NeoTheme.typography.caption, color = colors.textMuted)
            }
            val focused = state.status ?: PanelStatuses.first()
            PanelStatuses.forEach { status ->
                ChoiceRow(
                    name = statusName(status),
                    help =
                        state.episodes?.takeIf { status == ListStatus.COMPLETED }?.let {
                            pluralStringResource(
                                R.plurals.list_completed_help,
                                it,
                                it,
                            )
                        },
                    ticked = status == state.status,
                    onClick = { onChange(status) },
                    modifier =
                        Modifier.testTag(listStatusTag(status)).then(
                            if (status ==
                                focused
                            ) {
                                Modifier.focusRequester(first)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
            if (state.status != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                ChoiceRow(
                    name = stringResource(R.string.list_remove),
                    icon = NeoIcons.Remove,
                    onClick = { onChange(null) },
                    modifier = Modifier.testTag(LIST_REMOVE_TAG),
                )
            }
        }
    }
}

/** A row in a side panel or menu: a radio that's filled when [ticked], or an [icon] for an action such as Remove. */
@Composable
internal fun ChoiceRow(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier,
    help: String? = null,
    ticked: Boolean = false,
    icon: ImageVector? = null,
) {
    val colors = NeoTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RowShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.surfaceRaised,
                contentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(colors.paper.copy(alpha = ROW_GLOW_ALPHA), 9.dp)),
    ) {
        val focused = LocalContentColor.current == colors.onFocus
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) else Radio(ticked)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = NeoTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                help?.let {
                    Text(
                        it,
                        style = NeoTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = if (focused) colors.onFocusMuted else colors.textMuted,
                    )
                }
            }
        }
    }
}

/** A ring, filled in the middle when [ticked], so the status shows by shape and not by colour alone. */
@Composable
private fun Radio(ticked: Boolean) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(16.dp)) {
        val stroke = 1.5.dp.toPx()
        drawCircle(color, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
        if (ticked) drawCircle(color, radius = 4.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
    }
}

@Composable
private fun statusName(status: ListStatus): String =
    stringResource(
        when (status) {
            ListStatus.WATCHING -> R.string.list_watching
            ListStatus.PLANNING -> R.string.list_planning
            ListStatus.COMPLETED -> R.string.list_completed
            ListStatus.REWATCHING -> R.string.list_rewatching
            ListStatus.PAUSED -> R.string.list_paused
            ListStatus.DROPPED -> R.string.list_dropped
        },
    )
