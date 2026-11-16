package ani.saikou.neo.tv.schedule

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AirType
import ani.saikou.neo.shared.catalogue.LoadProblem
import ani.saikou.neo.shared.schedule.Countdown
import ani.saikou.neo.shared.schedule.ScheduleDay
import ani.saikou.neo.shared.schedule.ScheduleEvent
import ani.saikou.neo.shared.schedule.ScheduleFilter
import ani.saikou.neo.shared.schedule.ScheduleRow
import ani.saikou.neo.shared.schedule.ScheduleState
import ani.saikou.neo.shared.schedule.countdown
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.settings.ScreenTitle
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoButtonRow
import ani.saikou.neo.tv.ui.NeoTab
import ani.saikou.neo.tv.ui.RememberFocus
import ani.saikou.neo.tv.ui.placeholder
import ani.saikou.neo.tv.ui.rememberPulse
import ani.saikou.neo.tv.ui.remembersFocus
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

internal const val SCHEDULE_LOADING_TAG = "schedule-loading"

internal fun dayTag(index: Int) = "schedule-day-$index"

internal fun filterTag(filter: ScheduleFilter) = "schedule-filter-${filter.name}"

internal fun rowTag(row: ScheduleRow) = "schedule-row-${row.route}-${row.episodeNumber}-${row.release}"

// From the design: the header is the title row, then 14 dp down the day tabs, 92 by 72 dp and 8 dp apart. The rows
// start 27 dp under them, 66 dp tall and 8 dp apart, after a 56 dp column for the time.
private val HeaderGap = 14.dp
private val DayWidth = 92.dp
private val DayHeight = 72.dp
private val DayGap = 8.dp
private val DayShape = RoundedCornerShape(14.dp)
private val DayLine = 3.dp
private val ToggleHeight = 32.dp
private val TogglePadding = 14.dp
private val ToggleGap = 8.dp
private val RowsTop = 27.dp
private val MessageTop = 51.dp
private val RowGap = 8.dp
private val RowHeight = 66.dp
private val TimeWidth = 56.dp
private val TimeWidth12Hour = 76.dp
private val SlotGap = 16.dp
private val ThumbWidth = 104.dp
private val ThumbHeight = 58.dp
private val MessageWidth = 520.dp
private val ToastBottom = 32.dp

// The episode number sits in the thumbnail's lower right, cut off by its edges.
private val ThumbNumberX = 42.dp
private val ThumbNumberY = 24.dp
private const val THUMB_NUMBER_ALPHA = 0.35f

// A focused row grows a little, as the design's focused slot does.
private const val ROW_FOCUS_SCALE = 1.02f
private val RowGlowColor = Color.White.copy(alpha = 0.28f)

// The loading rows fade out down the list.
private val LoadingAlphas = listOf(1f, 0.8f, 0.6f, 0.4f)

private const val TOAST_MILLIS = 4_000L
private const val MINUTE_MILLIS = 60_000L

/** What Schedule's parts call. */
data class ScheduleActions(
    val onSelectDay: (index: Int) -> Unit = {},
    val onFilter: (filter: ScheduleFilter) -> Unit = {},
    val onOpen: (row: ScheduleRow) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onHome: () -> Unit = {},
    /** The [cantOpen] note has been shown long enough. */
    val onCantOpenShown: () -> Unit = {},
)

/**
 * Schedule: this week's episodes by day and air time, in the TV's time zone (ADR 0041). Moving along the days picks
 * each. Signed in, a toggle switches between your list and all shows. Selecting a row asks for its Details, and
 * [cantOpen] says when a show can't be opened. "Out now" and "in 2h" follow [clock], checked every minute.
 */
