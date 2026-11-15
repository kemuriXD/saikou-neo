package ani.saikou.neo.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrimTransparentEdgesTest {
    private val clear = 0x00FFFFFF
    private val ink = 0xFF000000.toInt()

    /** A [width] × [height] image, clear except for the pixels at [inked] (x to y). */
    private fun image(
        width: Int,
        height: Int,
        vararg inked: Pair<Int, Int>,
    ) = IntArray(width * height) { clear }.also { pixels -> inked.forEach { (x, y) -> pixels[y * width + x] = ink } }

    @Test
    fun findsTheBoxAroundWhatIsDrawn() {
        val pixels = image(6, 5, 2 to 1, 4 to 3)

        assertEquals(LogoBounds(left = 2, top = 1, width = 3, height = 3), opaqueBounds(pixels, 6, 5))
    }

    @Test
    fun barelyVisiblePixelsDoNotCount() {
        val pixels = image(4, 4, 1 to 1).also { it[0] = 0x08FFFFFF }

        assertEquals(LogoBounds(left = 1, top = 1, width = 1, height = 1), opaqueBounds(pixels, 4, 4))
    }

    @Test
    fun anImageWithNothingDrawnHasNoBounds() {
        assertNull(opaqueBounds(image(3, 3), 3, 3))
    }
}
