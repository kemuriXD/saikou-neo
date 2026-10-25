package ani.saikou.neo.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.home.More
import ani.saikou.neo.shared.home.SeeMoreState
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.KeylineScrolling
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton

internal const val POSTER_SKELETON_TAG = "poster-skeleton"

internal fun posterTag(index: Int) = "poster-$index"

private const val COLUMNS = 6

// More loads once focus is within two rows of the end.
private const val LOAD_AHEAD = COLUMNS * 2

// From the design: the title sits at 40 dp and the focused row rests at 104 dp, under it.
private val TitleTop = 40.dp
private val GridTop = 104.dp

// Rows scrolled up pass under the title, which keeps solid ink behind it.
private val TitleBand = 88.dp
private val TitleFade = 12.dp

/**
 * All of a Home [row] as a grid of posters. Selecting one calls [onOpen]. Focus nearing the end calls [onLoadMore];
 * Try again after a failed page calls [onRetry].
 */
@Composable
fun SeeMoreScreen(
    row: HomeRow,
    state: SeeMoreState,
    onOpen: (animeId: Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = NeoTheme.dimensions
    val ink = NeoTheme.colors.ink
    // The show with focus, so Back from Details and recreation return to it.
    var focusedId by rememberSaveable { mutableStateOf<Int?>(null) }
    // Not saved: after recreation, focus has to be placed again.
    var hasFocus by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val items = state.items
    val target = if (hasFocus) null else focusedId?.takeIf { id -> items.any { it.id == id } } ?: items.firstOrNull()?.id

    Box(modifier.fillMaxSize().background(ink)) {
        if (items.isEmpty() && state.more == More.Failed) {
            LoadFailed(onRetry, Modifier.padding(start = dimensions.gridMargin, top = GridTop))
        } else {
            KeylineScrolling(keyline = GridTop) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = dimensions.gridMargin,
                            end = dimensions.gridMargin,
                            top = GridTop,
                            bottom = 40.dp,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.gridGutter),
                    verticalArrangement = Arrangement.spacedBy(dimensions.gridGutter),
                ) {
                    if (items.isEmpty()) {
                        items(LOAD_AHEAD) {
                            Skeleton(
                                dimensions.cardPosterWidth,
                                dimensions.cardPosterHeight,
                                Modifier.testTag(POSTER_SKELETON_TAG),
                                dimensions.radiusSm,
                            )
                        }
                    }
                    itemsIndexed(items, key = { _, card -> card.id }) { index, card ->
                        val requester = remember { FocusRequester() }
                        if (card.id == target) LaunchedEffect(Unit) { requester.requestFocus() }
                        PosterCard(
                            card = card,
                            onClick = { onOpen(card.id) },
                            modifier =
                                Modifier
                                    .testTag(posterTag(index))
                                    .focusRequester(requester)
                                    .onFocusChanged {
                                        if (!it.isFocused) return@onFocusChanged
                                        focusedId = card.id
                                        hasFocus = true
                                        if (index >= items.size - LOAD_AHEAD) onLoadMore()
                                    },
                        )
                    }
                    if (items.isNotEmpty() && state.more == More.Failed) {
                        item(span = { GridItemSpan(2) }) {
                            LoadMoreFailed(onRetry = {
                                // The button goes once the page loads again, so focus returns to the last show.
                                focusedId = items.last().id
                                hasFocus = false
                                onRetry()
                            })
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(TitleBand + TitleFade)
                .background(Brush.verticalGradient(0f to ink, TitleBand / (TitleBand + TitleFade) to ink, 1f to ink.copy(alpha = 0f))),
        )
        Text(
            text = stringResource(rowTitles.getValue(row)),
            style = NeoTheme.typography.headline,
            color = NeoTheme.colors.paper,
            modifier = Modifier.padding(start = dimensions.gridMargin, top = TitleTop),
        )
    }
}

/** The first page couldn't load: what went wrong and Try again, as on Home. */
@Composable
private fun LoadFailed(
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val retry = remember { FocusRequester() }
    LaunchedEffect(Unit) { retry.requestFocus() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.see_more_failed), style = NeoTheme.typography.titleLarge, color = NeoTheme.colors.paper)
        Text(stringResource(R.string.see_more_failed_hint), style = NeoTheme.typography.bodyLarge, color = NeoTheme.colors.textMuted)
        NeoButton(stringResource(R.string.try_again), onClick = onRetry, modifier = Modifier.padding(top = 10.dp).focusRequester(retry))
    }
}

/** After the last show when a later page couldn't load. The shows already there stay. */
@Composable
private fun LoadMoreFailed(onRetry: () -> Unit) {
    Column(
        Modifier.height(NeoTheme.dimensions.cardPosterHeight).padding(start = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.see_more_failed_more), style = NeoTheme.typography.body, color = NeoTheme.colors.textMuted)
        NeoButton(stringResource(R.string.try_again), onClick = onRetry)
    }
}
