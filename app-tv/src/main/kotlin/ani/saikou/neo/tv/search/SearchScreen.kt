package ani.saikou.neo.tv.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.search.SearchGenre
import ani.saikou.neo.shared.search.SearchResults
import ani.saikou.neo.shared.search.SearchState
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.home.PosterCard
import ani.saikou.neo.tv.home.Skeleton
import ani.saikou.neo.tv.home.posterTag
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton

internal fun keyTag(key: Char) = "key-$key"

internal fun genreTag(genre: SearchGenre) = "genre-${genre.name}"

// From the design: 240 dp of keys and 560 dp of results, 32 dp apart, centred together under the safe area. Keys are
// 36 dp tall in six columns, 4 dp apart; results are four 124 dp posters a row.
private val KeyboardWidth = 240.dp
private val ResultsWidth = 560.dp
private val ColumnGap = 32.dp
private val BlockGap = 14.dp
private val FieldHeight = 44.dp
private val FieldBorder = 1.5.dp
private val FieldPadding = 16.dp
private val KeyHeight = 36.dp
private val KeyGap = 4.dp
private val PosterColumnGap = 20.dp
private val PosterRowGap = 18.dp
private val TileHeight = 64.dp
private val TileGap = 12.dp
private val TilePadding = 14.dp
private val AfterHint = 10.dp
private const val KEY_COLUMNS = 6
private const val POSTER_COLUMNS = 4
private const val TILE_COLUMNS = 3
private const val KEYS = "abcdefghijklmnopqrstuvwxyz1234567890"

// No results offers the first three genres; before you type, all six.
private const val GENRES_WITHOUT_RESULTS = 3

private val GlowColor = Color.White.copy(alpha = 0.3f)

// A blue glow off the top right corner: an ellipse 620 by 420 dp, centred 190 dp in from the right and 50 dp down.
private val BackdropGlow = Color(0xFF6A7FB8).copy(alpha = 0.35f)
private val BackdropRadiusX = 310.dp
private val BackdropRadiusY = 210.dp
private val BackdropInset = 190.dp
private val BackdropDown = 50.dp

// The font asks for more than the label's 16 dp line, so it holds to it.
private val TightLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both, LineHeightStyle.Mode.Tight)
private val FullLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

internal val genreLabels =
    mapOf(
        SearchGenre.ACTION to R.string.genre_action,
        SearchGenre.COMEDY to R.string.genre_comedy,
        SearchGenre.FANTASY to R.string.genre_fantasy,
        SearchGenre.ROMANCE to R.string.genre_romance,
        SearchGenre.SCI_FI to R.string.genre_sci_fi,
        SearchGenre.SLICE_OF_LIFE to R.string.genre_slice_of_life,
    )

// Each tile's gradient, from its top left corner to its bottom right.
private val genreFills =
    mapOf(
        SearchGenre.ACTION to listOf(Color(0xFF9C2F1E), Color(0xFFF28C28)),
        SearchGenre.COMEDY to listOf(Color(0xFF0E5E6F), Color(0xFFE9C46A)),
        SearchGenre.FANTASY to listOf(Color(0xFF2B3A67), Color(0xFF9FB4FF)),
        SearchGenre.ROMANCE to listOf(Color(0xFF8C1C4E), Color(0xFFF2B5D4)),
        SearchGenre.SCI_FI to listOf(Color(0xFF10102E), Color(0xFF6C5CE7)),
        SearchGenre.SLICE_OF_LIFE to listOf(Color(0xFF2D4A2B), Color(0xFFE9C46A)),
    )

/**
 * Search with the remote: an on-screen keyboard down the left, and what the query finds on the right. Before you type,
 * and when nothing matches, genres give you somewhere to start.
 */
