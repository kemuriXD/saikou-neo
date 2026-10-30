package ani.saikou.neo.tv.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.RelatedAnime
import ani.saikou.neo.shared.catalogue.Relation
import ani.saikou.neo.shared.details.DetailsSection
import ani.saikou.neo.shared.details.DetailsState
import ani.saikou.neo.shared.details.Ratings
import ani.saikou.neo.shared.details.scoreOutOfTen
import ani.saikou.neo.shared.details.sections
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.home.displayTitle
import ani.saikou.neo.tv.home.formatName
import ani.saikou.neo.tv.home.parseColour
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.FocusMemory
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoButtonRow
import ani.saikou.neo.tv.ui.NeoIcons
import ani.saikou.neo.tv.ui.RememberFocus
import ani.saikou.neo.tv.ui.ShowTitle
import ani.saikou.neo.tv.ui.logoRequest
import ani.saikou.neo.tv.ui.outerGlow
import ani.saikou.neo.tv.ui.remembersFocus
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext

internal const val DETAILS_META_TAG = "details-meta"
internal const val DETAILS_HERO_TAG = "details-hero"
internal const val DETAILS_SYNOPSIS_TAG = "details-synopsis"
internal const val DETAILS_LOADING_TAG = "details-loading"
internal const val DETAILS_OVERVIEW_TAB_TAG = "details-tab-overview"
internal const val DETAILS_COMPACT_TITLE_TAG = "details-compact-title"
internal const val DETAILS_HEADER_TAG = "details-header"
internal const val DETAILS_HEADER_LOGO_TAG = "details-header-logo"
internal const val DETAILS_TITLE_TAG = "details-title"
internal const val DETAILS_LOGO_TAG = "details-logo"
internal const val DETAILS_MAIN_BUTTON_TAG = "details-main-button"
internal const val DETAILS_EPISODES_BUTTON_TAG = "details-episodes-button"
internal const val DETAILS_RATING_TAG = "details-rating"
internal const val DETAILS_RATINGS_TAG = "details-ratings"

/** One site's rating in the facts, by the site's name, such as "IMDb". */
internal fun ratingTag(site: String) = "details-rating-$site"

// From the design: the Overview tabs rest at 352 dp, with the section peeking below the screen, and the title block sits
// on them, its buttons 40 dp above, however tall the title is. Focus in a section lifts the page so the tabs sit at
// 76 dp, under a small header.
private val HeroGap = 40.dp
private val HeroWidth = 440.dp
private val OverviewTop = 352.dp
private val OverviewLift = 276.dp

// From the design: the lifted page keeps a 40 dp header at the top, with the logo at its full height.
private val HeaderTop = 24.dp
private val HeaderEnd = 48.dp
private val HeaderHeight = 40.dp
private val HeaderLogoWidth = 200.dp
private val HeaderPillHeight = 36.dp
private val HeaderPillPadding = 16.dp
private val HeaderPillIcon = 11.dp
private val SynopsisBorder = 2.dp

// From the design: the buttons sit a little further below the genres than the lines above sit apart.
private val ButtonsGap = 8.dp

private val FullLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

// About 70 characters a line, a comfortable read from the sofa.
private val SynopsisWidth = 520.dp
private const val SYNOPSIS_PREVIEW_LINES = 2
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
    onOpen: (animeId: Int) -> Unit = {},
    onRecommendations: () -> Unit = {},
) = RememberFocus { focus -> DetailsPage(state, focus, modifier, onOpen, onRecommendations) }

