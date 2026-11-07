package ani.saikou.neo.shared.settings

import ani.saikou.neo.shared.playback.ServerEdition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubtitleStyleTest {
    @Test
    fun aNewStyleStartsFromTheTvsCaptions() {
        assertEquals(
            SubtitleStyle(SubtitleSize.TV, SubtitleColour.TV, SubtitleEdge.TV, SubtitlePosition.BOTTOM),
            SubtitleStyle(),
        )
    }

    @Test
    fun aStepChangesOnlyThatPart() {
        val style = SubtitleStyle()

        assertEquals(style.copy(size = SubtitleSize.SMALL), style.step(SubtitleStylePart.SIZE, forward = true))
        assertEquals(style.copy(colour = SubtitleColour.WHITE), style.step(SubtitleStylePart.COLOUR, forward = true))
        assertEquals(style.copy(edge = SubtitleEdge.OUTLINE), style.step(SubtitleStylePart.EDGE, forward = true))
        assertEquals(style.copy(position = SubtitlePosition.RAISED), style.step(SubtitleStylePart.POSITION, forward = true))
    }

    @Test
    fun aStepBackGoesToThePreviousChoice() {
        val style = SubtitleStyle(size = SubtitleSize.LARGE)

        assertEquals(SubtitleSize.MEDIUM, style.step(SubtitleStylePart.SIZE, forward = false).size)
    }

    @Test
    fun stepsStopAtEitherEnd() {
        val first = SubtitleStyle()
        val last = SubtitleStyle(SubtitleSize.EXTRA_LARGE, SubtitleColour.CYAN, SubtitleEdge.BACKGROUND, SubtitlePosition.HIGH)

        for (part in SubtitleStylePart.entries) {
            assertEquals(first, first.step(part, forward = false))
            assertEquals(last, last.step(part, forward = true))
        }
    }

    @Test
    fun subtitlesThatAreOnCanBeStyled() {
        assertNull(styleBlock(ServerEdition.SUB, subtitlesOn = true))
        assertNull(styleBlock(ServerEdition.HARD_SUB, subtitlesOn = true))
    }

    @Test
    fun subtitlesThatAreOffSayToTurnThemOn() {
        assertEquals(StyleBlock.SUBTITLES_OFF, styleBlock(ServerEdition.SUB, subtitlesOn = false))
        assertEquals(StyleBlock.SUBTITLES_OFF, styleBlock(ServerEdition.DUB, subtitlesOn = false))
    }

    @Test
    fun aHardSubServerWithNoTextTrackSaysTheyArePartOfTheVideo() {
        assertEquals(StyleBlock.BURNED_IN, styleBlock(ServerEdition.HARD_SUB, subtitlesOn = false))
    }
}
