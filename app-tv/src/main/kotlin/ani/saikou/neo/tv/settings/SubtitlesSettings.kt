package ani.saikou.neo.tv.settings

import android.view.accessibility.CaptioningManager
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.settings.BandColour
import ani.saikou.neo.shared.settings.BoxColour
import ani.saikou.neo.shared.settings.BuiltInLook
import ani.saikou.neo.shared.settings.Edge
import ani.saikou.neo.shared.settings.EdgeColour
import ani.saikou.neo.shared.settings.EdgeThickness
import ani.saikou.neo.shared.settings.LookId
import ani.saikou.neo.shared.settings.LookPart
import ani.saikou.neo.shared.settings.LookStyle
import ani.saikou.neo.shared.settings.StyledSubtitles
import ani.saikou.neo.shared.settings.SubtitleDrawing
import ani.saikou.neo.shared.settings.SubtitleFont
import ani.saikou.neo.shared.settings.SubtitleOption
import ani.saikou.neo.shared.settings.SubtitleSettings
import ani.saikou.neo.shared.settings.TextColour
import ani.saikou.neo.shared.settings.WhileWatching
import ani.saikou.neo.shared.settings.canChange
import ani.saikou.neo.shared.settings.drawing
import ani.saikou.neo.shared.settings.drawingFor
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.player.LookSubtitles
import ani.saikou.neo.tv.player.LookTile
import ani.saikou.neo.tv.player.lookLike
import ani.saikou.neo.tv.player.lookName
import ani.saikou.neo.tv.player.positionName
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoDialogButton
import ani.saikou.neo.tv.ui.NeoDialogButtons
import ani.saikou.neo.tv.ui.NeoIcons

internal const val SUBTITLES_ENTRY_TAG = "settings-subtitles"
internal const val SUBS_NEW_LOOK_TAG = "subs-new-look"
internal const val SUBS_HEAD_TAG = "subs-head-action"

internal fun subsRowTag(row: SubsRow) = "subs-${row.name.lowercase()}"

internal fun lookPartTag(part: LookPart) = "subs-part-${part.name.lowercase()}"

internal fun subsShowTag(id: Int) = "subs-show-$id"

/** What Settings > Subtitles tells its ViewModel. [addLook] returns the look it added, or null with three already. */
class SubtitleActions(
    val step: (SubtitleOption, by: Int) -> Unit = { _, _ -> },
    val use: (LookId) -> Unit = {},
    val addLook: () -> LookId? = { null },
    val edit: (LookId, LookPart, by: Int) -> Unit = { _, _, _ -> },
    val reset: (BuiltInLook) -> Unit = {},
    val delete: (number: Int) -> Unit = {},
    val clearShow: (showId: Int) -> Unit = {},
    val resetAll: () -> Unit = {},
)

/** The rows of the main page, top to bottom, with the option each one steps. */
internal enum class SubsRow(
    val option: SubtitleOption? = null,
) {
    LOOK,
    SIZE(SubtitleOption.SIZE),
    START_FROM_TV(SubtitleOption.START_FROM_TV),
    POSITION(SubtitleOption.POSITION),
    FINE_TUNE(SubtitleOption.FINE_TUNE),
    STYLED(SubtitleOption.STYLED),
    MY_SIZE(SubtitleOption.MY_SIZE_ON_STYLED),
    WHILE_WATCHING(SubtitleOption.WHILE_WATCHING),
    SHOWS,
    HDR(SubtitleOption.HDR),
    RESET,
}

private sealed interface Page {
    data class Main(
        val focus: SubsRow,
    ) : Page

    /** The tiles, with focus on [focus]. */
    data class Looks(
        val focus: LookId,
    ) : Page

    data class Editor(
        val look: LookId,
    ) : Page

    data object Shows : Page
}

/** What a side panel asks before it's done. */
private sealed interface Ask {
    data class ResetLook(
        val look: BuiltInLook,
    ) : Ask

    data class DeleteLook(
        val number: Int,
    ) : Ask

    data object ResetAll : Ask
}

/** The help under the preview for what has focus. */
private data class About(
    val title: String,
    val text: String,
)

/** What the preview draws: the look in use, another look, or a show's own look. */
private sealed interface Preview {
    data class Look(
        val look: LookId?,
    ) : Preview

    data class Show(
        val showId: Int,
    ) : Preview
}

