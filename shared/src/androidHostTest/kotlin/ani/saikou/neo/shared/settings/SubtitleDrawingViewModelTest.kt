package ani.saikou.neo.shared.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleDrawingViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aChangedSettingRedrawsAtOnce() {
        val saved = MutableStateFlow(SubtitleSettings())
        val model = SubtitleDrawingViewModel(saved, showId = 3)
        assertEquals(SubtitleSettings().drawingFor(3), model.drawing.value)

        saved.value = SubtitleSettings(size = SubtitleSize.P150)

        assertEquals(150, model.drawing.value.sizePercent)
    }
}
