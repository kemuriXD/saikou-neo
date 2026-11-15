package ani.saikou.neo.tv.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.placeholder
import ani.saikou.neo.tv.ui.rememberPulse
import coil3.compose.AsyncImage

// With the column's gap, 16 dp from the poster to a label above the title.
private val OverlineDrop = 8.dp

// The font asks for more than the design's small line heights, so the lines hold to them.
private val TightLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both, LineHeightStyle.Mode.Tight)

/** How big a [PosterCard] is: the poster, its corners and the gap down to its title. */
internal enum class PosterSize(
    val width: Dp,
    val height: Dp,
    val radius: Dp,
    val titleGap: Dp,
) {
    /** In rows on Home and Details. */
    Regular(124.dp, 186.dp, 8.dp, 8.dp),

    /** In See more's grid, where two whole rows fit with their titles. */
    Small(108.dp, 162.dp, 10.dp, 12.dp),
}

/**
 * A 2:3 poster for one anime, with its title and, under that, its format and year or a [caption] in their place. An
 * [overline], such as how it's related, goes above the title in [overlineColor], pink unless given. The title takes
 * up [titleLines] lines, even when it's shorter. [modifier] goes on the focusable poster.
 */
@Composable
internal fun PosterCard(
    card: AnimeCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    overline: String? = null,
    overlineColor: Color = NeoTheme.colors.pink,
    titleLines: Int = 1,
    size: PosterSize = PosterSize.Regular,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val small = size == PosterSize.Small
    val shape = RoundedCornerShape(size.radius)
    var focused by remember { mutableStateOf(false) }
    // The poster pulses until its picture is in, or until it can't come.
    var loading by remember(card.coverImageUrl) { mutableStateOf(card.coverImageUrl != null) }

    Column(Modifier.width(size.width), verticalArrangement = Arrangement.spacedBy(size.titleGap)) {
        Card(
            onClick = onClick,
            modifier =
                modifier
                    .size(size.width, size.height)
                    .onFocusChanged { focused = it.isFocused },
            shape = CardDefaults.shape(shape),
            scale = CardDefaults.scale(focusedScale = dimensions.focusScale),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(dimensions.focusRing, colors.focus), shape = shape)),
            glow = CardDefaults.glow(focusedGlow = Glow(Color.White.copy(alpha = 0.28f), dimensions.glowFocus)),
        ) {
            AsyncImage(
                model = card.coverImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                onSuccess = { loading = false },
                onError = { loading = false },
                modifier = Modifier.fillMaxSize().placeholder(colors.surfaceRaised, RectangleShape, rememberPulse(loading)),
            )
        }
        // From the design: a label sits further from the poster, clear of the poster's growth on focus.
        Column(
            Modifier.padding(top = if (overline != null) OverlineDrop else 0.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            overline?.let {
                Text(
                    text = it.uppercase(),
                    style = NeoTheme.typography.overline.copy(fontWeight = FontWeight.Bold),
                    color = overlineColor,
                    maxLines = 1,
                )
            }
            Text(
                text = card.displayTitle(),
                style = if (small) SmallTitle else NeoTheme.typography.label,
                color = if (focused) colors.paper else colors.textMuted,
                // Always as tall as its lines, so the lines under titles in a row stay level.
                minLines = titleLines,
                maxLines = titleLines,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // The episode count wouldn't fit under a poster.
                text = caption ?: listOfNotNull(card.formatName(), card.seasonYear?.toString()).joinToString(" · "),
                style = if (small) SmallCaption else NeoTheme.typography.caption,
                color = colors.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// The design's smaller lines under See more's posters, each its full height so the rows keep their spacing.
private val SmallTitle
    @Composable get() =
        NeoTheme.typography.label.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, lineHeightStyle = TightLine)

private val SmallCaption
    @Composable get() = NeoTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, lineHeightStyle = TightLine)
