package ani.saikou.neo.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.shared.playback.ControlsViewModel
import ani.saikou.neo.shared.playback.FoundStream
import ani.saikou.neo.shared.playback.PLAYBACK_SPEEDS
import ani.saikou.neo.shared.playback.PanelTab
import ani.saikou.neo.shared.playback.PlayRequest
import ani.saikou.neo.shared.playback.PlayerState
import ani.saikou.neo.shared.playback.formatSpeed
import ani.saikou.neo.shared.playback.formatSubtitleDelay
import ani.saikou.neo.shared.playback.nextSubtitleDelay
import ani.saikou.neo.shared.settings.StyleBlock
import ani.saikou.neo.shared.settings.SubtitleColour
import ani.saikou.neo.shared.settings.SubtitleEdge
import ani.saikou.neo.shared.settings.SubtitlePosition
import ani.saikou.neo.shared.settings.SubtitleSize
import ani.saikou.neo.shared.settings.SubtitleStyle
import ani.saikou.neo.shared.settings.SubtitleStylePart
import ani.saikou.neo.shared.settings.styleBlock
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.details.editionName
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoTab
import kotlin.math.ceil

internal const val PLAYER_PANEL_TAG = "player-panel"
internal const val PLAYER_PANEL_TIMING_TAG = "player-panel-timing"
internal const val PLAYER_PANEL_STYLE_TAG = "player-panel-style"

internal fun playerPanelStyleTag(part: SubtitleStylePart) = "player-panel-style-$part"

/** The subtitle style, and what moves its [part] to the next choice or, when not forward, the one before. */
internal data class SubtitleStyling(
    val style: SubtitleStyle = SubtitleStyle(),
    val step: (part: SubtitleStylePart, forward: Boolean) -> Unit = { _, _ -> },
)

internal fun playerPanelTabTag(tab: PanelTab) = "player-panel-$tab"

// From the design.
private val PanelWidth = 480.dp
private val PanelSide = 24.dp
private val PanelEdge = 27.dp
private val PanelGap = 16.dp
private val TabHeight = 36.dp
private val TabPadding = 14.dp
private val TabGap = 4.dp
private val ChoiceHeight = 44.dp
private val ChoiceGap = 6.dp
private val ChoicePadding = 14.dp
private val ChoiceRadius = 12.dp
private val ChoiceGlow = 18.dp
private val Dot = 14.dp
private val DotPicked = 4.dp
private val DotPlain = 2.dp
private val FadeHeight = 48.dp
private val FocusGlowColor = Color.White.copy(alpha = 0.3f)
private const val CHOICE_SCALE = 1.03f
private const val PANEL_SCRIM = 0.35f
private const val PANEL_FILL = 0.94f

// Four rows show at once, leaving room below for the timing and style rows, and the rest scroll with focus.
private const val ROWS_SHOWN = 4
private const val ROWS_SHOWN_ALONE = 7

/** One row of a list: picking it takes effect at once, and the panel stays open. */
private class Choice(
    val text: String,
    val picked: Boolean,
    val pick: () -> Unit,
)

/**
 * The side panel, over the video as it keeps playing. Focus starts on what's picked in [tab]. Left and Right move between
 * a tab's lists and then between tabs, and Up from the top of a list goes to the tabs. Subtitle size and style opens in
 * place of the tracks, and Back goes back to them.
 */
