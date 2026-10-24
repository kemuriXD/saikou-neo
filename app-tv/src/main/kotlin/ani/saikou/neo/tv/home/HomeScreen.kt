package ani.saikou.neo.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.delay
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

private const val BACKDROP_SETTLE_MILLIS = 300L

// A row is its 24 dp heading, a 10 dp gap and a 110 dp card. The next row's heading peeks at 478 dp.
private val HeadingToCards = 34.dp
private val RowGap = 34.dp

// Room below the last row so it can scroll up to the same line: 540 - 300 - (34 + 110).
private val LastRowRoom = 96.dp

private val FullLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private val LastCardsSaver =
    listSaver<SnapshotStateMap<HomeRow, Int>, Int>(
        save = { lastCards -> lastCards.flatMap { (row, animeId) -> listOf(row.ordinal, animeId) } },
        restore = { saved ->
            mutableStateMapOf<HomeRow, Int>().apply {
                saved.chunked(2).forEach { (row, animeId) -> put(HomeRow.entries[row], animeId) }
            }
        },
    )

/** Which anime, and where it was in its row, so focus and the spotlight survive Home being recreated. */
private data class CardKey(
    val row: HomeRow,
    val animeId: Int,
    val index: Int,
) : Serializable

/** Home: a spotlight on the focused show, over rows of anime that browse left and right. Selecting one calls [onOpen]. */
@Composable
fun HomeScreen(
    state: HomeState,
    modifier: Modifier = Modifier,
    onOpen: (animeId: Int) -> Unit = {},
) {
    var focused by rememberSaveable { mutableStateOf<CardKey?>(null) }
    // The anime each row last had focus on, so moving up and down returns to it.
    val lastCards = rememberSaveable(saver = LastCardsSaver) { mutableStateMapOf<HomeRow, Int>() }
    // Not saved: after recreation, focus has to be placed again on the saved card.
    var hasFocus by remember { mutableStateOf(false) }
    val columnState = rememberLazyListState()
    // Held here, not in the rows, so a row scrolled out of view keeps its place.
    val rowStates = HomeRow.entries.associateWith { rememberLazyListState() }
    // Where focus goes when nothing has it yet. Null while the row it belongs in is still loading.
    val target = if (hasFocus) null else state.focusTarget(focused)
    val spotlight = (if (hasFocus) focused else target)?.let { state.card(it) }
    val shownRows = HomeRow.entries.filter { state.rows[it].let { rowState -> rowState != null && rowState != RowState.Failed } }
    // A row that failed after recreation can shift the restored scroll, so bring the target's row up first.
    LaunchedEffect(target?.row) {
        val row = target?.row ?: return@LaunchedEffect
        columnState.scrollToItem(shownRows.indexOf(row))
    }

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
                for (row in shownRows) {
                    val rowState = state.rows.getValue(row)
                    item(key = row.name) {
                        HomeRowSection(
                            row = row,
                            rowState = rowState,
                            listState = rowStates.getValue(row),
                            isFocusedRow = hasFocus && focused?.row == row,
                            initialFocus = target?.takeIf { it.row == row }?.animeId,
                            lastCard = lastCards[row],
                            onOpen = onOpen,
                            onCardFocused = { animeId, index ->
                                focused = CardKey(row, animeId, index)
                                lastCards[row] = animeId
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
    lastCard: Int?,
    onOpen: (animeId: Int) -> Unit,
    onCardFocused: (animeId: Int, index: Int) -> Unit,
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
        // Up and down enter a row on the card you left, or its first card, not the one nearest on screen.
        val entryCard = lastCard?.takeIf { id -> rowState.items.any { it.id == id } } ?: rowState.items.firstOrNull()?.id
        val entry = remember { FocusRequester() }
        KeylineScrolling {
            LazyRow(
                state = listState,
                modifier =
                    Modifier
                        .focusProperties {
                            onEnter = {
                                if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
                                    entry.requestFocus()
                                }
                            }
                        }.focusGroup(),
                contentPadding = PaddingValues(horizontal = dimensions.gridMargin),
                horizontalArrangement = Arrangement.spacedBy(dimensions.gridGutter),
            ) {
                itemsIndexed(rowState.items, key = { _, card -> card.id }) { index, card ->
                    val requester = remember { FocusRequester() }
                    if (card.id == initialFocus) LaunchedEffect(Unit) { requester.requestFocus() }
                    HomeCard(
                        card = card,
                        onClick = { onOpen(card.id) },
                        modifier =
                            Modifier
                                .testTag(homeCardTag(row, index))
                                .focusRequester(requester)
                                .then(if (card.id == entryCard) Modifier.focusRequester(entry) else Modifier)
                                .onFocusChanged { if (it.isFocused) onCardFocused(card.id, index) },
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
    // Fetch the full-screen art only once focus rests, not for every card passed on the way.
    var resting by remember { mutableStateOf(art) }
    LaunchedEffect(art) {
        delay(BACKDROP_SETTLE_MILLIS)
        resting = art
    }
    // The last art that finished loading stays underneath, so the screen doesn't flash to ink in between.
    var shown by remember { mutableStateOf<String?>(null) }
    val context = LocalPlatformContext.current
    val duration = NeoTheme.dimensions.durationBackdropMillis
    val request =
        remember(resting) {
            ImageRequest
                .Builder(context)
                .data(resting)
                .crossfade(duration)
                .build()
        }
    AsyncImage(model = shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onSuccess = { shown = resting },
        modifier = Modifier.fillMaxSize(),
    )
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

private fun HomeState.card(key: CardKey): AnimeCard? = (rows[key.row] as? RowState.Loaded)?.items?.firstOrNull { it.id == key.animeId }

/**
 * The saved card if it's still there, or the card now in its place if its row reloaded without it.
 * With nothing saved, the first card of the first row that loads, waiting for rows above it.
 */
private fun HomeState.focusTarget(saved: CardKey?): CardKey? {
    val rows = if (saved != null) listOf(saved.row) + HomeRow.entries else HomeRow.entries
    for (row in rows) {
        val items =
            when (val rowState = this.rows[row]) {
                RowState.Loading -> return null
                is RowState.Loaded -> rowState.items
                else -> emptyList()
            }
        if (items.isEmpty()) continue
        if (row != saved?.row) return CardKey(row, items.first().id, 0)
        if (items.any { it.id == saved.animeId }) return saved
        val index = saved.index.coerceAtMost(items.lastIndex)
        return CardKey(row, items[index].id, index)
    }
    return null
}
