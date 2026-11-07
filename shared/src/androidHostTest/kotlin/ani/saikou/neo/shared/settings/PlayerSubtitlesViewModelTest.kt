package ani.saikou.neo.shared.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSubtitlesViewModelTest {
    private val classic = LookId.BuiltIn(BuiltInLook.CLASSIC)
    private val boxed = LookId.BuiltIn(BuiltInLook.BOXED)
    private val saved = MutableStateFlow(SubtitleSettings())

    private fun model() = PlayerSubtitlesViewModel(saved, { change -> saved.update(change) }, showId = 3, title = "Show")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aChangedSettingRedrawsAtOnce() {
        val model = model()

        saved.value = SubtitleSettings(size = SubtitleSize.P150)

        assertEquals(150, model.state.value.drawing.sizePercent)
    }

    @Test
    fun aFocusedLookShowsOnTheVideoWithoutBeingSaved() {
        val model = model()

        model.preview(boxed)

        assertEquals(BuiltInLook.BOXED.style, model.state.value.drawing.style)
        assertEquals(boxed, model.state.value.showing)
        assertEquals(classic, saved.value.look)
    }

    @Test
    fun okKeepsTheLookForEveryShow() {
        val model = model()
        model.preview(boxed)

        model.keep()

        assertEquals(boxed, saved.value.look)
        assertNull(model.state.value.preview)
        assertEquals(boxed, model.state.value.pick.look)
    }

    @Test
    fun okKeepsTheLookForThisShowOnlyWhenThatsTheSetting() {
        saved.value = SubtitleSettings(changesWhileWatching = WhileWatching.THIS_SHOW_ONLY)
        val model = model()
        model.preview(boxed)

        model.keep()

        assertEquals(classic, saved.value.look)
        assertEquals(ShowSubtitles("Show", SubtitlePick(boxed, SubtitleSize.P100, SubtitlePosition.BOTTOM)), saved.value.shows[3])
    }

    @Test
    fun backPutsTheSavedLookBack() {
        val model = model()
        model.preview(boxed)

        model.preview(null)

        assertEquals(BuiltInLook.CLASSIC.style, model.state.value.drawing.style)
    }

    @Test
    fun sizeAndPositionAreSavedAsTheyChange() {
        val model = model()

        model.changeSize(1)
        model.changePosition(1)

        assertEquals(SubtitleSize.P115, saved.value.size)
        assertEquals(SubtitlePosition.RAISED, saved.value.position)
        assertEquals(115, model.state.value.drawing.sizePercent)
    }

    @Test
    fun aStyledTrackKeepsItsLookUntilMyLookIsTurnedOnForIt() {
        val model = model()
        model.onTrack("Signs", styled = true)
        assertTrue(model.state.value.keepsItsLook)

        model.useMyLook(true)

        assertFalse(model.state.value.keepsItsLook)
        assertEquals(StyledSubtitles.USE_MY_LOOK, model.state.value.drawing.styled)
    }

    @Test
    fun myLookOnOneTrackStaysWithThatTrack() {
        val model = model()
        model.onTrack("Signs", styled = true)
        model.useMyLook(true)

        model.onTrack("Full", styled = true)
        assertTrue(model.state.value.keepsItsLook)
        assertEquals(StyledSubtitles.KEEP_THEIR_LOOK, model.state.value.drawing.styled)

        model.onTrack("Signs", styled = true)
        assertFalse(model.state.value.keepsItsLook)
    }

    @Test
    fun aStyledTrackFollowsTheSettingToUseMyLook() {
        saved.value = SubtitleSettings(styledSubtitles = StyledSubtitles.USE_MY_LOOK)
        val model = model()

        model.onTrack("Signs", styled = true)

        assertFalse(model.state.value.keepsItsLook)
    }
}