// From the design: the rows are 420 dp wide from the margin, and the preview 402 by 250 dp, 510 dp in.
private val ListTop = 80.dp
private val ListWidth = 420.dp
private val PreviewLeft = 510.dp
private val PreviewWidth = 402.dp
private val PreviewHeight = 250.dp
private val RowHeight = 38.dp
private val EditorRowHeight = 34.dp
private val RowRadius = 12.dp
private val RowPadding = 14.dp
private val RowGap = 4.dp
private val TileHeight = 84.dp
private val TileSwatch = 56.dp
private val TileName = 28.dp
private val TileGap = 8.dp
private val Swatch = 18.dp
private val PanelWidth = 360.dp
private const val ROW_SCALE = 1.02f
private const val GREYED = 0.45f
private const val GREY_FILL = 0.6f
private const val LOOKS_A_ROW = 3
private val FocusGlow = Color.White.copy(alpha = 0.3f)
private val PreviewSky = listOf(Color(0xFF1C2C3A), Color(0xFF3B2A55), Color(0xFF6A2E4E))
private val PreviewGlow = Color(0xD9FFD68C)
private const val GLOW_X = 0.3f
private const val GLOW_Y = 0.92f
private const val GLOW_RADIUS = 0.4f

/**
 * Settings > Subtitles: the options with a preview at real size, the looks, the look editor and the shows with their
 * own look. Back goes up a page, and from the first page calls [onClose].
 */
@Composable
internal fun SubtitlesPage(
    settings: SubtitleSettings,
    actions: SubtitleActions,
    onClose: () -> Unit,
) {
    val colors = NeoTheme.colors
    var page by remember { mutableStateOf<Page>(Page.Main(SubsRow.LOOK)) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var about by remember { mutableStateOf<About?>(null) }
    var preview by remember { mutableStateOf<Preview>(Preview.Look(null)) }
    // Where focus goes back to when a question is answered No.
    val asker = remember { FocusRequester() }
    var answered by remember { mutableStateOf(false) }
    LaunchedEffect(answered) {
        if (answered) runCatching { asker.requestFocus() }
        answered = false
    }
    // A deleted look's editor goes back to the looks, and an empty list of shows back to the first page.
    LaunchedEffect(settings.myLooks.keys) {
        val editing = (page as? Page.Editor)?.look
        if (editing != null && settings.style(editing) == null) page = Page.Looks(settings.look)
    }
    LaunchedEffect(settings.shows.isEmpty()) {
        if (page == Page.Shows && settings.shows.isEmpty()) page = Page.Main(SubsRow.WHILE_WATCHING)
    }
    BackHandler(enabled = ask == null) {
        when (val now = page) {
            is Page.Main -> onClose()
            is Page.Looks -> page = Page.Main(SubsRow.LOOK)
            is Page.Editor -> page = Page.Looks(now.look)
            Page.Shows -> page = Page.Main(SubsRow.SHOWS)
        }
    }
    val askFrom = { question: Ask ->
        ask = question
    }
    Box(Modifier.fillMaxSize().background(colors.ink)) {
        val now = page
        val (crumb, title) =
            when (now) {
                is Page.Main -> stringResource(R.string.subs_crumb) to stringResource(R.string.settings_subtitles)
                is Page.Looks -> stringResource(R.string.subs_crumb_subtitles) to stringResource(R.string.subs_look)
                is Page.Editor -> stringResource(R.string.subs_crumb_look) to lookName(now.look)
                Page.Shows -> stringResource(R.string.subs_crumb_subtitles) to stringResource(R.string.subs_shows)
            }
        Column(
            Modifier.padding(start = NeoTheme.dimensions.gridMargin, top = 22.dp).width(ListWidth),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                crumb,
                style = NeoTheme.typography.caption.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textMuted,
            )
            Row(
                Modifier.height(32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = ScreenTitle,
                    color = colors.paper,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (now is Page.Editor) {
                    when (val look = now.look) {
                        is LookId.BuiltIn -> {
                            HeadAction(
                                stringResource(R.string.subs_reset_look),
                                NeoIcons.Reset,
                                asker,
                            ) { askFrom(Ask.ResetLook(look.look)) }
                        }

                        is LookId.Mine -> {
                            HeadAction(
                                stringResource(R.string.subs_delete_look),
                                NeoIcons.Remove,
                                asker,
                            ) { askFrom(Ask.DeleteLook(look.number)) }
                        }
                    }
                }
            }
        }
        val pane =
            Modifier
                .padding(start = NeoTheme.dimensions.gridMargin, top = ListTop, bottom = NeoTheme.dimensions.safeY)
                .width(ListWidth)
        val onAbout = { it: About -> about = it }
        when (now) {
            is Page.Main -> {
                MainPage(
                    settings,
                    actions,
                    first = now.focus,
                    open = { page = it },
                    onFocus = { text ->
                        about = text
                        preview = Preview.Look(null)
                    },
                    askResetAll = { askFrom(Ask.ResetAll) },
                    asker = asker,
                    modifier = pane,
                )
            }

            is Page.Looks -> {
                LooksPage(
                    settings,
                    first = now.focus,
                    onFocus = { look, text ->
                        about = text
                        preview = Preview.Look(look)
                    },
                    onPick = { look -> if (look == settings.look) page = Page.Editor(look) else actions.use(look) },
                    onNew = { actions.addLook()?.let { page = Page.Editor(it) } },
                    modifier = pane,
                )
            }

            is Page.Editor -> {
                val style = settings.style(now.look)
                if (style != null) {
                    EditorPage(
                        now.look,
                        style,
                        actions,
                        onFocus = {
                            onAbout(it)
                            preview = Preview.Look(now.look)
                        },
                        head = asker,
                        modifier = pane,
                    )
                }
            }

            Page.Shows -> {
                ShowsPage(
                    settings,
                    onFocus = { id, text ->
                        about = text
                        preview = Preview.Show(id)
                    },
                    onClear = actions.clearShow,
                    modifier = pane,
                )
            }
        }
        val drawing =
            when (val shown = preview) {
                is Preview.Look -> settings.drawing(shown.look)
                is Preview.Show -> settings.drawingFor(shown.showId)
            }
        PreviewPane(drawing, about, Modifier.padding(start = PreviewLeft, top = ListTop).width(PreviewWidth))
        SlidingPanel(ask) { question, active, motion ->
            val close = { yes: Boolean ->
                ask = null
                if (!yes || question !is Ask.DeleteLook) answered = true
            }
            AskPanel(question, settings, active, onYes = {
                when (question) {
                    is Ask.ResetLook -> actions.reset(question.look)
                    is Ask.DeleteLook -> actions.delete(question.number)
                    Ask.ResetAll -> actions.resetAll()
                }
                close(true)
            }, onNo = { close(false) }, modifier = motion)
        }
    }
}

/** The pill beside an editor's title: Reset this look or Delete this look. */
@Composable
private fun HeadAction(
    text: String,
    icon: ImageVector,
    requester: FocusRequester,
    onClick: () -> Unit,
) {
    val colors = NeoTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.testTag(SUBS_HEAD_TAG).height(32.dp).focusRequester(requester),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.fillTonal,
                contentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = ROW_SCALE),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlow, NeoTheme.dimensions.glowFocus)),
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = NeoTheme.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