@Composable
private fun DetailsPage(
    state: DetailsState,
    focus: FocusMemory,
    modifier: Modifier,
    onOpen: (animeId: Int) -> Unit,
    onRecommendations: () -> Unit,
) {
    var reading by rememberSaveable { mutableStateOf(false) }
    var hasRead by rememberSaveable { mutableStateOf(false) }
    var lifted by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(DetailsSection.OVERVIEW) }
    // Set as a poster or See more opens another screen, so focus leaving for it doesn't lower the page. Coming back, focus
    // returns to what opened it, and the page can lower again.
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { opened = false }
    val loaded = state as? DetailsState.Loaded
    val mainFocus = remember { FocusRequester() }
    val tabFocus = remember { FocusRequester() }
    val synopsisFocus = remember { FocusRequester() }
    val episodesFocus = remember { FocusRequester() }
    // Set by the main button, for the Episodes section to take focus once it's shown.
    var toEpisodes by remember { mutableStateOf(false) }
    val hasEpisodes = loaded != null && DetailsSection.EPISODES in loaded.sections
    // Coming back to Details, focus returns to what had it. Otherwise it starts on the main button, or on the tabs for a
    // show with no episodes yet, and returns to the synopsis when the reader closes. The page stays composed under the
    // reader, so the synopsis is listening when focus comes back.
    LaunchedEffect(loaded != null, reading) {
        if (loaded == null || reading) return@LaunchedEffect
        focus.restoreOr {
            when {
                hasRead -> synopsisFocus.requestFocus()
                hasEpisodes -> mainFocus.requestFocus()
                else -> tabFocus.requestFocus()
            }
        }
    }
    // Down in a section, Back first brings the page back to the top. Back again leaves Details.
    BackHandler(enabled = lifted && !reading) {
        (if (hasEpisodes) mainFocus else tabFocus).requestFocus()
        lifted = false
    }
    val dimensions = NeoTheme.dimensions
    val lift by animateDpAsState(
        targetValue = if (lifted) -OverviewLift else 0.dp,
        animationSpec = tween(dimensions.durationBackdropMillis, easing = dimensions.easeStandard),
        label = "lift",
    )
    Box(modifier.fillMaxSize().background(NeoTheme.colors.ink)) {
        val tint = loaded?.let { detailsTint(it.details.card.coverColor, NeoTheme.colors.pink) }
        Backdrop(loaded?.backdrop, tint) { lift / -OverviewLift }
        val margin = Modifier.padding(start = dimensions.gridMargin)
        when (state) {
            DetailsState.Loading -> {
                LoadingPage(margin)
            }

            is DetailsState.Loaded -> {
                Box(Modifier.fillMaxSize().offset { IntOffset(0, lift.roundToPx()) }) {
                    Hero(
                        details = state.details,
                        logo = state.logo,
                        progress = state.progress,
                        // The main button goes to the episodes. Without any, there's nothing for it to do.
                        onMain =
                            if (hasEpisodes) {
                                {
                                    section = DetailsSection.EPISODES
                                    toEpisodes = true
                                }
                            } else {
                                null
                            },
                        mainFocus = mainFocus,
                        ratings = state.ratings,
                        modifier =
                            margin
                                .width(HeroWidth)
                                .onTheTabs()
                                .graphicsLayer { alpha = 1f - lift / -OverviewLift },
                    )
                    val sections = state.sections
                    DetailsSections(
                        sections = sections,
                        selected = { section.takeIf { it in sections } ?: sections.first() },
                        onSelect = { section = it },
                        // The reader and a related show's Details take focus from the page without lowering it.
                        onInside = { inside -> if (!reading && !opened) lifted = inside },
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

                            DetailsSection.EPISODES -> {
                                val episodes = state.episodes
                                if (episodes != null) {
                                    EpisodesSection(episodes, state.backdrop, Modifier.focusRequester(episodesFocus))
                                    LaunchedEffect(toEpisodes) {
                                        if (toEpisodes) episodesFocus.requestFocus()
                                        toEpisodes = false
                                    }
                                }
                            }

                            DetailsSection.RELATED -> {
                                PosterRowSection(
                                    posters = state.details.related.map { Poster(it.card, it.caption()) },
                                    onOpen = {
                                        opened = true
                                        onOpen(it)
                                    },
                                )
                            }

                            DetailsSection.WATCH_ORDER -> {
                                // Words only: there's nothing to do here until a watch order extension is installed.
                                Text(
                                    text = stringResource(R.string.details_watch_order_none),
                                    style = NeoTheme.typography.body,
                                    color = NeoTheme.colors.textMuted,
                                    modifier = Modifier.width(SynopsisWidth),
                                )
                            }

                            DetailsSection.RECOMMENDATIONS -> {
                                PosterRowSection(
                                    posters = state.details.recommendations.map { Poster(it) },
                                    onOpen = {
                                        opened = true
                                        onOpen(it)
                                    },
                                    seeMore =
                                        if (state.details.moreRecommendations) {
                                            {
                                                opened = true
                                                onRecommendations()
                                            }
                                        } else {
                                            null
                                        },
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(visible = lifted, enter = fadeIn(), exit = fadeOut()) {
                    LiftedHeader(state, hasEpisodes, margin.padding(top = HeaderTop, end = HeaderEnd))
                }
            }

            DetailsState.Failed -> {
                Text(
                    text = stringResource(R.string.details_failed),
                    style = NeoTheme.typography.bodyLarge,
                    color = NeoTheme.colors.paper,
                    modifier = margin.onTheTabs(),
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
    progress: WatchProgress?,
    onMain: (() -> Unit)?,
    mainFocus: FocusRequester,
    ratings: Ratings,
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
    // As on the spotlight: the title first, then its genres and how many episodes it has.
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
        val episodes = card.episodes?.let { pluralStringResource(R.plurals.home_episodes, it, it) }
        val meta = (details.genres + listOfNotNull(episodes)).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = NeoTheme.typography.label.copy(fontWeight = FontWeight.Medium),
                color = colors.textMuted,
                modifier = Modifier.testTag(DETAILS_META_TAG),
            )
        }
        if (ratings.mean != null) TopRatings(ratings)
        // Until the player comes, Resume and Play open the episodes too.
        onMain?.let {
            NeoButtonRow(Modifier.padding(top = ButtonsGap)) {
                // Focus grows it from its middle, so room for that keeps the gap to Episodes.
                val density = LocalDensity.current
                val grows = NeoTheme.dimensions.focusScaleButton - 1
                var width by remember { mutableIntStateOf(0) }
                Box(Modifier.padding(end = with(density) { (width * grows / 2).toDp() })) {
                    NeoButton(
                        text =
                            progress?.let { stringResource(R.string.details_resume, it.episode, it.minutesLeft) }
                                ?: stringResource(R.string.home_play),
                        onClick = it,
                        icon = NeoIcons.Play,
                        modifier =
                            Modifier
                                .onSizeChanged { size -> width = size.width }
                                .focusRequester(mainFocus)
                                .remembersFocus("main")
                                .testTag(DETAILS_MAIN_BUTTON_TAG),
                    )
                }
                NeoButton(
                    text = stringResource(R.string.details_episodes),
                    onClick = it,
                    modifier = Modifier.remembersFocus("episodes").testTag(DETAILS_EPISODES_BUTTON_TAG),
                )
            }
        }
    }
}

/**
 * The top of the lifted page: the logo small, or the title in words, then the rating and what the show is, and the way
 * back to its episode on the right, if it has episodes. The pill only shows the way: nothing can move focus up to it, since Up from a
 * section lowers the page.
 */
@Composable
private fun LiftedHeader(
    state: DetailsState.Loaded,
    hasEpisodes: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(HeaderHeight).testTag(DETAILS_HEADER_TAG),
        horizontalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The title and meta take whatever room the pill leaves, and a long title gives way first.
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) { HeaderTitle(state) }
        if (hasEpisodes) HeaderPill(state.progress)
    }
}

/** The logo at the header's height, or the title in words on one line, then the rating and what the show is. */
@Composable
private fun RowScope.HeaderTitle(state: DetailsState.Loaded) {
    val colors = NeoTheme.colors
    val title = state.details.card.displayTitle()
    val logo = state.logo as? Logo.Image
    var failed by remember(logo) { mutableStateOf(false) }
    if (logo != null && !failed) {
        val context = LocalPlatformContext.current
        AsyncImage(
            model = remember(logo.url) { logoRequest(context, logo.url) },
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { failed = true },
            modifier = Modifier.height(HeaderHeight).widthIn(max = HeaderLogoWidth).testTag(DETAILS_HEADER_LOGO_TAG),
        )
    } else {
        Text(
            text = title,
            style = NeoTheme.typography.title.copy(fontWeight = FontWeight.Bold),
            color = colors.paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).testTag(DETAILS_COMPACT_TITLE_TAG),
        )
    }
    Text(
        text = state.headerMeta(),
        style = NeoTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
        color = colors.textMuted,
        maxLines = 1,
    )
}

/** "Resume E7", or Play without progress. */
@Composable
private fun HeaderPill(progress: WatchProgress?) {
    val colors = NeoTheme.colors
    Row(
        Modifier
            .height(HeaderPillHeight)
            .background(colors.fillTonal, CircleShape)
            .padding(horizontal = HeaderPillPadding),
        horizontalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NeoIcons.Play, contentDescription = null, tint = colors.paper, modifier = Modifier.size(HeaderPillIcon))
        Text(
            text =
                progress?.let { stringResource(R.string.details_header_resume, it.episode) }
                    ?: stringResource(R.string.home_play),
            style = NeoTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.paper,
        )
    }
}

