package ani.saikou.neo.tv.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.settings.Setting
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.settings.isOn
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoDialogButton
import ani.saikou.neo.tv.ui.NeoDialogButtons
import ani.saikou.neo.tv.ui.NeoIcons
import ani.saikou.neo.tv.ui.NeoTopBar
import ani.saikou.neo.tv.ui.TopTab

internal const val SETTINGS_PANEL_TAG = "settings-panel"
internal const val SETTINGS_DIALOG_TAG = "settings-dialog"

internal fun settingsCategoryTag(category: SettingsCategory) = "settings-category-${category.name.lowercase()}"

internal fun settingTag(setting: Setting) = "setting-${setting.name.lowercase()}"

internal fun settingChoiceTag(
    setting: Setting,
    on: Boolean,
) = "setting-${setting.name.lowercase()}-${if (on) "on" else "off"}"

/** The groups on the left of Settings, each with a line on what it covers and its settings. */
enum class SettingsCategory(
    @param:StringRes val title: Int,
    @param:StringRes val intro: Int,
    val settings: List<Setting>,
) {
    BROWSING(R.string.settings_browsing, R.string.settings_browsing_intro, listOf(Setting.UNAIRED_ANIME, Setting.UPCOMING_EPISODES)),
    DISPLAY(R.string.settings_display, R.string.settings_display_intro, listOf(Setting.SLOW_TV)),
}

// From the design: both panes start 88 dp down. Categories are 228 dp wide from the margin, and the settings start at 318.
private val PaneTop = 88.dp
private val CategoryWidth = 228.dp
private val SettingsStart = 318.dp
private val CategoryHeight = 40.dp
private val CategoryPadding = 14.dp
private val CategoryGap = 4.dp
private val RowHeight = 62.dp
private val RowPadding = 18.dp
private val RowGap = 8.dp
private val Chevron = 16.dp
private const val ROW_FOCUS_SCALE = 1.025f
private val SelectedFill = Color.White.copy(alpha = 0.16f)
private val FocusGlowColor = Color.White.copy(alpha = 0.32f)

// The choices panel is 340 dp wide on the right, over a dimmed screen. Its choices are 48 dp tall with a 20 dp radio.
private val PanelWidth = 340.dp
private val PanelPadding = 28.dp
private val ChoiceHeight = 48.dp
private val ChoicePadding = 16.dp
private val ChoiceGap = 4.dp
private val Radio = 20.dp
private const val SCRIM_ALPHA = 0.55f

// The dialog is 480 dp wide and 96 dp down, as the other dialogs are.
private val DialogTop = 96.dp
private val DialogWidth = 480.dp
private val DialogPadding = 28.dp
private const val DIALOG_SCRIM_ALPHA = 0.88f

