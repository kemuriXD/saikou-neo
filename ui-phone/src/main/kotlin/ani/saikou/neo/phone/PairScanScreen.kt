package ani.saikou.neo.phone

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ani.saikou.neo.phone.theme.PhoneColors

/**
 * 3.1 Pair: scan the TV code, or 3.2 Pair: camera not allowed when [cameraAllowed] is false. While the phone is still
 * asking ([cameraAllowed] is null) the card stays empty. [viewfinder] fills the card with the camera. 4.1 Send a source
 * is the same screen with its own [title] and [body].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PairScanScreen(
    cameraAllowed: Boolean?,
    onClose: () -> Unit,
    onType: () -> Unit,
    onOpenSettings: () -> Unit,
    viewfinder: @Composable (Modifier) -> Unit,
    @StringRes title: Int = R.string.pair_title,
    @StringRes body: Int = R.string.pair_scan_body,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.icon_close), contentDescription = stringResource(R.string.pair_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        PhoneColumn(Modifier.padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                if (cameraAllowed == false) {
                    CameraOff(Modifier.weight(1f))
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Icon(painterResource(R.drawable.icon_settings), contentDescription = null, Modifier.size(24.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.pair_open_settings), style = MaterialTheme.typography.titleMedium)
                        }
                        TypeInstead(onType, small = true)
                    }
                } else {
                    Scan(cameraAllowed == true, viewfinder, body, Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().padding(16.dp)) { TypeInstead(onType, small = false) }
                }
            }
        }
    }
}

@Composable
private fun Scan(
    showCamera: Boolean,
    viewfinder: @Composable (Modifier) -> Unit,
    @StringRes body: Int,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(440.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(PhoneColors.surface),
        ) {
            if (showCamera) viewfinder(Modifier.fillMaxSize())
            // The four pink corners frame where the code should sit.
            Box(Modifier.align(Alignment.TopCenter).padding(top = 68.dp).size(240.dp)) {
                ScanCorner(0f, Modifier.align(Alignment.TopStart))
                ScanCorner(90f, Modifier.align(Alignment.TopEnd))
                ScanCorner(270f, Modifier.align(Alignment.BottomStart))
                ScanCorner(180f, Modifier.align(Alignment.BottomEnd))
            }
            Text(
                stringResource(R.string.pair_looking),
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 380.dp)
                        .background(PhoneColors.ink, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScanCorner(
    degrees: Float,
    modifier: Modifier,
) {
    Image(
        painterResource(R.drawable.scan_corner),
        contentDescription = null,
        modifier.size(48.dp).rotate(degrees),
        colorFilter = ColorFilter.tint(PhoneColors.pink),
    )
}

@Composable
private fun CameraOff(modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).background(PhoneColors.surfaceRaised, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.icon_videocam_off), contentDescription = null, Modifier.size(36.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.pair_camera_off),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.pair_camera_off_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Type the code instead: a tonal button under the camera, a text button under Open settings. */
@Composable
private fun TypeInstead(
    onType: () -> Unit,
    small: Boolean,
) {
    val content: @Composable () -> Unit = {
        Icon(painterResource(R.drawable.icon_keyboard), contentDescription = null, Modifier.size(if (small) 20.dp else 24.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.pair_type_instead),
            style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
        )
    }
    if (small) {
        TextButton(onClick = onType, modifier = Modifier.height(48.dp)) { content() }
    } else {
        FilledTonalButton(onClick = onType, modifier = Modifier.fillMaxWidth().height(56.dp)) { content() }
    }
}
