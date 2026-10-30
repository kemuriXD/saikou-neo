package ani.saikou.neo.tv.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.home.HomeState
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.shared.home.RowState
import ani.saikou.neo.shared.home.Spotlight
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.KeylineScrolling
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.theme.WindowScrolling
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoIcons
import ani.saikou.neo.tv.ui.PreloadLogo
import ani.saikou.neo.tv.ui.RailWidth
import ani.saikou.neo.tv.ui.logoRequest
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.Serializable

internal const val BACKDROP_ART_TAG = "backdrop-art"

// The longest the first show waits to come in one go.
private const val FIRST_SHOW_WAIT_MILLIS = 5_000L

internal fun seeMoreTag(row: HomeRow) = "${row.name.lowercase()}-see-more"

// Where focus is saved as an anime id, the See more button at a row's end.
private const val SEE_MORE = -1

internal fun homeCardTag(
    row: HomeRow,
    index: Int,
) = "${row.name.lowercase()}-card-$index"

internal val rowTitles =
    mapOf(
        HomeRow.CONTINUE_WATCHING to R.string.home_row_continue_watching,
        HomeRow.TRENDING to R.string.home_row_trending,
        HomeRow.THIS_SEASON to R.string.home_row_this_season,
        HomeRow.POPULAR to R.string.home_row_popular,
    )

// From the design: the spotlight's dots sit at 344 dp and the rows at 382 dp, so the first row's cards fit whole under
// it. Once focus is in the rows they move up so the first heading is at 30 dp, and three whole rows fill the screen.
// The spotlight may reach up to 70 dp, room for the line saying why a show is featured, an 80 dp logo, the meta line,
// two lines of story and the buttons. The logo box is 10 dp taller than the design's title, so this is 6 dp higher.
private val SpotlightTop = 70.dp

// The spotlight's buttons end 24 dp above the dots, with its text stacked on top of them.
private val SpotlightBottom = 320.dp

// Content starts 24 dp clear of the closed rail.
private val ContentStart = RailWidth + 24.dp
private val SpotlightWidth = 440.dp
private val DotsTop = 344.dp
private val RowsTop = 382.dp
private val BrowseTop = 30.dp

// How dark the art gets behind the rows.
private const val DIM_ALPHA = 0.55f

// A row is its 24 dp heading, a 10 dp gap and a 110 dp card.
private val HeadingToCards = 34.dp
private val RowGap = 24.dp

// Room below the last row's cards, which end at 510 dp: 540 - 30 - 3 * (34 + 110) - 2 * 24.
private val LastRowRoom = 30.dp
private const val ROWS_ON_SCREEN = 3

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