@Composable
fun SearchScreen(
    state: SearchState,
    onType: (key: Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (animeId: Int) -> Unit,
    onGenre: (genre: SearchGenre) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    // The show with focus, so Back from Details returns to it.
    var focusedId by rememberSaveable { mutableStateOf<Int?>(null) }
    // The genre with focus, so Back from its page returns to it.
    var focusedGenre by rememberSaveable { mutableStateOf<SearchGenre?>(null) }
    // Not saved: after recreation, focus has to be placed again.
    var hasFocus by remember { mutableStateOf(false) }
    val results = state.results
    val returnTo = focusedId.takeIf { id -> !hasFocus && results is SearchResults.Found && results.items.any { it.id == id } }
    val genres =
        when {
            results == SearchResults.Idle -> SearchGenre.entries
            results is SearchResults.Found && results.items.isEmpty() -> SearchGenre.entries.take(GENRES_WITHOUT_RESULTS)
            else -> emptyList()
        }
    val returnToGenre = focusedGenre.takeIf { !hasFocus && it in genres }
    val onGenreFocus: (SearchGenre) -> Unit = {
        focusedGenre = it
        focusedId = null
    }

    Box(modifier.fillMaxSize().drawBehind { drawBackdrop(colors.ink) }.onFocusChanged { hasFocus = it.hasFocus }) {
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = NeoTheme.dimensions.safeY).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(ColumnGap),
        ) {
            Keyboard(state.query, focusFirst = !hasFocus && returnTo == null && returnToGenre == null, onType, onDelete, onClear)
            Column(Modifier.width(ResultsWidth), verticalArrangement = Arrangement.spacedBy(BlockGap)) {
                when {
                    results == SearchResults.Idle -> {
                        Heading(stringResource(R.string.search_genres))
                        Genres(genres, returnToGenre, onGenre, onGenreFocus)
                    }

                    results == SearchResults.Failed -> {
                        Heading(stringResource(R.string.search_failed))
                        Text(stringResource(R.string.see_more_failed_hint), style = Hint, color = colors.textMuted)
                        NeoButton(stringResource(R.string.try_again), onClick = onRetry, modifier = Modifier.padding(top = AfterHint))
                    }

                    results is SearchResults.Found && results.items.isEmpty() -> {
                        Heading(stringResource(R.string.search_nothing, results.query))
                        Text(stringResource(R.string.search_nothing_hint), style = Hint, color = colors.paper.copy(alpha = 0.8f))
                        Text(
                            stringResource(R.string.search_genres_instead),
                            Modifier.padding(top = AfterHint),
                            style = NeoTheme.typography.rowTitle.copy(fontSize = 16.sp, lineHeight = 22.sp, lineHeightStyle = FullLine),
                            color = colors.textMuted,
                        )
                        Genres(genres, returnToGenre, onGenre, onGenreFocus)
                    }

                    else -> {
                        Heading(stringResource(R.string.search_results, (results as? SearchResults.Found)?.query ?: state.query.trim()))
                        Results(results as? SearchResults.Found, returnTo, onOpen) {
                            focusedId = it
                            focusedGenre = null
                        }
                    }
                }
            }
        }
    }
}

/** Four posters a row, or skeletons while the first search loads. */
@Composable
private fun Results(
    found: SearchResults.Found?,
    returnTo: Int?,
    onOpen: (animeId: Int) -> Unit,
    onFocus: (animeId: Int) -> Unit,
) {
    val dimensions = NeoTheme.dimensions
    LazyVerticalGrid(
        columns = GridCells.Fixed(POSTER_COLUMNS),
        // Exactly four posters wide, so each cell is a poster's width.
        modifier = Modifier.width(dimensions.cardPosterWidth * POSTER_COLUMNS + PosterColumnGap * (POSTER_COLUMNS - 1)),
        horizontalArrangement = Arrangement.spacedBy(PosterColumnGap),
        verticalArrangement = Arrangement.spacedBy(PosterRowGap),
    ) {
        if (found == null) {
            items(POSTER_COLUMNS * 2) {
                Skeleton(dimensions.cardPosterWidth, dimensions.cardPosterHeight, Modifier, dimensions.radiusSm)
            }
        } else {
            itemsIndexed(found.items, key = { _, card -> card.id }) { index, card ->
                val requester = remember { FocusRequester() }
                if (card.id == returnTo) LaunchedEffect(Unit) { requester.requestFocus() }
                PosterCard(
                    card = card,
                    onClick = { onOpen(card.id) },
                    modifier =
                        Modifier
                            .testTag(posterTag(index))
                            .focusRequester(requester)
                            .onFocusChanged { if (it.isFocused) onFocus(card.id) },
                )
            }
        }
    }
}