/**
 * Settings: the top bar over categories on the left and the shown one's settings on the right, each with its value.
 * Resting on a category shows its settings; Right goes into them and Left comes back. Selecting a setting opens its
 * choices in a panel, and selecting one there calls [onChoose].
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    onChoose: (Setting, on: Boolean) -> Unit,
    onTab: (TopTab) -> Unit,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    var shown by rememberSaveable { mutableStateOf(SettingsCategory.BROWSING) }
    var open by rememberSaveable { mutableStateOf<Setting?>(null) }
    // The setting whose panel just closed, to take focus back.
    var closed by remember { mutableStateOf<Setting?>(null) }
    val currentTab = remember { FocusRequester() }
    val categories = remember { SettingsCategory.entries.associateWith { FocusRequester() } }
    val rows = remember { Setting.entries.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { currentTab.requestFocus() }
    LaunchedEffect(closed) {
        closed?.let { rows.getValue(it).requestFocus() }
        closed = null
    }
    Box(Modifier.fillMaxSize().background(colors.ink)) {
        NeoTopBar(TopTab.SETTINGS, onTab, currentTab = currentTab, down = categories.getValue(shown))
        Column(
            Modifier.padding(start = dimensions.gridMargin, top = PaneTop).width(CategoryWidth),
            verticalArrangement = Arrangement.spacedBy(CategoryGap),
        ) {
            Text(stringResource(R.string.tab_settings), style = NeoTheme.typography.headline, color = colors.paper)
            Spacer(Modifier.height(10.dp - CategoryGap))
            SettingsCategory.entries.forEach { category ->
                Category(
                    category,
                    selected = category == shown,
                    onFocused = { shown = category },
                    modifier =
                        Modifier
                            .focusRequester(categories.getValue(category))
                            .focusProperties { right = rows.getValue(category.settings.first()) },
                )
            }
        }
        Column(
            Modifier
                .padding(start = SettingsStart, end = dimensions.gridMargin, top = PaneTop)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            Text(stringResource(shown.title), style = PaneTitle, color = colors.paper)
            Text(stringResource(shown.intro), style = Help, color = colors.textMuted, modifier = Modifier.padding(bottom = 6.dp))
            shown.settings.forEach { setting ->
                SettingRow(
                    setting,
                    on = settings.isOn(setting),
                    onClick = { open = setting },
                    modifier =
                        Modifier
                            .focusRequester(rows.getValue(setting))
                            .focusProperties { left = categories.getValue(shown) },
                )
            }
        }
        open?.let { setting ->
            val close = {
                open = null
                closed = setting
            }
            ChoicesPanel(
                setting,
                on = settings.isOn(setting),
                onChoose = { on ->
                    onChoose(setting, on)
                    close()
                },
                onClose = close,
            )
        }
    }
}

private val PaneTitle
    @Composable get() = NeoTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp)

private val Help
    @Composable get() = NeoTheme.typography.caption.copy(fontSize = 13.sp, lineHeight = 18.sp)

/** A category: a soft fill when it's the one shown, paper when focused. */
@Composable
private fun Category(
    category: SettingsCategory,
    selected: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    Surface(
        selected = selected,
        onClick = onFocused,
        modifier =
            modifier
                .testTag(settingsCategoryTag(category))
                .fillMaxWidth()
                .height(CategoryHeight)
                .onFocusChanged { if (it.isFocused) onFocused() },
        shape = SelectableSurfaceDefaults.shape(RoundedCornerShape(NeoTheme.dimensions.radiusMd)),
        colors =
            SelectableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = colors.textMuted,
                selectedContainerColor = SelectedFill,
                selectedContentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                focusedSelectedContainerColor = colors.focus,
                focusedSelectedContentColor = colors.onFocus,
            ),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = CategoryPadding), contentAlignment = Alignment.CenterStart) {
            Text(
                stringResource(category.title),
                style = NeoTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            )
        }
    }
}

/** A setting: its name with a help line under it, and its value with an arrow on the right. */
@Composable
private fun SettingRow(
    setting: Setting,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    var focused by remember { mutableStateOf(false) }
    val text = setting.text()
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .testTag(settingTag(setting))
                .fillMaxWidth()
                .height(RowHeight)
                .onFocusChanged { focused = it.isFocused },
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
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, NeoTheme.dimensions.glowFocus)),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = RowPadding),
            horizontalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(text.title), style = NeoTheme.typography.labelLarge)
                Text(stringResource(text.help), style = Help, color = LocalContentColor.current.copy(alpha = 0.72f))
            }
            val value = if (focused) colors.pinkDeep else colors.pink
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(text.value(on)),
                    style = NeoTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
                    color = value,
                )
                Icon(NeoIcons.Chevron, contentDescription = null, tint = value, modifier = Modifier.size(Chevron))
            }
        }
    }
}

/**
 * A setting's choices in a panel on the right, with focus on the current one. Focus stays in the panel until a choice
 * is selected, which calls [onChoose], or Back calls [onClose]. Back while resting on another choice asks first.
 */
