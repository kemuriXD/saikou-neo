package ani.saikou.neo.tv.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.SoftwareKeyboardControllerCompat
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.sources.AddError
import ani.saikou.neo.shared.sources.Adding
import ani.saikou.neo.shared.sources.Choice
import ani.saikou.neo.shared.sources.ChoiceStatus
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.shared.sources.SourceProblem
import ani.saikou.neo.shared.sources.SourcesState
import ani.saikou.neo.shared.sources.canPick
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoDialogButton
import ani.saikou.neo.tv.ui.NeoDialogButtons
import ani.saikou.neo.tv.ui.NeoIcons
import ani.saikou.neo.tv.ui.SourceLogo
import java.io.File

internal const val ADD_SOURCE_TAG = "add-source"
internal const val SOURCE_FIELD_TAG = "source-field"

internal fun sourceTag(id: String) = "source-$id"

internal fun removeTag(id: String) = "remove-$id"

internal fun choiceTag(id: String) = "choice-$id"

/**
 * What Settings > Sources does when you add a source from [onAdd]'s text, close a panel, remove a source by id, tick
 * a source in a list by id, or add the ticked ones.
 */
class SourceActions(
    val onAdd: (text: String) -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onRemove: (id: String) -> Unit = {},
    val onToggle: (id: String) -> Unit = {},
    val onAddTicked: () -> Unit = {},
)

/** The panels Sources opens: adding a source, or asking before one is removed. */
internal sealed interface SourcePanel {
    data object Add : SourcePanel

    data class Remove(
        val source: InstalledSource,
    ) : SourcePanel
}

/** What the Sources pane and its panels share: the open panel, and where focus goes once one closes. */
internal class SourcesUi {
    var panel by mutableStateOf<SourcePanel?>(null)
    val addSource = FocusRequester()
    val rows = mutableStateMapOf<String, FocusRequester>()

    /** The source to focus next, or "" for Add a source, once [waitingFor] has left the list. */
    var focusNext by mutableStateOf<String?>(null)
    var waitingFor by mutableStateOf<String?>(null)

    fun row(id: String) = rows.getOrPut(id) { FocusRequester() }
}

// From the design: Add a source is 60 dp tall, each source 68 dp with a 40 dp logo or letter. The add panel is 376 dp.
private val AddRowHeight = 60.dp
private val SourceRowHeight = 68.dp
private val RowPadding = 18.dp
private val RowRadius = 14.dp
private val RowSpacing = 14.dp
private val Badge = 40.dp
private val BadgeRadius = 10.dp
private val RemovePill = 36.dp
private val PanelWidth = 376.dp
private val FieldHeight = 52.dp
private val EmptyIcon = 72.dp
private val EmptyHeight = 400.dp
private val ButtonHeight = 52.dp
private val ButtonPadding = 26.dp
private val EmptyTextWidth = 430.dp
private val ChoiceHeight = 56.dp
private val ChoiceBadge = 36.dp
private val FocusGlowColor = Color.White.copy(alpha = 0.3f)

private val Overline
    @Composable get() =
        NeoTheme.typography.caption.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
        )

private val Small
    @Composable get() = NeoTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp)

private val Body
    @Composable get() = NeoTheme.typography.caption.copy(fontSize = 14.sp, lineHeight = 20.sp)

private val RowTitle
    @Composable get() = NeoTheme.typography.label.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)

/**
 * The installed sources, under a line on what sources are and Add a source. With none, a short explanation and Add a
 * source on their own. Left goes back to [category].
 */