@Composable
fun ScheduleScreen(
    state: ScheduleState,
    modifier: Modifier = Modifier,
    actions: ScheduleActions = ScheduleActions(),
    cantOpen: ScheduleEvent.CantOpen? = null,
    clock: () -> Instant = { Clock.System.now() },
) = RememberFocus { focus ->
    val colors = NeoTheme.colors
    var now by remember { mutableStateOf(clock()) }
    // A UI effect: the clock moves, the week doesn't need fetching again.
    LaunchedEffect(clock) {
        while (true) {
            now = clock()
            delay(MINUTE_MILLIS - now.toEpochMilliseconds() % MINUTE_MILLIS)
        }
    }
    val days = remember { List(7) { FocusRequester() } }
    val selectedDay = (state as? ScheduleState.Ready)?.selectedDay
    LaunchedEffect(selectedDay != null) {
        if (selectedDay != null) focus.restoreOr { days[selectedDay].requestFocus() }
    }

    Box(modifier.fillMaxSize().background(colors.ink)) {
        Column(Modifier.fillMaxSize()) {
            Header(state, days, actions)
            when (state) {
                ScheduleState.Loading -> {
                    LoadingRows()
                }

                ScheduleState.NoToken -> {
                    Message(stringResource(R.string.schedule_no_token), stringResource(R.string.schedule_no_token_body)) {
                        HomeButton(actions.onHome, first = true)
                    }
                }

                is ScheduleState.Failed -> {
                    Message(stringResource(R.string.schedule_failed), stringResource(failedBody(state.problem))) {
                        val retry = remember { FocusRequester() }
                        LaunchedEffect(Unit) { retry.requestFocus() }
                        NeoButton(stringResource(R.string.schedule_try_again), actions.onRetry, Modifier.focusRequester(retry))
                        HomeButton(actions.onHome)
                    }
                }

                is ScheduleState.Ready -> {
                    Day(state, now, actions)
                }
            }
        }
        cantOpen?.let { Toast(it, actions.onCantOpenShown, Modifier.align(Alignment.BottomCenter)) }
    }
}

@Composable
private fun Header(
    state: ScheduleState,
    days: List<FocusRequester>,
    actions: ScheduleActions,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val ready = state as? ScheduleState.Ready
    Column(
        Modifier.padding(start = dimensions.gridMargin, end = dimensions.gridMargin, top = dimensions.safeY),
        verticalArrangement = Arrangement.spacedBy(HeaderGap),
    ) {
        Row(Modifier.fillMaxWidth().height(ToggleHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.schedule_title), style = ScreenTitle, color = colors.paper, modifier = Modifier.weight(1f))
            val signedOut = ready != null && ready.filter == null
            Text(
                stringResource(if (signedOut) R.string.schedule_zone_signed_out else R.string.schedule_zone),
                style = NeoTheme.typography.caption,
                color = colors.textMuted,
                maxLines = 1,
            )
            ready?.filter?.let { Toggle(it, actions.onFilter) }
        }
        when {
            ready != null -> DayTabs(ready, days, actions.onSelectDay)
            state == ScheduleState.Loading -> LoadingDays()
        }
    }
}

/** Your list and All shows, with the one chosen filled. Up from the days lands on the chosen one. */
@Composable
private fun Toggle(
    chosen: ScheduleFilter,
    onFilter: (ScheduleFilter) -> Unit,
) {
    val chips = remember { ScheduleFilter.entries.associateWith { FocusRequester() } }
    Row(
        Modifier
            .padding(start = NeoTheme.dimensions.space4)
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
                        chips.getValue(chosen).requestFocus()
                    }
                }
            }.focusGroup()
            .semantics { selectableGroup() },
        horizontalArrangement = Arrangement.spacedBy(ToggleGap),
    ) {
        ScheduleFilter.entries.forEach { filter ->
            NeoTab(
                text = stringResource(if (filter == ScheduleFilter.YOUR_LIST) R.string.schedule_your_list else R.string.schedule_all_shows),
                selected = filter == chosen,
                onClick = { onFilter(filter) },
                modifier = Modifier.testTag(filterTag(filter)).focusRequester(chips.getValue(filter)),
                height = ToggleHeight,
                padding = TogglePadding,
                fontSize = 13.sp,
            )
        }
    }
}

