package ani.saikou.neo.tv.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.settings.BuiltInLook
import ani.saikou.neo.shared.settings.LookId
import ani.saikou.neo.shared.settings.LookRow
import ani.saikou.neo.shared.settings.Placement
import ani.saikou.neo.shared.settings.PlayerSubtitles
import ani.saikou.neo.shared.settings.SubtitlePosition
import ani.saikou.neo.shared.settings.SubtitleSettings
import ani.saikou.neo.shared.settings.SubtitleSize
import ani.saikou.neo.shared.settings.drawingFor
import ani.saikou.neo.shared.settings.toPaint
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoIcons

internal const val PLAYER_PANEL_LOOK_TAG = "player-panel-look"
internal const val PLAYER_LOOK_SIZE_TAG = "player-look-size"
internal const val PLAYER_LOOK_POSITION_TAG = "player-look-position"
internal const val PLAYER_LOOK_MINE_TAG = "player-look-mine"

internal fun lookTileTag(id: LookId) = "player-look-$id"

/** What the subtitle looks tell their ViewModel. */
internal data class LookActions(
    val preview: (LookId?) -> Unit = {},
    val keep: () -> Unit = {},
    val changeSize: (by: Int) -> Unit = {},
    val changePosition: (by: Int) -> Unit = {},
    val useMyLook: (Boolean) -> Unit = {},
)

/** The subtitles for the player: how they're set now, and what changes them. */
internal data class Looks(
    val subtitles: PlayerSubtitles = SubtitleSettings().let { PlayerSubtitles(it, it.pickFor(0), null, false, false, it.drawingFor(0)) },
    val actions: LookActions = LookActions(),
)

/** What has focus on the look page, for the keys shown on the video. */
internal sealed interface LookFocus {
    data class Tile(
        val look: LookId,
    ) : LookFocus

    data object Size : LookFocus

    data object Position : LookFocus

    data object Other : LookFocus
}

// From the design.
private val RowRadius = 12.dp
private val TwoLineHeight = 56.dp
private val SwitchHeight = 44.dp
private val TileGap = 8.dp
private val TileRadius = 12.dp
private val Swatch = 48.dp
private val SwatchStyled = 40.dp
private val NameHeight = 26.dp
private val TileRing = 3.dp
private val TileGlow = 24.dp
private val StepperHeight = 48.dp
private val StepperPad = 4.dp
private val CellRadius = 9.dp
private val CellLine = 2.dp
private val HintLeft = 48.dp
private val HintBottom = 20.dp
private val SwatchColors = listOf(Color(0xFF1C2C3A), Color(0xFF5F4BB6), Color(0xFF9C6ADE))
private val NoteInk = Color(0xFFFFE3B0)
private val FocusGlowColor = Color.White.copy(alpha = 0.35f)
private const val TILE_SCALE = 1.06f
private const val GREYED = 0.35f
private const val NOTE_FILL = 0.14f
private const val HINT_ALPHA = 0.85f
private const val GREY_FILL = 0.6f

// "Aa" in each tile: text 22 px tall in a 48 px swatch, centred.
private const val SWATCH_TEXT = 0.46f
private const val SWATCH_GAP = 0.2f
private const val SAMPLE_GLYPH_SP = 13f
private const val PERCENT = 100f

@Composable
internal fun lookName(id: LookId): String =
    when (id) {
        is LookId.Mine -> {
            stringResource(R.string.subtitle_look_mine, id.number)
        }

        is LookId.BuiltIn -> {
            stringResource(
                when (id.look) {
                    BuiltInLook.CLASSIC -> R.string.subtitle_look_classic
                    BuiltInLook.SOFT_SHADOW -> R.string.subtitle_look_soft_shadow
                    BuiltInLook.BOXED -> R.string.subtitle_look_boxed
                    BuiltInLook.YELLOW -> R.string.subtitle_look_yellow
                    BuiltInLook.HIGH_CONTRAST -> R.string.subtitle_look_high_contrast
                    BuiltInLook.MATCH_TV -> R.string.subtitle_look_match_tv
                },
            )
        }
    }

/**
 * A row with a [title] and a [detail] under it. Greyed out, it can't be [enabled] or focused, and the detail says why.
 */