/** The first page: every option in four groups, steps with Left and Right, and the pages they open. */
@Composable
private fun MainPage(
    settings: SubtitleSettings,
    actions: SubtitleActions,
    first: SubsRow,
    open: (Page) -> Unit,
    onFocus: (About) -> Unit,
    askResetAll: () -> Unit,
    asker: FocusRequester,
    modifier: Modifier,
) {
    val rows = remember { SubsRow.entries.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { runCatching { rows.getValue(first).requestFocus() } }
    val scroll = rememberScrollState()
    ScrolledList(scroll, stringResource(R.string.subs_more_above), stringResource(R.string.subs_more_below), modifier) {
        @Composable
        fun row(
            row: SubsRow,
            @StringRes name: Int,
            @StringRes about: Int,
            kind: Kind,
            enabled: Boolean = true,
        ) {
            val title = stringResource(name)
            val text = stringResource(about)
            OptionRow(
                title,
                subsRowTag(row),
                RowHeight,
                kind,
                onFocused = { onFocus(About(title, text)) },
                modifier =
                    Modifier
                        .focusRequester(rows.getValue(row))
                        .then(if (row == SubsRow.RESET) Modifier.focusRequester(asker) else Modifier),
                enabled = enabled,
                onClick = {
                    when (row) {
                        SubsRow.LOOK -> open(Page.Looks(settings.look))
                        SubsRow.SHOWS -> open(Page.Shows)
                        SubsRow.RESET -> askResetAll()
                        else -> if (kind is Kind.Switch) row.option?.let { actions.step(it, 1) }
                    }
                },
                onStep = if (kind is Kind.Step) row.option?.let { option -> { by: Int -> actions.step(option, by) } } else null,
            )
        }
        Group(R.string.subs_group_look, first = true)
        row(SubsRow.LOOK, R.string.subs_look, R.string.subs_look_about, Kind.Nav(lookName(settings.look)))
        row(
            SubsRow.SIZE,
            R.string.player_look_size,
            R.string.subs_size_about,
            Kind.Step(stringResource(R.string.player_look_percent, settings.size.percent)),
        )
        row(SubsRow.START_FROM_TV, R.string.subs_tv_size, R.string.subs_tv_size_about, Kind.Switch(settings.startFromTvTextSize))
        Group(R.string.subs_group_position)
        row(SubsRow.POSITION, R.string.player_look_position, R.string.subs_position_about, Kind.Step(positionName(settings.position)))
        row(
            SubsRow.FINE_TUNE,
            R.string.subs_fine_tune,
            R.string.subs_fine_tune_about,
            Kind.Step(stringResource(R.string.subs_fine_tune_value, settings.fineTunePercent)),
        )
        Group(R.string.subs_group_styled)
        val keep = settings.styledSubtitles == StyledSubtitles.KEEP_THEIR_LOOK
        val styled = if (keep) R.string.subs_styled_keep else R.string.subs_styled_mine
        row(SubsRow.STYLED, R.string.subs_styled, R.string.subs_styled_about, Kind.Step(stringResource(styled)))
        row(SubsRow.MY_SIZE, R.string.subs_my_size, R.string.subs_my_size_about, Kind.Switch(settings.mySizeOnStyled))
        Group(R.string.subs_group_more)
        val watching = if (settings.changesWhileWatching == WhileWatching.EVERY_SHOW) R.string.subs_every_show else R.string.subs_this_show
        row(SubsRow.WHILE_WATCHING, R.string.subs_while_watching, R.string.subs_while_watching_about, Kind.Step(stringResource(watching)))
        val shows = if (settings.shows.isEmpty()) stringResource(R.string.subs_none) else settings.shows.size.toString()
        row(SubsRow.SHOWS, R.string.subs_shows, R.string.subs_shows_about, Kind.Nav(shows), enabled = settings.shows.isNotEmpty())
        row(
            SubsRow.HDR,
            R.string.subs_hdr,
            R.string.subs_hdr_about,
            Kind.Step(stringResource(R.string.player_look_percent, settings.hdrBrightness.percent)),
        )
        row(SubsRow.RESET, R.string.subs_reset_all, R.string.subs_reset_all_about, Kind.Nav(""))
    }
}

/** Rows in a column that scrolls to the focused one, with a line above or below while there's more that way. */
@Composable
private fun ScrolledList(
    scroll: ScrollState,
    above: String?,
    below: String?,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hint = NeoTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(RowGap)) {
        if (above != null && scroll.canScrollBackward) Text(above, style = hint, color = NeoTheme.colors.textMuted)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(scroll).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(RowGap),
            content = content,
        )
        if (below != null && scroll.canScrollForward) Text(below, style = hint, color = NeoTheme.colors.textMuted)
    }
}

