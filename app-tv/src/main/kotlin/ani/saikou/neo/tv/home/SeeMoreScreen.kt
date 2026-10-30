package ani.saikou.neo.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.home.More
import ani.saikou.neo.shared.home.SeeMoreState
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.KeylineScrolling
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.PreloadLogo

internal const val POSTER_SKELETON_TAG = "poster-skeleton"

internal fun posterTag(index: Int) = "poster-$index"

private const val COLUMNS = 6

// More loads once focus is within two rows of the end.
private const val LOAD_AHEAD = COLUMNS * 2

// From the design: the heading sits on the safe area, its last line ending 79 dp down, and the focused row rests at
// 96 dp, under it. Posters are 108 dp wide with 24 dp between them, so the six take 768 dp, centred with the heading
// over their left edge.
private val HeadingHeight = 52.dp
private val GridTop = 96.dp
private val ColumnGap = 24.dp
private val RowGap = 12.dp
private val GridWidth = 768.dp

// Rows scrolled up pass under the heading, which keeps the backdrop behind it.
private val TitleBand = 80.dp
private val TitleFade = 12.dp
private val EyebrowGap = 2.dp

// A pink glow off the top right corner: an ellipse 700 by 460 dp, centred 190 dp in from the right and 30 dp down.
private val GlowColor = Color(0xFF8C1C4E).copy(alpha = 0.45f)
private val GlowRadiusX = 350.dp
private val GlowRadiusY = 230.dp
private val GlowInset = 190.dp
private val GlowDown = 30.dp

private val FullLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

// The font asks for more than the eyebrow's 16 dp line, so it holds to it.
private val TightLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both, LineHeightStyle.Mode.Tight)

/** All of a Home [row] as a grid of posters, headed by the row's name. */
@Composable
fun SeeMoreScreen(
    row: HomeRow,
    state: SeeMoreState,
    onOpen: (animeId: Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onRest: (card: AnimeCard?) -> Unit = {},
) = SeeMoreScreen(stringResource(rowTitles.getValue(row)), state, onOpen, onLoadMore, onRetry, modifier, onRest)

/**
 * A whole list as a grid of posters under its [title], with the show it's for as an [eyebrow] above, if any. Selecting
 * a poster calls [onOpen]. Focus nearing the end calls [onLoadMore]; Try again after a failed page calls [onRetry].
 */
@Composable
fun SeeMoreScreen(
    title: String,
    state: SeeMoreState,
    onOpen: (animeId: Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onRest: (card: AnimeCard?) -> Unit = {},
    eyebrow: String? = null,
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

    Box(modifier.fillMaxSize().drawBehind { drawBackdrop(ink) }) {
        PreloadLogo(state.restingLogo)
        if (items.isEmpty() && state.more == More.Failed) {
            LoadFailed(onRetry, Modifier.align(Alignment.TopCenter).padding(top = GridTop).width(GridWidth))
        } else {
            KeylineScrolling(keyline = GridTop) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    state = gridState,
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxHeight().width(dimensions.safeX + GridWidth + dimensions.safeX),
                    contentPadding =
                        PaddingValues(
                            start = dimensions.safeX,
                            end = dimensions.safeX,
                            top = GridTop,
                            bottom = 40.dp,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(ColumnGap),
                    verticalArrangement = Arrangement.spacedBy(RowGap),
                ) {
                    if (items.isEmpty()) {
                        items(LOAD_AHEAD) {
                            Skeleton(
                                PosterSize.Small.width,
                                PosterSize.Small.height,
                                Modifier.testTag(POSTER_SKELETON_TAG),
                                PosterSize.Small.radius,
                            )
                        }
                    }
                    itemsIndexed(items, key = { _, card -> card.id }) { index, card ->
                        val requester = remember { FocusRequester() }
                        if (card.id == target) LaunchedEffect(Unit) { requester.requestFocus() }
                        PosterCard(
                            card = card,
                            onClick = { onOpen(card.id) },
                            size = PosterSize.Small,
                            modifier =
                                Modifier
                                    .testTag(posterTag(index))
                                    .focusRequester(requester)
                                    .onFocusChanged {
                                        if (!it.isFocused) return@onFocusChanged
                                        focusedId = card.id
                                        onRest(card)
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
        // The backdrop again over the top of the grid, fading out at its foot.
        Box(
            Modifier
                .fillMaxWidth()
                .height(TitleBand + TitleFade)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawBehind {
                    drawBackdrop(ink)
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black,
                            TitleBand / (TitleBand + TitleFade) to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
        // The title keeps its place whether or not an eyebrow sits above it.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = dimensions.safeY)
                .width(GridWidth)
                .height(HeadingHeight),
            verticalArrangement = Arrangement.spacedBy(EyebrowGap, Alignment.Bottom),
        ) {
            eyebrow?.let {
                Text(text = it.uppercase(), style = Eyebrow, color = NeoTheme.colors.pink)
            }
            Text(text = title, style = Title, color = NeoTheme.colors.paper)
        }
    }
}

private val Title
    @Composable get() = NeoTheme.typography.headline.copy(fontSize = 28.sp, lineHeight = 34.sp, lineHeightStyle = FullLine)

private val Eyebrow
    @Composable get() =
        NeoTheme.typography.overline.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            lineHeightStyle = TightLine,
        )

/** Ink with the pink glow off the top right corner. */
private fun DrawScope.drawBackdrop(ink: Color) {
    drawRect(ink)
    val center = Offset(size.width - GlowInset.toPx(), GlowDown.toPx())
    val radius = GlowRadiusX.toPx()
    scale(scaleX = 1f, scaleY = GlowRadiusY / GlowRadiusX, pivot = center) {
        drawCircle(Brush.radialGradient(listOf(GlowColor, GlowColor.copy(alpha = 0f)), center, radius), radius, center)
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
