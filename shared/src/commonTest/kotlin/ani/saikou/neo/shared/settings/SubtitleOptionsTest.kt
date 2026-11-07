package ani.saikou.neo.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubtitleOptionsTest {
    private val defaults = SubtitleSettings()

    @Test
    fun sizeAndPositionStepAndStopAtTheEnds() {
        assertEquals(SubtitleSize.P115, defaults.stepped(SubtitleOption.SIZE, 1).size)
        assertEquals(SubtitleSize.P200, defaults.copy(size = SubtitleSize.P200).stepped(SubtitleOption.SIZE, 1).size)
        assertEquals(SubtitlePosition.BOTTOM, defaults.stepped(SubtitleOption.POSITION, -1).position)
        assertEquals(SubtitlePosition.RAISED, defaults.stepped(SubtitleOption.POSITION, 1).position)
    }

    @Test
    fun fineTuneStepsOnePercentFromMinusFiveToTwenty() {
        assertEquals(1, defaults.stepped(SubtitleOption.FINE_TUNE, 1).fineTunePercent)
        assertEquals(-5, defaults.withFineTune(-5).stepped(SubtitleOption.FINE_TUNE, -1).fineTunePercent)
        assertEquals(20, defaults.withFineTune(20).stepped(SubtitleOption.FINE_TUNE, 1).fineTunePercent)
    }

    @Test
    fun switchesFlipWhicheverWayTheyStep() {
        assertTrue(defaults.stepped(SubtitleOption.START_FROM_TV, 1).startFromTvTextSize)
        assertFalse(defaults.stepped(SubtitleOption.MY_SIZE_ON_STYLED, -1).mySizeOnStyled)
    }

    @Test
    fun twoWayChoicesStepBetweenTheirTwoValues() {
        assertEquals(StyledSubtitles.USE_MY_LOOK, defaults.stepped(SubtitleOption.STYLED, 1).styledSubtitles)
        assertEquals(StyledSubtitles.KEEP_THEIR_LOOK, defaults.stepped(SubtitleOption.STYLED, -1).styledSubtitles)
        assertEquals(WhileWatching.THIS_SHOW_ONLY, defaults.stepped(SubtitleOption.WHILE_WATCHING, 1).changesWhileWatching)
    }

    @Test
    fun hdrBrightnessStepsUpTo100AndDownTo60() {
        assertEquals(HdrBrightness.P100, defaults.stepped(SubtitleOption.HDR, 1).hdrBrightness)
        assertEquals(HdrBrightness.P60, defaults.stepped(SubtitleOption.HDR, -1).hdrBrightness)
        assertEquals(HdrBrightness.P60, defaults.copy(hdrBrightness = HdrBrightness.P60).stepped(SubtitleOption.HDR, -1).hdrBrightness)
    }

    @Test
    fun boxIsNoneOrThreeColoursAtFourStrengths() {
        assertEquals(13, BOX_CHOICES.size)
        assertNull(BOX_CHOICES.first())
        assertEquals(Box(BoxColour.BLACK, Opacity.P25), BOX_CHOICES[1])
        assertEquals(Box(BoxColour.WHITE, Opacity.P100), BOX_CHOICES.last())
    }

    @Test
    fun bandIsOffOrTwoColoursAtFourStrengths() {
        assertEquals(9, BAND_CHOICES.size)
        assertNull(BAND_CHOICES.first())
        assertEquals(Band(BandColour.GREY, Opacity.P100), BAND_CHOICES.last())
    }

    @Test
    fun boxedStepsFromBlackAt75ToBlackAt100() {
        val boxed = BuiltInLook.BOXED.style

        assertEquals(Box(BoxColour.BLACK, Opacity.P100), boxed.stepped(LookPart.BOX, 1).box)
        assertNull(boxed.stepped(LookPart.BOX, -3).box)
    }

    @Test
    fun opacityStepsDownFrom100To25() {
        val style = LookStyle()

        assertEquals(Opacity.P75, style.stepped(LookPart.OPACITY, 1).textOpacity)
        assertEquals(Opacity.P100, style.stepped(LookPart.OPACITY, -1).textOpacity)
    }

    @Test
    fun colourFontEdgeAndThicknessStepInTheirOrder() {
        val style = LookStyle()

        assertEquals(TextColour.LIGHT_GREY, style.stepped(LookPart.TEXT_COLOUR, 1).textColour)
        assertEquals(SubtitleFont.SANS_SERIF, style.stepped(LookPart.FONT, 1).font)
        assertEquals(Edge.SHADOW, style.stepped(LookPart.EDGE, 1).edge)
        assertEquals(EdgeThickness.THICK, style.stepped(LookPart.THICKNESS, 1).edgeThickness)
        assertEquals(EdgeColour.DARK_GREY, style.stepped(LookPart.EDGE_COLOUR, 1).edgeColour)
        assertFalse(style.stepped(LookPart.BOLD, 1).bold)
        assertEquals(Band(BandColour.BLACK, Opacity.P25), style.stepped(LookPart.BAND, 1).band)
    }

    @Test
    fun withNoEdgeItsThicknessAndColourCantChange() {
        val boxed = BuiltInLook.BOXED.style

        assertFalse(boxed.canChange(LookPart.THICKNESS))
        assertFalse(boxed.canChange(LookPart.EDGE_COLOUR))
        assertTrue(boxed.canChange(LookPart.BOX))
        assertTrue(LookStyle().canChange(LookPart.THICKNESS))
        assertEquals(boxed, boxed.stepped(LookPart.THICKNESS, 1))
    }

    @Test
    fun aPreviewUsesTheEveryShowSettingsEvenWhenAShowHasItsOwn() {
        val settings =
            defaults.copy(
                shows = mapOf(0 to ShowSubtitles("A", SubtitlePick(LookId.BuiltIn(BuiltInLook.YELLOW), SubtitleSize.P60, SubtitlePosition.TOP))),
            )

        assertEquals(BuiltInLook.CLASSIC.style, settings.drawing().style)
        assertEquals(100, settings.drawing().sizePercent)
        assertEquals(BuiltInLook.BOXED.style, settings.drawing(LookId.BuiltIn(BuiltInLook.BOXED)).style)
    }
}