/** A group's name over its rows. */
@Composable
private fun Group(
    @StringRes name: Int,
    first: Boolean = false,
    tight: Boolean = false,
) {
    Text(
        stringResource(name).uppercase(),
        style =
            NeoTheme.typography.caption.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            ),
        color = NeoTheme.colors.textMuted,
        modifier =
            Modifier.padding(
                top =
                    if (first) {
                        0.dp
                    } else if (tight) {
                        4.dp
                    } else {
                        8.dp
                    },
                bottom = if (tight) 0.dp else 2.dp,
            ),
    )
}

/** What a row shows on its right. */
private sealed interface Kind {
    /** Left and Right step the value. */
    data class Step(
        val value: String,
    ) : Kind

    /** OK opens another page or asks. */
    data class Nav(
        val value: String,
    ) : Kind

    /** OK flips it. */
    data class Switch(
        val on: Boolean,
    ) : Kind

    /** Left and Right step through colours, with the chosen one named and ringed. */
    data class Swatches(
        val value: String,
        val colours: List<Pair<String, Color>>,
        val chosen: Int,
    ) : Kind

    /** Left and Right step through named choices, all in view. */
    data class Chips(
        val labels: List<String>,
        val chosen: Int,
    ) : Kind
}

private val Kind.steps get() = this is Kind.Step || this is Kind.Swatches || this is Kind.Chips

/** A row: its name on the left and its value, in the way [kind] shows it, on the right. Greyed out, it can't be focused. */
@Composable
private fun OptionRow(
    name: String,
    tag: String,
    height: Dp,
    kind: Kind,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    onStep: ((Int) -> Unit)? = null,
) {
    val colors = NeoTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .testTag(tag)
                .fillMaxWidth()
                .height(height)
                .focusProperties { canFocus = enabled }
                .onFocusChanged { if (it.isFocused) onFocused() }
                .onKeyEvent { event ->
                    val by =
                        when (event.key) {
                            Key.DirectionLeft -> -1
                            Key.DirectionRight -> 1
                            else -> return@onKeyEvent false
                        }
                    if (onStep == null) return@onKeyEvent false
                    if (event.type == KeyEventType.KeyDown) onStep(by)
                    true
                }.semantics {
                    when (kind) {
                        is Kind.Switch -> {
                            role = Role.Switch
                            toggleableState = ToggleableState(kind.on)
                        }

                        is Kind.Step -> {
                            stateDescription = kind.value
                        }

                        is Kind.Swatches -> {
                            stateDescription = kind.value
                        }

                        is Kind.Chips -> {
                            stateDescription = kind.labels[kind.chosen]
                        }

                        // Its value is in its text.
                        is Kind.Nav -> {}
                    }
                },
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
                disabledContentColor = colors.paper.copy(alpha = GREYED),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = ROW_SCALE),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlow, NeoTheme.dimensions.glowFocus)),
        interactionSource = interactions,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = RowPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                style =
                    NeoTheme.typography.label.copy(
                        fontSize = 15.sp,
                        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val accent =
                when {
                    focused -> colors.pinkDeep
                    !enabled -> colors.paper.copy(alpha = GREYED)
                    else -> colors.pink
                }
            RowValue(kind, focused, enabled, accent)
        }
    }
}