@Composable
internal fun BoxScope.PlayerPanel(
    tab: PanelTab,
    state: PlayerState<Stream>,
    answered: FoundStream<Stream>,
    request: PlayRequest,
    actions: PlayerActions,
    playback: Playback,
    controls: ControlsViewModel,
    styling: SubtitleStyling,
) {
    val colors = NeoTheme.colors
    var onTiming by remember { mutableStateOf(false) }
    var styleOpen by remember { mutableStateOf(false) }
    var backToStyle by remember { mutableStateOf(false) }
    val styleRow = remember { FocusRequester() }
    val picked = remember { FocusRequester() }
    val currentTab = remember { FocusRequester() }
    // Set when focus should land on the next tab's list, not stay on the tabs.
    var enter by remember { mutableStateOf(true) }
    LaunchedEffect(tab) {
        if (tab != PanelTab.Tracks) styleOpen = false
        if (enter) picked.requestFocus()
        enter = false
    }
    LaunchedEffect(styleOpen) {
        if (styleOpen) {
            picked.requestFocus()
        } else if (backToStyle) {
            styleRow.requestFocus()
        }
        backToStyle = false
    }
    BackHandler(enabled = styleOpen) {
        backToStyle = true
        styleOpen = false
    }

    fun move(by: Int) {
        val next = PanelTab.entries.getOrNull(tab.ordinal + by) ?: return
        enter = true
        controls.openPanel(next)
    }

    Box(Modifier.matchParentSize().background(NeoTheme.colors.scrimPlayer.copy(alpha = PANEL_SCRIM)))
    Column(
        Modifier
            .align(Alignment.TopEnd)
            .width(PanelWidth)
            .fillMaxHeight()
            .background(colors.ink.copy(alpha = PANEL_FILL))
            .padding(horizontal = PanelSide, vertical = PanelEdge)
            .testTag(PLAYER_PANEL_TAG),
        verticalArrangement = Arrangement.spacedBy(PanelGap),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TabGap)) {
            for (each in PanelTab.entries) {
                NeoTab(
                    tabName(each),
                    each == tab,
                    { controls.openPanel(each) },
                    (if (each == tab) Modifier.focusRequester(currentTab) else Modifier)
                        .focusProperties { down = picked }
                        .onFocusChanged { if (it.isFocused && each != tab) controls.openPanel(each) }
                        .testTag(playerPanelTabTag(each)),
                    height = TabHeight,
                    padding = TabPadding,
                    fontSize = 14.sp,
                )
            }
        }
        when (tab) {
            PanelTab.Tracks if styleOpen -> {
                StyleRows(styling, picked)
            }

            PanelTab.Tracks -> {
                val apart =
                    answered.audio.mapIndexed { index, name ->
                        Choice(name, index == state.pickedAudio) { actions.onAudio(index, playback.positionMs) }
                    }
                Tracks(apart, playback, picked, currentTab, ::move)
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                SubtitleTiming(playback) { onTiming = it }
                val block = styleBlock(state.server.server.edition, playback.subtitles.any { it.selected })
                PanelRow(
                    stringResource(R.string.player_panel_style),
                    value = null,
                    onClick = { styleOpen = block == null },
                    modifier = Modifier.focusRequester(styleRow).testTag(PLAYER_PANEL_STYLE_TAG),
                    note =
                        when (block) {
                            StyleBlock.SUBTITLES_OFF -> stringResource(R.string.player_panel_style_off)
                            StyleBlock.BURNED_IN -> stringResource(R.string.player_panel_style_burned_in)
                            null -> null
                        },
                )
            }

            PanelTab.Quality -> {
                val height = state.height(answered)
                OneList(
                    answered.heights.map { each ->
                        Choice(
                            stringResource(R.string.player_quality, each),
                            each == height,
                        ) { actions.onQuality(each, playback.positionMs) }
                    },
                    picked,
                    currentTab,
                    ::move,
                )
            }

            PanelTab.Server -> {
                OneList(
                    request.servers.map { each ->
                        val name =
                            stringResource(
                                R.string.player_panel_server,
                                each.source.name,
                                each.server.name,
                                editionName(each.server.edition),
                            )
                        Choice(name, each == state.server) { actions.onServer(each, playback.positionMs) }
                    },
                    picked,
                    currentTab,
                    ::move,
                )
            }

            PanelTab.Speed -> {
                OneList(
                    PLAYBACK_SPEEDS.map { speed ->
                        Choice(
                            stringResource(R.string.player_speed, formatSpeed(speed)),
                            speed == playback.speed,
                        ) { playback.changeSpeed(speed) }
                    },
                    picked,
                    currentTab,
                    ::move,
                )
            }
        }
        val hint =
            when {
                tab != PanelTab.Tracks -> R.string.player_panel_hint
                styleOpen -> R.string.player_panel_style_hint
                onTiming -> R.string.player_panel_timing_hint
                else -> R.string.player_panel_hint
            }
        Text(
            stringResource(hint),
            style = NeoTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = colors.textFaint,
        )
    }
}

@Composable
private fun tabName(tab: PanelTab) =
    stringResource(
        when (tab) {
            PanelTab.Tracks -> R.string.player_audio_subtitles
            PanelTab.Quality -> R.string.player_panel_quality
            PanelTab.Server -> R.string.player_panel_server_tab
            PanelTab.Speed -> R.string.player_panel_speed
        },
    )

