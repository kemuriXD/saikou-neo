package ani.saikou.neo.phone

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrDecoderTest {
    private val text = "saikouneo://pair?v=1&host=192.168.1.20&port=40123&key=BAECAwQ&session=AAcOFRw"

    /** A camera frame's luminance plane: the QR code drawn dark on light, with each row padded out to [rowStride]. */
    private fun frame(
        size: Int,
        rowStride: Int,
    ): ByteArray {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        return ByteArray(rowStride * size) { index ->
            val x = index % rowStride
            val y = index / rowStride
            if (x < size && matrix[x, y]) 20 else 230.toByte()
        }
    }

    @Test
    fun readsAQrCodeFromALuminancePlane() {
        assertEquals(text, QrDecoder().decode(frame(400, 400), width = 400, height = 400, rowStride = 400))
    }

    @Test
    fun readsAFrameWhoseRowsArePadded() {
        assertEquals(text, QrDecoder().decode(frame(400, 448), width = 400, height = 400, rowStride = 448))
    }

    @Test
    fun aFrameWithNoCodeGivesNothing() {
        assertNull(QrDecoder().decode(ByteArray(400 * 400) { 128.toByte() }, width = 400, height = 400, rowStride = 400))
    }
}