@Composable
private fun RowScope.RowValue(
    kind: Kind,
    focused: Boolean,
    enabled: Boolean,
    accent: Color,
) {
    val colors = NeoTheme.colors
    val style = NeoTheme.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    when (kind) {
        is Kind.Step -> {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(NeoIcons.Chevron, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp).rotate(180f))
                Text(kind.value, style = style, color = accent, maxLines = 1)
                Icon(NeoIcons.Chevron, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            }
        }

        is Kind.Nav -> {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (kind.value.isNotEmpty()) Text(kind.value, style = style, color = accent, maxLines = 1)
                Icon(NeoIcons.Chevron, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            }
        }

        is Kind.Switch -> {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (kind.on) R.string.subs_on else R.string.subs_off), style = style, color = accent)
                SwitchMark(kind.on, focused)
            }
        }

        is Kind.Swatches -> {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(kind.value, style = style, color = accent, modifier = Modifier.padding(end = 4.dp))
                kind.colours.forEachIndexed { index, (name, colour) ->
                    val chosen = index == kind.chosen
                    val label = if (chosen) stringResource(R.string.subs_chosen, name) else name
                    Box(
                        Modifier
                            .size(Swatch)
                            .then(
                                if (chosen) {
                                    Modifier.border(2.dp, accent, CircleShape).padding(3.dp)
                                } else {
                                    Modifier
                                },
                            ).background(colour.copy(alpha = if (enabled) 1f else GREYED), CircleShape)
                            .border(1.5.dp, (if (focused) colors.onFocus else colors.paper).copy(alpha = 0.4f), CircleShape)
                            .semantics { contentDescription = label },
                    )
                }
            }
        }

        is Kind.Chips -> {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                kind.labels.forEachIndexed { index, label ->
                    val on = index == kind.chosen
                    val (fill, ink) =
                        when {
                            focused && on -> {
                                colors.onFocus to colors.paper
                            }

                            focused -> {
                                Color.Transparent to colors.onFocusMuted
                            }

                            !enabled -> {
                                (if (on) colors.paper.copy(alpha = 0.08f) else Color.Transparent) to colors.paper.copy(alpha = GREYED)
                            }

                            on -> {
                                colors.fillTonal to colors.paper
                            }

                            else -> {
                                Color.Transparent to colors.textMuted
                            }
                        }
                    Box(
                        Modifier
                            .height(24.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = style.copy(fontSize = 12.sp), color = ink)
                    }
                }
            }
        }
    }
}

/** An on or off switch: a pink track with the knob right when on, an outline with it left when off. */
@Composable
private fun SwitchMark(
    on: Boolean,
    focused: Boolean,
) {
    val colors = NeoTheme.colors
    val off = if (focused) colors.onFocusMuted else colors.paper.copy(alpha = 0.6f)
    Box(
        Modifier
            .width(34.dp)
            .height(20.dp)
            .then(
                if (on) {
                    Modifier.background(if (focused) colors.onFocus else colors.pink, CircleShape)
                } else {
                    Modifier.border(2.dp, off, CircleShape)
                },
            ),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(horizontal = 4.dp).size(12.dp).background(if (on) colors.paper else off, CircleShape))
    }
}