/** The rating in paper, then what the show is, such as "★ 8.4/10 · TV · 2025 · 22 eps". */
@Composable
private fun DetailsState.Loaded.headerMeta(): AnnotatedString {
    val card = details.card
    val rating = ratings.mean?.let { stringResource(R.string.details_header_rating, scoreOutOfTen(it).orEmpty()) }
    val episodes = card.episodes?.let { pluralStringResource(R.plurals.details_header_episodes, it, it) }
    val rest = listOfNotNull(card.formatName(), card.seasonYear?.toString(), episodes)
    val paper = NeoTheme.colors.paper
    return buildAnnotatedString {
        rating?.let { withStyle(SpanStyle(color = paper)) { append(it) } }
        if (rating != null && rest.isNotEmpty()) append(" · ")
        append(rest.joinToString(" · "))
    }
}

/** The mean of the show's ratings out of 10 with a star, such as "★ 8.6/10", then a film's Rotten Tomatoes beside it. */
@Composable
private fun TopRatings(ratings: Ratings) {
    Row(
        modifier = Modifier.padding(top = ButtonsGap),
        horizontalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ratings.mean?.let { MeanRating(it) }
        ratings.rottenTomatoes?.let { SiteRating(R.string.rating_rotten_tomatoes, R.drawable.rating_rotten_tomatoes, it) }
    }
}