@Composable
private fun BoxScope.ChoicesPanel(
    setting: Setting,
    on: Boolean,
    onChoose: (on: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val colors = NeoTheme.colors
    val choices = remember { mapOf(false to FocusRequester(), true to FocusRequester()) }
    val text = setting.text()
    // The choice focus is on, and whether the app is asking about it.
    var resting by remember { mutableStateOf(on) }
    var asking by remember { mutableStateOf(false) }
    // Focus starts on the current choice, and comes back to the one it left when the dialog closes.
    LaunchedEffect(asking) { if (!asking) choices.getValue(resting).requestFocus() }
    // Ahead of the navigation's own, so Back closes the panel and stays on Settings.
    BackHandler(enabled = !asking) { if (resting == on) onClose() else asking = true }
    Box(Modifier.fillMaxSize().background(colors.scrimPlayer.copy(alpha = SCRIM_ALPHA)))
    Column(
        Modifier
            .testTag(SETTINGS_PANEL_TAG)
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(PanelWidth)
            .background(colors.surface)
            .padding(start = PanelPadding, end = PanelPadding, top = PaneTop)
            .focusProperties { onExit = { if (!asking) cancelFocusChange() } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(ChoiceGap),
    ) {
        Text(stringResource(text.title), style = PaneTitle, color = colors.paper)
        Text(stringResource(text.help), style = Help, color = colors.textMuted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        listOf(false, true).forEach { value ->
            Choice(
                stringResource(text.value(value)),
                selected = value == on,
                onClick = { onChoose(value) },
                modifier =
                    Modifier
                        .testTag(settingChoiceTag(setting, value))
                        .focusRequester(choices.getValue(value))
                        .onFocusChanged { if (it.isFocused) resting = value },
            )
        }
    }
    if (asking) {
        UnsavedDialog(
            current = stringResource(text.value(on)),
            moved = stringResource(text.value(resting)),
            onChange = { onChoose(resting) },
            onKeep = onClose,
            onDismiss = { asking = false },
        )
    }
}

/**
 * Asks whether to save a choice the viewer moved to but didn't select. Back calls [onDismiss], to choose again.
 */
@Composable
private fun BoxScope.UnsavedDialog(
    current: String,
    moved: String,
    onChange: () -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NeoTheme.colors
    val change = remember { FocusRequester() }
    LaunchedEffect(Unit) { change.requestFocus() }
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(colors.ink.copy(alpha = DIALOG_SCRIM_ALPHA)))
    Column(
        Modifier
            .testTag(SETTINGS_DIALOG_TAG)
            .align(Alignment.TopCenter)
            .padding(top = DialogTop)
            .width(DialogWidth)
            .background(colors.surface, RoundedCornerShape(NeoTheme.dimensions.radiusXl))
            .padding(DialogPadding)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.settings_unsaved_title),
            style = NeoTheme.typography.titleLarge.copy(lineHeight = 30.sp),
            color = colors.paper,
        )
        Text(
            stringResource(R.string.settings_unsaved_body, moved),
            style = NeoTheme.typography.body,
            color = colors.textMuted,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        NeoDialogButtons {
            NeoDialogButton(stringResource(R.string.settings_unsaved_change, moved), onChange, Modifier.focusRequester(change))
            NeoDialogButton(stringResource(R.string.settings_unsaved_keep, current), onKeep)
        }
    }
}

/** One choice: a radio, filled when it's the current value, and its name. */
@Composable
private fun Choice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    var focused by remember { mutableStateOf(false) }
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(ChoiceHeight).onFocusChanged { focused = it.isFocused },
        shape = SelectableSurfaceDefaults.shape(RoundedCornerShape(NeoTheme.dimensions.radiusLg)),
        colors =
            SelectableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = colors.paper,
                selectedContainerColor = Color.Transparent,
                selectedContentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                focusedSelectedContainerColor = colors.focus,
                focusedSelectedContentColor = colors.onFocus,
            ),
        scale = SelectableSurfaceDefaults.scale(focusedScale = ROW_FOCUS_SCALE),
        glow = SelectableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, NeoTheme.dimensions.glowFocus)),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = ChoicePadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ring =
                when {
                    selected && focused -> colors.pinkDeep
                    selected -> colors.pink
                    focused -> colors.onFocusMuted
                    else -> colors.textFaint
                }
            Canvas(Modifier.size(Radio)) {
                drawCircle(ring, radius = 8.dp.toPx(), style = Stroke(2.dp.toPx()))
                if (selected) drawCircle(ring, radius = 4.5.dp.toPx())
            }
            Text(
                label,
                style = NeoTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            )
        }
    }
}

private class SettingText(
    @param:StringRes val title: Int,
    @param:StringRes val help: Int,
    @param:StringRes val on: Int,
    @param:StringRes val off: Int,
) {
    @StringRes
    fun value(on: Boolean) = if (on) this.on else off
}

private fun Setting.text() =
    when (this) {
        Setting.UNAIRED_ANIME -> {
            SettingText(
                R.string.settings_unaired_anime,
                R.string.settings_unaired_anime_help,
                R.string.settings_shown,
                R.string.settings_hidden,
            )
        }

        Setting.UPCOMING_EPISODES -> {
            SettingText(
                R.string.settings_upcoming_episodes,
                R.string.settings_upcoming_episodes_help,
                R.string.settings_shown,
                R.string.settings_hidden,
            )
        }

        Setting.SLOW_TV -> {
            SettingText(R.string.settings_slow_tv, R.string.settings_slow_tv_help, R.string.settings_on, R.string.settings_off)
        }
    }