/** The looks as tiles, three a row, then New look while there's room for one of your own. */
@Composable
private fun LooksPage(
    settings: SubtitleSettings,
    first: LookId,
    onFocus: (LookId?, About) -> Unit,
    onPick: (LookId) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val tiles = remember(settings.looks) { settings.looks.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { runCatching { tiles[first]?.requestFocus() } }
    val inUseText = stringResource(R.string.subs_tile_in_use)
    val builtInText = stringResource(R.string.subs_tile_built_in)
    val mineText = stringResource(R.string.subs_tile_mine)
    val newTitle = stringResource(R.string.subs_new_look)
    val newText = stringResource(R.string.subs_new_look_about)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val cells: List<LookId?> = settings.looks + if (settings.canAddLook) listOf(null) else emptyList()
        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(TileGap)) {
            cells.chunked(LOOKS_A_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    row.forEach { id ->
                        if (id == null) {
                            NewLookTile(onNew, { onFocus(null, About(newTitle, newText)) }, Modifier.weight(1f))
                        } else {
                            val name = lookName(id)
                            val text =
                                when {
                                    id == settings.look -> inUseText
                                    id is LookId.Mine -> mineText
                                    else -> builtInText
                                }
                            LookTile(
                                id,
                                settings,
                                inUse = id == settings.look,
                                greyed = false,
                                Modifier.weight(1f).focusRequester(tiles.getValue(id)),
                                onFocused = { onFocus(id, About(name, text)) },
                                onClick = { onPick(id) },
                                swatch = TileSwatch,
                                nameHeight = TileName,
                            )
                        }
                    }
                    repeat(LOOKS_A_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val keys = NeoTheme.typography.caption.copy(fontSize = 14.sp, lineHeight = 20.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            val pairs = listOf(R.string.subs_key_ok to R.string.subs_key_use, R.string.subs_key_ok_again to R.string.subs_key_edit)
            pairs.forEach { (key, does) ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(key), style = keys.copy(fontWeight = FontWeight.Bold), color = colors.paper)
                    Text(stringResource(does), style = keys, color = colors.paper.copy(alpha = 0.85f))
                }
            }
        }
        Text(newText, style = NeoTheme.typography.caption.copy(fontSize = 13.sp, lineHeight = 19.sp), color = colors.textMuted)
    }
}

