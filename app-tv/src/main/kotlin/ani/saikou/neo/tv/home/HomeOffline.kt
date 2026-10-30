package ani.saikou.neo.tv.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoButtonRow
import ani.saikou.neo.tv.ui.NeoIcons

// From the design: the message starts 160 dp in and 140 dp down, 520 dp wide.
private val MessageStart = 160.dp
private val MessageTop = 140.dp
private val MessageWidth = 520.dp
private const val BODY_ALPHA = 0.8f
private const val WATERMARK_ALPHA = 0.04f

/**
 * What Home shows when the TV isn't online: Try again, which has focus, calls [onRetry], and Network settings calls
 * [onNetworkSettings]. [onInView] is called with true while it's shown and false once it isn't.
 */
@Composable
internal fun HomeOffline(
    onRetry: () -> Unit,
    onNetworkSettings: () -> Unit,
    onInView: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val typography = NeoTheme.typography
    val retry = remember { FocusRequester() }
    LaunchedEffect(Unit) { retry.requestFocus() }
    DisposableEffect(Unit) {
        onInView(true)
        onDispose { onInView(false) }
    }
    Box(modifier.fillMaxSize()) {
        // A large faint word in the corner, as in the design.
        Text(
            text = stringResource(R.string.home_offline_watermark),
            style = typography.display.copy(fontSize = 300.sp, lineHeight = 300.sp, letterSpacing = (-12).sp),
            color = colors.paper.copy(alpha = WATERMARK_ALPHA),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(Alignment.BottomEnd).offset(30.dp, 80.dp).clearAndSetSemantics {},
        )
        Column(
            Modifier.padding(start = MessageStart, top = MessageTop).width(MessageWidth),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(NeoIcons.WifiOff, contentDescription = null, tint = colors.warning, modifier = Modifier.size(40.dp))
            Text(
                text = stringResource(R.string.home_offline_title),
                style = typography.headline.copy(fontSize = 32.sp),
                color = colors.paper,
            )
            Text(
                text = stringResource(R.string.home_offline_body),
                style = typography.labelLarge.copy(fontWeight = FontWeight.Normal, lineHeight = 24.sp),
                color = colors.paper.copy(alpha = BODY_ALPHA),
            )
            NeoButtonRow(Modifier.padding(top = 10.dp)) {
                NeoButton(stringResource(R.string.try_again), onClick = onRetry, modifier = Modifier.focusRequester(retry))
                NeoButton(stringResource(R.string.home_offline_settings), onClick = onNetworkSettings)
            }
            Text(
                text = stringResource(R.string.home_offline_retrying),
                style = typography.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textFaint,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
