package ani.saikou.neo.tv.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.details.DetailsSection
import ani.saikou.neo.shared.details.DetailsState
import ani.saikou.neo.shared.details.scoreOutOfTen
import ani.saikou.neo.shared.details.sections
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.home.displayTitle
import ani.saikou.neo.tv.home.formatName
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.ShowTitle
import ani.saikou.neo.tv.ui.outerGlow
import coil3.compose.AsyncImage

internal const val DETAILS_META_TAG = "details-meta"
internal const val DETAILS_GENRES_TAG = "details-genres"
internal const val DETAILS_HERO_TAG = "details-hero"
internal const val DETAILS_SYNOPSIS_TAG = "details-synopsis"
internal const val DETAILS_LOADING_TAG = "details-loading"
internal const val DETAILS_OVERVIEW_TAB_TAG = "details-tab-overview"
internal const val DETAILS_COMPACT_TITLE_TAG = "details-compact-title"
internal const val DETAILS_TITLE_TAG = "details-title"
internal const val DETAILS_LOGO_TAG = "details-logo"

// From the design: the title block starts 150 dp down and the Overview tabs rest at 420 dp, with the
// synopsis peeking below the screen. Focus on the synopsis lifts the page so the tabs sit at 72 dp,
// and a small title stays at the top.
private val HeroTop = 150.dp
private val HeroWidth = 580.dp
private val OverviewTop = 420.dp
private val OverviewLift = 348.dp
private val CompactTitleTop = 27.dp
private val SynopsisBorder = 2.dp

private val FullLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

// About 70 characters a line, a comfortable read from the sofa.
private val SynopsisWidth = 520.dp
private const val SYNOPSIS_PREVIEW_LINES = 3
private const val SYNOPSIS_FOCUS_SCALE = 1.025f

// A first visit downloads the logo once the art is found. Not loaded by now, it stays words for the visit.
private const val LOGO_WAIT_MILLIS = 1_500L

private val seasonNames =
    mapOf(
        AnimeSeason.WINTER to R.string.details_season_winter,
        AnimeSeason.SPRING to R.string.details_season_spring,
        AnimeSeason.SUMMER to R.string.details_season_summer,
        AnimeSeason.FALL to R.string.details_season_fall,
    )

/**
 * One anime over its dimmed art: what it is at the top, and an Overview with a short synopsis and the facts below.
 * Selecting the synopsis opens all of it, notes included.
 */
@Composable
fun DetailsScreen(
    state: DetailsState,
    modifier: Modifier = Modifier,
) {
    var reading by rememberSaveable { mutableStateOf(false) }
    var hasRead by rememberSaveable { mutableStateOf(false) }
    var lifted by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(DetailsSection.OVERVIEW) }
    val loaded = state as? DetailsState.Loaded
    val tabFocus = remember { FocusRequester() }
    val synopsisFocus = remember { FocusRequester() }
    // Focus starts on the tabs until the actions arrive (#69), and returns to the synopsis when the reader closes. The
    // page stays composed under the reader, so the synopsis is listening when focus comes back.
    LaunchedEffect(loaded != null, reading) {
        if (loaded == null || reading) return@LaunchedEffect
        if (hasRead) synopsisFocus.requestFocus() else tabFocus.requestFocus()
    }
    val dimensions = NeoTheme.dimensions
    val lift by animateDpAsState(
        targetValue = if (lifted) -OverviewLift else 0.dp,
        animationSpec = tween(dimensions.durationBackdropMillis, easing = dimensions.easeStandard),
        label = "lift",
    )
    Box(modifier.fillMaxSize().background(NeoTheme.colors.ink)) {
        Backdrop(loaded?.backdrop) { lift / -OverviewLift }
        val margin = Modifier.padding(start = dimensions.gridMargin)
        when (state) {
            DetailsState.Loading -> {
                LoadingPage(margin)
            }

            is DetailsState.Loaded -> {
                Box(Modifier.fillMaxSize().offset { IntOffset(0, lift.roundToPx()) }) {
                    Hero(state.details, state.logo, margin.padding(top = HeroTop).width(HeroWidth))
                    val sections = state.sections
                    DetailsSections(
                        sections = sections,
                        selected = section.takeIf { it in sections } ?: sections.first(),
                        onSelect = { section = it },
                        // The reader takes focus from the page without lowering it.
                        onInside = { inside -> if (!reading) lifted = inside },
                        // Measured at full height, so it runs past the bottom of the screen until the page lifts.
                        modifier =
                            margin
                                .padding(top = OverviewTop)
                                .wrapContentHeight(Alignment.Top, unbounded = true)
                                .focusRequester(tabFocus),
                    ) { shown ->
                        when (shown) {
                            DetailsSection.OVERVIEW -> {
                                Overview(
                                    state = state,
                                    reading = reading,
                                    synopsisFocus = synopsisFocus,
                                    onRead = {
                                        reading = true
                                        hasRead = true
                                    },
                                )
                            }

                            else -> {}
                        }
                    }
                }
                AnimatedVisibility(visible = lifted, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = state.details.card.displayTitle(),
                        style = NeoTheme.typography.label,
                        color = NeoTheme.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = margin.padding(top = CompactTitleTop).width(HeroWidth).testTag(DETAILS_COMPACT_TITLE_TAG),
                    )
                }
            }

            DetailsState.Failed -> {
                Text(
                    text = stringResource(R.string.details_failed),
                    style = NeoTheme.typography.bodyLarge,
                    color = NeoTheme.colors.paper,
                    modifier = margin.padding(top = HeroTop),
                )
            }
        }
        // Over the page rather than instead of it, so the page keeps its place underneath.
        if (reading && loaded != null) {
            SynopsisReader(loaded.details.card.displayTitle(), loaded.synopsis, onClose = { reading = false })
        }
    }
}

