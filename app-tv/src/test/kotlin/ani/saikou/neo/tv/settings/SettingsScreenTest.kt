package ani.saikou.neo.tv.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import ani.saikou.neo.shared.settings.Setting
import ani.saikou.neo.shared.settings.Settings
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
class SettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val chosen = mutableListOf<Pair<Setting, Boolean>>()

    private fun show(settings: Settings = Settings()) =
        compose.setContent {
            NeoTheme { SettingsScreen(settings, onChoose = { setting, on -> chosen += setting to on }) }
        }

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun category(category: SettingsCategory) = compose.onNodeWithTag(settingsCategoryTag(category))

    private fun setting(setting: Setting) = compose.onNodeWithTag(settingTag(setting))

    private fun choice(
        setting: Setting,
        on: Boolean,
    ) = compose.onNodeWithTag(settingChoiceTag(setting, on))

    private fun panel() = compose.onNodeWithTag(SETTINGS_PANEL_TAG)

    // The remote's Back reaches the activity's back handlers, as Details and the navigation use, not just the focused key.
    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    // From Browsing, Select on its first setting.
    private fun openFirstSetting() {
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
    }

    @Test
    fun opensOnBrowsing() {
        show()

        category(SettingsCategory.BROWSING).assertIsFocused().assertIsSelected()
        category(SettingsCategory.DISPLAY).assertIsNotSelected()
        panel().assertDoesNotExist()
    }

    // From the design, less the rail: the title on the safe area, categories under it and the settings beside them.
    @Test
    fun laysOutTitleCategoriesAndSettingsAsDesigned() {
        show()

        compose.onNodeWithText("Settings").assertLeftPositionInRootIsEqualTo(58.dp).assertTopPositionInRootIsEqualTo(27.dp)
        category(SettingsCategory.BROWSING)
            .assertLeftPositionInRootIsEqualTo(58.dp)
            .assertTopPositionInRootIsEqualTo(80.dp)
            .assertWidthIsEqualTo(220.dp)
            .assertHeightIsEqualTo(44.dp)
        category(SettingsCategory.DISPLAY).assertTopPositionInRootIsEqualTo(128.dp)
        setting(Setting.UNAIRED_ANIME)
            .assertLeftPositionInRootIsEqualTo(306.dp)
            .assertTopPositionInRootIsEqualTo(114.dp)
            .assertWidthIsEqualTo(606.dp)
            .assertHeightIsEqualTo(62.dp)
        setting(Setting.UPCOMING_EPISODES).assertTopPositionInRootIsEqualTo(184.dp)
    }

    @Test
    fun thePanelTakesTheRightOfTheScreen() {
        show()

        openFirstSetting()

        panel().assertLeftPositionInRootIsEqualTo(600.dp).assertWidthIsEqualTo(360.dp)
        choice(Setting.UNAIRED_ANIME, on = false).assertLeftPositionInRootIsEqualTo(624.dp).assertHeightIsEqualTo(64.dp)
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
    fun focusingACategoryShowsItsSettings() {
        show(Settings(slowTv = true))

        press(Key.DirectionDown)

        category(SettingsCategory.DISPLAY).assertIsFocused().assertIsSelected()
        category(SettingsCategory.BROWSING).assertIsNotSelected()
        compose.onNodeWithText("Slow TV mode").assertIsDisplayed()
        compose.onNodeWithText("On").assertIsDisplayed()
        setting(Setting.UNAIRED_ANIME).assertDoesNotExist()
    }

    @Test
    fun selectOpensEveryChoiceWithTheCurrentOneMarked() {
        show()

        openFirstSetting()

        panel().assertIsDisplayed()
        choice(Setting.UNAIRED_ANIME, on = false).assertIsFocused().assertIsSelected()
        choice(Setting.UNAIRED_ANIME, on = true).assertIsNotSelected()
    }

    @Test
    fun selectingAChoiceSetsItAndClosesThePanel() {
        show()

        openFirstSetting()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertEquals(listOf(Setting.UNAIRED_ANIME to true), chosen)
        panel().assertDoesNotExist()
        setting(Setting.UNAIRED_ANIME).assertIsFocused()
    }

    @Test
    fun backClosesThePanelWithoutChangingAnything() {
        show()

        openFirstSetting()
        press(Key.DirectionDown)
        back()

        assertEquals(emptyList<Pair<Setting, Boolean>>(), chosen)
        panel().assertDoesNotExist()
        setting(Setting.UNAIRED_ANIME).assertIsFocused()
    }

    @Test
    fun focusStaysInThePanelWhileItsOpen() {
        show()

        openFirstSetting()
        press(Key.DirectionLeft)
        press(Key.DirectionUp)

        choice(Setting.UNAIRED_ANIME, on = false).assertIsFocused()
    }

    @Test
    fun leftFromASettingGoesBackToItsCategory() {
        show()

        press(Key.DirectionDown)
        press(Key.DirectionRight)
        setting(Setting.SLOW_TV).assertIsFocused()
        press(Key.DirectionLeft)

        category(SettingsCategory.DISPLAY).assertIsFocused()
    }

    /** Shows Settings, runs [before], then recreates it as after the app was killed. */
    private fun recreate(before: () -> Unit) {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { NeoTheme { SettingsScreen(Settings(), onChoose = { _, _ -> }) } }
        before()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
    }

    @Test
    fun focusStaysOnASettingAfterRecreation() {
        recreate {
            press(Key.DirectionDown)
            press(Key.DirectionRight)
        }

        setting(Setting.SLOW_TV).assertIsFocused()
    }

    @Test
    fun focusStaysOnACategoryAfterRecreation() {
        recreate { press(Key.DirectionDown) }

        category(SettingsCategory.DISPLAY).assertIsFocused()
    }

    @Test
    fun anOpenPanelKeepsFocusAfterRecreation() {
        recreate {
            openFirstSetting()
            press(Key.DirectionDown)
        }

        choice(Setting.UNAIRED_ANIME, on = false).assertIsFocused()
    }
}
