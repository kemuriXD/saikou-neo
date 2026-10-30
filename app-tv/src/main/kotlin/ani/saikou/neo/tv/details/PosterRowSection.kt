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

internal fun detailsPosterTag(animeId: Int) = "details-poster-$animeId"

internal const val DETAILS_SEE_MORE_TAG = "details-see-more"
internal const val DETAILS_POSTER_ROW_TAG = "details-poster-row"

// The See more button's key in the row, apart from every anime id.
private const val SEE_MORE = -1

/**
 * A row of posters, as on See more, ending in a [seeMore] button when there are more to see. Selecting a poster calls
 * [onOpen]. Coming back from what it opened, focus returns to that poster or See more. A screen with several rows
 * gives each its own [focusKey], so focus returns to the right one.
 */
@Composable
internal fun PosterRowSection(
    cards: List<AnimeCard>,
    onOpen: (animeId: Int) -> Unit,
    modifier: Modifier = Modifier,
    seeMore: (() -> Unit)? = null,
    focusKey: String = "poster",
) {
    val dimensions = NeoTheme.dimensions
    val requesters = remember(cards) { cards.associate { it.id to FocusRequester() } }
    // Room above for a focused poster's growth and glow.
    LazyRow(
        // Up or down into the row lands on its first poster, not the one nearest the tab above.
        modifier
            .outToTheScreenEdge(dimensions.gridMargin)
            .testTag(DETAILS_POSTER_ROW_TAG)
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
                        cards.firstOrNull()?.let { requesters.getValue(it.id).requestFocus() }
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
        items(cards, key = { it.id }) { card ->
            PosterCard(
                card = card,
                onClick = { onOpen(card.id) },
                modifier =
                    Modifier
                        .focusRequester(requesters.getValue(card.id))
                        .remembersFocus("$focusKey-${card.id}")
                        .testTag(detailsPosterTag(card.id)),
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