/** Audio and subtitles, side by side. Focus starts on the picked subtitle, or Off. */
@Composable
private fun Tracks(
    apartAudio: List<Choice>,
    playback: Playback,
    picked: FocusRequester,
    tabs: FocusRequester,
    move: (Int) -> Unit,
) {
    // Audio served apart from the video is the source's to list. The player only knows the one playing.
    val audio = apartAudio.ifEmpty { playback.audio.mapIndexed { index, track -> track.choice(index) { playback.selectTrack(track) } } }
    val subtitles = playback.subtitles
    Row(horizontalArrangement = Arrangement.spacedBy(PanelGap)) {
        ChoiceList(
            stringResource(R.string.player_panel_audio, audio.size),
            audio,
            focus = null,
            up = tabs,
            height = rowsHeight(ROWS_SHOWN),
            modifier = Modifier.weight(1f).sideKeys(left = { move(-1) }, right = null),
        )
        val off = Choice(stringResource(R.string.player_panel_off), subtitles.none { it.selected }, playback::subtitlesOff)
        ChoiceList(
            stringResource(R.string.player_panel_subtitles, subtitles.size + 1),
            listOf(off) + subtitles.mapIndexed { index, track -> track.choice(index) { playback.selectTrack(track) } },
            focus = picked,
            up = tabs,
            height = rowsHeight(ROWS_SHOWN),
            modifier = Modifier.weight(1f).sideKeys(left = if (audio.isEmpty()) ({ move(-1) }) else null, right = { move(1) }),
        )
    }
}

/** Left and Right move subtitles earlier and later, held to go on moving, and OK puts them back. */
@Composable
private fun SubtitleTiming(
    playback: Playback,
    onFocused: (Boolean) -> Unit,
) {
    PanelRow(
        stringResource(R.string.player_panel_timing),
        formatSubtitleDelay(playback.subtitleDelayMs),
        onClick = { playback.changeSubtitleDelay(0) },
        modifier = Modifier.testTag(PLAYER_PANEL_TIMING_TAG),
        onFocused = onFocused,
        onSide = { later -> playback.changeSubtitleDelay(nextSubtitleDelay(playback.subtitleDelayMs, later)) },
    )
}

/** Size, colour, edge and position, where Left and Right change each one at once. The first row takes [first]. */
@Composable
private fun StyleRows(
    styling: SubtitleStyling,
    first: FocusRequester,
) {
    val title = NeoTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.player_panel_style), style = title, color = NeoTheme.colors.textFaint)
    Column(verticalArrangement = Arrangement.spacedBy(ChoiceGap)) {
        for (part in SubtitleStylePart.entries) {
            PanelRow(
                stringResource(part.label()),
                stringResource(styling.style.choice(part)),
                onClick = {},
                modifier = (if (part.ordinal == 0) Modifier.focusRequester(first) else Modifier).testTag(playerPanelStyleTag(part)),
                onSide = { later -> styling.step(part, later) },
            )
        }
    }
}

private fun SubtitleStylePart.label() =
    when (this) {
        SubtitleStylePart.SIZE -> R.string.player_style_size
        SubtitleStylePart.COLOUR -> R.string.player_style_colour
        SubtitleStylePart.EDGE -> R.string.player_style_edge
        SubtitleStylePart.POSITION -> R.string.player_style_position
    }

/** The name of the choice this style has for [part]. */
private fun SubtitleStyle.choice(part: SubtitleStylePart) =
    when (part) {
        SubtitleStylePart.SIZE -> {
            when (size) {
                SubtitleSize.TV -> R.string.player_style_tv
                SubtitleSize.SMALL -> R.string.player_style_small
                SubtitleSize.MEDIUM -> R.string.player_style_medium
                SubtitleSize.LARGE -> R.string.player_style_large
                SubtitleSize.EXTRA_LARGE -> R.string.player_style_extra_large
            }
        }

        SubtitleStylePart.COLOUR -> {
            when (colour) {
                SubtitleColour.TV -> R.string.player_style_tv
                SubtitleColour.WHITE -> R.string.player_style_white
                SubtitleColour.YELLOW -> R.string.player_style_yellow
                SubtitleColour.CYAN -> R.string.player_style_cyan
            }
        }

        SubtitleStylePart.EDGE -> {
            when (edge) {
                SubtitleEdge.TV -> R.string.player_style_tv
                SubtitleEdge.OUTLINE -> R.string.player_style_outline
                SubtitleEdge.SHADOW -> R.string.player_style_shadow
                SubtitleEdge.BACKGROUND -> R.string.player_style_background
            }
        }

        SubtitleStylePart.POSITION -> {
            when (position) {
                SubtitlePosition.BOTTOM -> R.string.player_style_bottom
                SubtitlePosition.RAISED -> R.string.player_style_raised
                SubtitlePosition.HIGH -> R.string.player_style_high
            }
        }
    }

/**
 * A row with a [value] on the right, where [onSide] takes Left and Right, held or not, when given. A [note] under the
 * greyed [label] says why the row can't do anything now, and the row still takes focus so it can be read.
 */
