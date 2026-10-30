package ani.saikou.neo.tv.details

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.art
import ani.saikou.neo.shared.details.WatchOrderStep
import ani.saikou.neo.shared.details.watchOrder
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.home.displayTitle
import ani.saikou.neo.tv.home.formatName
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.outToTheScreenEdge
import ani.saikou.neo.tv.ui.remembersFocus
import coil3.compose.AsyncImage

internal fun watchOrderTag(animeId: Int) = "watch-order-$animeId"

internal fun watchOrderDotTag(animeId: Int) = "watch-order-dot-$animeId"

// From the design: five steps across the content width, each a numbered dot on a line over a card.
private val StepWidth = 156.dp
private val StepGap = 16.dp
private val DotSize = 28.dp
private val LineWidth = 2.dp
private val ArtHeight = 84.dp
private const val STEP_FOCUS_SCALE = 1.05f

/**
 * The show and the anime AniList relates to it, numbered in the order they came out, along a line. The show on this
 * page says "You are here", with the episode this TV got to, and the others say how they're related to it. The line
 * fills in pink up to the step focus is on, or up to this show while focus is elsewhere. Up or down into the path
 * lands on the step after this show. Selecting another step calls [onOpen].
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
            horizontalArrangement = Arrangement.spacedBy(StepGap),
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

/**
 * One step: its number on the line, pink once [reached], then a card with its art, title, format and episodes, and how
 * it's related. The line on to the next step is pink once [lineReached].
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
    Column(Modifier.width(StepWidth), verticalArrangement = Arrangement.spacedBy(dimensions.space3)) {
        // The line runs on from each dot to the next one's middle.
        Box(
            Modifier.fillMaxWidth().height(DotSize).drawBehind {
                if (last) return@drawBehind
                val y = size.height / 2
                drawLine(
                    line,
                    Offset(DotSize.toPx() / 2, y),
                    Offset(size.width + StepGap.toPx() + DotSize.toPx() / 2, y),
                    LineWidth.toPx(),
                )
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
        val shape = RoundedCornerShape(dimensions.radiusLg)
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = ClickableSurfaceDefaults.shape(shape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = colors.surfaceScrim,
                    contentColor = colors.paper,
                    focusedContainerColor = colors.focus,
                    focusedContentColor = colors.onFocus,
                    pressedContainerColor = colors.focus,
                    pressedContentColor = colors.onFocus,
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(dimensions.focusRing, colors.focus), shape = shape),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = STEP_FOCUS_SCALE),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.White.copy(alpha = 0.3f), dimensions.glowFocus)),
        ) {
            Column(
                Modifier.padding(start = dimensions.space2, top = dimensions.space2, end = dimensions.space2, bottom = dimensions.space3),
                verticalArrangement = Arrangement.spacedBy(dimensions.space1),
            ) {
                AsyncImage(
                    model = card.art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(ArtHeight)
                            .clip(RoundedCornerShape(dimensions.radiusSm))
                            .background(colors.surfaceRaised),
                )
                Text(
                    text = card.displayTitle(),
                    style = NeoTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val episodes = card.episodes?.let { pluralStringResource(R.plurals.details_header_episodes, it, it) }
                val meta = listOfNotNull(card.formatName(), episodes).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = NeoTheme.typography.caption,
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }
                val relation = step.relation
                Text(
                    text =
                        when {
                            relation != null -> stringResource(relationNames.getValue(relation))
                            progress != null -> stringResource(R.string.details_you_are_here_on, progress.episode)
                            else -> stringResource(R.string.details_you_are_here)
                        },
                    style = NeoTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color =
                        when {
                            relation != null -> LocalContentColor.current.copy(alpha = 0.52f)
                            LocalContentColor.current == colors.onFocus -> colors.pinkDeep
                            else -> colors.pink
                        },
                )
            }
        }
    }
}
