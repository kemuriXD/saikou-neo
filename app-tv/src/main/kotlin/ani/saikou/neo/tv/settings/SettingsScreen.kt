package ani.saikou.neo.tv.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import ani.saikou.neo.tv.ui.NeoIcons
import ani.saikou.neo.tv.ui.NeoTopBar
import ani.saikou.neo.tv.ui.TopTab

internal const val SETTINGS_TICK_TAG = "settings-tick"

internal fun settingsCategoryTag(category: SettingsCategory) = "settings-category-${category.name.lowercase()}"

internal fun settingTag(setting: Setting) = "setting-${setting.name.lowercase()}"

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
private val Tick = 16.dp
private val RowHeight = 62.dp
private val RowPadding = 18.dp
private val RowGap = 8.dp
private val RowGlow = 20.dp
private const val ROW_FOCUS_SCALE = 1.025f
private val SelectedFill = Color.White.copy(alpha = 0.16f)
private val FocusGlowColor = Color.White.copy(alpha = 0.32f)

/**
 * Settings: the top bar over categories on the left and the chosen one's settings on the right. Resting on a category
 * shows its settings; Right goes into them and Left comes back. Selecting a setting calls [onToggle].
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    onToggle: (Setting) -> Unit,
    onTab: (TopTab) -> Unit,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    var chosen by rememberSaveable { mutableStateOf(SettingsCategory.BROWSING) }
    val currentTab = remember { FocusRequester() }
    val categories = remember { SettingsCategory.entries.associateWith { FocusRequester() } }
    val firstSetting = remember { FocusRequester() }
    LaunchedEffect(Unit) { currentTab.requestFocus() }
    Box(Modifier.fillMaxSize().background(colors.ink)) {
        NeoTopBar(TopTab.SETTINGS, onTab, currentTab = currentTab, down = categories.getValue(chosen))
        Column(
            Modifier.padding(start = dimensions.gridMargin, top = PaneTop).width(CategoryWidth),
            verticalArrangement = Arrangement.spacedBy(CategoryGap),
        ) {
            Text(stringResource(R.string.tab_settings), style = NeoTheme.typography.headline, color = colors.paper)
            Spacer(Modifier.height(10.dp - CategoryGap))
            SettingsCategory.entries.forEach { category ->
                Category(
                    category,
                    selected = category == chosen,
                    onFocused = { chosen = category },
                    modifier =
                        Modifier
                            .focusRequester(categories.getValue(category))
                            .focusProperties { right = firstSetting },
                )
            }
        }
        Column(
            Modifier
                .padding(start = SettingsStart, end = dimensions.gridMargin, top = PaneTop)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            Text(stringResource(chosen.title), style = PaneTitle, color = colors.paper)
            Text(
                stringResource(chosen.intro),
                style = NeoTheme.typography.caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            chosen.settings.forEachIndexed { index, setting ->
                SettingRow(
                    setting,
                    on = settings.isOn(setting),
                    onClick = { onToggle(setting) },
                    modifier =
                        Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstSetting) else Modifier)
                            .focusProperties { left = categories.getValue(chosen) },
                )
            }
        }
    }
}

private val PaneTitle
    @Composable get() = NeoTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp)

/** A category: a fill and a pink tick when it's the one shown, paper when focused. */
@Composable
private fun Category(
    category: SettingsCategory,
    selected: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    var focused by remember { mutableStateOf(false) }
    Surface(
        selected = selected,
        onClick = onFocused,
        modifier =
            modifier
                .testTag(settingsCategoryTag(category))
                .fillMaxWidth()
                .height(CategoryHeight)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                },
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
        Row(
            Modifier.fillMaxSize().padding(horizontal = CategoryPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(category.title),
                style = NeoTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            )
            if (selected) {
                Icon(
                    NeoIcons.Tick,
                    contentDescription = null,
                    tint = if (focused) colors.pinkDeep else colors.pink,
                    modifier = Modifier.size(Tick).testTag(SETTINGS_TICK_TAG),
                )
            }
        }
    }
}

/** A setting: its name with a help line under it, and its value on the right. */
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
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, RowGlow)),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = RowPadding),
            horizontalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(text.title), style = NeoTheme.typography.labelLarge)
                Text(
                    stringResource(text.help),
                    style = NeoTheme.typography.caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = LocalContentColor.current.copy(alpha = 0.72f),
                )
            }
            Text(
                stringResource(if (on) text.on else text.off),
                style = NeoTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
                color = if (focused) colors.pinkDeep else colors.pink,
            )
        }
    }
}

private class SettingText(
    @param:StringRes val title: Int,
    @param:StringRes val help: Int,
    @param:StringRes val on: Int,
    @param:StringRes val off: Int,
)

private fun Setting.text() =
    when (this) {
        Setting.UNAIRED_ANIME ->
            SettingText(R.string.settings_unaired_anime, R.string.settings_unaired_anime_help, R.string.settings_shown, R.string.settings_hidden)
        Setting.UPCOMING_EPISODES ->
            SettingText(
                R.string.settings_upcoming_episodes,
                R.string.settings_upcoming_episodes_help,
                R.string.settings_shown,
                R.string.settings_hidden,
            )
        Setting.SLOW_TV -> SettingText(R.string.settings_slow_tv, R.string.settings_slow_tv_help, R.string.settings_on, R.string.settings_off)
    }