@Composable
private fun PanelRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    onFocused: (Boolean) -> Unit = {},
    onSide: ((later: Boolean) -> Unit)? = null,
) {
    val colors = NeoTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocused(focused) }
    val said = value ?: note
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .height(ChoiceHeight)
                .onKeyEvent { event ->
                    if (onSide == null || (event.key != Key.DirectionLeft && event.key != Key.DirectionRight)) return@onKeyEvent false
                    if (event.type == KeyEventType.KeyDown) onSide(event.key == Key.DirectionRight)
                    true
                }.semantics { if (said != null) stateDescription = said },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(ChoiceRadius)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.surface,
                contentColor = if (note != null) colors.textFaint else colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = CHOICE_SCALE),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, ChoiceGlow)),
        interactionSource = interactions,
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = ChoicePadding), verticalAlignment = Alignment.CenterVertically) {
            val style = NeoTheme.typography.label.copy(fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
            val muted = if (focused) colors.onFocusMuted else colors.textMuted
            Column(Modifier.weight(1f)) {
                Text(label, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
                note?.let { Text(it, style = NeoTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 15.sp), color = muted) }
            }
            value?.let { Text(it, style = style.copy(fontFeatureSettings = "tnum"), color = muted) }
        }
    }
}

@Composable
private fun TrackOption.choice(
    index: Int,
    pick: () -> Unit,
) = Choice(label ?: stringResource(R.string.player_panel_track, index + 1), selected, pick)

/** A tab with one list, the panel's width, where Left and Right only move between tabs. */
@Composable
private fun OneList(
    choices: List<Choice>,
    picked: FocusRequester,
    tabs: FocusRequester,
    move: (Int) -> Unit,
) {
    ChoiceList(
        null,
        choices,
        focus = picked,
        up = tabs,
        height = rowsHeight(choices.size.coerceAtMost(ROWS_SHOWN_ALONE)),
        modifier = Modifier.sideKeys(left = { move(-1) }, right = { move(1) }),
    )
}

private fun rowsHeight(rows: Int) = ChoiceHeight * rows + ChoiceGap * (rows - 1)

/** Runs [left] or [right] for those keys, leaving the other directions to focus. */
private fun Modifier.sideKeys(
    left: (() -> Unit)?,
    right: (() -> Unit)?,
) = onKeyEvent { event ->
    val action =
        when (event.key) {
            Key.DirectionLeft -> left
            Key.DirectionRight -> right
            else -> null
        } ?: return@onKeyEvent false
    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) action()
    true
}

/** A list with a [title], where the picked row takes [focus] if given and the top row's Up goes to [up]. */
@Composable
private fun ChoiceList(
    title: String?,
    choices: List<Choice>,
    focus: FocusRequester?,
    up: FocusRequester,
    height: Dp,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val style = NeoTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp)
    val scroll = rememberScrollState()
    val step = with(LocalDensity.current) { (ChoiceHeight + ChoiceGap).toPx() }
    val total = choices.size
    val below by remember(step, total) {
        derivedStateOf { ceil((scroll.maxValue - scroll.value) / step).toInt().coerceIn(0, total) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ChoiceGap)) {
        title?.let { Text(it, style = style.copy(fontWeight = FontWeight.Bold), color = colors.textFaint) }
        Box(Modifier.height(height)) {
            Column(Modifier.fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(ChoiceGap)) {
                choices.forEachIndexed { index, choice ->
                    ChoiceRow(
                        choice,
                        (if (focus != null && choice.picked) Modifier.focusRequester(focus) else Modifier)
                            .focusProperties { if (index == 0) this.up = up },
                    )
                }
            }
            if (below > 0) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(FadeHeight)
                        .background(Brush.verticalGradient(listOf(colors.ink.copy(alpha = 0f), colors.ink.copy(alpha = PANEL_FILL)))),
                )
            }
        }
        Text(
            if (below >
                0
            ) {
                pluralStringResource(R.plurals.player_panel_more, below, below)
            } else {
                ""
            },
            style = style,
            color = colors.textMuted,
        )
    }
}

/** A row with a radio dot. The dot says what's picked, so it doesn't rest on colour alone. */
@Composable
private fun ChoiceRow(
    choice: Choice,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Surface(
        onClick = choice.pick,
        modifier =
            modifier.fillMaxWidth().height(ChoiceHeight).semantics {
                role = Role.RadioButton
                selected = choice.picked
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(ChoiceRadius)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.surface,
                contentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = CHOICE_SCALE),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, ChoiceGlow)),
        interactionSource = interactions,
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = ChoicePadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ring =
                when {
                    choice.picked && focused -> colors.onFocus
                    choice.picked -> colors.pink
                    focused -> colors.onFocusMuted
                    else -> colors.outline
                }
            Box(Modifier.size(Dot).border(if (choice.picked) DotPicked else DotPlain, ring, CircleShape))
            Text(
                choice.text,
                style = NeoTheme.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