@Composable
private fun Hero(
    details: AnimeDetails,
    logo: Logo,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val card = details.card
    // Unseen until the title is the logo or words for good, then all of it fades in at once, so nothing moves.
    var settled by remember(logo) { mutableStateOf(false) }
    val shown by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = tween(NeoTheme.dimensions.durationBackdropMillis / 2),
        label = "hero",
    )
    val unseen = if (settled) Modifier else Modifier.semantics { hideFromAccessibility() }
    // As on the spotlight: the title first, then what the show is, then its genres.
    Column(
        modifier.testTag(DETAILS_HERO_TAG).graphicsLayer { alpha = shown }.then(unseen),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val title = card.displayTitle()
        val words: @Composable () -> Unit = {
            Text(
                text = title,
                style = NeoTheme.typography.headline,
                color = colors.paper,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(DETAILS_TITLE_TAG),
            )
        }
        ShowTitle(title, logo, DETAILS_LOGO_TAG, LOGO_WAIT_MILLIS, placeholder = words, onSettled = { settled = true }, text = words)
        val meta = details.meta()
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = NeoTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.paper,
                modifier = Modifier.testTag(DETAILS_META_TAG),
            )
        }
        if (details.genres.isNotEmpty()) {
            Text(
                text = details.genres.joinToString(" · "),
                style = NeoTheme.typography.label,
                color = colors.textMuted,
                modifier = Modifier.testTag(DETAILS_GENRES_TAG),
            )
        }
    }
}

/** A short synopsis and the facts. */
@Composable
private fun Overview(
    state: DetailsState.Loaded,
    reading: Boolean,
    synopsisFocus: FocusRequester,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = NeoTheme.dimensions
    // The synopsis box sits a padding's width left, so its text lines up with the grid margin.
    val outdent = Modifier.offset(x = -dimensions.space4)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.synopsis.story?.let { story ->
            SynopsisPreview(
                story = story,
                enabled = !reading,
                onRead = onRead,
                modifier = outdent.focusRequester(synopsisFocus),
            )
        }
        Facts(state.details, Modifier.padding(top = dimensions.space1))
    }
}

/** The start of the synopsis, with no box of its own until focus outlines it. Selecting it opens the reader. */
@Composable
private fun SynopsisPreview(
    story: String,
    enabled: Boolean,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val shape = RoundedCornerShape(dimensions.radiusLg)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val focus by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(dimensions.durationFocusMillis, easing = dimensions.easeStandard),
        label = "focus",
    )
    Surface(
        onClick = onRead,
        enabled = enabled,
        // The box grows and glows on its own layer, so the glow stays outside the border and grows with it.
        modifier =
            modifier
                .width(SynopsisWidth)
                .graphicsLayer {
                    val scale = 1f + (SYNOPSIS_FOCUS_SCALE - 1f) * focus
                    scaleX = scale
                    scaleY = scale
                }.outerGlow(Color.White.copy(alpha = 0.32f * focus), dimensions.glowFocus, dimensions.radiusLg)
                .testTag(DETAILS_SYNOPSIS_TAG),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = colors.textMuted,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = colors.paper,
                pressedContainerColor = Color.Transparent,
                pressedContentColor = colors.paper,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = colors.textMuted,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(SynopsisBorder, Color.Transparent), shape = shape),
                focusedBorder = Border(BorderStroke(SynopsisBorder, colors.focus), shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        interactionSource = interactions,
    ) {
        Text(
            text = story,
            // Whole lines, so three of them make the box the design's height.
            style = NeoTheme.typography.body.copy(lineHeightStyle = FullLine),
            maxLines = SYNOPSIS_PREVIEW_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = dimensions.space4, vertical = dimensions.space3),
        )
    }
}

