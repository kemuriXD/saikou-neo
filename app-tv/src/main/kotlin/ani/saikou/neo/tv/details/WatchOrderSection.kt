package ani.saikou.neo.tv.details

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.details.WatchOrderStep
import ani.saikou.neo.shared.details.watchOrder
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.home.PosterCard
import ani.saikou.neo.tv.home.formatName
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.theme.WindowScrolling
import ani.saikou.neo.tv.ui.outToTheScreenEdge
import ani.saikou.neo.tv.ui.remembersFocus

internal fun watchOrderTag(animeId: Int) = "watch-order-$animeId"

internal fun watchOrderDotTag(animeId: Int) = "watch-order-dot-$animeId"

// Each step is a numbered dot on a line, over a poster.
private val DotSize = 28.dp
private val LineWidth = 2.dp

/**
 * The show and the anime AniList relates to it, numbered in the order they came out, along a line. Each is a poster, as
 * on Related. The show on this page says "You are here", with the episode this TV got to, and the others say how
 * they're related to it. The line fills in pink up to the step focus is on, or up to this show while focus is
 * elsewhere. Up or down into the path lands on the step after this show. Selecting another step calls [onOpen].
 */
@Composable
internal fun WatchOrderSection(
    details: AnimeDetails,
    progress: WatchProgress?,
    onOpen: (animeId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = NeoTheme.dimensions
    val steps = remember(details) { details.watchOrder() }
    val here = steps.indexOfFirst { it.relation == null }
    var focused by remember { mutableStateOf<Int?>(null) }
    val reached = focused ?: here
    val landing = remember { FocusRequester() }
    val landsOn = (here + 1).takeIf { it < steps.size } ?: here
    Column(modifier, verticalArrangement = Arrangement.spacedBy(dimensions.space2)) {
        Text(
            text = stringResource(R.string.details_watch_order_note),
            style = NeoTheme.typography.caption,
            color = NeoTheme.colors.textMuted,
        )
        // Focus scrolls the path only as far as it must to keep the step inside the margins.
        WindowScrolling(leading = dimensions.gridMargin, trailing = dimensions.gridMargin) {
            LazyRow(
                modifier =
                    Modifier
                        .outToTheScreenEdge(dimensions.gridMargin)
                        .onFocusChanged { if (!it.hasFocus) focused = null }
                        .focusProperties {
                            onEnter = {
                                if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
                                    landing.requestFocus()
                                }
                            }
                        }.focusGroup(),
                contentPadding = PaddingValues(horizontal = dimensions.gridMargin, vertical = dimensions.space4),
                horizontalArrangement = Arrangement.spacedBy(dimensions.gridGutter),
            ) {
                itemsIndexed(steps, key = { _, step -> step.card.id }) { index, step ->
                    Step(
                        number = index + 1,
                        step = step,
                        progress = progress,
                        reached = index <= reached,
                        lineReached = index < reached,
                        last = index == steps.lastIndex,
                        onClick = { if (step.relation != null) onOpen(step.card.id) },
                        modifier =
                            Modifier
                                .then(if (index == landsOn) Modifier.focusRequester(landing) else Modifier)
                                .onFocusChanged { if (it.isFocused) focused = index }
                                .remembersFocus("step-${step.card.id}")
                                .testTag(watchOrderTag(step.card.id)),
                    )
                }
            }
        }
    }
}

/**
 * One step: its number on the line, pink once [reached], then its poster with how it's related above the title, and
 * its format and episodes below. The line on to the next step is pink once [lineReached].
 */
@Composable
private fun Step(
    number: Int,
    step: WatchOrderStep,
    progress: WatchProgress?,
    reached: Boolean,
    lineReached: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val card = step.card
    val fill = tween<Color>(dimensions.durationFocusMillis, easing = dimensions.easeStandard)
    val dot by animateColorAsState(if (reached) colors.pink else colors.ink, fill, label = "dot")
    val ring by animateColorAsState(if (reached) colors.pink else colors.line, fill, label = "ring")
    val line by animateColorAsState(if (lineReached) colors.pink else colors.line, fill, label = "line")
    Column(Modifier.width(dimensions.cardPosterWidth), verticalArrangement = Arrangement.spacedBy(dimensions.space3)) {
        // The line runs on from each dot to the next one's middle.
        val gap = dimensions.gridGutter
        Box(
            Modifier.fillMaxWidth().height(DotSize).drawBehind {
                if (last) return@drawBehind
                val y = size.height / 2
                drawLine(line, Offset(DotSize.toPx() / 2, y), Offset(size.width + gap.toPx() + DotSize.toPx() / 2, y), LineWidth.toPx())
            },
        ) {
            Box(
                Modifier
                    .size(DotSize)
                    .testTag(watchOrderDotTag(card.id))
                    .background(dot, CircleShape)
                    .border(LineWidth, ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString(),
                    style = NeoTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = if (reached) colors.ink else colors.textMuted,
                )
            }
        }
        val relation = step.relation
        val episodes = card.episodes?.let { pluralStringResource(R.plurals.details_header_episodes, it, it) }
        PosterCard(
            card = card,
            onClick = onClick,
            modifier = modifier,
            // The episode this TV got to leads the line under this show's title, as a poster's label has no room for it.
            caption =
                listOfNotNull(
                    progress?.takeIf { relation == null }?.let { stringResource(R.string.details_on_episode, it.episode) },
                    card.formatName(),
                    episodes,
                ).joinToString(" · "),
            overline =
                when {
                    relation != null -> stringResource(relationNames.getValue(relation))
                    else -> stringResource(R.string.details_you_are_here)
                },
        )
    }
}
