package ani.saikou.neo.tv.player

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import ani.saikou.neo.shared.settings.SubtitleColour
import ani.saikou.neo.shared.settings.SubtitleEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

@OptIn(UnstableApi::class)
class SubtitleLookTest {
    // A TV set to yellow text on blue, with a raised edge.
    private val tv = CaptionStyleCompat(Color.YELLOW, Color.BLUE, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_RAISED, Color.RED, null)

    @Test
    fun tvKeepsTheTvsCaptionSettings() {
        val look = tv.with(SubtitleColour.TV, SubtitleEdge.TV)

        assertEquals(Color.YELLOW, look.foregroundColor)
        assertEquals(Color.BLUE, look.backgroundColor)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_RAISED, look.edgeType)
    }

    @Test
    fun aColourReplacesOnlyTheText() {
        val look = tv.with(SubtitleColour.WHITE, SubtitleEdge.TV)

        assertEquals(Color.WHITE, look.foregroundColor)
        assertEquals(Color.BLUE, look.backgroundColor)
    }

    @Test
    fun anOutlineDropsTheBackground() {
        val look = tv.with(SubtitleColour.TV, SubtitleEdge.OUTLINE)

        assertEquals(CaptionStyleCompat.EDGE_TYPE_OUTLINE, look.edgeType)
        assertEquals(Color.BLACK, look.edgeColor)
        assertEquals(Color.TRANSPARENT, look.backgroundColor)
    }

    @Test
    fun aBackgroundHasNoEdge() {
        val look = tv.with(SubtitleColour.TV, SubtitleEdge.BACKGROUND)

        assertEquals(CaptionStyleCompat.EDGE_TYPE_NONE, look.edgeType)
        assertNotEquals(0, look.backgroundColor ushr 24)
    }
}
