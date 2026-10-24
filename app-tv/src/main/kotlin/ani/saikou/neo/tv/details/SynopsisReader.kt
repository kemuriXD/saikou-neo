package ani.saikou.neo.tv.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import ani.saikou.neo.shared.details.Synopsis
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import kotlinx.coroutines.launch

internal const val DETAILS_READER_TAG = "details-reader"

private val ReaderTop = 54.dp
private val ReaderWidth = 640.dp

// One press moves about four lines, keeping some of the last screen in view.
private val ScrollStep = 88.dp

/** The whole synopsis on its own, with any editors' notes and the source credit after it. Up and down scroll; Back closes. */
@Composable
internal fun SynopsisReader(
    title: String,
    synopsis: Synopsis,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { ScrollStep.toPx() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.ink)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                val by =
                    when (event.key) {
                        Key.DirectionDown -> step
                        Key.DirectionUp -> -step
                        else -> return@onPreviewKeyEvent false
                    }
                if (event.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(by) }
                true
            }.focusable()
            .testTag(DETAILS_READER_TAG)
            .verticalScroll(scroll)
            .padding(start = dimensions.gridMargin, top = ReaderTop, bottom = dimensions.safeY),
        verticalArrangement = Arrangement.spacedBy(dimensions.space4),
    ) {
        Text(text = title, style = NeoTheme.typography.titleLarge, color = colors.paper, modifier = Modifier.width(ReaderWidth))
        synopsis.story?.let {
            Text(text = it, style = NeoTheme.typography.bodyLarge, color = colors.paper, modifier = Modifier.width(ReaderWidth))
        }
        for (note in synopsis.notes) {
            Column(Modifier.width(ReaderWidth)) {
                Text(text = stringResource(R.string.details_note).uppercase(), style = NeoTheme.typography.overline, color = colors.pink)
                Text(text = note, style = NeoTheme.typography.body, color = colors.textMuted)
            }
        }
        synopsis.source?.let {
            Text(text = stringResource(R.string.details_source, it), style = NeoTheme.typography.caption, color = colors.textFaint)
        }
    }
}