/** The query in its field over the keys, with Space, Delete and Clear under them. */
@Composable
private fun Keyboard(
    query: String,
    focusFirst: Boolean,
    onType: (key: Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = NeoTheme.colors
    val first = remember { FocusRequester() }
    if (focusFirst) LaunchedEffect(Unit) { first.requestFocus() }
    Column(Modifier.width(KeyboardWidth), verticalArrangement = Arrangement.spacedBy(BlockGap)) {
        Text(stringResource(R.string.search_label), style = Label, color = colors.textFaint)
        Box(
            Modifier
                .fillMaxWidth()
                .height(FieldHeight)
                .background(colors.surface, CircleShape)
                .border(FieldBorder, colors.line, CircleShape)
                .padding(horizontal = FieldPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(query, style = NeoTheme.typography.label.copy(fontSize = 16.sp), color = colors.paper, maxLines = 1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(KeyGap)) {
            KEYS.chunked(KEY_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                    row.forEach { key ->
                        val focus = if (key == KEYS.first()) Modifier.focusRequester(first) else Modifier
                        Key(key.toString(), { onType(key) }, colors.surface, focus.testTag(keyTag(key)).weight(1f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                Key(stringResource(R.string.search_space), { onType(' ') }, colors.surfaceRaised, Modifier.weight(1f), small = true)
                Key(stringResource(R.string.search_delete), onDelete, colors.surfaceRaised, Modifier.weight(1f), small = true)
                Key(stringResource(R.string.search_clear), onClear, colors.surfaceRaised, Modifier.weight(1f), small = true)
            }
        }
    }
}

/** One key: a fill at rest; paper, a glow and growth on focus. */
@Composable
private fun Key(
    label: String,
    onClick: () -> Unit,
    fill: Color,
    modifier: Modifier,
    small: Boolean = false,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    Surface(
        onClick = onClick,
        modifier = modifier.height(KeyHeight),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(dimensions.radiusSm)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = fill,
                contentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = dimensions.focusScaleButton),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(GlowColor, dimensions.glowFocus)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = NeoTheme.typography.label.copy(fontSize = if (small) 13.sp else 15.sp, lineHeightStyle = TightLine))
        }
    }
}

/** Genre tiles, three a row. Choosing one opens its most popular anime. */
@Composable
private fun Genres(
    genres: List<SearchGenre>,
    returnTo: SearchGenre?,
    onGenre: (genre: SearchGenre) -> Unit,
    onFocus: (genre: SearchGenre) -> Unit,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val shape = RoundedCornerShape(dimensions.radiusLg)
    Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
        genres.chunked(TILE_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                row.forEach { genre ->
                    val requester = remember { FocusRequester() }
                    if (genre == returnTo) LaunchedEffect(Unit) { requester.requestFocus() }
                    Surface(
                        onClick = { onGenre(genre) },
                        modifier =
                            Modifier
                                .testTag(genreTag(genre))
                                .focusRequester(requester)
                                .onFocusChanged { if (it.isFocused) onFocus(genre) }
                                .weight(1f)
                                .height(TileHeight),
                        shape = ClickableSurfaceDefaults.shape(shape),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                            ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = dimensions.focusScale),
                        border =
                            ClickableSurfaceDefaults.border(
                                focusedBorder = Border(BorderStroke(dimensions.focusRing, colors.focus), shape = shape),
                            ),
                        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(GlowColor, dimensions.glowFocus)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(genreFills.getValue(genre)),
                                ).padding(horizontal = TilePadding),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                stringResource(genreLabels.getValue(genre)),
                                style = NeoTheme.typography.label.copy(fontWeight = FontWeight.Bold),
                                color = colors.paper,
                            )
                        }
                    }
                }
                // A short last row keeps its tiles the width of the others.
                repeat(TILE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Ink with the blue glow off the top right corner. */
private fun DrawScope.drawBackdrop(ink: Color) {
    drawRect(ink)
    val center = Offset(size.width - BackdropInset.toPx(), BackdropDown.toPx())
    val radius = BackdropRadiusX.toPx()
    scale(scaleX = 1f, scaleY = BackdropRadiusY / BackdropRadiusX, pivot = center) {
        drawCircle(Brush.radialGradient(listOf(BackdropGlow, BackdropGlow.copy(alpha = 0f)), center, radius), radius, center)
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = NeoTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, lineHeightStyle = FullLine),
        color = NeoTheme.colors.paper,
        maxLines = 1,
    )
}

private val Label
    @Composable get() =
        NeoTheme.typography.caption.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeightStyle = TightLine,
        )

private val Hint
    @Composable get() = NeoTheme.typography.body.copy(lineHeight = 22.sp)