@Composable
internal fun TwoLineRow(
    title: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(TwoLineHeight).focusProperties { canFocus = enabled },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(RowRadius)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.surface,
                contentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
                disabledContainerColor = colors.surface.copy(alpha = GREY_FILL),
                disabledContentColor = colors.paper.copy(alpha = GREY_FILL),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, 18.dp)),
        interactionSource = interactions,
    ) {
        Column(
            Modifier.fillMaxHeight().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Text(title, style = NeoTheme.typography.label.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium))
            Text(
                detail,
                style = NeoTheme.typography.caption.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color =
                    when {
                        focused -> colors.onFocusMuted
                        enabled -> colors.textMuted
                        else -> colors.paper.copy(alpha = 0.78f)
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The Subtitle look row under the tracks: the look in use, or greyed out with the reason. */
@Composable
internal fun SubtitleLookRow(
    row: LookRow,
    look: LookId,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail =
        when (row) {
            LookRow.READY -> lookName(look)
            LookRow.OFF -> stringResource(R.string.player_look_off)
            LookRow.BURNED -> stringResource(R.string.player_look_burned)
        }
    TwoLineRow(stringResource(R.string.player_look), detail, row == LookRow.READY, onOpen, modifier.testTag(PLAYER_PANEL_LOOK_TAG))
}

/**
 * The looks, in place of the Audio and subtitles lists. A focused tile shows on the video, OK keeps it, and Left and
 * Right change the size and the position. Focus starts on [first]: the look in use, or Size when the track keeps its
 * own look. Up from the top goes to [tabs].
 */
@Composable
internal fun LookPage(
    looks: Looks,
    first: FocusRequester,
    tabs: FocusRequester,
    onFocus: (LookFocus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitles = looks.subtitles
    val actions = looks.actions
    val colors = NeoTheme.colors
    val preview by rememberUpdatedState(actions.preview)
    // However the page closes, the video goes back to the saved look.
    DisposableEffect(Unit) { onDispose { preview(null) } }
    val greyed = subtitles.keepsItsLook
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(26.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(NeoIcons.Chevron, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp).rotate(180f))
            Text(
                stringResource(R.string.player_look),
                style = NeoTheme.typography.title.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
            )
        }
        if (greyed) StyledNote()
        Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
            subtitles.settings.looks.chunked(LOOKS_A_ROW).forEachIndexed { index, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    row.forEach { id ->
                        LookTile(
                            id,
                            subtitles.settings,
                            inUse = id == subtitles.pick.look,
                            greyed = greyed,
                            (if (!greyed && id == subtitles.pick.look) Modifier.focusRequester(first) else Modifier)
                                .focusProperties { if (index == 0) up = tabs }
                                .weight(1f),
                            onFocused = {
                                actions.preview(id)
                                onFocus(LookFocus.Tile(id))
                            },
                            onClick = actions.keep,
                        )
                    }
                    repeat(LOOKS_A_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val size = subtitles.pick.size
        Stepper(
            stringResource(R.string.player_look_size),
            stringResource(R.string.player_look_percent, size.percent),
            PLAYER_LOOK_SIZE_TAG,
            actions.changeSize,
            (if (greyed) Modifier.focusRequester(first).focusProperties { up = tabs } else Modifier),
            onFocused = {
                actions.preview(null)
                onFocus(LookFocus.Size)
            },
        ) { focused ->
            SubtitleSize.entries.forEach { each ->
                Cell(each == size, focused, stringResource(R.string.player_look_percent, each.percent)) { color ->
                    Text(
                        "Aa",
                        style =
                            NeoTheme.typography.label.copy(
                                fontSize = (SAMPLE_GLYPH_SP * each.percent / PERCENT).sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        color = color,
                    )
                }
            }
        }
        if (!greyed) {
            val position = subtitles.pick.position
            Stepper(
                stringResource(R.string.player_look_position),
                null,
                PLAYER_LOOK_POSITION_TAG,
                actions.changePosition,
                Modifier,
                onFocused = {
                    actions.preview(null)
                    onFocus(LookFocus.Position)
                },
            ) { focused ->
                SubtitlePosition.entries.forEach { each ->
                    val name = positionName(each)
                    Cell(each == position, focused, name) { color ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PositionMark(each, color)
                            Text(
                                name,
                                style = NeoTheme.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                                color = color,
                            )
                        }
                    }
                }
            }
        }
        if (subtitles.styledTrack) {
            MyLookSwitch(subtitles.myLookOnTrack, { actions.useMyLook(!subtitles.myLookOnTrack) }) {
                actions.preview(null)
                onFocus(LookFocus.Other)
            }
        }
    }
}

private const val LOOKS_A_ROW = 3

@Composable
internal fun positionName(position: SubtitlePosition) =
    stringResource(
        when (position) {
            SubtitlePosition.BOTTOM -> R.string.player_look_bottom
            SubtitlePosition.RAISED -> R.string.player_look_raised
            SubtitlePosition.TOP -> R.string.player_look_top
        },
    )

@Composable
private fun StyledNote() {
    val colors = NeoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.warning.copy(alpha = NOTE_FILL), RoundedCornerShape(RowRadius))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(NeoIcons.Info, contentDescription = null, tint = colors.warning, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.player_look_styled),
            style = NeoTheme.typography.caption.copy(fontSize = 14.sp, lineHeight = 19.sp),
            color = NoteInk,
        )
    }
}

/**
 * A look's name over "Aa" drawn in it, by the same view that draws the subtitles, ticked when it's [inUse]. Greyed, it
 * can't be focused. The swatch is [swatch] tall, or [SwatchStyled] when greyed, and the name [nameHeight].
 */
@Composable
internal fun LookTile(
    id: LookId,
    settings: SubtitleSettings,
    inUse: Boolean,
    greyed: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    swatch: Dp = Swatch,
    nameHeight: Dp = NameHeight,
) {
    val colors = NeoTheme.colors
    val paint = settings.style(id)?.toPaint() ?: return
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val shape = RoundedCornerShape(TileRadius)
    Surface(
        onClick = onClick,
        enabled = !greyed,
        modifier =
            modifier
                .focusProperties { canFocus = !greyed }
                .onFocusChanged { if (it.isFocused) onFocused() }
                .alpha(if (greyed) GREYED else 1f)
                .testTag(lookTileTag(id))
                .semantics {
                    role = Role.RadioButton
                    selected = inUse
                },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.surface,
                contentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
                disabledContainerColor = colors.surface,
                disabledContentColor = colors.paper,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TILE_SCALE),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(TileRing, colors.focus), shape = shape)),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, TileGlow)),
        interactionSource = interactions,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(if (greyed) SwatchStyled else swatch).background(Brush.linearGradient(SwatchColors))) {
                AndroidView(
                    factory = { LookSubtitles(it) },
                    update = {
                        it.look(paint, SWATCH_TEXT, Placement(fromTop = false, fraction = SWATCH_GAP))
                        it.show(listOf("Aa"))
                    },
                    modifier = Modifier.matchParentSize(),
                )
            }
            Row(
                Modifier.fillMaxWidth().height(nameHeight).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    lookName(id),
                    style = NeoTheme.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (inUse) {
                    Icon(
                        NeoIcons.Tick,
                        contentDescription = stringResource(R.string.player_look_in_use),
                        tint = if (focused) colors.pinkDeep else colors.pink,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** A row of [cells] under a [title], where Left and Right [step] the pick and the row takes focus as one. */
@Composable
private fun Stepper(
    title: String,
    value: String?,
    tag: String,
    step: (Int) -> Unit,
    modifier: Modifier,
    onFocused: () -> Unit,
    cells: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    val colors = NeoTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val shape = RoundedCornerShape(RowRadius)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            val style = NeoTheme.typography.caption.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            Text(title, style = style, color = colors.textMuted, modifier = Modifier.weight(1f))
            value?.let { Text(it, style = style.copy(fontFeatureSettings = "tnum"), color = colors.paper) }
        }
        Surface(
            onClick = {},
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(StepperHeight)
                    .onFocusChanged { if (it.isFocused) onFocused() }
                    .onKeyEvent { event ->
                        val by =
                            when (event.key) {
                                Key.DirectionLeft -> -1
                                Key.DirectionRight -> 1
                                else -> return@onKeyEvent false
                            }
                        if (event.type == KeyEventType.KeyDown) step(by)
                        true
                    }.semantics { value?.let { stateDescription = it } }
                    .testTag(tag),
            shape = ClickableSurfaceDefaults.shape(shape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = colors.surface,
                    contentColor = colors.paper,
                    focusedContainerColor = colors.surface,
                    focusedContentColor = colors.paper,
                    pressedContainerColor = colors.surface,
                    pressedContentColor = colors.paper,
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(TileRing, colors.focus), shape = shape)),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, 20.dp)),
            interactionSource = interactions,
        ) {
            Row(Modifier.fillMaxHeight().padding(StepperPad), horizontalArrangement = Arrangement.spacedBy(StepperPad)) { cells(focused) }
        }
    }
}

