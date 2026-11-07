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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleSettingsViewModelTest {
    private val boxed = LookId.BuiltIn(BuiltInLook.BOXED)
    private val saved = MutableStateFlow(SubtitleSettings())

    private fun model() = SubtitleSettingsViewModel(saved) { change -> saved.update(change) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theStateFollowsTheSavedSettings() {
        val model = model()

        saved.value = SubtitleSettings(size = SubtitleSize.P150)

        assertEquals(SubtitleSize.P150, model.state.value?.size)
    }

    @Test
    fun steppingAnOptionSavesIt() {
        model().step(SubtitleOption.SIZE, 1)

        assertEquals(SubtitleSize.P115, saved.value.size)
    }

    @Test
    fun usingALookSavesItForEveryShow() {
        model().use(boxed)

        assertEquals(boxed, saved.value.look)
    }

    @Test
    fun aNewLookCopiesTheOneInUseAndIsUsed() {
        saved.value = SubtitleSettings(look = boxed)

        val added = model().addLook()

        assertEquals(LookId.Mine(1), added)
        assertEquals(LookId.Mine(1), saved.value.look)
        assertEquals(BuiltInLook.BOXED.style, saved.value.myLooks[1])
    }

    @Test
    fun editingAPartOfALookSavesIt() {
        model().edit(boxed, LookPart.TEXT_COLOUR, 2)

        assertEquals(TextColour.YELLOW, saved.value.style(boxed)?.textColour)
    }

    @Test
    fun resettingABuiltInLookPutsItBack() {
        val model = model()
        model.edit(boxed, LookPart.BOX, 1)

        model.reset(BuiltInLook.BOXED)

        assertEquals(BuiltInLook.BOXED.style, saved.value.style(boxed))
    }

    @Test
    fun deletingTheLookInUseGoesBackToClassic() {
        val model = model()
        model.addLook()

        model.delete(1)

        assertEquals(LookId.BuiltIn(BuiltInLook.CLASSIC), saved.value.look)
        assertTrue(saved.value.myLooks.isEmpty())
    }

    @Test
    fun clearingAShowRemovesItsOwnLook() {
        saved.value = SubtitleSettings(shows = mapOf(7 to ShowSubtitles("A", SubtitlePick(boxed, SubtitleSize.P60, SubtitlePosition.TOP))))

        model().clearShow(7)

        assertTrue(saved.value.shows.isEmpty())
    }

    @Test
    fun resettingEverythingGoesBackToTheDefaults() {
        saved.value = SubtitleSettings(size = SubtitleSize.P200, myLooks = mapOf(1 to LookStyle()))

        model().resetAll()

        assertEquals(SubtitleSettings(), saved.value)
    }
}