@Composable
internal fun SourcesPane(
    state: SourcesState,
    ui: SourcesUi,
    category: FocusRequester,
) {
    val installed = state.installed ?: return
    // Focus moves at once, so it never falls back to the categories, and again once a removed source has gone.
    LaunchedEffect(ui.focusNext, installed) {
        val next = ui.focusNext ?: return@LaunchedEffect
        (if (next.isEmpty()) ui.addSource else ui.row(next)).requestFocus()
        if (installed.none { it.id == ui.waitingFor }) {
            ui.focusNext = null
            ui.waitingFor = null
        }
    }
    val toCategory = Modifier.focusProperties { left = category }
    val empty = installed.isEmpty()
    val colors = NeoTheme.colors
    // One column for both looks, so Add a source stays the same node and keeps focus when the last source goes.
    Column(
        if (empty) Modifier.fillMaxWidth().height(EmptyHeight) else Modifier,
        verticalArrangement = if (empty) Arrangement.spacedBy(16.dp, Alignment.CenterVertically) else Arrangement.spacedBy(12.dp),
        horizontalAlignment = if (empty) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (empty) {
            NoSourcesHeader()
        } else {
            Text(stringResource(R.string.settings_sources_intro), style = Body, color = colors.textMuted)
        }
        AddSource(empty, onClick = { ui.panel = SourcePanel.Add }, modifier = toCategory.focusRequester(ui.addSource))
        if (empty) {
            Text(
                stringResource(R.string.sources_trust),
                style = Small,
                color = colors.textFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = EmptyTextWidth).padding(top = 10.dp),
            )
        } else {
            Text(
                stringResource(R.string.sources_installed).uppercase(),
                style = Overline,
                color = colors.textFaint,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
        }
        installed.forEach { source ->
            key(source.id) {
                SourceRow(
                    source,
                    onClick = { ui.panel = SourcePanel.Remove(source) },
                    modifier = toCategory.focusRequester(ui.row(source.id)),
                )
            }
        }
    }
}

/** The add, remove and can't-use panels, over the whole screen. */
@Composable
internal fun BoxScope.SourcesPanels(
    state: SourcesState,
    ui: SourcesUi,
    actions: SourceActions,
) {
    val adding = state.adding
    val added = adding is Adding.Added || adding is Adding.AddedAll
    // Once a source is added or can't be used, the add panel has done its job.
    LaunchedEffect(adding) {
        if (added || adding is Adding.CantUse) ui.panel = null
        if (added) {
            actions.onDismiss()
            ui.focusNext = ""
        }
    }
    val closeAdd = {
        ui.panel = null
        actions.onDismiss()
        ui.focusNext = ""
    }
    val choosing = adding as? Adding.Choosing
    // Closed in the same frame, so the next panel can take focus. A list gets its own panel, so focus moves straight
    // from the field to the list and never drops out to the categories.
    val open = ui.panel.takeUnless { added || adding is Adding.CantUse || choosing != null }
    SlidingPanel(open) { panel, active, motion ->
        when (panel) {
            SourcePanel.Add -> {
                AddPanel(adding, active, onAdd = actions.onAdd, onClose = closeAdd, modifier = motion)
            }

            is SourcePanel.Remove -> {
                val keep = {
                    ui.panel = null
                    ui.focusNext = panel.source.id
                }
                RemovePanel(
                    panel.source,
                    active,
                    onRemove = {
                        val installed = state.installed.orEmpty()
                        val index = installed.indexOfFirst { it.id == panel.source.id }
                        ui.panel = null
                        ui.waitingFor = panel.source.id
                        ui.focusNext = (installed.getOrNull(index + 1) ?: installed.getOrNull(index - 1))?.id ?: ""
                        actions.onRemove(panel.source.id)
                    },
                    onKeep = keep,
                    modifier = motion,
                )
            }
        }
    }
    // Closing it clears the panel at once, so focus can leave before the list does.
    SlidingPanel(choosing?.takeIf { ui.panel == SourcePanel.Add }) { list, active, motion ->
        ChoosePanel(list, state.icons, active, actions, onClose = closeAdd, modifier = motion)
    }
    SlidingPanel((adding as? Adding.CantUse)?.source) { source, active, motion ->
        CantUsePanel(source, active, onClose = closeAdd, modifier = motion)
    }
}

