package ani.saikou.neo.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.home.HomeState
import ani.saikou.neo.shared.home.RowState
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.KeylineScrolling
import ani.saikou.neo.tv.theme.NeoTheme
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.Serializable

internal fun homeCardTag(
    row: HomeRow,
    index: Int,
) = "${row.name.lowercase()}-card-$index"

internal const val SPOTLIGHT_TITLE_TAG = "spotlight-title"

private val rowTitles =
    mapOf(
        HomeRow.TRENDING to R.string.home_row_trending,
        HomeRow.THIS_SEASON to R.string.home_row_this_season,
        HomeRow.POPULAR to R.string.home_row_popular,
    )

// From the design: the spotlight starts 96 dp down, and the focused row's heading rests at 300 dp.
private val SpotlightTop = 96.dp
private val SpotlightWidth = 420.dp
private val RowsTop = 300.dp

// A row is its 24 dp heading, a 10 dp gap and a 110 dp card. The next row's heading peeks at 478 dp.
private val HeadingToCards = 34.dp
private val RowGap = 34.dp

// Room below the last row so it can scroll up to the same line: 540 - 300 - (34 + 110).
private val LastRowRoom = 96.dp

private val FullLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** Which card, so focus and the spotlight survive Home being recreated. */
private data class CardKey(
    val row: HomeRow,
    val index: Int,
) : Serializable

/** Home: a spotlight on the focused show, over rows of anime that browse left and right. */
@Composable
fun HomeScreen(
    state: HomeState,
    modifier: Modifier = Modifier,
) {
    var focused by rememberSaveable { mutableStateOf<CardKey?>(null) }
    // Not saved: after recreation, focus has to be placed again on the saved card.
    var hasFocus by remember { mutableStateOf(false) }
    val target = focused ?: state.firstCard()
    val spotlight = target?.let { state.card(it) }
    val columnState = rememberLazyListState()
    // Held here, not in the rows, so a row scrolled out of view keeps its place.
    val rowStates = HomeRow.entries.associateWith { rememberLazyListState() }

    Box(modifier.fillMaxSize().background(NeoTheme.colors.ink)) {
        Backdrop(spotlight)
        Spotlight(spotlight, Modifier.padding(start = NeoTheme.dimensions.gridMargin, top = SpotlightTop).width(SpotlightWidth))
        KeylineScrolling(keyline = HeadingToCards) {
            LazyColumn(
                state = columnState,
                modifier = Modifier.fillMaxSize().padding(top = RowsTop),
                verticalArrangement = Arrangement.spacedBy(RowGap),
                contentPadding = PaddingValues(bottom = LastRowRoom),
            ) {
                for (row in HomeRow.entries) {
                    val rowState = state.rows[row]
                    if (rowState == null || rowState == RowState.Failed) continue
                    item(key = row.name) {
                        HomeRowSection(
                            row = row,
                            rowState = rowState,
                            listState = rowStates.getValue(row),
                            isFocusedRow = hasFocus && focused?.row == row,
                            initialFocus = target.takeIf { !hasFocus && it?.row == row }?.index,
                            onCardFocused = { index ->
                                focused = CardKey(row, index)
                                hasFocus = true
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRowSection(
    row: HomeRow,
    rowState: RowState,
    listState: LazyListState,
    isFocusedRow: Boolean,
    initialFocus: Int?,
    onCardFocused: (Int) -> Unit,
) {
    val dimensions = NeoTheme.dimensions
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(rowTitles.getValue(row)),
            // Keep the whole 24 sp line, as the design spaces rows by it.
            style = NeoTheme.typography.rowTitle.copy(lineHeightStyle = FullLine),
            color = if (isFocusedRow) NeoTheme.colors.paper else NeoTheme.colors.textMuted,
            modifier = Modifier.padding(start = dimensions.gridMargin),
        )
        if (rowState !is RowState.Loaded) {
            SkeletonCards()
            return@Column
        }
        KeylineScrolling {
            LazyRow(
                state = listState,
                // Coming back to a row lands on the card you left, not the one nearest.
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(horizontal = dimensions.gridMargin),
                horizontalArrangement = Arrangement.spacedBy(dimensions.gridGutter),
            ) {
                itemsIndexed(rowState.items, key = { _, card -> card.id }) { index, card ->
                    val requester = remember { FocusRequester() }
                    if (index == initialFocus) LaunchedEffect(Unit) { requester.requestFocus() }
                    HomeCard(
                        card = card,
                        // Details arrive in #68.
                        onClick = {},
                        modifier =
                            Modifier
                                .testTag(homeCardTag(row, index))
                                .focusRequester(requester)
                                .onFocusChanged { if (it.isFocused) onCardFocused(index) },
                    )
                }
            }
        }
    }
}

/** Static placeholders in the card's place while a row loads, so nothing moves when it arrives. */
@Composable
private fun SkeletonCards() {
    val dimensions = NeoTheme.dimensions
    Row(
        modifier = Modifier.padding(start = dimensions.gridMargin),
        horizontalArrangement = Arrangement.spacedBy(dimensions.gridGutter),
    ) {
        repeat(5) {
            Skeleton(dimensions.cardWideWidth, dimensions.cardWideHeight, dimensions.radiusMd)
        }
    }
}

@Composable
private fun Skeleton(
    width: Dp,
    height: Dp,
    radius: Dp = 6.dp,
) = Box(Modifier.size(width, height).background(NeoTheme.colors.surfaceRaised, RoundedCornerShape(radius)))

@Composable
private fun Spotlight(
    card: AnimeCard?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (card == null) {
            Skeleton(300.dp, 56.dp, NeoTheme.dimensions.radiusSm)
            Skeleton(200.dp, 16.dp)
            return@Column
        }
        Text(
            text = card.displayTitle(),
            style = NeoTheme.typography.display,
            color = NeoTheme.colors.paper,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(SPOTLIGHT_TITLE_TAG),
        )
        Text(
            text = card.meta(),
            style = NeoTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = NeoTheme.colors.paper,
        )
    }
}

/** The spotlit show's art behind everything, faded into ink on the left and bottom so text stays readable. */
@Composable
private fun Backdrop(card: AnimeCard?) {
    val ink = NeoTheme.colors.ink
    val art = card?.let { it.bannerImageUrl ?: it.coverImageUrl }
    val request =
        ImageRequest
            .Builder(LocalPlatformContext.current)
            .data(art)
            .crossfade(NeoTheme.dimensions.durationBackdropMillis)
            .build()
    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f to ink.copy(alpha = 0.88f),
                0.34f to ink.copy(alpha = 0.6f),
                0.62f to ink.copy(alpha = 0.08f),
                1f to ink.copy(alpha = 0f),
            ),
        ),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to ink.copy(alpha = 0.65f),
                0.18f to ink.copy(alpha = 0f),
                0.52f to ink.copy(alpha = 0f),
                0.77f to ink.copy(alpha = 0.85f),
                1f to ink,
            ),
        ),
    )
}

private fun HomeState.card(key: CardKey): AnimeCard? = (rows[key.row] as? RowState.Loaded)?.items?.getOrNull(key.index)

/** The first card of the first row that has loaded. */
private fun HomeState.firstCard(): CardKey? =
    HomeRow.entries
        .firstOrNull { (rows[it] as? RowState.Loaded)?.items?.isNotEmpty() == true }
        ?.let { CardKey(it, 0) }
