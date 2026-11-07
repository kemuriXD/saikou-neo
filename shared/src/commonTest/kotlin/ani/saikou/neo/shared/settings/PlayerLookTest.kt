package ani.saikou.neo.shared.settings

import ani.saikou.neo.shared.playback.ServerEdition
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerLookTest {
    private val boxed = LookId.BuiltIn(BuiltInLook.BOXED)

    @Test
    fun aPreviewDrawsTheFocusedLookInPlaceOfTheSavedOne() {
        val drawing = SubtitleSettings(size = SubtitleSize.P130).drawingFor(5, preview = boxed)

        assertEquals(BuiltInLook.BOXED.style, drawing.style)
        assertEquals(130, drawing.sizePercent)
    }

    @Test
    fun aPreviewOfALookThatsGoneDrawsTheSavedOne() {
        val drawing = SubtitleSettings().drawingFor(5, preview = LookId.Mine(2))

        assertEquals(BuiltInLook.CLASSIC.style, drawing.style)
    }

    @Test
    fun sizeStepsOneAtATimeAndStopsAtTheEnds() {
        assertEquals(SubtitleSize.P115, SubtitleSize.P100.stepped(1))
        assertEquals(SubtitleSize.P90, SubtitleSize.P100.stepped(-1))
        assertEquals(SubtitleSize.P200, SubtitleSize.P200.stepped(1))
        assertEquals(SubtitleSize.P60, SubtitleSize.P60.stepped(-1))
    }

    @Test
    fun positionStepsFromBottomToTopAndStopsAtTheEnds() {
        assertEquals(SubtitlePosition.RAISED, SubtitlePosition.BOTTOM.stepped(1))
        assertEquals(SubtitlePosition.TOP, SubtitlePosition.TOP.stepped(1))
        assertEquals(SubtitlePosition.BOTTOM, SubtitlePosition.BOTTOM.stepped(-1))
    }

    @Test
    fun theLookRowWaitsForATrack() {
        assertEquals(LookRow.OFF, lookRow(subtitlesOn = false, ServerEdition.SUB))
        assertEquals(LookRow.READY, lookRow(subtitlesOn = true, ServerEdition.SUB))
    }

    @Test
    fun aHardSubServerWithNoTrackHasItsSubtitlesInThePicture() {
        assertEquals(LookRow.BURNED, lookRow(subtitlesOn = false, ServerEdition.HARD_SUB))
        assertEquals(LookRow.READY, lookRow(subtitlesOn = true, ServerEdition.HARD_SUB))
    }
}
