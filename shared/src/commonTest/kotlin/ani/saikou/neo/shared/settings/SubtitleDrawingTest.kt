package ani.saikou.neo.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleDrawingTest {
    @Test
    fun theDefaultsDrawLikeMedia3Did() {
        val drawing = SubtitleSettings().drawingFor(showId = 1)
        assertEquals(BuiltInLook.CLASSIC.style, drawing.style)
        assertEquals(false, drawing.followsTv)
        assertEquals(0.0533f, drawing.textFraction(tvFontScale = 1.5f), 0.0001f)
        assertEquals(Placement(fromTop = false, fraction = 0.08f), drawing.placement)
    }

    @Test
    fun aShowWithItsOwnLookDrawsThatLook() {
        val yellow = LookId.BuiltIn(BuiltInLook.YELLOW)
        val settings =
            SubtitleSettings(
                shows = mapOf(7 to ShowSubtitles("A show", SubtitlePick(yellow, SubtitleSize.P150, SubtitlePosition.TOP))),
            )
        val drawing = settings.drawingFor(7)
        assertEquals(BuiltInLook.YELLOW.style, drawing.style)
        assertEquals(0.0533f * 1.5f, drawing.textFraction(1f), 0.0001f)
        assertEquals(Placement(fromTop = true, fraction = 0.08f), drawing.placement)
        assertEquals(BuiltInLook.CLASSIC.style, settings.drawingFor(8).style)
    }

    @Test
    fun aChangedBuiltInLookDrawsAsChanged() {
        val classic = LookId.BuiltIn(BuiltInLook.CLASSIC)
        val thick = LookStyle(edgeThickness = EdgeThickness.THICK)
        assertEquals(thick, SubtitleSettings().editLook(classic, thick).drawingFor(1).style)
    }

    @Test
    fun matchTvFollowsTheTv() {
        val drawing = SubtitleSettings(look = LookId.BuiltIn(BuiltInLook.MATCH_TV)).drawingFor(1)
        assertEquals(true, drawing.followsTv)
        assertEquals(BuiltInLook.MATCH_TV.style, drawing.style)
    }

    @Test
    fun theTvTextSizeScalesOursWhenAskedTo() {
        val drawing = SubtitleSettings(size = SubtitleSize.P75, startFromTvTextSize = true).drawingFor(1)
        assertEquals(0.0533f * 0.75f * 1.5f, drawing.textFraction(tvFontScale = 1.5f), 0.0001f)
    }

    @Test
    fun fineTuneMovesLinesUpFromWhereverTheyAre() {
        assertEquals(Placement(false, 0.13f), SubtitleSettings(fineTunePercent = 5).drawingFor(1).placement)
        assertEquals(Placement(false, 0.03f), SubtitleSettings(fineTunePercent = -5).drawingFor(1).placement)
        assertEquals(Placement(false, 0.20f), SubtitleSettings(position = SubtitlePosition.RAISED).drawingFor(1).placement)
        assertEquals(Placement(true, 0.03f), SubtitleSettings(position = SubtitlePosition.TOP, fineTunePercent = 5).drawingFor(1).placement)
        assertEquals(Placement(true, 0f), SubtitleSettings(position = SubtitlePosition.TOP, fineTunePercent = 20).drawingFor(1).placement)
    }

    @Test
    fun styledChoicesAndHdrCarryOver() {
        val drawing =
            SubtitleSettings(styledSubtitles = StyledSubtitles.USE_MY_LOOK, mySizeOnStyled = false, hdrBrightness = HdrBrightness.P60)
                .drawingFor(1)
        assertEquals(StyledSubtitles.USE_MY_LOOK, drawing.styled)
        assertEquals(false, drawing.mySizeOnStyled)
        assertEquals(60, drawing.hdrPercent)
    }

    @Test
    fun aDeletedLookOfYourOwnDrawsAsClassic() {
        val settings = SubtitleSettings(look = LookId.Mine(2))
        assertEquals(BuiltInLook.CLASSIC.style, settings.drawingFor(1).style)
    }
}