@Composable
private fun MeanRating(mean: Int) {
    val score = scoreOutOfTen(mean).orEmpty()
    val description = stringResource(R.string.details_rating, score)
    Row(
        modifier = Modifier.testTag(DETAILS_RATING_TAG).clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NeoIcons.Star, contentDescription = null, tint = NeoTheme.colors.pink, modifier = Modifier.size(18.dp))
        // The score and "/10" differ in size, so they share a baseline rather than a bottom edge.
        Row {
            Text(
                text = score,
                style = NeoTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = NeoTheme.colors.paper,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = stringResource(R.string.details_out_of_ten),
                style = NeoTheme.typography.label,
                color = NeoTheme.colors.textMuted,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/** The facts and ratings, then a short synopsis. */
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
        Facts(state.details, state.ratings, Modifier.padding(top = dimensions.space1))
        state.synopsis.story?.let { story ->
            SynopsisPreview(
                story = story,
                enabled = !reading,
                onRead = onRead,
                modifier = outdent.focusRequester(synopsisFocus).remembersFocus("synopsis"),
            )
        }
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

/**
 * Format, release and studio, side by side, then past a thin line AniList's, MAL's and IMDb's logos with their ratings
 * beside them. Any that isn't known is left out. A film's Rotten Tomatoes sits by the star instead.
 */
@Composable
private fun Facts(
    details: AnimeDetails,
    ratings: Ratings,
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
        )
    val sites =
        listOfNotNull(
            ratings.aniList?.let { Triple(R.string.rating_anilist, R.drawable.rating_anilist, it) },
            ratings.mal?.let { Triple(R.string.rating_mal, R.drawable.rating_mal, it) },
            ratings.imdb?.let { Triple(R.string.rating_imdb, R.drawable.rating_imdb, it) },
        )
    Row(modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space8)) {
        for ((label, value) in facts) {
            Column {
                Text(text = stringResource(label), style = NeoTheme.typography.caption, color = NeoTheme.colors.textFaint)
                Text(text = value, style = NeoTheme.typography.label, color = NeoTheme.colors.paper)
            }
        }
        if (sites.isNotEmpty()) {
            if (facts.isNotEmpty()) Box(Modifier.width(1.dp).fillMaxHeight().background(NeoTheme.colors.paper.copy(alpha = RULE)))
            Row(
                Modifier.align(Alignment.CenterVertically).testTag(DETAILS_RATINGS_TAG),
                horizontalArrangement = Arrangement.spacedBy(RatingsGap),
                // Logos differ in height, so each site is centred and every score sits on the same line.
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for ((site, logo, rating) in sites) SiteRating(site, logo, rating)
            }
        }
    }
}

/** One site's logo with its rating out of 10 beside it. Each logo keeps its own shape, centred on the score. */
@Composable
private fun SiteRating(
    site: Int,
    logo: Int,
    rating: Int,
) {
    val name = stringResource(site)
    val score = scoreOutOfTen(rating).orEmpty()
    val description = stringResource(R.string.details_site_rating, name, score)
    Row(
        modifier = Modifier.testTag(ratingTag(name)).clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(logo), contentDescription = null)
        Text(text = score, style = NeoTheme.typography.label, color = NeoTheme.colors.paper)
    }
}

