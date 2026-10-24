package ani.saikou.neo.tv.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import coil3.compose.AsyncImage

/** A wide card for one anime: its banner, or its cover cropped from the top, with the title on a scrim. */
@Composable
internal fun HomeCard(
    card: AnimeCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val shape = RoundedCornerShape(dimensions.radiusMd)
    val placeholder = remember(card.coverColor) { parseColour(card.coverColor) ?: colors.surfaceRaised }

    Card(
        onClick = onClick,
        modifier = modifier.size(width = dimensions.cardWideWidth, height = dimensions.cardWideHeight),
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = dimensions.focusScale),
        border =
            CardDefaults.border(
                focusedBorder = Border(BorderStroke(dimensions.focusRing, colors.focus), shape = shape),
            ),
        glow = CardDefaults.glow(focusedGlow = Glow(Color.White.copy(alpha = 0.28f), dimensions.glowFocus)),
    ) {
        Box(Modifier.fillMaxSize().background(placeholder)) {
            // AsyncImage decodes at the card's size, not the source's.
            AsyncImage(
                model = card.bannerImageUrl ?: card.coverImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = if (card.bannerImageUrl != null) Alignment.Center else Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, colors.ink.copy(alpha = 0.9f)))),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
            ) {
                Text(
                    text = card.displayTitle(),
                    style = NeoTheme.typography.label,
                    color = colors.paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.meta(),
                    style = NeoTheme.typography.caption,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The English title where AniList has one, as most viewers here read English. */
internal fun AnimeCard.displayTitle(): String = title.english ?: title.romaji ?: title.native.orEmpty()

/** Format, year and episode count, such as "TV · 2026 · 12 episodes". */
@Composable
internal fun AnimeCard.meta(): String {
    val format = formatName()
    val episodes = episodes?.let { pluralStringResource(R.plurals.home_episodes, it, it) }
    return listOfNotNull(format, seasonYear?.toString(), episodes).joinToString(" · ")
}

/** The format in words, such as "Movie", or AniList's own name for a format the app doesn't know yet. */
@Composable
internal fun AnimeCard.formatName(): String? = formatNames[format]?.let { stringResource(it) } ?: format

private val formatNames =
    mapOf(
        "TV" to R.string.home_format_tv,
        "TV_SHORT" to R.string.home_format_tv_short,
        "MOVIE" to R.string.home_format_movie,
        "SPECIAL" to R.string.home_format_special,
        "OVA" to R.string.home_format_ova,
        "ONA" to R.string.home_format_ona,
        "MUSIC" to R.string.home_format_music,
    )

/** AniList gives cover colours as `#rrggbb`. */
private fun parseColour(hex: String?): Color? =
    hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let {
        Color(0xFF000000 or it)
    }
