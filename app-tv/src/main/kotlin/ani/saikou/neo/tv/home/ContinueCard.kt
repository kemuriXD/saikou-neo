package ani.saikou.neo.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.placeholder
import ani.saikou.neo.tv.ui.rememberPulse
import coil3.compose.AsyncImage

private const val TRACK_ALPHA = 0.2f
private val ProgressHeight = 4.dp
private val ProgressBottom = 10.dp

// The title and time left sit 8 dp above the progress line.
private val TextBottom = ProgressBottom + ProgressHeight + 8.dp

/** A show to carry on with: the episode's still, the title, the episode and time left, and how far in it is. */
@Composable
internal fun ContinueCard(
    card: AnimeCard,
    progress: WatchProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    // The card pulses until its still is in, or until it can't come.
    var loading by remember(progress.still) { mutableStateOf(progress.still != null) }
    WideCard(onClick, modifier) {
        Box(Modifier.fillMaxSize().placeholder(colors.surfaceRaised, RectangleShape, rememberPulse(loading))) {
            AsyncImage(
                model = progress.still,
                onSuccess = { loading = false },
                onError = { loading = false },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0f to colors.ink.copy(alpha = 0.85f), 0.7f to colors.ink.copy(alpha = 0.2f)),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 60.dp, bottom = TextBottom)) {
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
            // Inset and round at both ends, so it doesn't run into the card's rounded corners.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = ProgressBottom)
                    .fillMaxWidth()
                    .height(ProgressHeight)
                    .background(colors.paper.copy(alpha = TRACK_ALPHA), CircleShape),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.fraction).background(colors.pink, CircleShape))
            }
        }
    }
}
