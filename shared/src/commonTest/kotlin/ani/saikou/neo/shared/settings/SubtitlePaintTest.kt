package ani.saikou.neo.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubtitlePaintTest {
    // A 1080p TV: 2 px a dp, text 57.6 px tall at 100%.
    private val density = 2f
    private val textPx = 57.6f

    private fun paint(
        edge: Edge,
        thickness: EdgeThickness,
    ) = LookStyle(edge = edge, edgeThickness = thickness).toPaint()

    @Test
    fun thinOutlinesAndShadowsMatchTheOldTwoDp() {
        assertEquals(4f, paint(Edge.OUTLINE, EdgeThickness.THIN).outlineWidth(textPx, density))
        assertEquals(Shadow(radius = 4f, dx = 4f, dy = 4f), paint(Edge.SHADOW, EdgeThickness.THIN).shadow(textPx, density))
    }

    @Test
    fun mediumAndThickOutlinesFollowTheTextSize() {
        assertEquals(textPx * 6 / 26, paint(Edge.OUTLINE, EdgeThickness.MEDIUM).outlineWidth(textPx, density), 0.01f)
        assertEquals(textPx * 9 / 26, paint(Edge.OUTLINE, EdgeThickness.THICK).outlineWidth(textPx, density), 0.01f)
        assertEquals(2 * textPx * 6 / 26, paint(Edge.OUTLINE, EdgeThickness.MEDIUM).outlineWidth(2 * textPx, density), 0.01f)
    }

    @Test
    fun anEdgeIsNeverThinnerThanThin() {
        val tiny = 6f
        assertEquals(4f, paint(Edge.OUTLINE, EdgeThickness.MEDIUM).outlineWidth(tiny, density))
        assertEquals(4f, paint(Edge.SHADOW, EdgeThickness.MEDIUM).shadow(tiny, density).dy)
        assertEquals(2f, paint(Edge.RAISED, EdgeThickness.THICK).reliefUnit(tiny, density))
    }

    @Test
    fun shadowsGrowWithThickness() {
        val medium = paint(Edge.SHADOW, EdgeThickness.MEDIUM).shadow(textPx, density)
        val thick = paint(Edge.SHADOW, EdgeThickness.THICK).shadow(textPx, density)
        assertEquals(0f, medium.dx)
        assertEquals(textPx * 0.08f, medium.dy, 0.01f)
        assertEquals(medium.dy * 1.5f, thick.dy, 0.01f)
        assertEquals(medium.radius * 1.5f, thick.radius, 0.01f)
    }

    @Test
    fun raisedAndSunkenStepsGrowWithThickness() {
        assertEquals(2f, paint(Edge.RAISED, EdgeThickness.THIN).reliefUnit(textPx, density))
        assertEquals(textPx * 1.5f / 26, paint(Edge.SUNKEN, EdgeThickness.MEDIUM).reliefUnit(textPx, density), 0.01f)
        assertEquals(textPx * 2.25f / 26, paint(Edge.RAISED, EdgeThickness.THICK).reliefUnit(textPx, density), 0.01f)
    }

    @Test
    fun coloursCarryTheirOpacity() {
        val style =
            LookStyle(
                textColour = TextColour.YELLOW,
                textOpacity = Opacity.P75,
                edgeColour = EdgeColour.DARK_GREY,
                box = Box(BoxColour.BLACK, Opacity.P50),
                band = Band(BandColour.GREY, Opacity.P25),
            )
        val paint = style.toPaint()
        assertEquals(0xBFFFE14D.toInt(), paint.textArgb)
        assertEquals(0xFF404040.toInt(), paint.edgeArgb)
        assertEquals(0x80000000.toInt(), paint.boxArgb)
        assertEquals(0x40808080, paint.bandArgb)
        assertEquals(SubtitleFont.ATKINSON_HYPERLEGIBLE, paint.font)
        assertEquals(true, paint.bold)
    }

    @Test
    fun noBoxOrBandMeansNone() {
        val paint = LookStyle().toPaint()
        assertNull(paint.boxArgb)
        assertNull(paint.bandArgb)
    }

    @Test
    fun hdrDimsLightColoursButKeepsTheirOpacity() {
        val paint = LookStyle(textOpacity = Opacity.P75, box = Box(BoxColour.WHITE, Opacity.P50)).toPaint().dimmed(80)
        assertEquals(0xBFCCCCCC.toInt(), paint.textArgb)
        assertEquals(0x80CCCCCC.toInt(), paint.boxArgb)
        assertEquals(0xFF000000.toInt(), paint.edgeArgb)
        assertEquals(paint, paint.dimmed(100))
    }

    @Test
    fun theTvStyleDrawsAsTheTvSetsIt() {
        val tv =
            TvCaptions(
                foreground = 0xFFFFFF00.toInt(),
                background = 0,
                window = 0x80000000.toInt(),
                edge = Edge.SHADOW,
                edgeArgb = 0xFF000000.toInt(),
            )
        val paint = tv.toPaint()
        assertEquals(0xFFFFFF00.toInt(), paint.textArgb)
        assertNull(paint.boxArgb)
        assertEquals(0x80000000.toInt(), paint.bandArgb)
        assertEquals(Edge.SHADOW, paint.edge)
        assertEquals(EdgeThickness.THIN, paint.edgeThickness)
        assertNull(paint.font)
        assertEquals(false, paint.bold)
    }
}
