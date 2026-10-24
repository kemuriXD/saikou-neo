package ani.saikou.neo.tv.home

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

// Pixels fainter than this are left over from antialiasing or a soft shadow, not part of the logo.
private const val MIN_ALPHA = 16

internal data class LogoBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/** The smallest box around the visible pixels of a [width] × [height] ARGB image, or null if nothing is visible. */
internal fun opaqueBounds(
    pixels: IntArray,
    width: Int,
    height: Int,
): LogoBounds? {
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (pixels[y * width + x] ushr 24 < MIN_ALPHA) continue
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            bottom = y
        }
    }
    return if (right < 0) null else LogoBounds(left, top, right - left + 1, bottom - top + 1)
}

/** Crops a logo's empty margins, so every logo starts on the same line as the text under it. */
internal object TrimTransparentEdges : Transformation() {
    override val cacheKey = "trim-transparent-edges"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        val pixels = IntArray(input.width * input.height)
        input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        val bounds = opaqueBounds(pixels, input.width, input.height) ?: return input
        return Bitmap.createBitmap(input, bounds.left, bounds.top, bounds.width, bounds.height)
    }
}
