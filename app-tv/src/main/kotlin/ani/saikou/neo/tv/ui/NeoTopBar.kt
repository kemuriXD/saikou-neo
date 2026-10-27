package ani.saikou.neo.tv.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme

internal const val TOP_BAR_TAG = "top-bar"
internal const val TOP_BAR_LOGO_TAG = "top-bar-logo"

internal fun topTabTag(tab: TopTab) = "top-tab-${tab.name.lowercase()}"

/** The screens the top bar moves between. */
enum class TopTab(
    @param:StringRes val label: Int,
) {
    HOME(R.string.tab_home),
    DISCOVER(R.string.tab_discover),
    SEARCH(R.string.tab_search),
    SETTINGS(R.string.tab_settings),
}

// From the design: the bar is 44 dp tall, 18 dp from the top and 48 dp in from each side, with the tabs 4 dp apart.
private val BarTop = 18.dp
private val BarHeight = 44.dp
private val BarMargin = 48.dp
private val TabGap = 4.dp
private val SymbolHeight = 26.dp

// A tab's name sits 88 dp down, under the bar, with a line under it.
private val PlaceholderTop = 88.dp
private val PlaceholderGap = 6.dp

/** How far down the screen the bar reaches, to slide it out of sight. */
val TopBarBottom = BarTop + BarHeight

/**
 * The logo, with the [current] tab and the others in the middle. Focus coming into the bar lands on the current tab,
 * and [down] is where Down from it goes. Selecting a tab calls [onSelect]. The right side is kept for the Library
 * button, which comes with signing in.
 */
@Composable
fun NeoTopBar(
    current: TopTab,
    onSelect: (TopTab) -> Unit,
    modifier: Modifier = Modifier,
    currentTab: FocusRequester = remember { FocusRequester() },
    down: FocusRequester? = null,
) {
    Box(
        modifier
            .testTag(TOP_BAR_TAG)
            .fillMaxWidth()
            .padding(start = BarMargin, end = BarMargin, top = BarTop)
            .height(BarHeight),
    ) {
        Image(
            painterResource(R.drawable.saikou_neo_symbol),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterStart).height(SymbolHeight).testTag(TOP_BAR_LOGO_TAG),
        )
        Row(
            Modifier
                .align(Alignment.Center)
                .focusProperties { onEnter = { currentTab.requestFocus() } }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(TabGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopTab.entries.forEach { tab ->
                NeoTab(
                    text = stringResource(tab.label),
                    selected = tab == current,
                    onClick = { onSelect(tab) },
                    modifier =
                        Modifier
                            .testTag(topTabTag(tab))
                            .then(if (tab == current) Modifier.focusRequester(currentTab) else Modifier)
                            .then(if (down != null) Modifier.focusProperties { this.down = down } else Modifier),
                )
            }
        }
    }
}

/** A tab whose screen isn't built yet: the bar, with focus on [tab], over the tab's name and a line saying it's coming. */
@Composable
fun TabPlaceholder(
    tab: TopTab,
    onSelect: (TopTab) -> Unit,
) {
    val colors = NeoTheme.colors
    val currentTab = remember { FocusRequester() }
    LaunchedEffect(Unit) { currentTab.requestFocus() }
    Box(Modifier.fillMaxSize().background(colors.ink)) {
        NeoTopBar(tab, onSelect, currentTab = currentTab)
        Column(
            Modifier.padding(start = NeoTheme.dimensions.gridMargin, top = PlaceholderTop),
            verticalArrangement = Arrangement.spacedBy(PlaceholderGap),
        ) {
            Text(stringResource(tab.label), style = NeoTheme.typography.headline, color = colors.paper)
            Text(stringResource(R.string.tab_coming_soon), style = NeoTheme.typography.bodyLarge, color = colors.textMuted)
        }
    }
}