/** No sources yet, and what they're for. */
@Composable
private fun NoSourcesHeader() {
    val colors = NeoTheme.colors
    Box(
        Modifier
            .size(EmptyIcon)
            .background(colors.surface, CircleShape)
            .border(1.dp, colors.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(NeoIcons.Puzzle, contentDescription = null, tint = colors.pink, modifier = Modifier.size(34.dp))
    }
    Text(
        stringResource(R.string.sources_empty_title),
        style = NeoTheme.typography.headline.copy(fontSize = 24.sp, lineHeight = 32.sp),
        color = colors.paper,
    )
    Text(
        stringResource(R.string.sources_empty_body),
        style = Body,
        color = colors.textMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = EmptyTextWidth),
    )
}

/** A wide row that turns paper with a glow on focus, without growing. */
@Composable
private fun WideRow(
    onClick: () -> Unit,
    height: Dp,
    container: Color,
    modifier: Modifier,
    button: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = NeoTheme.colors
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .then(if (button) Modifier else Modifier.fillMaxWidth())
                .heightIn(min = height),
        shape = ClickableSurfaceDefaults.shape(if (button) CircleShape else RoundedCornerShape(RowRadius)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = container,
                contentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (button) NeoTheme.dimensions.focusScaleButton else 1f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, NeoTheme.dimensions.glowFocus)),
    ) {
        Row(
            Modifier
                .heightIn(min = height)
                .padding(horizontal = if (button) ButtonPadding else RowPadding, vertical = if (button) 0.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(RowSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

/** Add a source: a wide row over the list, or with no sources, a button of its own. */
@Composable
private fun AddSource(
    empty: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    WideRow(
        onClick,
        if (empty) ButtonHeight else AddRowHeight,
        if (empty) colors.fillTonal else colors.surfaceRaised,
        modifier.testTag(ADD_SOURCE_TAG).padding(top = if (empty) 4.dp else 0.dp),
        button = empty,
    ) {
        Icon(NeoIcons.Add, contentDescription = null, modifier = Modifier.size(if (empty) 20.dp else 22.dp))
        if (empty) {
            Text(stringResource(R.string.sources_add), style = RowTitle.copy(fontSize = 16.sp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.sources_add), style = RowTitle)
                Text(stringResource(R.string.sources_add_help), style = Small, color = LocalContentColor.current.copy(alpha = 0.72f))
            }
        }
    }
}

/**
 * A source: its letter, name, and version or why it can't be used, with a Remove pill at the end. The row is a card;
 * focus goes to the pill, which turns paper, and the row lifts to the raised surface around it. [modifier] is the
 * pill's. TalkBack reads the source with Remove.
 */
@Composable
private fun SourceRow(
    source: InstalledSource,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val detail = source.problem?.let { stringResource(it.reason) } ?: stringResource(R.string.sources_version, source.version)
    val remove = stringResource(R.string.sources_remove)
    var focused by remember { mutableStateOf(false) }
    val lift by animateColorAsState(if (focused) colors.surfaceRaised else colors.surface, label = "row")
    Row(
        Modifier
            .testTag(sourceTag(source.id))
            .fillMaxWidth()
            .heightIn(min = SourceRowHeight)
            .background(lift, RoundedCornerShape(RowRadius))
            .padding(horizontal = RowPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceBadge(source.name, source.icon?.let(::File), if (focused) colors.line else colors.surfaceRaised)
        Column(Modifier.weight(1f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(source.name, style = RowTitle, color = colors.paper, maxLines = 1)
            Text(detail, style = Small, color = colors.textMuted)
        }
        Surface(
            onClick = onClick,
            modifier =
                modifier
                    .testTag(removeTag(source.id))
                    .height(RemovePill)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = "${source.name}, $detail. $remove" },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = colors.surfaceRaised,
                    contentColor = colors.textMuted,
                    focusedContainerColor = colors.focus,
                    focusedContentColor = colors.onFocus,
                    pressedContainerColor = colors.focus,
                    pressedContentColor = colors.onFocus,
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = NeoTheme.dimensions.focusScaleButton),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, NeoTheme.dimensions.glowFocus)),
        ) {
            Row(
                Modifier.height(RemovePill).padding(horizontal = 14.dp).clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(NeoIcons.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(remove, style = Body.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

/** A source's logo, [icon], on its own, or else the first letter of its [name] on a square of [background]. Decoration only. */
@Composable
private fun SourceBadge(
    name: String,
    icon: Any?,
    background: Color = NeoTheme.colors.surfaceRaised,
    size: Dp = Badge,
    dimmed: Boolean = false,
) = SourceLogo(name, icon, size, radius = size / 4, background = background, dimmed = dimmed)

/**
 * Add a source: a field for the link or code, which opens the TV's keyboard on OK, and Add. While the app checks,
 * Add says so; if it fails, the reason shows under the field and the text stays.
 */
@Composable
private fun BoxScope.AddPanel(
    adding: Adding,
    active: Boolean,
    onAdd: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val text = rememberTextFieldState()
    val field = remember { FocusRequester() }
    // LocalSoftwareKeyboardController can't open the keyboard for a BasicTextField whose session we don't
    // show on focus, so ask the IME for this view directly (same thing a tap would do).
    val view = LocalView.current
    val keyboard = remember(view) { SoftwareKeyboardControllerCompat(view) }
    var focused by remember { mutableStateOf(false) }
    val checking = adding == Adding.Checking
    val add = { if (!checking) onAdd(text.text.toString()) }
    LaunchedEffect(Unit) { field.requestFocus() }
    val title = stringResource(R.string.sources_add)
    SidePanel(title, PanelWidth, active, onClose, modifier) {
        Text(title, style = PaneTitle, color = colors.paper)
        Text(stringResource(R.string.sources_add_body), style = Body, color = colors.textMuted)
        Text(
            stringResource(R.string.sources_field_label).uppercase(),
            style = Overline,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        val label = stringResource(R.string.sources_field_label)
        BasicTextField(
            state = text,
            modifier =
                Modifier
                    .testTag(SOURCE_FIELD_TAG)
                    .focusRequester(field)
                    .onFocusChanged { focused = it.isFocused }
                    // OK opens the keyboard, so moving through the panel doesn't.
                    .onPreviewKeyEvent { event ->
                        val ok = event.key == Key.DirectionCenter || event.key == Key.Enter
                        if (ok && event.type == KeyEventType.KeyUp) keyboard.show()
                        ok
                    }.semantics { contentDescription = label },
            textStyle = NeoTheme.typography.body.copy(fontSize = 16.sp, color = colors.paper),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                    autoCorrectEnabled = false,
                    showKeyboardOnFocus = false,
                ),
            onKeyboardAction = {
                keyboard.hide()
                add()
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(colors.paper),
            decorator = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(FieldHeight)
                        .background(colors.ink, RoundedCornerShape(12.dp))
                        .border(
                            if (focused) BorderStroke(NeoTheme.dimensions.focusRing, colors.focus) else BorderStroke(1.dp, colors.line),
                            RoundedCornerShape(12.dp),
                        ).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.text.isEmpty()) Text(label, style = NeoTheme.typography.body.copy(fontSize = 16.sp), color = colors.textFaint)
                    inner()
                }
            },
        )
        (adding as? Adding.Failed)?.let {
            Text(stringResource(it.error.message), style = Body, color = colors.warning)
        }
        NeoButton(
            stringResource(if (checking) R.string.sources_checking else R.string.sources_add_button),
            onClick = add,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Choose sources: the sources a list names, with the first you can pick focused, and Add for the ticked ones. Rows you
 * can't pick say why, and focus skips them. While sources are added, Add says how far it has got and keeps focus, so a
 * row that changes can't take it away, and Back waits for them.
 */
@Composable
private fun BoxScope.ChoosePanel(
    choosing: Adding.Choosing,
    icons: Map<String, ByteArray>,
    active: Boolean,
    actions: SourceActions,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val first = remember { FocusRequester() }
    val firstId =
        choosing.choices
            .firstOrNull { it.status.canPick }
            ?.source
            ?.id
    LaunchedEffect(Unit) { first.requestFocus() }
    val ticked = choosing.choices.count { it.ticked }
    val progress = choosing.progress
    val title = stringResource(R.string.sources_choose_title)
    SidePanel(title, PanelWidth, active, onClose = { if (progress == null) onClose() }, modifier) {
        Text(title, style = PaneTitle, color = colors.paper)
        Text(stringResource(R.string.sources_choose_body), style = Body, color = colors.textMuted)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            choosing.choices.forEach { choice ->
                key(choice.source.id) {
                    ChoiceRow(
                        choice,
                        icons[choice.source.id],
                        onToggle = { actions.onToggle(choice.source.id) },
                        modifier =
                            (if (choice.source.id == firstId) Modifier.focusRequester(first) else Modifier)
                                .focusProperties { canFocus = progress == null },
                    )
                }
            }
        }
        NeoButton(
            when {
                progress != null -> stringResource(R.string.sources_adding, progress.done + 1, progress.total)
                ticked == 0 -> stringResource(R.string.sources_add_none)
                else -> pluralStringResource(R.plurals.sources_add_count, ticked, ticked)
            },
            onClick = { if (ticked > 0 && progress == null) actions.onAddTicked() },
            modifier = (if (firstId == null) Modifier.focusRequester(first) else Modifier).padding(top = 4.dp),
        )
    }
}

/**
 * A source in a list: a row with a tick box when it can be picked, else a dimmed line that says why. [icon] is the
 * logo the list links to, once it has arrived.
 */
@Composable
private fun ChoiceRow(
    choice: Choice,
    icon: ByteArray?,
    onToggle: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val source = choice.source
    val status = choice.status
    val detail =
        when (status) {
            ChoiceStatus.New -> stringResource(R.string.sources_version, source.version)
            is ChoiceStatus.Update -> stringResource(R.string.sources_update, status.from, source.version)
            ChoiceStatus.Installed -> stringResource(R.string.sources_installed_version, source.version)
            is ChoiceStatus.CantUse -> stringResource(status.problem.short)
            ChoiceStatus.Failed -> stringResource(R.string.sources_failed)
        }
    val described = Modifier.testTag(choiceTag(source.id)).semantics { contentDescription = "${source.name}, $detail" }
    val warn = status is ChoiceStatus.CantUse || status == ChoiceStatus.Failed
    if (!status.canPick) {
        Row(
            described.fillMaxWidth().height(ChoiceHeight).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceBadge(source.name, icon, colors.surfaceRaised, ChoiceBadge, dimmed = true)
            ChoiceText(source.name, detail, colors.textMuted, if (warn) colors.warning else colors.textMuted)
        }
        return
    }
    var focused by remember { mutableStateOf(false) }
    // A tick box for TalkBack, though it's drawn as a row.
    val tick =
        Modifier.semantics {
            role = Role.Checkbox
            toggleableState = ToggleableState(choice.ticked)
        }
    Surface(
        onClick = onToggle,
        modifier =
            modifier
                .then(described)
                .then(tick)
                .fillMaxWidth()
                .height(ChoiceHeight)
                .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
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
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, NeoTheme.dimensions.glowFocus)),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val content = LocalContentColor.current
            SourceBadge(source.name, icon, colors.line, ChoiceBadge)
            ChoiceText(source.name, detail, content, if (warn && !focused) colors.warning else content.copy(alpha = 0.72f))
            TickBox(choice.ticked, outline = content.copy(alpha = if (focused) 1f else 0.6f))
        }
    }
}

@Composable
private fun RowScope.ChoiceText(
    name: String,
    detail: String,
    nameColor: Color,
    detailColor: Color,
) = Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(name, style = RowTitle, color = nameColor, maxLines = 1)
    Text(detail, style = Small, color = detailColor, maxLines = 1)
}

/** Pink with an ink tick when [ticked], else an empty square drawn in [outline]. */
@Composable
private fun TickBox(
    ticked: Boolean,
    outline: Color,
) {
    val colors = NeoTheme.colors
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .size(24.dp)
            .then(if (ticked) Modifier.background(colors.pink, shape) else Modifier.border(2.dp, outline, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (ticked) Icon(NeoIcons.Tick, contentDescription = null, tint = colors.ink, modifier = Modifier.size(16.dp))
    }
}

/** Asks before [source] is removed, with Keep focused. */
@Composable
private fun BoxScope.RemovePanel(
    source: InstalledSource,
    active: Boolean,
    onRemove: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier,
) {
    val keep = remember { FocusRequester() }
    LaunchedEffect(Unit) { keep.requestFocus() }
    val title = stringResource(R.string.sources_remove_title, source.name)
    SidePanel(title, PanelWidth, active, onKeep, modifier) {
        Text(title, style = PaneTitle, color = NeoTheme.colors.paper, modifier = Modifier.padding(bottom = 8.dp))
        NeoDialogButtons {
            NeoDialogButton(stringResource(R.string.sources_remove), onClick = onRemove, icon = NeoIcons.Remove)
            NeoDialogButton(stringResource(R.string.sources_keep), onClick = onKeep, modifier = Modifier.focusRequester(keep))
        }
    }
}

/** Can't use this source: which source, and why. Close is the only button. */
@Composable
private fun BoxScope.CantUsePanel(
    source: InstalledSource,
    active: Boolean,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { close.requestFocus() }
    val title = stringResource(R.string.sources_cant_use)
    SidePanel(title, PanelWidth, active, onClose, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(NeoIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(24.dp))
            Text(title, style = PaneTitle, color = colors.paper)
        }
        // The source, between two lines.
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            Row(
                Modifier.padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Not installed, so there's no logo saved for it.
                SourceBadge(source.name, icon = null)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(source.name, style = RowTitle, color = colors.paper)
                    Text(stringResource(R.string.sources_version, source.version), style = Small, color = colors.textMuted)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        }
        source.problem?.let { Text(stringResource(it.reason), style = Body, color = colors.textMuted) }
        Spacer(Modifier.weight(1f))
        NeoButton(stringResource(R.string.sources_close), onClick = onClose, modifier = Modifier.focusRequester(close))
    }
}

private val SourceProblem.reason
    get() =
        when (this) {
            SourceProblem.BUILT_FOR_NEWER_APP -> R.string.sources_built_for_newer_app
            SourceProblem.BUILT_FOR_OLDER_APP -> R.string.sources_built_for_older_app
            SourceProblem.BROKEN -> R.string.sources_broken
        }

private val SourceProblem.short
    get() =
        when (this) {
            SourceProblem.BUILT_FOR_NEWER_APP -> R.string.sources_needs_newer_app
            SourceProblem.BUILT_FOR_OLDER_APP -> R.string.sources_needs_newer_source
            SourceProblem.BROKEN -> R.string.sources_does_not_work
        }

private val AddError.message
    get() =
        when (this) {
            AddError.NO_SUCH_CODE -> R.string.sources_error_code
            AddError.NOT_SECURE -> R.string.sources_error_not_secure
            AddError.NOT_A_SOURCE -> R.string.sources_error_not_a_source
            AddError.BROKEN_LIST -> R.string.sources_error_broken_list
            AddError.NOTHING_IN_LIST -> R.string.sources_error_nothing_in_list
            AddError.ALL_INSTALLED -> R.string.sources_error_all_installed
        }