/** Format, release, studio and score, side by side. Any AniList doesn't know is left out. */
@Composable
private fun Facts(
    details: AnimeDetails,
    modifier: Modifier = Modifier,
) {
    val card = details.card
    val facts =
        listOfNotNull(
            card.formatName()?.let { R.string.details_fact_format to it },
            card.seasonName()?.let { R.string.details_fact_released to it },
            details.studios
                .take(2)
                .joinToString(", ")
                .ifEmpty { null }
                ?.let { R.string.details_fact_studio to it },
            scoreOutOfTen(card.averageScore)?.let { R.string.details_fact_score to it },
        )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space8)) {
        for ((label, value) in facts) {
            Column {
                Text(text = stringResource(label), style = NeoTheme.typography.caption, color = NeoTheme.colors.textFaint)
                Text(text = value, style = NeoTheme.typography.label, color = NeoTheme.colors.paper)
            }
        }
    }
}

/** Placeholders where the title block, the Overview tab, the synopsis and the facts will be, sized as in the design. */
@Composable
private fun LoadingPage(modifier: Modifier = Modifier) {
    val dimensions = NeoTheme.dimensions
    Box(modifier.fillMaxSize().testTag(DETAILS_LOADING_TAG)) {
        Column(Modifier.padding(top = HeroTop), verticalArrangement = Arrangement.spacedBy(dimensions.space3)) {
            Skeleton(110.dp, 16.dp)
            Skeleton(300.dp, 34.dp)
            Skeleton(210.dp, 16.dp)
        }
        Column(Modifier.padding(top = OverviewTop), verticalArrangement = Arrangement.spacedBy(dimensions.space3)) {
            Skeleton(92.dp, 28.dp, CircleShape)
            Skeleton(500.dp, 14.dp)
            Skeleton(480.dp, 14.dp)
            Skeleton(300.dp, 14.dp)
            Row(Modifier.padding(top = dimensions.space1), horizontalArrangement = Arrangement.spacedBy(dimensions.space8)) {
                Skeleton(60.dp, 32.dp)
                Skeleton(90.dp, 32.dp)
                Skeleton(120.dp, 32.dp)
                Skeleton(50.dp, 32.dp)
            }
        }
    }
}

@Composable
private fun Skeleton(
    width: Dp,
    height: Dp,
    shape: Shape = RoundedCornerShape(6.dp),
) = Box(Modifier.size(width, height).background(NeoTheme.colors.surfaceRaised, shape))

/**
 * The show's art, faded in once it's known. As on the spotlight, it fades into ink on the left and from just above the
 * Overview tab down, so the art shows at the top right. [lifted] dims all of it while the page is lifted, from 0 to 1,
 * since the synopsis then sits high over the art.
 */
@Composable
private fun Backdrop(
    art: String?,
    lifted: () -> Float,
) {
    val ink = NeoTheme.colors.ink
    val dimensions = NeoTheme.dimensions
    Crossfade(
        targetState = art,
        animationSpec = tween(dimensions.durationBackdropMillis, easing = dimensions.easeStandard),
        label = "art",
    ) {
        AsyncImage(
            model = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(*AcrossStops.inInk(ink))))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(*DownStops.inInk(ink))))
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = lifted() }.background(ink.copy(alpha = LIFTED_DIM)))
}

private fun List<Pair<Float, Float>>.inInk(ink: Color) = map { (at, alpha) -> at to ink.copy(alpha = alpha) }.toTypedArray()

// From the design: ink over the backdrop from the left edge to the right, the same as the spotlight's.
private val AcrossStops = listOf(0f to 0.88f, 0.34f to 0.7f, 0.55f to 0.42f, 0.8f to 0f, 1f to 0f)

// From the design: ink from the top down. Clear above 62%, just over the Overview tab at 78%, and solid at the bottom,
// under the synopsis and facts.
private val DownStops = listOf(0f to 0f, 0.62f to 0f, 0.84f to 0.85f, 1f to 1f)

// Lifted, the synopsis and facts sit at the top of the screen, over the clearest part of the art.
private const val LIFTED_DIM = 0.85f

/** The ink's alpha over the backdrop [across] the screen, from 0 at the left edge to 1 at the right. */
internal fun detailsDimAcross(across: Float) = AcrossStops.alphaAt(across)

/** The ink's alpha over the backdrop [down] the screen, from 0 at the top to 1 at the bottom. */
internal fun detailsDimDown(down: Float) = DownStops.alphaAt(down)

private fun List<Pair<Float, Float>>.alphaAt(at: Float): Float {
    val (start, end) = zipWithNext().first { (_, end) -> at <= end.first }
    return start.second + (end.second - start.second) * (at - start.first) / (end.first - start.first)
}

/** What the show is, such as "TV · Fall 2026 · 12 episodes". */
@Composable
private fun AnimeDetails.meta(): String {
    val episodes = card.episodes?.let { pluralStringResource(R.plurals.home_episodes, it, it) }
    return listOfNotNull(card.formatName(), card.seasonName(), episodes).joinToString(" · ")
}

/** Such as "Fall 2026", or just the year when AniList doesn't give the season. */
@Composable
private fun AnimeCard.seasonName(): String? {
    val year = seasonYear ?: return null
    return seasonNames[season]?.let { stringResource(it, year) } ?: year.toString()
}