/** Monday to Sunday. Focus picks a day, so moving along them shows each one's episodes. */
@Composable
private fun DayTabs(
    state: ScheduleState.Ready,
    days: List<FocusRequester>,
    onSelectDay: (Int) -> Unit,
) {
    Row(
        Modifier
            .focusProperties { onEnter = { days[state.selectedDay].requestFocus() } }
            .focusGroup()
            .semantics { selectableGroup() },
        horizontalArrangement = Arrangement.spacedBy(DayGap),
    ) {
        state.days.forEachIndexed { index, day ->
            DayTab(
                day,
                today = index == state.today,
                selected = index == state.selectedDay,
                onSelect = { onSelectDay(index) },
                modifier =
                    Modifier
                        .testTag(dayTag(index))
                        .focusRequester(days[index])
                        .remembersFocus(dayTag(index))
                        .onFocusChanged { if (it.isFocused && index != state.selectedDay) onSelectDay(index) },
            )
        }
    }
}

@Composable
private fun DayTab(
    day: ScheduleDay,
    today: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val typography = NeoTheme.typography
    val focusedGlow = Glow(RowGlowColor, dimensions.glowFocus)
    val border = Border(BorderStroke(1.5.dp, colors.line), shape = DayShape)
    Surface(
        selected = selected,
        onClick = onSelect,
        modifier = modifier.size(DayWidth, DayHeight),
        shape = SelectableSurfaceDefaults.shape(DayShape),
        colors =
            SelectableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = colors.textMuted,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
                selectedContainerColor = colors.fillTonal,
                selectedContentColor = colors.paper,
                focusedSelectedContainerColor = colors.focus,
                focusedSelectedContentColor = colors.onFocus,
                pressedSelectedContainerColor = colors.focus,
                pressedSelectedContentColor = colors.onFocus,
            ),
        border = SelectableSurfaceDefaults.border(border = border, focusedBorder = Border.None, selectedBorder = Border.None),
        scale = SelectableSurfaceDefaults.scale(focusedScale = dimensions.focusScale, focusedSelectedScale = dimensions.focusScale),
        glow = SelectableSurfaceDefaults.glow(focusedGlow = focusedGlow, focusedSelectedGlow = focusedGlow),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (today) stringResource(R.string.schedule_today) else day.date.format("EEE"),
                style = typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                color = LocalContentColor.current.copy(alpha = 0.8f),
            )
            Text(day.date.day.toString(), style = typography.rowTitle.copy(fontSize = 20.sp))
            Text(
                pluralStringResource(R.plurals.schedule_day_episodes, day.rows.size, day.rows.size),
                style = typography.caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
            )
        }
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(DayLine)
                    .background(colors.pink),
            )
        }
    }
}

