package ani.saikou.neo.tv.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.details.DetailsState
import ani.saikou.neo.shared.details.scoreOutOfTen
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.home.displayTitle
import ani.saikou.neo.tv.home.formatName
import ani.saikou.neo.tv.theme.NeoTheme
import coil3.compose.AsyncImage

internal const val DETAILS_META_TAG = "details-meta"
internal const val DETAILS_SYNOPSIS_TAG = "details-synopsis"
internal const val DETAILS_LOADING_TAG = "details-loading"

// The title block starts 90 dp down. Overview sits lower, at 300 dp, leaving room for the actions (#69) between.
private val HeroTop = 90.dp
private val HeroWidth = 580.dp
private val OverviewTop = 300.dp

// About 70 characters a line, a comfortable read from the sofa.
private val SynopsisWidth = 520.dp
private const val SYNOPSIS_PREVIEW_LINES = 3

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
    val loaded = state as? DetailsState.Loaded
    Box(modifier.fillMaxSize().background(NeoTheme.colors.ink)) {
        Backdrop(loaded?.details?.card)
        val margin = Modifier.padding(start = NeoTheme.dimensions.gridMargin)
        when (state) {
            DetailsState.Loading -> {
                LoadingHero(margin.padding(top = HeroTop))
            }

            is DetailsState.Loaded -> {
                Hero(state.details, margin.padding(top = HeroTop).width(HeroWidth))
                Overview(state, reading, onRead = { reading = true }, margin.padding(top = OverviewTop))
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
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val card = details.card
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space3)) {
        val overline = listOfNotNull(card.formatName(), card.seasonName()).joinToString(" · ")
        if (overline.isNotEmpty()) {
            Text(text = overline.uppercase(), style = NeoTheme.typography.overline, color = colors.pink)
        }
        Text(
            text = card.displayTitle(),
            style = NeoTheme.typography.headline,
            color = colors.paper,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = details.meta()
        if (meta.isNotEmpty()) {
            Text(text = meta, style = NeoTheme.typography.label, color = colors.textMuted, modifier = Modifier.testTag(DETAILS_META_TAG))
        }
    }
}

@Composable
private fun Overview(
    state: DetailsState.Loaded,
    reading: Boolean,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = NeoTheme.dimensions
    Column(modifier, verticalArrangement = Arrangement.spacedBy(dimensions.space3)) {
        Text(text = stringResource(R.string.details_overview), style = NeoTheme.typography.title, color = NeoTheme.colors.paper)
        state.synopsis.story?.let { story -> SynopsisPreview(story, enabled = !reading, onRead) }
        Facts(state.details, Modifier.padding(top = dimensions.space1))
    }
}

/** The start of the synopsis. It's the only thing to focus until the actions arrive, so focus starts here. */
@Composable
private fun SynopsisPreview(
    story: String,
    enabled: Boolean,
    onRead: () -> Unit,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val shape = RoundedCornerShape(dimensions.radiusLg)
    val focus = remember { FocusRequester() }
    // Asked again each time the reader closes. The card stays on screen under the reader, so it is
    // attached and listening when focus returns, and draws as focused.
    LaunchedEffect(enabled) { if (enabled) focus.requestFocus() }
    Surface(
        onClick = onRead,
        enabled = enabled,
        modifier = Modifier.width(SynopsisWidth).focusRequester(focus).testTag(DETAILS_SYNOPSIS_TAG),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.surfaceScrim,
                contentColor = colors.textMuted,
                focusedContainerColor = colors.paper,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.paper,
                pressedContentColor = colors.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.025f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.White.copy(alpha = 0.28f), dimensions.glowFocus)),
    ) {
        Text(
            text = story,
            style = NeoTheme.typography.body,
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

/** Placeholders in the text's place, sized as in the design. */
@Composable
private fun LoadingHero(modifier: Modifier = Modifier) {
    Column(modifier.testTag(DETAILS_LOADING_TAG), verticalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space3)) {
        Skeleton(100.dp, 16.dp)
        Skeleton(280.dp, 38.dp)
        Skeleton(240.dp, 14.dp)
    }
}

@Composable
private fun Skeleton(
    width: Dp,
    height: Dp,
) = Box(Modifier.size(width, height).background(NeoTheme.colors.surfaceRaised, RoundedCornerShape(6.dp)))

/** The banner, or the cover where there's none, dimmed so the text over it stays readable. */
@Composable
private fun Backdrop(card: AnimeCard?) {
    val ink = NeoTheme.colors.ink
    AsyncImage(
        model = card?.let { it.bannerImageUrl ?: it.coverImageUrl },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f to ink.copy(alpha = 0.97f),
                0.5f to ink.copy(alpha = 0.9f),
                1f to ink.copy(alpha = 0.72f),
            ),
        ),
    )
}

/** Genres and episode count, such as "Drama · Mystery · 12 episodes". */
@Composable
private fun AnimeDetails.meta(): String {
    val episodes = card.episodes?.let { pluralStringResource(R.plurals.home_episodes, it, it) }
    return (genres + listOfNotNull(episodes)).joinToString(" · ")
}

/** Such as "Fall 2026", or just the year when AniList doesn't give the season. */
@Composable
private fun AnimeCard.seasonName(): String? {
    val year = seasonYear ?: return null
    return seasonNames[season]?.let { stringResource(it, year) } ?: year.toString()
}