/** One choice in a [Stepper]: white while its row has focus, and underlined in pink while it doesn't. */
@Composable
private fun RowScope.Cell(
    picked: Boolean,
    rowFocused: Boolean,
    name: String,
    content: @Composable (Color) -> Unit,
) {
    val colors = NeoTheme.colors
    val shape = RoundedCornerShape(CellRadius)
    val fill =
        when {
            picked && rowFocused -> colors.focus
            picked -> colors.fillTonal
            else -> Color.Transparent
        }
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(shape)
            .background(fill)
            .drawBehind {
                if (picked && !rowFocused) {
                    val line = CellLine.toPx()
                    drawRect(colors.pink, Offset(0f, size.height - line), Size(size.width, line))
                }
            }.semantics {
                selected = picked
                stateDescription = name
            },
        contentAlignment = Alignment.Center,
    ) {
        content(
            when {
                picked && rowFocused -> colors.onFocus
                picked -> colors.paper
                else -> colors.textMuted
            },
        )
    }
}

/** A screen with a line where subtitles sit at [position]. */
@Composable
private fun PositionMark(
    position: SubtitlePosition,
    color: Color,
) {
    Box(Modifier.width(28.dp).height(16.dp).border(1.5.dp, color, RoundedCornerShape(3.dp))) {
        val (align, gap) =
            when (position) {
                SubtitlePosition.BOTTOM -> Alignment.BottomCenter to (-2).dp
                SubtitlePosition.RAISED -> Alignment.BottomCenter to (-5).dp
                SubtitlePosition.TOP -> Alignment.TopCenter to 2.dp
            }
        Box(
            Modifier
                .align(align)
                .offset(y = gap)
                .padding(horizontal = 5.dp)
                .fillMaxWidth()
                .height(2.5.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
    }
}

/** Use my look on this track, for a styled track, with what it does under it. */
@Composable
private fun MyLookSwitch(
    on: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    val colors = NeoTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(SwitchHeight)
                    .onFocusChanged { if (it.isFocused) onFocused() }
                    .semantics {
                        role = Role.Switch
                        toggleableState = ToggleableState(on)
                    }.testTag(PLAYER_LOOK_MINE_TAG),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(RowRadius)),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = colors.surface,
                    contentColor = colors.paper,
                    focusedContainerColor = colors.focus,
                    focusedContentColor = colors.onFocus,
                    pressedContainerColor = colors.focus,
                    pressedContentColor = colors.onFocus,
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, 18.dp)),
            interactionSource = interactions,
        ) {
            Row(Modifier.fillMaxHeight().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.player_look_mine),
                    style = NeoTheme.typography.label.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f),
                )
                SwitchMark(on, if (focused) colors.onFocus else colors.paper)
            }
        }
        Text(
            stringResource(R.string.player_look_mine_help),
            style = NeoTheme.typography.caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = colors.textMuted,
        )
    }
}