/** The picked day's rows, or a word on why it has none. */
@Composable
private fun Day(
    state: ScheduleState.Ready,
    now: Instant,
    actions: ScheduleActions,
) {
    val dimensions = NeoTheme.dimensions
    val day = state.days[state.selectedDay]
    if (day.rows.isEmpty()) {
        val today = state.selectedDay == state.today
        val weekday = day.date.format("EEEE")
        if (state.filter == ScheduleFilter.YOUR_LIST) {
            Message(
                if (today) {
                    stringResource(R.string.schedule_nothing_yours_today)
                } else {
                    stringResource(R.string.schedule_nothing_yours_on, weekday)
                },
                stringResource(R.string.schedule_nothing_yours_body),
            ) { NeoButton(stringResource(R.string.schedule_see_all), { actions.onFilter(ScheduleFilter.ALL_SHOWS) }) }
        } else {
            Message(
                if (today) stringResource(R.string.schedule_nothing_today) else stringResource(R.string.schedule_nothing_on, weekday),
                stringResource(R.string.schedule_nothing_body),
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(start = dimensions.gridMargin, end = dimensions.gridMargin, top = RowsTop, bottom = dimensions.safeY),
        verticalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        items(day.rows, key = ::rowTag) { row ->
            Slot(row, now, onOpen = { actions.onOpen(row) }, modifier = Modifier.testTag(rowTag(row)).remembersFocus(rowTag(row)))
        }
    }
}

/** One episode: its time, then a row with its art, title, status and when it's out. */
@Composable
private fun Slot(
    row: ScheduleRow,
    now: Instant,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val typography = NeoTheme.typography
    val context = LocalContext.current
    val out = countdown(row.airsAt, now) == Countdown.Out
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val twentyFourHour = DateFormat.is24HourFormat(context)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SlotGap)) {
        Text(
            row.airsAt.time(context),
            style = typography.label.copy(fontWeight = FontWeight.SemiBold),
            color = if (out && !focused) colors.textFaint else colors.paper,
            maxLines = 1,
            modifier = Modifier.width(if (twentyFourHour) TimeWidth else TimeWidth12Hour),
        )
        Surface(
            onClick = onOpen,
            modifier = modifier.weight(1f).height(RowHeight),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NeoTheme.dimensions.radiusLg)),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = colors.surface,
                    contentColor = colors.paper,
                    focusedContainerColor = colors.focus,
                    focusedContentColor = colors.onFocus,
                    pressedContainerColor = colors.focus,
                    pressedContentColor = colors.onFocus,
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = ROW_FOCUS_SCALE),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(RowGlowColor, NeoTheme.dimensions.glowFocus)),
            interactionSource = interactions,
        ) {
            Row(
                Modifier.fillMaxSize().padding(start = 4.dp, end = SlotGap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SlotGap),
            ) {
                Thumb(row)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.title, style = typography.label.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                    Text(
                        status(row, now, out),
                        style = typography.caption,
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }
                Chip(
                    when {
                        out -> stringResource(R.string.schedule_out_now)
                        row.delayed -> stringResource(R.string.schedule_delayed)
                        else -> stringResource(R.string.schedule_when, row.airsAt.format("EEE d MMM"), row.airsAt.time(context))
                    },
                    container =
                        when {
                            focused -> colors.onFocusTonal
                            out -> colors.pink
                            else -> colors.fillTonal
                        },
                    content = if (focused || out) colors.ink else colors.paper,
                )
            }
        }
    }
}

/** The show's art, or the episode number over its corner when there's none. */
@Composable
private fun Thumb(row: ScheduleRow) {
    val colors = NeoTheme.colors
    Box(Modifier.size(ThumbWidth, ThumbHeight).clip(RoundedCornerShape(NeoTheme.dimensions.radiusSm)).background(colors.surfaceRaised)) {
        row.imageUrl?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // Over real art the number is noise, and the line beside it says it anyway.
        row.episodeNumber?.takeIf { row.imageUrl == null }?.let {
            Text(
                "%02d".format(it),
                style = NeoTheme.typography.display.copy(fontSize = 48.sp, lineHeight = 48.sp, letterSpacing = 0.sp),
                color = colors.paper.copy(alpha = THUMB_NUMBER_ALPHA),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.offset(ThumbNumberX, ThumbNumberY).wrapContentSize(Alignment.TopStart, unbounded = true),
            )
        }
    }
}

