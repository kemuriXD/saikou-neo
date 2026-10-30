package ani.saikou.neo.tv.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import ani.saikou.neo.shared.discover.DiscoverRow
import ani.saikou.neo.shared.discover.DiscoverState
import ani.saikou.neo.shared.home.RowState
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.details.PosterRowSection
import ani.saikou.neo.tv.details.seasonNames
import ani.saikou.neo.tv.home.HomeOffline
import ani.saikou.neo.tv.home.PosterSize
import ani.saikou.neo.tv.home.Skeleton
import ani.saikou.neo.tv.search.genreLabels
import ani.saikou.neo.tv.settings.ScreenTitle
import ani.saikou.neo.tv.theme.KeylineScrolling
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.RememberFocus

internal const val DISCOVER_SKELETON_TAG = "discover-skeleton"

// From the design, less the top bar: the title sits on the safe area as on Settings, and the rows start 80 dp down.
// Each is its heading, then its posters, which pad themselves 16 dp above and below for focus to grow into.
private val RowsTop = 80.dp
private val HeadingGap = 10.dp
private val PosterRoom = 16.dp

// The title and caption under a poster, so a row keeps its height once it loads.
private val PosterLines = 44.dp

/**
 * Discover: rows of posters by theme, this season and then a few genres. Selecting a poster calls [onOpen]. A row
 * pulses while it loads and is left out if it fails. When the TV is offline, Discover says so as Home does: Try again
 * calls [onRetry], Network settings calls [onNetworkSettings], and [onOfflineInView] says whether that's showing.
 */
@Composable
fun DiscoverScreen(
    state: DiscoverState,
    modifier: Modifier = Modifier,
    onOpen: (animeId: Int) -> Unit = {},
    onRetry: () -> Unit = {},
    onNetworkSettings: () -> Unit = {},
    onOfflineInView: (inView: Boolean) -> Unit = {},
) = RememberFocus { focus ->
    if (state.offline) {
        HomeOffline(onRetry, onNetworkSettings, onOfflineInView, modifier)
        return@RememberFocus
    }
    val dimensions = NeoTheme.dimensions
    val colors = NeoTheme.colors
    val shownRows = state.rows.filterValues { it != RowState.Failed }
    val entries = remember(state.rows.keys) { state.rows.keys.associateWith { FocusRequester() } }
    // Focus starts on the first row that loads, or comes back to the poster it left for Details.
    val firstLoaded = shownRows.entries.firstOrNull { it.value is RowState.Loaded }?.key
    LaunchedEffect(firstLoaded) {
        val row = firstLoaded ?: return@LaunchedEffect
        // A frame for the row to show before focus goes into it.
        withFrameNanos {}
        focus.restoreOr { entries[row]?.requestFocus() }
    }

    Box(modifier.fillMaxSize().background(colors.ink)) {
        Text(
            stringResource(R.string.section_discover),
            style = ScreenTitle,
            color = colors.paper,
            modifier = Modifier.padding(start = dimensions.gridMargin, top = dimensions.safeY),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = dimensions.gridMargin, top = RowsTop, bottom = dimensions.safeY),
        ) {
            shownRows.forEach { (row, rowState) ->
                item(key = row.toString()) {
                    Column(verticalArrangement = Arrangement.spacedBy(HeadingGap - PosterRoom)) {
                        Text(rowTitle(row), style = NeoTheme.typography.rowTitle, color = colors.paper)
                        if (rowState is RowState.Loaded) {
                            KeylineScrolling {
                                PosterRowSection(
                                    cards = rowState.items,
                                    onOpen = onOpen,
                                    focusKey = row.toString(),
                                    modifier = Modifier.focusRequester(entries.getValue(row)),
                                )
                            }
                        } else {
                            SkeletonPosters()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rowTitle(row: DiscoverRow): String =
    when (row) {
        is DiscoverRow.Season -> stringResource(seasonNames.getValue(row.season), row.year)
        is DiscoverRow.Genre -> stringResource(genreLabels.getValue(row.genre))
    }

/** Pulsing posters in a row's place while it loads, as on Home. */
@Composable
private fun SkeletonPosters() {
    val dimensions = NeoTheme.dimensions
    val size = PosterSize.Regular
    Row(
        modifier = Modifier.padding(top = PosterRoom, bottom = PosterRoom + PosterLines).testTag(DISCOVER_SKELETON_TAG),
        horizontalArrangement = Arrangement.spacedBy(dimensions.gridGutter),
    ) {
        repeat(7) { Skeleton(size.width, size.height, radius = size.radius) }
    }
}
