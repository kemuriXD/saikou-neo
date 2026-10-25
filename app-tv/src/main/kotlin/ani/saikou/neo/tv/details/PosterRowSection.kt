package ani.saikou.neo.tv.details

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.tv.home.PosterCard
import ani.saikou.neo.tv.theme.NeoTheme

internal fun relatedPosterTag(animeId: Int) = "related-$animeId"

/** A poster and the line under its title. */
internal data class Poster(
    val card: AnimeCard,
    val caption: String? = null,
)

/**
 * A row of posters, as on See more. Selecting one calls [onOpen]. Coming back from the show it opened, focus returns to
 * the poster [returnTo], and [onReturned] is called.
 */
@Composable
internal fun PosterRowSection(
    posters: List<Poster>,
    onOpen: (animeId: Int) -> Unit,
    returnTo: Int?,
    onReturned: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = NeoTheme.dimensions
    val requesters = remember(posters) { posters.associate { it.card.id to FocusRequester() } }
    // Room above for a focused poster's growth and glow.
    LazyRow(
        // Up or down into the row lands on its first poster, not the one nearest the tab above.
        modifier
            .fillMaxWidth()
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
                        posters.firstOrNull()?.let { requesters.getValue(it.card.id).requestFocus() }
                    }
                }
            }.focusGroup(),
        contentPadding = PaddingValues(top = dimensions.space4, bottom = dimensions.space4, end = dimensions.gridMargin),
        horizontalArrangement = Arrangement.spacedBy(dimensions.gridGutter),
    ) {
        items(posters, key = { it.card.id }) { poster ->
            val requester = requesters.getValue(poster.card.id)
            if (poster.card.id == returnTo) {
                LaunchedEffect(Unit) {
                    requester.requestFocus()
                    onReturned()
                }
            }
            PosterCard(
                card = poster.card,
                onClick = { onOpen(poster.card.id) },
                modifier = Modifier.focusRequester(requester).testTag(relatedPosterTag(poster.card.id)),
                caption = poster.caption,
            )
        }
    }
}
