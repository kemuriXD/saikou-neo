package ani.saikou.neo.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ani.saikou.neo.phone.theme.PhoneColors

/** Below this width the window is compact (a phone) and screens use all of it. */
private val COMPACT_WIDTH = 600.dp

/** The widest a screen's content gets on a tablet or an unfolded foldable, centred (#72). */
private val COLUMN_WIDTH = 480.dp

/** Holds a screen's content: the whole width on a phone, a centred column of at most 480 dp on anything wider. */
@Composable
internal fun PhoneColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val width = if (maxWidth < COMPACT_WIDTH) Dp.Unspecified else COLUMN_WIDTH
        Box(Modifier.fillMaxHeight().widthIn(max = width)) { content() }
    }
}

/** The AniList user's first letter on pink: 32 dp in the app bar, 56 dp on Account. */
@Composable
internal fun Avatar(
    name: String,
    size: Dp,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size).background(PhoneColors.pink, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            text = name.firstOrNull()?.uppercase().orEmpty(),
            style = style,
            color = PhoneColors.ink,
        )
    }
}
