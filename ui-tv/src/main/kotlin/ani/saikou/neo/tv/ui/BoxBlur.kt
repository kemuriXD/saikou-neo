package ani.saikou.neo.tv.ui

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Blurs a small copy of an image, so it scales up soft rather than blocky. It works on every Android version; blur
 * effects in Compose need Android 12, which Fire TV doesn't have.
 */
class BlurTransformation(
    private val radius: Int,
    private val passes: Int = 2,
) : Transformation() {
    override val cacheKey: String = "blur-$radius-$passes"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        val bitmap = input.copy(Bitmap.Config.ARGB_8888, true)
        val width = bitmap.width
        val height = bitmap.height
        var pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        repeat(passes) { pixels = boxBlur(pixels, width, height, radius) }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}

/** Each pixel of a [width] by [height] image becomes the average of those within [radius], across then down. Opaque. */
internal fun boxBlur(
    pixels: IntArray,
    width: Int,
    height: Int,
    radius: Int,
): IntArray {
    val across = IntArray(pixels.size)
    for (y in 0 until height) average(pixels, across, start = y * width, step = 1, count = width, radius)
    val down = IntArray(pixels.size)
    for (x in 0 until width) average(across, down, start = x, step = width, count = height, radius)
    return down
}

// One row or column: the average of each pixel's neighbours within radius, with the edge pixels repeated past the edge.
private fun average(
    from: IntArray,
    to: IntArray,
    start: Int,
    step: Int,
    count: Int,
    radius: Int,
) {
    val window = 2 * radius + 1
    for (i in 0 until count) {
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val pixel = from[start + (i + offset).coerceIn(0, count - 1) * step]
            red += pixel shr 16 and 0xFF
            green += pixel shr 8 and 0xFF
            blue += pixel and 0xFF
        }
        to[start + i * step] = (0xFF shl 24) or (red / window shl 16) or (green / window shl 8) or (blue / window)
    }
}
