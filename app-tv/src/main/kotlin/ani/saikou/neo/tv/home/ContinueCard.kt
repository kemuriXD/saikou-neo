package ani.saikou.neo.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import coil3.compose.AsyncImage

// From the design: a faint episode number runs off the card's bottom right, behind the title.
private const val EPISODE_ALPHA = 0.28f
private const val TRACK_ALPHA = 0.2f
private val ProgressHeight = 4.dp

/** A show to carry on with: the episode's still and number, the title, the time left and how far in it is. */
@Composable
internal fun ContinueCard(
    card: AnimeCard,
    progress: WatchProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    WideCard(onClick, modifier) {
        Box(Modifier.fillMaxSize().background(colors.surfaceRaised)) {
            AsyncImage(
                model = progress.still,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = progress.episode.toString().padStart(2, '0'),
                style = NeoTheme.typography.display.copy(fontSize = 88.sp, lineHeight = 88.sp, letterSpacing = (-4).sp),
                color = colors.paper.copy(alpha = EPISODE_ALPHA),
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 22.dp).semantics { hideFromAccessibility() },
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to colors.ink.copy(alpha = 0.85f), 0.7f to colors.ink.copy(alpha = 0.2f)),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 60.dp, bottom = 14.dp)) {
                Text(
                    text = card.displayTitle(),
                    style = NeoTheme.typography.label.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
                    color = colors.paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.home_continue_meta, progress.episode, progress.minutesLeft),
                    style = NeoTheme.typography.caption,
                    color = colors.textMuted,
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(ProgressHeight)
                    .background(colors.paper.copy(alpha = TRACK_ALPHA)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.fraction)
                    .height(ProgressHeight)
                    // A round end, as a square one looks cut off; the card's corner already rounds the start.
                    .background(colors.pink, RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)),
            )
        }
    }
}
