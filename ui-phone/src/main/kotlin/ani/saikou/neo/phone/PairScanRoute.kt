package ani.saikou.neo.phone

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * 3.1 Pair or 4.1 Send a source: scan the TV code, asking for the camera first. Denied, it shows 3.2. Coming back from the phone's settings
 * with the camera allowed starts it. Only the phone screens ever ask for the camera (ADR 0038).
 */
@Composable
internal fun PairScanRoute(
    onClose: () -> Unit,
    onType: () -> Unit,
    onQrCode: (String) -> Boolean,
    @StringRes title: Int = R.string.pair_title,
    @StringRes body: Int = R.string.pair_scan_body,
) {
    val context = LocalContext.current
    var allowed by rememberSaveable { mutableStateOf(if (context.hasCamera()) true else null) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> allowed = granted }
    LaunchedEffect(Unit) {
        if (allowed == null && !asked) {
            asked = true
            ask.launch(Manifest.permission.CAMERA)
        }
    }
    LifecycleResumeEffect(context) {
        if (context.hasCamera()) allowed = true
        onPauseOrDispose {}
    }

    PairScanScreen(
        cameraAllowed = allowed,
        onClose = onClose,
        onType = onType,
        onOpenSettings = { context.openAppSettings() },
        viewfinder = { modifier -> QrViewfinder(onQrCode, modifier) },
        title = title,
        body = body,
    )
}

private fun Context.hasCamera(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** This app's page in the phone's settings, where the camera can be allowed. */
private fun Context.openAppSettings() {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
    try {
        startActivity(details)
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
