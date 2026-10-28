package ani.saikou.neo.tv.details

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.home.PosterCard
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoIcons
import ani.saikou.neo.tv.ui.outToTheScreenEdge
import ani.saikou.neo.tv.ui.remembersFocus

internal fun relatedPosterTag(animeId: Int) = "related-$animeId"

internal const val DETAILS_SEE_MORE_TAG = "details-see-more"
internal const val DETAILS_POSTER_ROW_TAG = "details-poster-row"

// The See more button's key in the row, apart from every anime id.
private const val SEE_MORE = -1

/** A poster and the line under its title. */
internal data class Poster(
    val card: AnimeCard,
    val caption: String? = null,
)

/**
 * A row of posters, as on See more, ending in a [seeMore] button when there are more to see. Selecting a poster calls
 * [onOpen]. Coming back from what it opened, focus returns to that poster or See more.
 */
@Composable
internal fun PosterRowSection(
    posters: List<Poster>,
    onOpen: (animeId: Int) -> Unit,
    modifier: Modifier = Modifier,
    seeMore: (() -> Unit)? = null,
) {
    val dimensions = NeoTheme.dimensions
    val requesters = remember(posters) { posters.associate { it.card.id to FocusRequester() } }
    // Room above for a focused poster's growth and glow.
    LazyRow(
        // Up or down into the row lands on its first poster, not the one nearest the tab above.
        modifier
            .outToTheScreenEdge(dimensions.gridMargin)
            .testTag(DETAILS_POSTER_ROW_TAG)
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
                        posters.firstOrNull()?.let { requesters.getValue(it.card.id).requestFocus() }
                    }
                }
            }.focusGroup(),
        contentPadding =
            PaddingValues(
                start = dimensions.gridMargin,
                top = dimensions.space4,
                end = dimensions.gridMargin,
                bottom = dimensions.space4,
            ),
        horizontalArrangement = Arrangement.spacedBy(dimensions.gridGutter),
    ) {
        items(posters, key = { it.card.id }) { poster ->
            PosterCard(
                card = poster.card,
                onClick = { onOpen(poster.card.id) },
                modifier =
                    Modifier
                        .focusRequester(requesters.getValue(poster.card.id))
                        .remembersFocus("poster-${poster.card.id}")
                        .testTag(relatedPosterTag(poster.card.id)),
                caption = poster.caption,
            )
        }
        if (seeMore != null) {
            item(key = SEE_MORE) {
                // Level with the middle of the posters, as at the end of a Home row.
                Box(Modifier.height(dimensions.cardPosterHeight), contentAlignment = Alignment.Center) {
                    NeoButton(
                        text = stringResource(R.string.home_see_more),
                        onClick = seeMore,
                        trailingIcon = NeoIcons.Arrow,
                        modifier = Modifier.remembersFocus("see-more").testTag(DETAILS_SEE_MORE_TAG),
                    )
                }
            }
        }
    }
}
