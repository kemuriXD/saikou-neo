package ani.saikou.neo.tv.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.IntOffset
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
import ani.saikou.neo.tv.ui.FocusMemory
import ani.saikou.neo.tv.ui.NeoIcons
import ani.saikou.neo.tv.ui.RememberFocus
import ani.saikou.neo.tv.ui.remembersFocus

internal const val SETTINGS_PANEL_TAG = "settings-panel"

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

// From the design, less the rail: the title sits on the safe area and both panes start 80 dp down. Categories are
// 220 dp wide from the margin, and the settings start 248 dp after it and run to the safe area.
private val PaneTop = 80.dp
private val CategoryWidth = 220.dp
private val SettingsOffset = 248.dp
private val CategoryHeight = 44.dp
private val CategoryPadding = 16.dp
private val CategoryGap = 4.dp
private val RowHeight = 62.dp
private val RowPadding = 18.dp
private val RowGap = 8.dp
private val RowRadius = 14.dp
private val RowSpacing = 14.dp
private val Chevron = 16.dp
private const val ROW_FOCUS_SCALE = 1.02f
private val FocusGlowColor = Color.White.copy(alpha = 0.3f)

// The choices panel is 360 dp wide on the right, over a dimmed screen, with a shadow off its left edge. Its choices
// are 64 dp tall with a 16 dp radio.
private val PanelWidth = 360.dp
private val PanelPaddingX = 24.dp
private val PanelGap = 10.dp
private val PanelShadow = 40.dp
private const val PANEL_SHADOW_ALPHA = 0.5f
private val ChoiceHeight = 64.dp
private val ChoicePadding = 16.dp
private val Radio = 16.dp
private const val SCRIM_ALPHA = 0.55f

// A category's settings rise this far as they fade in.
private val CategoryRise = 12.dp

// The panel slides in a quarter of its width as it fades in.
private const val PANEL_SLIDE_PART = 4

/**
 * Settings: categories on the left and the shown one's settings on the right, each with its value.
 * Resting on a category shows its settings; Right goes into them and Left comes back. Selecting a setting opens its
 * choices in a panel, and selecting one there calls [onChoose].
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    onChoose: (Setting, on: Boolean) -> Unit,
) = RememberFocus { focus -> SettingsPage(settings, focus, onChoose) }

@Composable
private fun SettingsPage(
    settings: Settings,
    focus: FocusMemory,
    onChoose: (Setting, on: Boolean) -> Unit,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    var shown by rememberSaveable { mutableStateOf(SettingsCategory.BROWSING) }
    var open by rememberSaveable { mutableStateOf<Setting?>(null) }
    // The setting whose panel just closed, to take focus back.
    var closed by remember { mutableStateOf<Setting?>(null) }
    val categories = remember { SettingsCategory.entries.associateWith { FocusRequester() } }
    val rows = remember { Setting.entries.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { focus.restoreOr { categories.getValue(shown).requestFocus() } }
    LaunchedEffect(closed) {
        closed?.let { rows.getValue(it).requestFocus() }
        closed = null
    }
    Box(Modifier.fillMaxSize().background(colors.ink)) {
        Text(
            stringResource(R.string.section_settings),
            style = ScreenTitle,
            color = colors.paper,
            modifier = Modifier.padding(start = dimensions.gridMargin, top = dimensions.safeY),
        )
        Column(
            Modifier.padding(start = dimensions.gridMargin, top = PaneTop).width(CategoryWidth),
            verticalArrangement = Arrangement.spacedBy(CategoryGap),
        ) {
            SettingsCategory.entries.forEach { category ->
                Category(
                    category,
                    selected = category == shown,
                    onFocused = { shown = category },
                    modifier =
                        Modifier
                            .focusRequester(categories.getValue(category))
                            .remembersFocus("category-${category.name}")
                            .focusProperties { right = rows.getValue(category.settings.first()) },
                )
            }
        }
        // Moving to another category fades the last settings out quickly, then fades the new ones in as they rise a little,
        // so the two never show at once.
        val rise = with(LocalDensity.current) { CategoryRise.roundToPx() }
        val out = dimensions.durationPanelMillis / 3
        val inMillis = dimensions.durationPanelMillis - out
        AnimatedContent(
            targetState = shown,
            modifier =
                Modifier
                    .padding(start = dimensions.gridMargin + SettingsOffset, end = dimensions.safeX, top = PaneTop)
                    .focusGroup(),
            transitionSpec = {
                (
                    fadeIn(tween(inMillis, delayMillis = out, easing = dimensions.easeStandard)) +
                        slideInVertically(tween(inMillis, delayMillis = out, easing = dimensions.easeStandard)) { rise }
                ).togetherWith(fadeOut(tween(out))).using(SizeTransform(clip = false))
            },
            label = "category",
        ) { category ->
            Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
                Text(stringResource(category.intro), style = Intro, color = colors.textMuted, modifier = Modifier.padding(bottom = 6.dp))
                category.settings.forEach { setting ->
                    SettingRow(
                        setting,
                        on = settings.isOn(setting),
                        onClick = { open = setting },
                        modifier =
                            Modifier
                                .focusRequester(rows.getValue(setting))
                                .remembersFocus("setting-${setting.name}")
                                .focusProperties { left = categories.getValue(category) },
                    )
                }
            }
        }
        // The panel fades in with the screen dimming behind it and slides a little way in from the right, and back out.
        // It keeps showing the setting it was opened for while it goes.
        var panelFor by remember { mutableStateOf(open) }
        if (open != null) panelFor = open
        val duration = tween<Float>(dimensions.durationPanelMillis, easing = dimensions.easeStandard)
        val slide = tween<IntOffset>(dimensions.durationPanelMillis, easing = dimensions.easeStandard)
        AnimatedVisibility(visible = open != null, modifier = Modifier.fillMaxSize(), enter = fadeIn(duration), exit = fadeOut(duration)) {
            val setting = panelFor ?: return@AnimatedVisibility
            val close = {
                open = null
                closed = setting
            }
            Box(Modifier.fillMaxSize()) {
                ChoicesPanel(
                    setting,
                    on = settings.isOn(setting),
                    active = open != null,
                    onChoose = { on ->
                        onChoose(setting, on)
                        close()
                    },
                    onClose = close,
                    modifier =
                        Modifier.animateEnterExit(
                            enter = slideInHorizontally(slide) { it / PANEL_SLIDE_PART },
                            exit = slideOutHorizontally(slide) { it / PANEL_SLIDE_PART },
                        ),
                )
            }
        }
    }
}

internal val ScreenTitle
    @Composable get() = NeoTheme.typography.headline.copy(fontSize = 26.sp, lineHeight = 32.sp)

private val PaneTitle
    @Composable get() = NeoTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp)

// The whole line, so the settings under the intro start where the design has them.
private val Intro
    @Composable get() = NeoTheme.typography.caption.copy(fontSize = 14.sp, lineHeight = 20.sp, lineHeightStyle = FullLine)

private val FullLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private val Name
    @Composable get() = NeoTheme.typography.label.copy(fontWeight = FontWeight.SemiBold)

private val Help
    @Composable get() = NeoTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp)

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
        shape = SelectableSurfaceDefaults.shape(CircleShape),
        colors =
            SelectableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = colors.textMuted,
                selectedContainerColor = colors.fillTonal,
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
                style = NeoTheme.typography.body.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
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
        scale = ClickableSurfaceDefaults.scale(focusedScale = ROW_FOCUS_SCALE),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, NeoTheme.dimensions.glowFocus)),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = RowPadding),
            horizontalArrangement = Arrangement.spacedBy(RowSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(text.title), style = Name)
                Text(stringResource(text.help), style = Help, color = LocalContentColor.current.copy(alpha = 0.72f))
            }
            val value = if (focused) colors.pinkDeep else colors.pink
            Text(
                stringResource(text.value(on)),
                style = NeoTheme.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = value,
            )
            Icon(NeoIcons.Chevron, contentDescription = null, tint = value, modifier = Modifier.size(Chevron))
        }
    }
}

/**
 * A setting's choices in a panel on the right, with focus on the current one. Focus stays in the panel until a choice
 * is selected, which calls [onChoose], or Back calls [onClose]. Once it isn't [active], as while it slides away, it
 * lets focus and Back go.
 */
