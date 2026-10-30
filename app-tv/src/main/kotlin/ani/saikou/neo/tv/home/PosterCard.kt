package ani.saikou.neo.tv.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/**
 * A 2:3 poster for one anime, with its title and, under that, its format and year or a [caption] in their place.
 * [modifier] goes on the focusable poster.
 */
@Composable
internal fun PosterCard(
    card: AnimeCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val shape = RoundedCornerShape(dimensions.radiusSm)
    var focused by remember { mutableStateOf(false) }
    // The poster pulses until its picture is in, or until it can't come.
    var loading by remember(card.coverImageUrl) { mutableStateOf(card.coverImageUrl != null) }

    Column(Modifier.width(dimensions.cardPosterWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            onClick = onClick,
            modifier =
                modifier
                    .size(dimensions.cardPosterWidth, dimensions.cardPosterHeight)
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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = card.displayTitle(),
                style = NeoTheme.typography.label,
                color = if (focused) colors.paper else colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // The episode count wouldn't fit under a poster.
                text = caption ?: listOfNotNull(card.formatName(), card.seasonYear?.toString()).joinToString(" · "),
                style = NeoTheme.typography.caption,
                color = colors.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