@Composable
private fun Chip(
    text: String,
    container: Color,
    content: Color,
) {
    Text(
        text,
        style = NeoTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
        color = content,
        maxLines = 1,
        modifier = Modifier.background(container, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/** "Episode 10 · Sub out now, dub Friday", or "Episode 4 · Sub · in 2h 14m". */
@Composable
private fun status(
    row: ScheduleRow,
    now: Instant,
    out: Boolean,
): String {
    val release =
        stringResource(
            when (row.release) {
                AirType.SUB -> R.string.schedule_sub
                AirType.RAW -> R.string.schedule_raw
                AirType.DUB -> R.string.schedule_dub
            },
        )
    val dubAt = row.dubAt?.takeIf { row.release != AirType.DUB }
    val kind =
        when {
            dubAt == null -> release
            out && countdown(dubAt, now) == Countdown.Out -> stringResource(R.string.schedule_out_dub_out, release)
            out -> stringResource(R.string.schedule_out_dub_later, release, dubAt.format("EEEE"))
            else -> stringResource(R.string.schedule_dub_later, release, dubAt.format("EEEE"))
        }
    val left = (countdown(row.airsAt, now) as? Countdown.In)?.left?.takeIf { it < 24.hours && !row.delayed }
    val parts =
        listOfNotNull(
            row.episodeNumber?.let { stringResource(R.string.schedule_episode, it) },
            kind,
            left?.let {
                if (it.inWholeHours > 0) {
                    stringResource(R.string.schedule_in_hours, it.inWholeHours.toInt(), (it.inWholeMinutes % 60).toInt())
                } else {
                    stringResource(R.string.schedule_in_minutes, it.inWholeMinutes.toInt().coerceAtLeast(1))
                }
            },
            stringResource(R.string.schedule_delayed).takeIf { row.delayed },
        )
    val resources = LocalResources.current
    return parts.reduce { joined, part -> resources.getString(R.string.schedule_status, joined, part) }
}

/** A title, a line under it and any buttons, where the rows would be. */
@Composable
private fun Message(
    title: String,
    body: String,
    buttons: (@Composable () -> Unit)? = null,
) {
    val colors = NeoTheme.colors
    Column(
        Modifier.padding(start = NeoTheme.dimensions.gridMargin, top = MessageTop).width(MessageWidth),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = ScreenTitle, color = colors.paper)
        Text(body, style = NeoTheme.typography.bodyLarge, color = colors.textMuted)
        buttons?.let { NeoButtonRow(Modifier.padding(top = 12.dp)) { it() } }
    }
}

@Composable
private fun HomeButton(
    onHome: () -> Unit,
    first: Boolean = false,
) {
    val requester = remember { FocusRequester() }
    if (first) LaunchedEffect(Unit) { requester.requestFocus() }
    NeoButton(stringResource(R.string.schedule_back_home), onHome, Modifier.focusRequester(requester))
}

private fun failedBody(problem: LoadProblem) =
    when (problem) {
        LoadProblem.NO_NETWORK -> R.string.schedule_failed_network
        LoadProblem.RATE_LIMITED -> R.string.schedule_failed_busy
        LoadProblem.OTHER -> R.string.schedule_failed_other
    }

/** Seven empty days while the week loads. */
@Composable
private fun LoadingDays() {
    val pulse = rememberPulse()
    Row(horizontalArrangement = Arrangement.spacedBy(DayGap)) {
        repeat(7) { Box(Modifier.size(DayWidth, DayHeight).placeholder(NeoTheme.colors.surfaceRaised, DayShape, pulse)) }
    }
}

/** Pulsing rows where the episodes will be, past the time column, fading down the list. */
@Composable
private fun LoadingRows() {
    val dimensions = NeoTheme.dimensions
    val fill = NeoTheme.colors.fillTonal
    val pulse = rememberPulse()
    Column(
        Modifier
            .testTag(SCHEDULE_LOADING_TAG)
            .padding(start = dimensions.gridMargin + TimeWidth + SlotGap, end = dimensions.gridMargin, top = RowsTop),
        verticalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        LoadingAlphas.forEach { alpha ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .placeholder(fill.copy(alpha = fill.alpha * alpha), RoundedCornerShape(dimensions.radiusLg), pulse),
            )
        }
    }
}

/** A note along the bottom that a show can't be opened. It goes after a few seconds. */
@Composable
private fun Toast(
    cantOpen: ScheduleEvent.CantOpen,
    onShown: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    LaunchedEffect(cantOpen) {
        delay(TOAST_MILLIS)
        onShown()
    }
    Text(
        stringResource(if (cantOpen.tryAgain) R.string.schedule_cant_open_retry else R.string.schedule_cant_open),
        style = NeoTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
        color = colors.ink,
        modifier =
            modifier
                .padding(bottom = ToastBottom)
                .shadow(24.dp, CircleShape)
                .background(colors.paper, CircleShape)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private fun Instant.time(context: android.content.Context): String = DateFormat.getTimeFormat(context).format(Date(toEpochMilliseconds()))

private fun Instant.format(pattern: String): String = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(toEpochMilliseconds()))

private fun AirDate.format(pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(
        Calendar.getInstance().apply { set(year, month - 1, day, 12, 0, 0) }.time,
    )