/**
 * Home: a spotlight on featured shows, over rows of anime that browse left and right. Selecting a show calls [onOpen];
 * the spotlight's dots call [onPick], and the button at the end of each row calls [onSeeMore]. [onSpotlightInView] says whether the spotlight is showing, so it only moves on
 * while seen. [onRest] gets the card focus is on, or null off the cards, so resting on one can find its art.
 * When the TV is offline, Home says so instead: Try again calls [onRetry], Network settings calls [onNetworkSettings],
 * and [onOfflineInView] says whether that's showing.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    modifier: Modifier = Modifier,
    onOpen: (animeId: Int) -> Unit = {},
    onPick: (index: Int) -> Unit = {},
    onSpotlightInView: (inView: Boolean) -> Unit = {},
    onSeeMore: (row: HomeRow) -> Unit = {},
    onRest: (card: AnimeCard?) -> Unit = {},
    onRetry: () -> Unit = {},
    onNetworkSettings: () -> Unit = {},
    onOfflineInView: (inView: Boolean) -> Unit = {},
) {
    if (state.offline) {
        HomeOffline(onRetry, onNetworkSettings, onOfflineInView, modifier)
        return
    }
    // The card with focus, or null while the spotlight has it.
    var focused by rememberSaveable { mutableStateOf<CardKey?>(null) }
    // The anime each row last had focus on, so moving up and down returns to it.
    val lastCards = rememberSaveable(saver = LastCardsSaver) { mutableStateMapOf<HomeRow, Int>() }
    // Not saved: after recreation, focus has to be placed again on the saved card.
    var hasFocus by remember { mutableStateOf(false) }
    val columnState = rememberLazyListState()
    // Held here, not in the rows, so a row scrolled out of view keeps its place.
    val rowStates = HomeRow.entries.associateWith { rememberLazyListState() }
    // Each row's way in, on the card it last had.
    val rowEntries = remember { HomeRow.entries.associateWith { FocusRequester() } }
    val spotlight = rememberFirstShow(state.spotlight)
    val play = remember { FocusRequester() }
    val dots = remember { FocusRequester() }
    // Where focus goes when nothing has it yet: the spotlight's Play button, there even while loading, or the saved card
    // if focus was in the rows. Null while the saved card's row is still loading.
    val focusPlay = !hasFocus && focused == null
    val target = if (hasFocus || focusPlay) null else state.focusTarget(focused)
    if (focusPlay) LaunchedEffect(Unit) { play.requestFocus() }
    val inRows = focused != null
    val dimensions = NeoTheme.dimensions
    val shown by animateFloatAsState(
        targetValue = if (inRows) 0f else 1f,
        animationSpec = tween(dimensions.durationBackdropMillis, easing = dimensions.easeStandard),
        label = "spotlight",
    )
    val rowsTop by animateDpAsState(
        targetValue = if (inRows) BrowseTop else RowsTop,
        animationSpec = tween(dimensions.durationBackdropMillis, easing = dimensions.easeStandard),
        label = "rows",
    )
    DisposableEffect(inRows) {
        onSpotlightInView(!inRows)
        onDispose {}
    }
    DisposableEffect(Unit) { onDispose { onSpotlightInView(false) } }
    // Behind the rows, the spotlight fades out and TalkBack skips it.
    val hide =
        Modifier.graphicsLayer { alpha = shown }.then(if (inRows) Modifier.semantics { hideFromAccessibility() } else Modifier)
    // Continue watching is left out with nothing in it.
    val nothingToContinue = state.rows[HomeRow.CONTINUE_WATCHING] == RowState.Loaded(emptyList())
    val shownRows =
        HomeRow.entries.filter {
            state.rows[it].let { rowState -> rowState != null && rowState != RowState.Failed } &&
                !(it == HomeRow.CONTINUE_WATCHING && nothingToContinue)
        }
    // A row that failed after recreation can shift the restored scroll, so bring the target's row on screen first.
    LaunchedEffect(target?.row) {
        val row = target?.row ?: return@LaunchedEffect
        columnState.scrollToItem((shownRows.indexOf(row) - (ROWS_ON_SCREEN - 1)).coerceAtLeast(0))
    }

    Box(modifier.fillMaxSize().background(NeoTheme.colors.ink)) {
        Backdrop(spotlight?.let { it.backdrop(it.show) })
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - shown }.background(NeoTheme.colors.ink.copy(alpha = DIM_ALPHA)))
        Box(
            Modifier.fillMaxSize().onFocusChanged {
                if (it.hasFocus) {
                    focused = null
                    hasFocus = true
                    onRest(null)
                }
            },
        ) {
            val place =
                Modifier
                    .padding(start = ContentStart, top = SpotlightTop)
                    .size(SpotlightWidth, SpotlightBottom - SpotlightTop)
            SpotlightShow(spotlight, state.progress, play, dots, onOpen, place.then(hide))
            // Centred beside the rail, as in the design.
            val dotsPlace = Modifier.align(Alignment.TopCenter).padding(start = RailWidth, top = DotsTop)
            if (spotlight == null) {
                SpotlightDotsPlaceholder(dotsPlace)
            } else {
                PreloadSpotlight(state.spotlight ?: spotlight)
                PreloadLogo(state.restingLogo)
                SpotlightDots(spotlight, dots, play, onPick, dotsPlace.then(hide))
            }
        }
        // Scrolls only to show the focused row whole. The list keeps its size and is moved down under the spotlight,
        // so the space it scrolls in doesn't change while the rows come up.
        WindowScrolling(leading = HeadingToCards, trailing = LastRowRoom) {
            LazyColumn(
                state = columnState,
                // Back from the rail, focus returns to the card it was on.
                modifier =
                    Modifier
                        .focusProperties {
                            onEnter = {
                                val card = focused
                                if (requestedFocusDirection == FocusDirection.Enter && card != null) {
                                    rowEntries.getValue(card.row).requestFocus()
                                }
                            }
                        }.offset { IntOffset(0, (rowsTop - BrowseTop).roundToPx()) }
                        .fillMaxSize()
                        .padding(top = BrowseTop),
                verticalArrangement = Arrangement.spacedBy(RowGap),
                contentPadding = PaddingValues(bottom = LastRowRoom),
            ) {
                for (row in shownRows) {
                    val rowState = state.rows.getValue(row)
                    item(key = row.name) {
                        HomeRowSection(
                            row = row,
                            rowState = rowState,
                            progress = state.progress,
                            listState = rowStates.getValue(row),
                            isFocusedRow = hasFocus && focused?.row == row,
                            initialFocus = target?.takeIf { it.row == row }?.animeId,
                            lastCard = lastCards[row],
                            entry = rowEntries.getValue(row),
                            // Up from the first row goes to the spotlight's dots, wherever they are.
                            up = dots.takeIf { spotlight != null && row == shownRows.first() },
                            onOpen = onOpen,
                            onSeeMore = { onSeeMore(row) },
                            onCardFocused = { animeId, index ->
                                focused = CardKey(row, animeId, index)
                                lastCards[row] = animeId
                                hasFocus = true
                                onRest((rowState as? RowState.Loaded)?.items?.firstOrNull { it.id == animeId })
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
    progress: Map<Int, WatchProgress>,
    listState: LazyListState,
    isFocusedRow: Boolean,
    initialFocus: Int?,
    lastCard: Int?,
    up: FocusRequester?,
    entry: FocusRequester,
    onOpen: (animeId: Int) -> Unit,
    onSeeMore: () -> Unit,
    onCardFocused: (animeId: Int, index: Int) -> Unit,
) {
    val dimensions = NeoTheme.dimensions
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(rowTitles.getValue(row)),
            // Keep the whole 24 sp line, as the design spaces rows by it.
            style = NeoTheme.typography.rowTitle.copy(lineHeightStyle = FullLine),
            color = if (isFocusedRow) NeoTheme.colors.paper else NeoTheme.colors.textMuted,
            modifier = Modifier.padding(start = ContentStart),
        )
        if (rowState !is RowState.Loaded) {
            SkeletonCards()
            return@Column
        }
        // Up and down enter a row on the card you left, or its first card, not the one nearest on screen.
        val entryCard = lastCard?.takeIf { id -> id == SEE_MORE || rowState.items.any { it.id == id } } ?: rowState.items.firstOrNull()?.id
        KeylineScrolling(keyline = ContentStart) {
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
                contentPadding = PaddingValues(start = ContentStart, end = dimensions.gridMargin),
                horizontalArrangement = Arrangement.spacedBy(dimensions.gridGutter),
            ) {
                // A card, or the See more button as SEE_MORE: focus is placed, entered and saved the same way.
                @Composable
                fun Modifier.rowItem(
                    animeId: Int,
                    index: Int,
                ): Modifier {
                    val requester = remember { FocusRequester() }
                    if (animeId == initialFocus) LaunchedEffect(Unit) { requester.requestFocus() }
                    return focusRequester(requester)
                        .then(if (animeId == entryCard) Modifier.focusRequester(entry) else Modifier)
                        .then(if (up != null) Modifier.focusProperties { this.up = up } else Modifier)
                        .onFocusChanged { if (it.isFocused) onCardFocused(animeId, index) }
                }
                itemsIndexed(rowState.items, key = { _, card -> card.id }) { index, card ->
                    val cardModifier = Modifier.testTag(homeCardTag(row, index)).rowItem(card.id, index)
                    val watched = progress[card.id]?.takeIf { row == HomeRow.CONTINUE_WATCHING }
                    if (watched != null) {
                        ContinueCard(card, watched, onClick = { onOpen(card.id) }, modifier = cardModifier)
                    } else {
                        HomeCard(card = card, onClick = { onOpen(card.id) }, modifier = cardModifier)
                    }
                }
                // Continue watching is all there is, so it has no See more.
                if (row != HomeRow.CONTINUE_WATCHING) {
                    item(key = SEE_MORE) {
                        Box(Modifier.height(dimensions.cardWideHeight), contentAlignment = Alignment.Center) {
                            NeoButton(
                                text = stringResource(R.string.home_see_more),
                                onClick = onSeeMore,
                                trailingIcon = NeoIcons.Arrow,
                                modifier = Modifier.testTag(seeMoreTag(row)).rowItem(SEE_MORE, rowState.items.size),
                            )
                        }
                    }
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
        modifier = Modifier.padding(start = ContentStart),
        horizontalArrangement = Arrangement.spacedBy(dimensions.gridGutter),
    ) {
        repeat(5) {
            Skeleton(dimensions.cardWideWidth, dimensions.cardWideHeight, radius = dimensions.radiusMd)
        }
    }
}

@Composable
internal fun Skeleton(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    radius: Dp = 6.dp,
) = Box(modifier.size(width, height).background(NeoTheme.colors.surfaceRaised, RoundedCornerShape(radius)))

/** Starts loading every featured show's art and logo, so each fades in as soon as the spotlight moves on. */
@Composable
private fun PreloadSpotlight(spotlight: Spotlight) {
    val context = LocalPlatformContext.current
    LaunchedEffect(spotlight.shows, spotlight.logos, spotlight.fanArt) {
        val loader = SingletonImageLoader.get(context)
        spotlight.shows.forEach { show ->
            spotlight.backdrop(show)?.let { loader.enqueue(ImageRequest.Builder(context).data(it).build()) }
            (spotlight.logo(show.id) as? Logo.Image)?.let { loader.enqueue(logoRequest(context, it.url)) }
        }
    }
}

