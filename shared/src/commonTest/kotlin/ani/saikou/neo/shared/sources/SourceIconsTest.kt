package ani.saikou.neo.shared.sources

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The start of a PNG [width] by [height] pixels: its signature and header chunk, which is all the check reads. */
internal fun png(
    width: Int,
    height: Int,
): ByteArray {
    fun int(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
    val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    // The chunk's length, its type, then width, height, bit depth, colour type, compression, filter, interlace and CRC.
    return signature + int(13) + "IHDR".encodeToByteArray() + int(width) + int(height) + byteArrayOf(8, 6, 0, 0, 0) + int(0)
}

class SourceIconsTest {
    @Test
    fun aPngUpTo1024PixelsASideIsUsable() {
        assertTrue(isUsableIcon(png(96, 96)))
        assertTrue(isUsableIcon(png(1024, 512)))
        assertTrue(isUsableIcon(png(1, 1)))
    }

    @Test
    fun aBiggerPngIsNot() {
        assertFalse(isUsableIcon(png(1025, 96)))
        assertFalse(isUsableIcon(png(96, 40_000)))
        // Read as an unsigned number, this is over two billion.
        assertFalse(isUsableIcon(png(-1, 96)))
        assertFalse(isUsableIcon(png(0, 96)))
    }

    @Test
    fun anythingElseIsNot() {
        assertFalse(isUsableIcon(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(30)))
        assertFalse(isUsableIcon("<svg></svg>".encodeToByteArray()))
        assertFalse(isUsableIcon(png(96, 96).copyOf(20)))
        assertFalse(isUsableIcon(ByteArray(0)))
        val wrongChunk = png(96, 96).also { "IDAT".encodeToByteArray().copyInto(it, 12) }
        assertFalse(isUsableIcon(wrongChunk))
    }
}
