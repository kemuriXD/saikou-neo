package ani.saikou.neo.tv.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.shared.settings.BuiltInLook
import ani.saikou.neo.shared.settings.LookId
import ani.saikou.neo.shared.settings.LookPart
import ani.saikou.neo.shared.settings.LookStyle
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.settings.ShowSubtitles
import ani.saikou.neo.shared.settings.SubtitleOption
import ani.saikou.neo.shared.settings.SubtitlePick
import ani.saikou.neo.shared.settings.SubtitlePosition
import ani.saikou.neo.shared.settings.SubtitleSettings
import ani.saikou.neo.shared.settings.SubtitleSize
import ani.saikou.neo.tv.player.lookTileTag
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SubtitlesSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val boxed = LookId.BuiltIn(BuiltInLook.BOXED)
    private val classic = LookId.BuiltIn(BuiltInLook.CLASSIC)
    private val calls = mutableListOf<Any>()
    private var settings by mutableStateOf(SubtitleSettings())
    private var closed = false

    private val actions =
        SubtitleActions(
            step = { option, by -> calls += option to by },
            use = { calls += "use" to it },
            addLook = {
                calls += "add"
                LookId.Mine(1)
            },
            edit = { look, part, by -> calls += Triple(look, part, by) },
            reset = { calls += "reset" to it },
            delete = { calls += "delete" to it },
            clearShow = { calls += "clear" to it },
            resetAll = { calls += "reset all" },
        )

    private fun show(start: SubtitleSettings = SubtitleSettings()) {
        settings = start
        compose.setContent { NeoTheme { SubtitlesPage(settings, actions, onClose = { closed = true }) } }
        compose.waitForIdle()
    }

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun row(tag: String) = compose.onNodeWithTag(tag)

    @Test
    fun focusStartsOnLookAndLeftOrRightStepsTheFocusedRow() {
        show()
        row(subsRowTag(SubsRow.LOOK)).assertIsFocused()

        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionLeft)

        assertEquals(listOf(SubtitleOption.SIZE to 1, SubtitleOption.SIZE to -1), calls)
    }

    @Test
    fun okOnASwitchFlipsIt() {
        show()
        press(Key.DirectionDown, 2)

        press(Key.DirectionCenter)

        assertEquals(listOf(SubtitleOption.START_FROM_TV to 1), calls)
    }

    @Test
    fun okOnAnotherLookUsesItAndOkOnTheOneInUseEditsIt() {
        show()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(lookTileTag(classic)).assertIsFocused()

        press(Key.DirectionRight, 2)
        press(Key.DirectionCenter)
        assertEquals(listOf("use" to boxed), calls)

        settings = settings.withLook(boxed)
        compose.waitForIdle()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Reset this look").assertIsDisplayed()
    }

    @Test
    fun newLookAddsOneAndOpensItsEditor() {
        show(SubtitleSettings(myLooks = mapOf(1 to LookStyle(), 2 to LookStyle())))
        press(Key.DirectionCenter)

        press(Key.DirectionDown, 2)
        press(Key.DirectionRight, 2)
        press(Key.DirectionCenter)
        settings = settings.addLook()
        compose.waitForIdle()

        assertEquals(listOf("add"), calls)
        compose.onNodeWithText("Delete this look").assertIsDisplayed()
    }

    @Test
    fun withThreeOfYourOwnThereIsNoNewLook() {
        show(SubtitleSettings(myLooks = mapOf(1 to LookStyle(), 2 to LookStyle(), 3 to LookStyle())))
        press(Key.DirectionCenter)

        compose.onNodeWithTag(SUBS_NEW_LOOK_TAG).assertDoesNotExist()
    }

    @Test
    fun theEditorStepsEachPartOfTheLook() {
        show(SubtitleSettings(look = boxed))
        press(Key.DirectionCenter, 2)

        press(Key.DirectionRight)

        assertEquals(listOf(Triple(boxed, LookPart.TEXT_COLOUR, 1)), calls)
    }

    @Test
    fun withNoEdgeThicknessAndEdgeColourAreSkipped() {
        show(SubtitleSettings(look = boxed))
        press(Key.DirectionCenter, 2)

        // Colour, opacity, font, bold, edge, then box.
        press(Key.DirectionDown, 5)

        row(lookPartTag(LookPart.BOX)).assertIsFocused()
        row(lookPartTag(LookPart.THICKNESS)).assertIsNotFocused()
    }

    @Test
    fun resettingABuiltInLookAsksFirst() {
        show(SubtitleSettings(look = boxed))
        press(Key.DirectionCenter, 2)

        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Reset Boxed?").assertIsDisplayed()
        assertEquals(emptyList<Any>(), calls)

        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertEquals(listOf("reset" to BuiltInLook.BOXED), calls)
    }

    @Test
    fun deletingYourOwnLookAsksFirstAndGoesBackToTheLooks() {
        show(SubtitleSettings(look = LookId.Mine(1), myLooks = mapOf(1 to LookStyle())))
        press(Key.DirectionCenter, 2)

        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Delete My look 1?").assertIsDisplayed()
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        settings = settings.deleteLook(1)
        compose.waitForIdle()

        assertEquals(listOf("delete" to 1), calls)
        compose.onNodeWithTag(lookTileTag(classic)).assertIsFocused()
    }

    @Test
    fun showsWithTheirOwnLookAreListedAndOkClearsOne() {
        val pick = SubtitlePick(boxed, SubtitleSize.P130, SubtitlePosition.TOP)
        show(SubtitleSettings(shows = mapOf(21 to ShowSubtitles("First show", pick))))
        press(Key.DirectionDown, 8)
        row(subsRowTag(SubsRow.SHOWS)).assertIsFocused()

        press(Key.DirectionCenter)
        compose.onNodeWithText("First show").assertIsDisplayed()
        press(Key.DirectionCenter)

        assertEquals(listOf("clear" to 21), calls)
    }

    @Test
    fun resetSubtitleSettingsAsksFirst() {
        show()
        press(Key.DirectionDown, 10)
        row(subsRowTag(SubsRow.RESET)).assertIsFocused()

        press(Key.DirectionCenter)
        compose.onNodeWithText("Reset subtitle settings?").assertIsDisplayed()
        press(Key.DirectionUp)
        press(Key.DirectionCenter)

        assertEquals(listOf("reset all"), calls)
    }

    @Test
    fun backFromTheMainPageCloses() {
        show()

        back()

        assertEquals(true, closed)
    }

    @Test
    fun subtitlesInSettingsOpensThePage() {
        compose.setContent {
            NeoTheme {
                SettingsScreen(Settings(), onChoose = { _, _ -> }, subtitles = SubtitleSettings(), subtitleActions = actions)
            }
        }
        compose.waitForIdle()
        press(Key.DirectionDown, 2)
        press(Key.DirectionRight)

        press(Key.DirectionCenter)
        row(subsRowTag(SubsRow.LOOK)).assertIsFocused()

        back()
        row(SUBTITLES_ENTRY_TAG).assertIsFocused()
    }
}
