package ani.saikou.neo.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SubtitleSettingsTest {
    private val classic = LookId.BuiltIn(BuiltInLook.CLASSIC)
    private val boxed = LookId.BuiltIn(BuiltInLook.BOXED)
    private val cyan = LookStyle(textColour = TextColour.CYAN, font = SubtitleFont.SERIF)

    @Test
    fun aNewInstallHasTheAgreedDefaults() {
        val settings = SubtitleSettings()

        assertEquals(classic, settings.look)
        assertEquals(100, settings.size.percent)
        assertEquals(false, settings.startFromTvTextSize)
        assertEquals(SubtitlePosition.BOTTOM, settings.position)
        assertEquals(0, settings.fineTunePercent)
        assertEquals(StyledSubtitles.KEEP_THEIR_LOOK, settings.styledSubtitles)
        assertEquals(true, settings.mySizeOnStyled)
        assertEquals(WhileWatching.EVERY_SHOW, settings.changesWhileWatching)
        assertEquals(80, settings.hdrBrightness.percent)
        val style = settings.style(classic)!!
        assertEquals(Opacity.P100, style.textOpacity)
        assertEquals(SubtitleFont.ATKINSON_HYPERLEGIBLE, style.font)
        assertEquals(true, style.bold)
        assertEquals(EdgeThickness.MEDIUM, style.edgeThickness)
        assertEquals(EdgeColour.BLACK, style.edgeColour)
        assertNull(style.band)
    }

    @Test
    fun sizeHasNineStepsFrom60To200() {
        assertEquals(listOf(60, 75, 90, 100, 115, 130, 150, 175, 200), SubtitleSize.entries.map { it.percent })
    }

    @Test
    fun fineTuneStaysBetweenMinus5AndPlus20() {
        assertEquals(20, SubtitleSettings().withFineTune(25).fineTunePercent)
        assertEquals(-5, SubtitleSettings().withFineTune(-9).fineTunePercent)
        assertEquals(7, SubtitleSettings().withFineTune(7).fineTunePercent)
    }

    @Test
    fun theBuiltInLooksMatchTheDesign() {
        assertEquals(LookStyle(edge = Edge.OUTLINE), BuiltInLook.CLASSIC.style)
        assertEquals(LookStyle(edge = Edge.SHADOW), BuiltInLook.SOFT_SHADOW.style)
        assertEquals(LookStyle(edge = Edge.NONE, box = Box(BoxColour.BLACK, Opacity.P75)), BuiltInLook.BOXED.style)
        assertEquals(LookStyle(textColour = TextColour.YELLOW, edge = Edge.OUTLINE), BuiltInLook.YELLOW.style)
        assertEquals(
            LookStyle(textColour = TextColour.YELLOW, edge = Edge.NONE, box = Box(BoxColour.BLACK, Opacity.P100)),
            BuiltInLook.HIGH_CONTRAST.style,
        )
        assertEquals(true, BuiltInLook.MATCH_TV.followsTv)
        assertEquals(BuiltInLook.entries.map { LookId.BuiltIn(it) }, SubtitleSettings().looks)
    }

    @Test
    fun aBuiltInLookCanBeChangedAndReset() {
        val changed = SubtitleSettings().editLook(boxed, cyan)

        assertEquals(cyan, changed.style(boxed))
        assertEquals(BuiltInLook.BOXED.style, changed.resetLook(BuiltInLook.BOXED).style(boxed))
    }

    @Test
    fun aNewLookCopiesTheOneInUseAndIsUsed() {
        val settings = SubtitleSettings().editLook(boxed, cyan).withLook(boxed).addLook()

        assertEquals(LookId.Mine(1), settings.look)
        assertEquals(cyan, settings.style(LookId.Mine(1)))
        assertEquals(BuiltInLook.entries.map { LookId.BuiltIn(it) } + LookId.Mine(1), settings.looks)
    }

    @Test
    fun onlyThreeLooksOfYourOwn() {
        val three = SubtitleSettings().addLook().addLook().addLook()

        assertEquals(false, three.canAddLook)
        assertSame(three, three.addLook())
        assertEquals(listOf(1, 2, 3), three.myLooks.keys.sorted())
    }

    @Test
    fun yourOwnLookCanBeChangedAndDeleted() {
        val settings = SubtitleSettings().addLook().editLook(LookId.Mine(1), cyan)
        assertEquals(cyan, settings.style(LookId.Mine(1)))

        val deleted = settings.deleteLook(1)

        assertNull(deleted.style(LookId.Mine(1)))
        // The look in use is gone, so Classic takes its place.
        assertEquals(classic, deleted.look)
    }

    @Test
    fun aNewLookTakesTheFirstFreeNumber() {
        val settings =
            SubtitleSettings()
                .addLook()
                .addLook()
                .deleteLook(1)
                .addLook()

        assertEquals(listOf(1, 2), settings.myLooks.keys.sorted())
    }

    @Test
    fun aChangeWhileWatchingGoesToEveryShowByDefault() {
        val settings = SubtitleSettings().changeWhileWatching(SHOW, TITLE) { it.copy(size = SubtitleSize.P150) }

        assertEquals(SubtitleSize.P150, settings.size)
        assertEquals(emptyMap(), settings.shows)
    }

    @Test
    fun withThisShowOnlyAChangeWhileWatchingStaysWithTheShow() {
        val settings =
            SubtitleSettings(changesWhileWatching = WhileWatching.THIS_SHOW_ONLY)
                .changeWhileWatching(SHOW, TITLE) { it.copy(look = boxed, position = SubtitlePosition.TOP) }

        assertEquals(SubtitlePick(boxed, SubtitleSize.P100, SubtitlePosition.TOP), settings.pickFor(SHOW))
        assertEquals(SubtitlePick(classic, SubtitleSize.P100, SubtitlePosition.BOTTOM), settings.pickFor(OTHER_SHOW))
        assertEquals(TITLE, settings.shows.getValue(SHOW).title)
    }

    @Test
    fun aShowKeepsItsOwnLookUntilCleared() {
        val settings =
            SubtitleSettings(changesWhileWatching = WhileWatching.THIS_SHOW_ONLY)
                .changeWhileWatching(SHOW, TITLE) { it.copy(look = boxed) }
                .changeWhileWatching(SHOW, TITLE) { it.copy(size = SubtitleSize.P60) }

        assertEquals(SubtitlePick(boxed, SubtitleSize.P60, SubtitlePosition.BOTTOM), settings.pickFor(SHOW))

        val cleared = settings.clearShow(SHOW)

        assertEquals(emptyMap(), cleared.shows)
        assertEquals(cleared.pickFor(OTHER_SHOW), cleared.pickFor(SHOW))
    }

    @Test
    fun withEveryShowAChangeWhileWatchingAShowWithItsOwnLookClearsIt() {
        // Otherwise the show's own look would hide the change.
        val settings =
            SubtitleSettings(changesWhileWatching = WhileWatching.THIS_SHOW_ONLY)
                .changeWhileWatching(SHOW, TITLE) { it.copy(look = boxed) }
                .copy(changesWhileWatching = WhileWatching.EVERY_SHOW)
                .changeWhileWatching(SHOW, TITLE) { it.copy(size = SubtitleSize.P175) }

        assertEquals(SubtitlePick(boxed, SubtitleSize.P175, SubtitlePosition.BOTTOM), settings.pickFor(SHOW))
        assertEquals(SubtitleSize.P175, settings.size)
        assertEquals(emptyMap(), settings.shows)
    }

    @Test
    fun deletingALookAShowUsesPutsThatShowOnClassic() {
        val settings =
            SubtitleSettings(changesWhileWatching = WhileWatching.THIS_SHOW_ONLY)
                .addLook()
                .changeWhileWatching(SHOW, TITLE) { it.copy(look = LookId.Mine(1)) }
                .deleteLook(1)

        assertEquals(classic, settings.pickFor(SHOW).look)
    }

    private companion object {
        const val SHOW = 21
        const val OTHER_SHOW = 99
        const val TITLE = "A show; with: separators"
    }
}
