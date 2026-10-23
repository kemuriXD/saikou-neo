package ani.saikou.neo.tv.sample

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Text
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.KeylineScrolling
import ani.saikou.neo.tv.theme.NeoTheme

internal fun sampleCardTag(index: Int) = "sample-card-$index"

// Placeholder art until real key art arrives with Home (#66).
private val sampleArt =
    listOf(
        Color(0xFF13394A) to Color(0xFF5F4BB6),
        Color(0xFF2B3A67) to Color(0xFF9FB4FF),
        Color(0xFF5A1E1E) to Color(0xFFF28C28),
        Color(0xFF0E5E6F) to Color(0xFF7BD389),
        Color(0xFF0B2545) to Color(0xFF13A8D6),
        Color(0xFF3A1F3A) to Color(0xFFE58FB0),
    )

// Enough cards to scroll well past the keyline.
private const val SAMPLE_CARD_COUNT = 12

/** A row of wide cards that shows the design system's focus style on a real TV. */
@Composable
fun SampleRow(modifier: Modifier = Modifier) {
    val dimensions = NeoTheme.dimensions
    val firstCard = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstCard.requestFocus() }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.sample_row_title),
            style = NeoTheme.typography.rowTitle,
            color = NeoTheme.colors.paper,
            modifier = Modifier.padding(start = dimensions.safeX),
        )
        KeylineScrolling {
            LazyRow(
                contentPadding = PaddingValues(horizontal = dimensions.safeX, vertical = dimensions.space4),
                horizontalArrangement = Arrangement.spacedBy(dimensions.space4),
            ) {
                items(SAMPLE_CARD_COUNT) { index ->
                    SampleCard(
                        index = index,
                        modifier =
                            Modifier
                                .testTag(sampleCardTag(index))
                                .then(if (index == 0) Modifier.focusRequester(firstCard) else Modifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleCard(
    index: Int,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val shape = RoundedCornerShape(dimensions.radiusMd)
    val (from, to) = sampleArt[index % sampleArt.size]
    val progress = (index % sampleArt.size + 1) / (sampleArt.size + 1f)

    Card(
        onClick = {},
        modifier = modifier.size(width = 208.dp, height = 117.dp),
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = dimensions.focusScale),
        border =
            CardDefaults.border(
                focusedBorder = Border(BorderStroke(dimensions.focusRing, colors.focus), shape = shape),
            ),
        glow = CardDefaults.glow(focusedGlow = Glow(Color.White.copy(alpha = 0.28f), dimensions.glowFocus)),
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(from, to)))) {
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
                    text = stringResource(R.string.sample_card_title, index + 1),
                    style = NeoTheme.typography.labelLarge.copy(fontSize = NeoTheme.typography.body.fontSize),
                    color = colors.paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.sample_card_meta, index + 2, 24 - index % sampleArt.size * 3),
                    style = NeoTheme.typography.caption,
                    color = colors.textMuted,
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(colors.fillTonal),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(colors.pink))
            }
        }
    }
}
