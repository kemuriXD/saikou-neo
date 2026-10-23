package ani.saikou.neo.tv.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class NeoDimensionsTest {
    private val dimensions = NeoDimensions()

    @Test
    fun gridFillsTheTvScreen() {
        // Android TV's 12-column grid on a 960 dp wide screen.
        with(dimensions) {
            assertEquals(960.dp, gridMargin * 2 + gridColumn * 12 + gridGutter * 11)
        }
    }

    @Test
    fun cardsSpanWholeGridColumns() {
        with(dimensions) {
            assertEquals(gridColumn * 3 + gridGutter * 2, cardWideWidth)
            assertEquals(gridColumn * 2 + gridGutter, cardPosterWidth)
        }
    }

    @Test
    fun focusedButtonsScaleToTheGuidesSize() {
        assertEquals(1.1f, dimensions.focusScaleButton)
    }
}
