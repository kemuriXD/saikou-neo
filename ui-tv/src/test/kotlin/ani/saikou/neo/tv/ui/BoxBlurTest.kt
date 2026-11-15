package ani.saikou.neo.tv.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

private fun red(pixel: Int) = pixel shr 16 and 0xFF

class BoxBlurTest {
    @Test
    fun aPlainImageStaysTheSame() {
        val grey = 0xFF808080.toInt()
        val pixels = IntArray(8 * 6) { grey }

        assertArrayEquals(pixels, boxBlur(pixels, 8, 6, radius = 2))
    }

    @Test
    fun aBrightDotSpreadsToItsNeighbours() {
        val pixels = IntArray(9 * 9) { BLACK }
        pixels[4 * 9 + 4] = WHITE

        val blurred = boxBlur(pixels, 9, 9, radius = 1)

        val centre = red(blurred[4 * 9 + 4])
        val beside = red(blurred[4 * 9 + 5])
        val far = red(blurred[0])
        assertTrue("centre $centre", centre in 1..254)
        assertTrue("beside $beside", beside in 1 until centre + 1)
        assertEquals(0, far)
    }

    @Test
    fun pixelsStayOpaque() {
        val pixels = IntArray(5 * 5) { if (it % 2 == 0) WHITE else BLACK }

        boxBlur(pixels, 5, 5, radius = 2).forEach { assertEquals(0xFF, it ushr 24) }
    }
}