@Composable
private fun BoxScope.ChoicesPanel(
    setting: Setting,
    on: Boolean,
    onChoose: (on: Boolean) -> Unit,
    onClose: () -> Unit,
    active: Boolean,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val choices = remember { mapOf(false to FocusRequester(), true to FocusRequester()) }
    val text = setting.text()
    LaunchedEffect(Unit) { choices.getValue(on).requestFocus() }
    // Ahead of the navigation's own, so Back closes the panel and stays on Settings.
    BackHandler(enabled = active, onBack = onClose)
    Box(Modifier.fillMaxSize().background(colors.scrimPlayer.copy(alpha = SCRIM_ALPHA)))
    Row(modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(PanelShadow)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = PANEL_SHADOW_ALPHA)))),
        )
        Column(
            Modifier
                .testTag(SETTINGS_PANEL_TAG)
                .fillMaxHeight()
                .width(PanelWidth)
                .background(colors.surface)
                .padding(horizontal = PanelPaddingX, vertical = NeoTheme.dimensions.safeY)
                .focusProperties { onExit = { if (active) cancelFocusChange() } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(PanelGap),
        ) {
            Text(stringResource(text.title), style = PaneTitle, color = colors.paper)
            Text(
                stringResource(text.help),
                style = NeoTheme.typography.caption.copy(lineHeight = 19.sp),
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            listOf(false, true).forEach { value ->
                Choice(
                    stringResource(text.value(value)),
                    selected = value == on,
                    onClick = { onChoose(value) },
                    modifier =
                        Modifier
                            .testTag(settingChoiceTag(setting, value))
                            .focusRequester(choices.getValue(value)),
                )
            }
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
        shape = SelectableSurfaceDefaults.shape(RoundedCornerShape(RowRadius)),
        colors =
            SelectableSurfaceDefaults.colors(
                containerColor = colors.surfaceRaised,
                contentColor = colors.paper,
                selectedContainerColor = colors.surfaceRaised,
                selectedContentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                focusedSelectedContainerColor = colors.focus,
                focusedSelectedContentColor = colors.onFocus,
            ),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1f),
        glow = SelectableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, NeoTheme.dimensions.glowFocus)),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = ChoicePadding),
            horizontalArrangement = Arrangement.spacedBy(RowSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The current value is a thick ring, ink when focused and pink otherwise; the others a thin one.
            val ring =
                when {
                    selected && focused -> colors.onFocus
                    selected -> colors.pink
                    focused -> colors.onFocusMuted
                    else -> colors.outline
                }
            Canvas(Modifier.size(Radio)) {
                val width = (if (selected) 5.dp else 2.dp).toPx()
                drawCircle(ring, radius = (size.minDimension - width) / 2, style = Stroke(width))
            }
            Text(label, style = Name)
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
