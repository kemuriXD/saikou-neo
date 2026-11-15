package ani.saikou.neo.tv.player

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.ui.CaptionStyleCompat
import ani.saikou.neo.shared.settings.Edge
import ani.saikou.neo.shared.settings.StyledSubtitles
import ani.saikou.neo.shared.settings.TvCaptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubtitleCuesTest {
    private fun text(value: CharSequence) = Cue.Builder().setText(value).build()

    private val picture = Cue.Builder().setBitmap(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)).build()

    @Test
    fun styledTracksAreKnownByTheirFormat() {
        val parsed =
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
                .setCodecs(MimeTypes.TEXT_SSA)
                .build()
        assertTrue(parsed.isStyledText())
        assertTrue(
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.TEXT_SSA)
                .build()
                .isStyledText(),
        )
        assertFalse(
            Format
                .Builder()
                .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
                .setCodecs(MimeTypes.TEXT_VTT)
                .build()
                .isStyledText(),
        )
        assertFalse(null.isStyledText())
    }

    @Test
    fun plainLinesAreOurs() {
        val route = routeCues(listOf(text("One"), text("Two")), styledTrack = false, StyledSubtitles.KEEP_THEIR_LOOK)
        assertEquals(listOf("One", "Two"), route.ours.map { it.toString() })
        assertFalse(route.media3)
    }

    @Test
    fun styledLinesKeepTheirLookUnlessYouPickYours() {
        val keep = routeCues(listOf(text("Sign")), styledTrack = true, StyledSubtitles.KEEP_THEIR_LOOK)
        assertTrue(keep.ours.isEmpty())
        assertTrue(keep.media3)

        val mine = routeCues(listOf(text("Sign")), styledTrack = true, StyledSubtitles.USE_MY_LOOK)
        assertEquals(listOf("Sign"), mine.ours.map { it.toString() })
        assertFalse(mine.media3)
    }

    @Test
    fun picturesStayWithMedia3() {
        val route = routeCues(listOf(picture), styledTrack = false, StyledSubtitles.USE_MY_LOOK)
        assertTrue(route.ours.isEmpty())
        assertTrue(route.media3)
    }

    @Test
    fun ourLinesKeepItalicsButNotTheirColourOrSize() {
        val line = SpannableString("Hello there")
        line.setSpan(StyleSpan(Typeface.ITALIC), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        line.setSpan(ForegroundColorSpan(Color.RED), 0, 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        line.setSpan(AbsoluteSizeSpan(40), 0, 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val ours = routeCues(listOf(text(line)), styledTrack = false, StyledSubtitles.KEEP_THEIR_LOOK).ours.single() as Spanned

        assertEquals(1, ours.getSpans(0, ours.length, StyleSpan::class.java).size)
        assertEquals(0, ours.getSpans(0, ours.length, ForegroundColorSpan::class.java).size)
        assertEquals(0, ours.getSpans(0, ours.length, AbsoluteSizeSpan::class.java).size)
    }

    @Test
    fun theTvStyleMapsItsEdges() {
        val style =
            CaptionStyleCompat(Color.YELLOW, Color.TRANSPARENT, Color.BLACK, CaptionStyleCompat.EDGE_TYPE_DEPRESSED, Color.GRAY, null)
        assertEquals(TvCaptions(Color.YELLOW, Color.TRANSPARENT, Color.BLACK, Edge.SUNKEN, Color.GRAY), style.toTvCaptions())
        assertEquals(
            Edge.SHADOW,
            CaptionStyleCompat(Color.WHITE, 0, 0, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, 0, null).toTvCaptions().edge,
        )
    }
}
