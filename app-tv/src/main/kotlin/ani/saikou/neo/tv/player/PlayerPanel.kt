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
import ani.saikou.neo.shared.settings.LookRow
import ani.saikou.neo.shared.settings.lookRow
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.details.editionName
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoTab
import kotlin.math.ceil

internal const val PLAYER_PANEL_TAG = "player-panel"
internal const val PLAYER_PANEL_TIMING_TAG = "player-panel-timing"

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

// Four rows show at once, and the rest scroll with focus.
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
 * a tab's lists and then between tabs, and Up from the top of a list goes to the tabs. The subtitle [looks] open over
 * the Audio and subtitles tab while [looksOpen], and Back goes from them to their row.
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
    looks: Looks,
    looksOpen: Boolean,
) {
    val colors = NeoTheme.colors
    var onTiming by remember { mutableStateOf(false) }
    val picked = remember { FocusRequester() }
    val currentTab = remember { FocusRequester() }
    val lookRow = remember { FocusRequester() }
    // Set when focus should land on the next tab's list, not stay on the tabs.
    var enter by remember { mutableStateOf(true) }
    // Set when Back leaves the looks, so focus goes back to their row.
    var fromLooks by remember { mutableStateOf(false) }
    var lookFocus by remember { mutableStateOf<LookFocus?>(null) }
    LaunchedEffect(tab, looksOpen) {
        when {
            looksOpen || enter -> picked.requestFocus()
            fromLooks -> lookRow.requestFocus()
        }
        enter = false
        fromLooks = false
    }
    BackHandler(enabled = looksOpen) {
        fromLooks = true
        controls.closeLooks()
    }

    fun move(by: Int) {
        val next = PanelTab.entries.getOrNull(tab.ordinal + by) ?: return
        enter = true
        controls.openPanel(next)
    }

    // Without the scrim while the looks are open, so subtitles show in their true colours.
    if (!looksOpen) Box(Modifier.matchParentSize().background(NeoTheme.colors.scrimPlayer.copy(alpha = PANEL_SCRIM)))
    Column(
        Modifier
            .align(Alignment.TopEnd)
            .width(PanelWidth)
            .fillMaxHeight()
            // Solid, so a bright scene can't show through and wash out the rows (#359).
            .background(colors.ink)
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
        when {
            looksOpen -> {
                LookPage(looks, picked, currentTab, { lookFocus = it }, Modifier.weight(1f))
            }

            tab == PanelTab.Tracks -> {
                val apart =
                    answered.audio.mapIndexed { index, name ->
                        Choice(name, index == state.pickedAudio) { actions.onAudio(index, playback.positionMs) }
                    }
                Tracks(apart, playback, picked, currentTab, ::move)
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                val row = lookRow(playback.subtitles.any { it.selected }, state.server.server.edition)
                Column(verticalArrangement = Arrangement.spacedBy(ChoiceGap)) {
                    SubtitleLookRow(row, looks.subtitles.pick.look, controls::openLooks, Modifier.focusRequester(lookRow))
                    if (row == LookRow.BURNED) {
                        TwoLineRow(
                            stringResource(R.string.player_panel_timing),
                            stringResource(R.string.player_timing_burned),
                            enabled = false,
                            onClick = {},
                        )
                    } else {
                        SubtitleTiming(playback) { onTiming = it }
                    }
                }
            }

            tab == PanelTab.Quality -> {
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

            tab == PanelTab.Server -> {
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

            else -> {
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
        if (!looksOpen) {
            Text(
                stringResource(if (onTiming && tab == PanelTab.Tracks) R.string.player_panel_timing_hint else R.string.player_panel_hint),
                style = NeoTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colors.textFaint,
            )
        }
    }
    if (looksOpen) LookKeys(lookFocus, looks.subtitles.pick.look)
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
    val colors = NeoTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocused(focused) }
    val label = stringResource(R.string.player_panel_timing)
    val value = formatSubtitleDelay(playback.subtitleDelayMs)
    Surface(
        onClick = { playback.changeSubtitleDelay(0) },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(ChoiceHeight)
                .onKeyEvent { event ->
                    if (event.key != Key.DirectionLeft && event.key != Key.DirectionRight) return@onKeyEvent false
                    if (event.type == KeyEventType.KeyDown) {
                        playback.changeSubtitleDelay(nextSubtitleDelay(playback.subtitleDelayMs, later = event.key == Key.DirectionRight))
                    }
                    true
                }.semantics { stateDescription = value }
                .testTag(PLAYER_PANEL_TIMING_TAG),
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
        Row(Modifier.fillMaxHeight().padding(horizontal = ChoicePadding), verticalAlignment = Alignment.CenterVertically) {
            val style = NeoTheme.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(label, style = style, modifier = Modifier.weight(1f))
            Text(value, style = style.copy(fontFeatureSettings = "tnum"), color = if (focused) colors.onFocusMuted else colors.textMuted)
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
                        .background(Brush.verticalGradient(listOf(colors.ink.copy(alpha = 0f), colors.ink))),
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

                    // As bright as muted text, so the ring reads on a TV too (#359).
                    else -> colors.textMuted
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
