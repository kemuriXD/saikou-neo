package ani.saikou.neo.tv.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.details.DetailsState
import ani.saikou.neo.shared.details.plainSynopsis
import ani.saikou.neo.shared.details.scoreOutOfTen
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.home.displayTitle
import ani.saikou.neo.tv.home.formatName
import ani.saikou.neo.tv.theme.NeoTheme
import coil3.compose.AsyncImage

internal const val DETAILS_META_TAG = "details-meta"
internal const val DETAILS_SYNOPSIS_TAG = "details-synopsis"
internal const val DETAILS_LOADING_TAG = "details-loading"

// From the design: the About column starts 90 dp down and is 340 dp wide.
private val AboutTop = 90.dp
private val AboutWidth = 340.dp

// The rest of the column is the episode list's (#69), so a long synopsis stops here.
private const val SYNOPSIS_MAX_LINES = 10

private val seasonNames =
    mapOf(
        AnimeSeason.WINTER to R.string.details_season_winter,
        AnimeSeason.SPRING to R.string.details_season_spring,
        AnimeSeason.SUMMER to R.string.details_season_summer,
        AnimeSeason.FALL to R.string.details_season_fall,
    )

/** One anime: what it is, when it aired, how it scores and what it's about, over its dimmed art. */
@Composable
fun DetailsScreen(
    state: DetailsState,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(NeoTheme.colors.ink)) {
        Backdrop((state as? DetailsState.Loaded)?.details?.card)
        val about = Modifier.padding(start = NeoTheme.dimensions.gridMargin, top = AboutTop).width(AboutWidth)
        when (state) {
            DetailsState.Loading -> {
                LoadingAbout(about)
            }

            is DetailsState.Loaded -> {
                About(state.details, about)
            }

            DetailsState.Failed -> {
                Text(
                    text = stringResource(R.string.details_failed),
                    style = NeoTheme.typography.bodyLarge,
                    color = NeoTheme.colors.paper,
                    modifier = about,
                )
            }
        }
    }
}

@Composable
private fun About(
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
        plainSynopsis(details.description)?.let { synopsis ->
            Text(
                text = synopsis,
                style = NeoTheme.typography.body,
                color = colors.textMuted,
                maxLines = SYNOPSIS_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag(DETAILS_SYNOPSIS_TAG),
            )
        }
    }
}

/** Placeholders in the text's place, sized as in the design. */
@Composable
private fun LoadingAbout(modifier: Modifier = Modifier) {
    val dimensions = NeoTheme.dimensions
    Column(modifier.testTag(DETAILS_LOADING_TAG), verticalArrangement = Arrangement.spacedBy(dimensions.space3)) {
        Skeleton(100.dp, 16.dp)
        Skeleton(280.dp, 38.dp)
        Skeleton(240.dp, 14.dp)
        Skeleton(330.dp, 14.dp)
        Skeleton(300.dp, 14.dp)
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

/** Genres, episode count and score, such as "Drama · Mystery · 12 episodes · Score 8.5". */
@Composable
private fun AnimeDetails.meta(): String {
    val episodes = card.episodes?.let { pluralStringResource(R.plurals.home_episodes, it, it) }
    val score = scoreOutOfTen(card.averageScore)?.let { stringResource(R.string.details_score, it) }
    return (genres + listOfNotNull(episodes, score)).joinToString(" · ")
}

/** Such as "Fall 2026", or just the year when AniList doesn't give the season. */
@Composable
private fun AnimeCard.seasonName(): String? {
    val year = seasonYear ?: return null
    return seasonNames[season]?.let { stringResource(it, year) } ?: year.toString()
}
