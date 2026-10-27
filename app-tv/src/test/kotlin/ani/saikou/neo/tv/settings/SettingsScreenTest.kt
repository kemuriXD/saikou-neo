package ani.saikou.neo.tv.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.shared.settings.Setting
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.TopTab
import ani.saikou.neo.tv.ui.topTabTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val toggled = mutableListOf<Setting>()
    private val tabs = mutableListOf<TopTab>()

    private fun show(settings: Settings = Settings()) =
        compose.setContent { NeoTheme { SettingsScreen(settings, onToggle = { toggled += it }, onTab = { tabs += it }) } }

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun category(category: SettingsCategory) = compose.onNodeWithTag(settingsCategoryTag(category))

    private fun setting(setting: Setting) = compose.onNodeWithTag(settingTag(setting))

    private fun tick(category: SettingsCategory) =
        compose.onNode(hasTestTag(SETTINGS_TICK_TAG) and hasAnyAncestor(hasTestTag(settingsCategoryTag(category))), useUnmergedTree = true)

    @Test
    fun opensOnTheSettingsTabWithBrowsingChosen() {
        show()

        compose.onNodeWithTag(topTabTag(TopTab.SETTINGS)).assertIsFocused().assertIsSelected()
        category(SettingsCategory.BROWSING).assertIsSelected()
        tick(SettingsCategory.BROWSING).assertExists()
        tick(SettingsCategory.DISPLAY).assertDoesNotExist()
    }

    @Test
    fun eachSettingShowsItsHelpAndValue() {
        show()

        compose.onNodeWithText("Anime that haven't aired").assertIsDisplayed()
        compose.onNodeWithText("Shows them in rows and search before their first episode is out.").assertIsDisplayed()
        compose.onNodeWithText("Hidden").assertIsDisplayed()
        compose.onNodeWithText("Upcoming episodes").assertIsDisplayed()
        compose.onNodeWithText("Shown").assertIsDisplayed()
    }

    @Test
    fun downFromTheBarReachesTheChosenCategory() {
        show()

        press(Key.DirectionDown)

        category(SettingsCategory.BROWSING).assertIsFocused()
    }

    @Test
    fun focusingACategoryShowsItsSettingsAndMovesTheTick() {
        show(Settings(slowTv = true))

        press(Key.DirectionDown, times = 2)

        category(SettingsCategory.DISPLAY).assertIsFocused().assertIsSelected()
        tick(SettingsCategory.DISPLAY).assertExists()
        tick(SettingsCategory.BROWSING).assertDoesNotExist()
        compose.onNodeWithText("Slow TV mode").assertIsDisplayed()
        compose.onNodeWithText("On").assertIsDisplayed()
        setting(Setting.UNAIRED_ANIME).assertDoesNotExist()
    }

    @Test
    fun selectSwitchesTheFocusedSetting() {
        show()

        press(Key.DirectionDown)
        press(Key.DirectionRight)
        setting(Setting.UNAIRED_ANIME).assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertEquals(listOf(Setting.UPCOMING_EPISODES), toggled)
    }

    @Test
    fun leftFromASettingGoesBackToItsCategory() {
        show()

        press(Key.DirectionDown, times = 2)
        press(Key.DirectionRight)
        setting(Setting.SLOW_TV).assertIsFocused()
        press(Key.DirectionLeft)

        category(SettingsCategory.DISPLAY).assertIsFocused()
    }

    @Test
    fun upFromTheFirstSettingReachesTheBar() {
        show()

        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionUp)

        compose.onNodeWithTag(topTabTag(TopTab.SETTINGS)).assertIsFocused()
    }

    @Test
    fun theBarOpensOtherTabs() {
        show()

        press(Key.DirectionLeft)
        press(Key.DirectionCenter)

        assertEquals(listOf(TopTab.SEARCH), tabs)
    }
}