/** New look: a dashed outline with a plus, the size of a look's tile. */
@Composable
private fun NewLookTile(
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.testTag(SUBS_NEW_LOOK_TAG).height(TileHeight).onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(RowRadius)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlow, 24.dp)),
    ) {
        val dash = colors.paper.copy(alpha = 0.45f)
        Column(
            Modifier.fillMaxSize().drawDashedOutline(dash),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(NeoIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.subs_new_look),
                style = NeoTheme.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

private fun Modifier.drawDashedOutline(color: Color) =
    drawBehind {
        val width = 2.dp.toPx()
        val radius = RowRadius.toPx()
        drawRoundRect(
            color,
            topLeft = Offset(width / 2, width / 2),
            size = Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
        )
    }

/** Every part of [look] in three groups. Up from the first row goes to Reset or Delete at [head]. */
@Composable
private fun EditorPage(
    look: LookId,
    style: LookStyle,
    actions: SubtitleActions,
    onFocus: (About) -> Unit,
    head: FocusRequester,
    modifier: Modifier,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(look) { runCatching { first.requestFocus() } }
    val scroll = rememberScrollState()
    ScrolledList(scroll, null, null, modifier) {
        @Composable
        fun part(
            part: LookPart,
            @StringRes name: Int,
            @StringRes about: Int,
            kind: Kind,
        ) {
            val title = stringResource(name)
            val noEdge = stringResource(R.string.subs_no_edge)
            val text = stringResource(about).let { if (part == LookPart.EDGE && style.edge == Edge.NONE) "$it $noEdge" else it }
            val enabled = style.canChange(part)
            OptionRow(
                title,
                lookPartTag(part),
                EditorRowHeight,
                kind,
                onFocused = { onFocus(About(title, text)) },
                modifier = if (part == LookPart.TEXT_COLOUR) Modifier.focusRequester(first).focusProperties { up = head } else Modifier,
                enabled = enabled,
                onClick = { if (kind is Kind.Switch) actions.edit(look, part, 1) },
                onStep = if (kind.steps) ({ by: Int -> actions.edit(look, part, by) }) else null,
            )
        }
        Group(R.string.subs_group_text, first = true)
        val textColours = TextColour.entries.map { stringResource(it.label) to Color(0xFF000000 or it.rgb.toLong()) }
        part(
            LookPart.TEXT_COLOUR,
            R.string.subs_colour,
            R.string.subs_colour_about,
            Kind.Swatches(stringResource(style.textColour.label), textColours, style.textColour.ordinal),
        )
        part(
            LookPart.OPACITY,
            R.string.subs_opacity,
            R.string.subs_opacity_about,
            Kind.Step(stringResource(R.string.player_look_percent, style.textOpacity.percent)),
        )
        part(LookPart.FONT, R.string.subs_font, R.string.subs_font_about, Kind.Step(stringResource(style.font.label)))
        part(LookPart.BOLD, R.string.subs_bold, R.string.subs_bold_about, Kind.Switch(style.bold))
        Group(R.string.subs_group_edge, tight = true)
        part(
            LookPart.EDGE,
            R.string.subs_edge,
            R.string.subs_edge_about,
            Kind.Chips(Edge.entries.map { stringResource(it.label) }, style.edge.ordinal),
        )
        part(
            LookPart.THICKNESS,
            R.string.subs_thickness,
            R.string.subs_thickness_about,
            Kind.Chips(EdgeThickness.entries.map { stringResource(it.label) }, style.edgeThickness.ordinal),
        )
        val edgeColours = EdgeColour.entries.map { stringResource(it.label) to Color(0xFF000000 or it.rgb.toLong()) }
        part(
            LookPart.EDGE_COLOUR,
            R.string.subs_edge_colour,
            R.string.subs_edge_colour_about,
            Kind.Swatches(stringResource(style.edgeColour.label), edgeColours, style.edgeColour.ordinal),
        )
        Group(R.string.subs_group_behind, tight = true)
        val box = style.box?.let { stringResource(R.string.subs_colour_strength, stringResource(it.colour.label), it.opacity.percent) }
        part(LookPart.BOX, R.string.subs_box, R.string.subs_box_about, Kind.Step(box ?: stringResource(R.string.subs_none)))
        val band = style.band?.let { stringResource(R.string.subs_colour_strength, stringResource(it.colour.label), it.opacity.percent) }
        part(LookPart.BAND, R.string.subs_band, R.string.subs_band_about, Kind.Step(band ?: stringResource(R.string.subs_off)))
    }
}

/** One row a show with its own look, named with its look and size. OK clears it. */
@Composable
private fun ShowsPage(
    settings: SubtitleSettings,
    onFocus: (Int, About) -> Unit,
    onClear: (Int) -> Unit,
    modifier: Modifier,
) {
    var cleared by remember { mutableIntStateOf(0) }
    val shows = settings.shows.entries.sortedBy { it.value.title.lowercase() }
    val rows = remember(settings.shows.keys) { settings.shows.keys.associateWith { FocusRequester() } }
    // At first focus goes to the top show, and after a clear to the one that took its place.
    LaunchedEffect(shows.size) {
        val at = shows.getOrNull(cleared.coerceAtMost(shows.lastIndex)) ?: return@LaunchedEffect
        runCatching { rows.getValue(at.key).requestFocus() }
    }
    val about = stringResource(R.string.subs_shows_list_about)
    val title = stringResource(R.string.subs_shows)
    ScrolledList(rememberScrollState(), null, null, modifier) {
        shows.forEachIndexed { index, (id, show) ->
            OptionRow(
                show.title,
                subsShowTag(id),
                RowHeight,
                Kind.Nav(stringResource(R.string.subs_summary, lookName(show.pick.look), show.pick.size.percent)),
                onFocused = { onFocus(id, About(title, about)) },
                modifier = Modifier.focusRequester(rows.getValue(id)),
                onClick = {
                    cleared = index
                    onClear(id)
                },
            )
        }
    }
}

/**
 * The preview at real size: the sample line in the look, over a picture, with a small screen in the corner that shows
 * where the line sits. What has focus is explained under it.
 */
@Composable
private fun PreviewPane(
    drawing: SubtitleDrawing,
    about: About?,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val context = LocalContext.current
    val captioning = remember { context.getSystemService(CaptioningManager::class.java) }
    // Text is as tall as it is on the whole screen, so the preview shows its real size.
    val scale = LocalWindowInfo.current.containerSize.height / with(LocalDensity.current) { PreviewHeight.toPx() }
    val sample = stringResource(R.string.player_look_sample)
    val label = stringResource(R.string.subs_preview_label)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(PreviewHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(PreviewSky))
                .drawBehind {
                    val glow = Offset(size.width * GLOW_X, size.height * GLOW_Y)
                    drawRect(Brush.radialGradient(listOf(PreviewGlow, Color.Transparent), glow, size.width * GLOW_RADIUS))
                }.semantics { contentDescription = label },
        ) {
            AndroidView(
                factory = { LookSubtitles(it) },
                update = {
                    it.lookLike(drawing, captioning, scale = scale)
                    it.show(listOf(sample))
                },
                modifier = Modifier.matchParentSize(),
            )
            Text(
                label,
                style = NeoTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                color = colors.paper,
                modifier =
                    Modifier
                        .padding(12.dp)
                        .background(colors.ink.copy(alpha = 0.8f), CircleShape)
                        .padding(horizontal = 10.dp),
            )
            ScreenMark(drawing, Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        if (about != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    about.title,
                    style = NeoTheme.typography.label.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
                    color = colors.paper,
                )
                Text(
                    about.text,
                    style = NeoTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                    color = colors.paper.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** A small screen with a line where subtitles sit. */
@Composable
private fun ScreenMark(
    drawing: SubtitleDrawing,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val height = 26.dp
    Box(
        modifier
            .width(44.dp)
            .height(height)
            .background(colors.ink.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .border(1.5.dp, colors.paper.copy(alpha = 0.85f), RoundedCornerShape(4.dp)),
    ) {
        val gap = (height * drawing.placement.fraction).coerceAtLeast(3.dp)
        Box(
            Modifier
                .align(if (drawing.placement.fromTop) Alignment.TopCenter else Alignment.BottomCenter)
                .offset(y = if (drawing.placement.fromTop) gap else -gap)
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .height(3.dp)
                .background(colors.paper, RoundedCornerShape(2.dp)),
        )
    }
}

/** Asks before a reset or a delete, with No focused. */
@Composable
private fun BoxScope.AskPanel(
    question: Ask,
    settings: SubtitleSettings,
    active: Boolean,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val no = remember { FocusRequester() }
    LaunchedEffect(Unit) { no.requestFocus() }
    val title: String
    val detail: String
    val yes: String
    val keep: String
    when (question) {
        is Ask.ResetLook -> {
            title = stringResource(R.string.subs_reset_look_ask, lookName(LookId.BuiltIn(question.look)))
            detail = stringResource(R.string.subs_reset_look_detail)
            yes = stringResource(R.string.subs_reset)
            keep = stringResource(R.string.subs_cancel)
        }

        is Ask.DeleteLook -> {
            title = stringResource(R.string.subs_delete_look_ask, lookName(LookId.Mine(question.number)))
            detail = stringResource(R.string.subs_delete_look_detail)
            yes = stringResource(R.string.subs_delete)
            keep = stringResource(R.string.subs_keep)
        }

        Ask.ResetAll -> {
            title = stringResource(R.string.subs_reset_all_ask)
            detail = stringResource(R.string.subs_reset_all_detail)
            yes = stringResource(R.string.subs_reset)
            keep = stringResource(R.string.subs_cancel)
        }
    }
    SidePanel(title, PanelWidth, active, onNo, modifier) {
        Text(title, style = PaneTitle, color = colors.paper)
        Text(
            detail,
            style = NeoTheme.typography.caption.copy(lineHeight = 19.sp),
            color = colors.textMuted,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        NeoDialogButtons {
            NeoDialogButton(yes, onClick = onYes, icon = if (question is Ask.DeleteLook) NeoIcons.Remove else NeoIcons.Reset)
            NeoDialogButton(keep, onClick = onNo, modifier = Modifier.focusRequester(no))
        }
    }
}

@get:StringRes
private val TextColour.label
    get() =
        when (this) {
            TextColour.WHITE -> R.string.subs_white
            TextColour.LIGHT_GREY -> R.string.subs_light_grey
            TextColour.YELLOW -> R.string.subs_yellow
            TextColour.CYAN -> R.string.subs_cyan
            TextColour.GREEN -> R.string.subs_green
            TextColour.PINK -> R.string.subs_pink
            TextColour.RED -> R.string.subs_red
            TextColour.BLACK -> R.string.subs_black
        }

@get:StringRes
private val EdgeColour.label
    get() =
        when (this) {
            EdgeColour.BLACK -> R.string.subs_black
            EdgeColour.DARK_GREY -> R.string.subs_dark_grey
            EdgeColour.WHITE -> R.string.subs_white
        }

@get:StringRes
private val BoxColour.label
    get() =
        when (this) {
            BoxColour.BLACK -> R.string.subs_black
            BoxColour.GREY -> R.string.subs_grey
            BoxColour.WHITE -> R.string.subs_white
        }

@get:StringRes
private val BandColour.label
    get() =
        when (this) {
            BandColour.BLACK -> R.string.subs_black
            BandColour.GREY -> R.string.subs_grey
        }

@get:StringRes
private val SubtitleFont.label
    get() =
        when (this) {
            SubtitleFont.ATKINSON_HYPERLEGIBLE -> R.string.subs_font_atkinson
            SubtitleFont.SANS_SERIF -> R.string.subs_font_sans
            SubtitleFont.SERIF -> R.string.subs_font_serif
            SubtitleFont.MONOSPACE -> R.string.subs_font_mono
            SubtitleFont.CASUAL -> R.string.subs_font_casual
        }

@get:StringRes
private val Edge.label
    get() =
        when (this) {
            Edge.NONE -> R.string.subs_none
            Edge.OUTLINE -> R.string.subs_edge_outline
            Edge.SHADOW -> R.string.subs_edge_shadow
            Edge.RAISED -> R.string.subs_edge_raised
            Edge.SUNKEN -> R.string.subs_edge_sunken
        }

@get:StringRes
private val EdgeThickness.label
    get() =
        when (this) {
            EdgeThickness.THIN -> R.string.subs_thin
            EdgeThickness.MEDIUM -> R.string.subs_medium
            EdgeThickness.THICK -> R.string.subs_thick
        }