/**
 * [spotlight] once its first show can come in one go: its story and reason, backdrop and logo. Until then null, so the
 * spotlight keeps its placeholders. After five seconds it comes anyway, with the title in words if the logo hasn't
 * loaded. Later shows load while the one before is showing.
 */
@Composable
private fun rememberFirstShow(spotlight: Spotlight?): Spotlight? {
    var ready by rememberSaveable { mutableStateOf(false) }
    // The show whose logo hadn't loaded when the wait ran out.
    var gaveUpOn by rememberSaveable { mutableStateOf<Int?>(null) }
    var logoLoaded by remember { mutableStateOf(false) }
    if (spotlight == null) return null
    if (!ready) {
        val current by rememberUpdatedState(spotlight)
        LaunchedEffect(Unit) {
            delay(FIRST_SHOW_WAIT_MILLIS)
            current.show.id
                .takeIf { current.logo(it) != Logo.None && !logoLoaded }
                ?.let { gaveUpOn = it }
            ready = true
        }
        val show = spotlight.show
        val logo = spotlight.logo(show.id)
        val context = LocalPlatformContext.current
        LaunchedEffect(spotlight.detailsLoaded && logo != Logo.Loading) {
            if (!spotlight.detailsLoaded || logo == Logo.Loading) return@LaunchedEffect
            val loader = SingletonImageLoader.get(context)
            coroutineScope {
                spotlight.backdrop(show)?.let { launch { loader.execute(ImageRequest.Builder(context).data(it).build()) } }
                (logo as? Logo.Image)?.let {
                    launch {
                        loader.execute(logoRequest(context, it.url))
                        logoLoaded = true
                    }
                }
            }
            ready = true
        }
        return null
    }
    return gaveUpOn?.let { spotlight.copy(logos = spotlight.logos + (it to Logo.None)) } ?: spotlight
}

/** The spotlight show's art behind everything, faded into ink on the left and bottom so text stays readable. */
@Composable
private fun Backdrop(art: String?) {
    val ink = NeoTheme.colors.ink
    val dimensions = NeoTheme.dimensions
    // Faded here rather than by Coil, which doesn't fade art it already has in memory, as preloaded art is.
    Crossfade(
        targetState = art,
        animationSpec = tween(dimensions.durationBackdropMillis, easing = dimensions.easeStandard),
        label = "art",
    ) { art ->
        AsyncImage(
            model = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().testTag(BACKDROP_ART_TAG),
        )
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                // Fan art is brighter than AniList's banners, so the ink stays deep under the whole text column.
                0f to ink.copy(alpha = 0.88f),
                0.34f to ink.copy(alpha = 0.7f),
                0.55f to ink.copy(alpha = 0.42f),
                0.8f to ink.copy(alpha = 0f),
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
        if (saved.animeId == SEE_MORE || items.any { it.id == saved.animeId }) return saved
        val index = saved.index.coerceAtMost(items.lastIndex)
        return CardKey(row, items[index].id, index)
    }
    return null
}
