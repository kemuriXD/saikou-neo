package ani.saikou.neo.tv.player

import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import ani.saikou.neo.shared.settings.Box
import ani.saikou.neo.shared.settings.BoxColour
import ani.saikou.neo.shared.settings.LookStyle
import ani.saikou.neo.shared.settings.Opacity
import ani.saikou.neo.shared.settings.Placement
import ani.saikou.neo.shared.settings.toPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class LookSubtitlesTest {
    private val view = LookSubtitles(ApplicationProvider.getApplicationContext())

    private fun layOut() {
        view.measure(MeasureSpec.makeMeasureSpec(1920, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY))
        view.layout(0, 0, 1920, 1080)
    }

    @Test
    fun linesSitAboveTheBottomBy8Percent() {
        view.look(LookStyle().toPaint(), textFraction = 0.0533f, Placement(fromTop = false, fraction = 0.08f))
        view.show(listOf("One line"))
        layOut()

        val text = view.textBounds()!!
        assertEquals(1080 * 0.92f, text.bottom, 1f)
        assertEquals(1080 * 0.0533f, view.textPx, 0.5f)
        assertTrue(text.centerX() in 950f..970f)
    }

    @Test
    fun topLinesHangFromTheTop() {
        view.look(LookStyle().toPaint(), textFraction = 0.0533f, Placement(fromTop = true, fraction = 0.1f))
        view.show(listOf("One", "Two"))
        layOut()

        assertEquals(108f, view.textBounds()!!.top, 1f)
    }

    @Test
    fun aBoxDoesntPushLinesOffTheirPlace() {
        val boxed = LookStyle(box = Box(BoxColour.BLACK, Opacity.P75)).toPaint()
        view.look(boxed, textFraction = 0.0533f, Placement(fromTop = false, fraction = 0.08f))
        view.show(listOf("Boxed"))
        layOut()

        assertEquals(1080 * 0.92f, view.textBounds()!!.bottom, 1f)
    }

    @Test
    fun noLinesDrawNothing() {
        view.look(LookStyle().toPaint(), textFraction = 0.0533f, Placement(fromTop = false, fraction = 0.08f))
        view.show(emptyList())
        layOut()

        assertEquals(null, view.textBounds())
    }

    @Test
    fun besideThePanelLinesCentreInTheHalfItLeaves() {
        view.look(LookStyle().toPaint(), textFraction = 0.0533f, Placement(fromTop = false, fraction = 0.08f))
        view.aside = true
        view.show(listOf("One line"))
        layOut()

        assertTrue(view.textBounds()!!.centerX() in 470f..490f)
    }

    @Test
    fun aSampleIsLabelledAboveItsLine() {
        view.look(LookStyle().toPaint(), textFraction = 0.0533f, Placement(fromTop = false, fraction = 0.08f))
        view.show(listOf("Subtitles will look like this"), label = "Sample")
        layOut()

        val text = view.textBounds()!!
        val label = view.labelBounds()!!
        assertTrue(label.bottom < text.top)
        assertEquals(text.centerX(), label.centerX(), 1f)
    }

    @Test
    fun linesWithoutALabelHaveNone() {
        view.look(LookStyle().toPaint(), textFraction = 0.0533f, Placement(fromTop = false, fraction = 0.08f))
        view.show(listOf("One line"))
        layOut()

        assertEquals(null, view.labelBounds())
    }
}