// The line between the facts and the ratings, and the space between sites.
private const val RULE = 0.24f
private val RatingsGap = 20.dp

/** Sits the title block's bottom [HeroGap] above the tabs. A tall title grows up the screen. */
private fun Modifier.onTheTabs() = height(OverviewTop - HeroGap).wrapContentHeight(Alignment.Bottom, unbounded = true)

/** Placeholders where the title block, the Overview tab, the synopsis and the facts will be, sized as in the design. */
@Composable
private fun LoadingPage(modifier: Modifier = Modifier) {
    val dimensions = NeoTheme.dimensions
    Box(modifier.fillMaxSize().testTag(DETAILS_LOADING_TAG)) {
        Column(Modifier.onTheTabs(), verticalArrangement = Arrangement.spacedBy(dimensions.space3)) {
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
 * since the synopsis then sits high over the art. A soft wash of the show's [tint] glows up from the bottom left.
 */
@Composable
private fun Backdrop(
    art: String?,
    tint: Color?,
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
    if (tint != null) TintWash(tint)
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = lifted() }.background(ink.copy(alpha = LIFTED_DIM)))
}

/** From the design: an ellipse of [tint] fading out from its middle, centred below the bottom left corner. */
@Composable
private fun TintWash(tint: Color) {
    val wash = tint.copy(alpha = TINT_ALPHA)
    Box(
        Modifier
            // Unbounded, so the screen's height doesn't squeeze it or move it.
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .offset(x = TintLeft, y = TintTop)
            .size(TintWidth, TintHeight)
            .drawBehind {
                val radius = size.width / 2
                scale(scaleX = 1f, scaleY = size.height / size.width) {
                    drawCircle(Brush.radialGradient(listOf(wash, Color.Transparent), center, radius), radius)
                }
            },
    )
}

private val TintLeft = (-200).dp
private val TintTop = 280.dp
private val TintWidth = 760.dp
private val TintHeight = 560.dp
private const val TINT_ALPHA = 0.35f

/** The show's AniList cover colour, or [fallback] when AniList doesn't give one. */
internal fun detailsTint(
    coverColor: String?,
    fallback: Color,
) = parseColour(coverColor) ?: fallback

private fun List<Pair<Float, Float>>.inInk(ink: Color) = map { (at, alpha) -> at to ink.copy(alpha = alpha) }.toTypedArray()

// From the design: ink over the backdrop from the left edge to the right, the same as the spotlight's.
private val AcrossStops = listOf(0f to 0.88f, 0.34f to 0.7f, 0.55f to 0.42f, 0.8f to 0f, 1f to 0f)

// From the design: ink from the top down. Clear above 280 dp, 0.85 by the facts at 415 dp, and solid at the bottom, under
// the synopsis.
private val DownStops = listOf(0f to 0f, 280f / 540 to 0f, 415f / 540 to 0.85f, 1f to 1f)

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

private val relationNames =
    mapOf(
        Relation.PARENT to R.string.details_relation_parent,
        Relation.PREQUEL to R.string.details_relation_prequel,
        Relation.SEQUEL to R.string.details_relation_sequel,
        Relation.SIDE_STORY to R.string.details_relation_side_story,
        Relation.SPIN_OFF to R.string.details_relation_spin_off,
    )

/** How it's related, then its format and year, such as "Sequel · TV · 2008". */
@Composable
private fun RelatedAnime.caption(): String =
    listOfNotNull(stringResource(relationNames.getValue(relation)), card.formatName(), card.seasonYear?.toString()).joinToString(" · ")

/** Such as "Fall 2026", or just the year when AniList doesn't give the season. */
@Composable
private fun AnimeCard.seasonName(): String? {
    val year = seasonYear ?: return null
    return seasonNames[season]?.let { stringResource(it, year) } ?: year.toString()
}