@Composable
private fun SwitchMark(
    on: Boolean,
    color: Color,
) {
    val colors = NeoTheme.colors
    val track = if (on) colors.pink else Color.Transparent
    Box(
        Modifier
            .width(
                36.dp,
            ).height(20.dp)
            .background(track, CircleShape)
            .border(2.dp, if (on) colors.pink else color.copy(alpha = 0.6f), CircleShape),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(horizontal = 4.dp).size(12.dp).background(if (on) colors.paper else color.copy(alpha = 0.75f), CircleShape))
    }
}

/** The keys to press, on the video under the sample, for what has [focus] on the look page. */
@Composable
internal fun BoxScope.LookKeys(
    focus: LookFocus?,
    inUse: LookId,
) {
    val ok = stringResource(R.string.player_key_ok)
    val back = stringResource(R.string.player_key_back)
    val sides = stringResource(R.string.player_key_left_right)
    val close = back to stringResource(R.string.player_look_close)
    val keys =
        when {
            focus is LookFocus.Tile && focus.look != inUse -> {
                listOf(
                    ok to stringResource(R.string.player_look_use, lookName(focus.look)),
                    back to stringResource(R.string.player_look_keep, lookName(inUse)),
                )
            }

            focus == LookFocus.Size -> {
                listOf(sides to stringResource(R.string.player_look_change_size), close)
            }

            focus == LookFocus.Position -> {
                listOf(sides to stringResource(R.string.player_look_move), close)
            }

            else -> {
                listOf(close)
            }
        }
    val colors = NeoTheme.colors
    val style = NeoTheme.typography.caption.copy(fontSize = 13.sp, lineHeight = 18.sp)
    Row(
        Modifier.align(Alignment.BottomStart).padding(start = HintLeft, bottom = HintBottom),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        keys.forEach { (key, does) ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(key, style = style.copy(fontWeight = FontWeight.Bold), color = colors.paper)
                Text(does, style = style, color = colors.paper.copy(alpha = HINT_ALPHA))
            }
        }
    }
}
