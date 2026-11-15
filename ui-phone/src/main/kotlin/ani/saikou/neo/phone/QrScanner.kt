package ani.saikou.neo.phone

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Reads a QR code from a camera frame's luminance (Y) plane with ZXing (ADR 0039). One per analysis thread. */
internal class QrDecoder {
    private val reader = QRCodeReader()
    private val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))

    /** The text of the QR code in the frame, or null if there's none it can read. */
    fun decode(
        luminance: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
    ): String? {
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (_: ReaderException) {
            null
        } catch (_: RuntimeException) {
            // ZXing can throw on odd input; a frame it can't read is just a frame without a code.
            null
        } finally {
            reader.reset()
        }
    }
}

/**
 * The back camera, with every frame checked for a QR code. [onQrCode] runs on the main thread with each new code it
 * reads and says whether that's the one wanted. Once it says yes, scanning stops. The camera follows the screen's
 * lifecycle, so it's off while the app is in the background.
 */
@Composable
internal fun QrViewfinder(
    onQrCode: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val wanted by rememberUpdatedState(onQrCode)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    LaunchedEffect(context, lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        val analysis =
            ImageAnalysis
                .Builder()
                .setResolutionSelector(
                    ResolutionSelector
                        .Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        ).build(),
                ).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
        val worker = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val found = AtomicBoolean(false)
        // Off once the screen leaves, so a code read just before then can't still act.
        val active = AtomicBoolean(true)
        val decoder = QrDecoder()
        var last: String? = null
        analysis.setAnalyzer(worker) { image ->
            val text = image.use { if (found.get()) null else decoder.decode(it) }
            if (text != null && text != last) {
                last = text
                main.execute { if (active.get() && !found.get() && wanted(text)) found.set(true) }
            }
        }
        try {
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            awaitCancellation()
        } finally {
            active.set(false)
            analysis.clearAnalyzer()
            provider.unbind(preview, analysis)
            worker.shutdown()
        }
    }

    val description = stringResource(R.string.pair_camera)
    surfaceRequest?.let { request ->
        CameraXViewfinder(surfaceRequest = request, modifier = modifier.semantics { contentDescription = description })
    }
}

private fun QrDecoder.decode(image: ImageProxy): String? {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    return decode(bytes, image.width, image.height, plane.rowStride)
}

/** Big enough to read a TV's code from across a room, small enough to check every frame. */
private val ANALYSIS_SIZE = android.util.Size(1280, 720)
