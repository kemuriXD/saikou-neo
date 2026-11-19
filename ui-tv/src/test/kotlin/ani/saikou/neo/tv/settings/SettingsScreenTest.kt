package ani.saikou.neo.tv.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.catalogue.ShowFormat
import ani.saikou.neo.shared.settings.ScheduleRelease
import ani.saikou.neo.shared.settings.ScheduleSettings
import ani.saikou.neo.shared.settings.Setting
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.settings.SubOrDub
import ani.saikou.neo.shared.settings.TickList
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private val chosen = mutableListOf<Pair<Setting, Any>>()
    private val ticks = mutableListOf<Triple<TickList, Any, Boolean>>()

    private fun show(settings: Settings = Settings()) =
        compose.setContent {
            NeoTheme {
                SettingsScreen(
                    settings,
                    onChoose = { setting, value -> chosen += setting to value },
                    onTick = { list, item, on -> ticks += Triple(list, item, on) },
                )
            }
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
        value: Any,
    ) = compose.onNodeWithTag(settingChoiceTag(setting, value))

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

    // Opened from another screen, Settings can lose its first request while that screen fades out. Focus coming in
    // afterwards still lands on the category it opened on.
    @Test
    fun focusComingInLandsOnTheCategoryShown() {
        lateinit var focusManager: FocusManager
        compose.setContent {
            focusManager = LocalFocusManager.current
            // The last category, so the Down that brings focus in can't move it on.
            NeoTheme { SettingsScreen(Settings(), onChoose = { _, _ -> }, start = SettingsCategory.ACCOUNT) }
        }
        compose.runOnIdle { focusManager.clearFocus(force = true) }

        press(Key.DirectionDown)

        category(SettingsCategory.ACCOUNT).assertIsFocused().assertIsSelected()
    }

    @Test
    fun accountOffersSignInWhileSignedOutAndShowsTheNameOnceSignedIn() {
        var signIns = 0
        val account = mutableStateOf(AccountSettings(onSignIn = { signIns++ }))
        compose.setContent {
            NeoTheme { SettingsScreen(Settings(), onChoose = { _, _ -> }, start = SettingsCategory.ACCOUNT, account = account.value) }
        }

        press(Key.DirectionRight)
        compose.onNodeWithTag(SIGN_IN_ENTRY_TAG).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, signIns)

        account.value = AccountSettings(AniListProfile(7, "Hikari", null))
        compose.onNodeWithText("Hikari", useUnmergedTree = true).assertExists()
        // Like Add a source, Sign in stays on top to switch accounts.
        compose.onNodeWithTag(SIGN_IN_ENTRY_TAG).assertExists()
    }

    private fun signedIn(onSignOut: () -> Unit) {
        val account = AccountSettings(AniListProfile(7, "Hikari", null), onSignOut = onSignOut)
        compose.setContent {
            NeoTheme { SettingsScreen(Settings(), onChoose = { _, _ -> }, start = SettingsCategory.ACCOUNT, account = account) }
        }
        press(Key.DirectionRight)
        compose.onNodeWithTag(SIGN_IN_ENTRY_TAG).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(SIGN_OUT_TAG).assertIsFocused()
        press(Key.DirectionCenter)
    }

    @Test
    fun signOutAsksFirstWithStaySignedInFocused() {
        var signOuts = 0
        signedIn { signOuts++ }

        compose.onNodeWithTag(SIGN_OUT_DIALOG_TAG).assertIsDisplayed()
        compose.onNodeWithText("Stay signed in").assertIsFocused()
        // Focus can't wander out of the dialog.
        press(Key.DirectionLeft)
        press(Key.DirectionUp)
        compose.onNodeWithText("Stay signed in").assertIsFocused()

        press(Key.DirectionCenter)

        assertEquals(0, signOuts)
        compose.onNodeWithTag(SIGN_OUT_DIALOG_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SIGN_OUT_TAG).assertIsFocused()
    }

    @Test
    fun backStaysSignedIn() {
        var signOuts = 0
        signedIn { signOuts++ }

        back()

        assertEquals(0, signOuts)
        compose.onNodeWithTag(SIGN_OUT_DIALOG_TAG).assertDoesNotExist()
    }

    @Test
    fun signOutInTheDialogSignsTheTvOut() {
        var signOuts = 0
        signedIn { signOuts++ }

        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertEquals(1, signOuts)
    }

    // Signed out, Account is laid out like Sources with none: icon, title, a line on signing in, the button and a note,
    // all centred in the pane.
    @Test
    fun signedOutAccountIsCentredLikeEmptySources() {
        compose.setContent {
            NeoTheme { SettingsScreen(Settings(), onChoose = { _, _ -> }, start = SettingsCategory.ACCOUNT) }
        }

        compose.onNodeWithText("Not signed in").assertIsDisplayed()
        compose.onNodeWithText("Sign in to sync your AniList list and watch progress on this TV.").assertIsDisplayed()
        compose.onNodeWithText("Shows a code to scan with Saikou Neo on your phone.").assertIsDisplayed()
        compose.onNodeWithTag(SIGN_IN_LOGO_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Signed in").assertDoesNotExist()
        // Sign in is a button under the title, not a row along the pane's left edge.
        val signIn = compose.onNodeWithTag(SIGN_IN_ENTRY_TAG).getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText("Not signed in").getUnclippedBoundsInRoot()
        assertEquals(((title.left + title.right) / 2).value, ((signIn.left + signIn.right) / 2).value, 0.5f)
        assertTrue(signIn.left > 400.dp)
        compose.onNodeWithTag(SIGN_IN_ENTRY_TAG).assertHeightIsEqualTo(52.dp)
    }

    @Test
    fun signedInAccountKeepsTheWideSignInRow() {
        val account = AccountSettings(AniListProfile(7, "Hikari", null))
        compose.setContent {
            NeoTheme { SettingsScreen(Settings(), onChoose = { _, _ -> }, start = SettingsCategory.ACCOUNT, account = account) }
        }

        compose.onNodeWithText("Not signed in").assertDoesNotExist()
        compose.onNodeWithTag(SIGN_IN_LOGO_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Your AniList account on this TV. Sign in again with your phone to use a different one.").assertIsDisplayed()
        compose
            .onNodeWithTag(SIGN_IN_ENTRY_TAG)
            .assertLeftPositionInRootIsEqualTo(306.dp)
            .assertHeightIsEqualTo(60.dp)
    }

    @Test
    fun signingOutFocusesSignInAndLeftGoesBackToTheCategories() {
        val account = mutableStateOf(AccountSettings())
        account.value = AccountSettings(AniListProfile(7, "Hikari", null), onSignOut = { account.value = AccountSettings() })
        compose.setContent {
            NeoTheme { SettingsScreen(Settings(), onChoose = { _, _ -> }, start = SettingsCategory.ACCOUNT, account = account.value) }
        }
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        compose.onNodeWithTag(SIGN_OUT_TAG).assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        compose.onNodeWithText("Not signed in").assertIsDisplayed()
        compose.onNodeWithTag(SIGN_IN_ENTRY_TAG).assertIsFocused()
        press(Key.DirectionLeft)
        category(SettingsCategory.ACCOUNT).assertIsFocused()
    }

    @Test
    fun canOpenOnAnotherCategory() {
        compose.setContent { NeoTheme { SettingsScreen(Settings(), onChoose = { _, _ -> }, start = SettingsCategory.SOURCES) } }

        category(SettingsCategory.SOURCES).assertIsFocused().assertIsSelected()
        category(SettingsCategory.BROWSING).assertIsNotSelected()
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
        category(SettingsCategory.SCHEDULE).assertTopPositionInRootIsEqualTo(128.dp)
        category(SettingsCategory.PLAYBACK).assertTopPositionInRootIsEqualTo(176.dp)
        category(SettingsCategory.SUBTITLES).assertTopPositionInRootIsEqualTo(224.dp)
        category(SettingsCategory.DISPLAY).assertTopPositionInRootIsEqualTo(272.dp)
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
        choice(Setting.UNAIRED_ANIME, false).assertLeftPositionInRootIsEqualTo(624.dp).assertHeightIsEqualTo(64.dp)
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

        press(Key.DirectionDown, times = 4)

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
        choice(Setting.UNAIRED_ANIME, false).assertIsFocused().assertIsSelected()
        choice(Setting.UNAIRED_ANIME, true).assertIsNotSelected()
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

        assertEquals(emptyList<Pair<Setting, Any>>(), chosen)
        panel().assertDoesNotExist()
        setting(Setting.UNAIRED_ANIME).assertIsFocused()
    }

    @Test
    fun focusStaysInThePanelWhileItsOpen() {
        show()

        openFirstSetting()
        press(Key.DirectionLeft)
        press(Key.DirectionUp)

        choice(Setting.UNAIRED_ANIME, false).assertIsFocused()
    }

    @Test
    fun leftFromASettingGoesBackToItsCategory() {
        show()

        press(Key.DirectionDown, times = 4)
        press(Key.DirectionRight)
        setting(Setting.SLOW_TV).assertIsFocused()
        press(Key.DirectionLeft)

        category(SettingsCategory.DISPLAY).assertIsFocused()
    }

    // Playback follows Schedule, as on the board, with Sub or Dub as its only setting so far.
    @Test
    fun playbackHasSubOrDub() {
        show()

        press(Key.DirectionDown, times = 2)

        category(SettingsCategory.PLAYBACK).assertIsFocused().assertIsSelected()
        compose.onNodeWithText("How episodes start, skip and carry on.").assertIsDisplayed()
        compose.onNodeWithText("Sub or Dub").assertIsDisplayed()
        compose.onNodeWithText("Which servers to show").assertIsDisplayed()
        compose.onNodeWithText("Both").assertIsDisplayed()
    }

    @Test
    fun subOrDubOffersSubDubAndBoth() {
        show()

        press(Key.DirectionDown, times = 2)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        // Both is last and marked, with Dub and then Sub above it.
        choice(Setting.SUB_OR_DUB, SubOrDub.BOTH).assertIsFocused().assertIsSelected()
        press(Key.DirectionUp)
        choice(Setting.SUB_OR_DUB, SubOrDub.DUB).assertIsFocused().assertIsNotSelected()
        press(Key.DirectionUp)
        choice(Setting.SUB_OR_DUB, SubOrDub.SUB).assertIsFocused().assertIsNotSelected()
        compose.onNodeWithText("Sub").assertIsDisplayed()
    }

    @Test
    fun choosingDubSetsIt() {
        show()

        press(Key.DirectionDown, times = 2)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        press(Key.DirectionUp)
        press(Key.DirectionCenter)

        assertEquals(listOf(Setting.SUB_OR_DUB to SubOrDub.DUB), chosen)
        setting(Setting.SUB_OR_DUB).assertIsFocused()
    }

    @Test
    fun theChosenEditionShowsAsTheValue() {
        show(Settings(subOrDub = SubOrDub.DUB))

        press(Key.DirectionDown, times = 2)

        compose.onNodeWithText("Dub").assertIsDisplayed()
    }

    private fun tickList(list: TickList) = compose.onNodeWithTag(tickListTag(list))

    private fun tickItem(
        list: TickList,
        item: Any,
    ) = compose.onNodeWithTag(tickItemTag(list, item))

    // Schedule comes after Browsing, with Releases, Formats, Donghua and Short episodes.
    @Test
    fun scheduleListsItsRowsWithTheirValues() {
        show()

        press(Key.DirectionDown)

        category(SettingsCategory.SCHEDULE).assertIsFocused().assertIsSelected()
        compose.onNodeWithText("What shows up in the Schedule each week.").assertIsDisplayed()
        tickList(TickList.RELEASES).assertTopPositionInRootIsEqualTo(114.dp).assertHeightIsEqualTo(62.dp)
        tickList(TickList.FORMATS).assertTopPositionInRootIsEqualTo(184.dp)
        setting(Setting.CHINESE_ANIMATION).assertTopPositionInRootIsEqualTo(254.dp)
        setting(Setting.SHORT_EPISODES).assertTopPositionInRootIsEqualTo(324.dp)
        compose.onNodeWithText("Sub always shows. Add raw episodes or dubs").assertIsDisplayed()
        compose.onNodeWithText("Episodes under 15 minutes").assertIsDisplayed()
        compose.onNodeWithText("Donghua").assertIsDisplayed()
        compose.onNodeWithText("Off").assertIsDisplayed()
        compose.onNodeWithText("On").assertIsDisplayed()
        press(Key.DirectionRight)
        tickList(TickList.RELEASES).assertIsFocused()
    }

    private fun showSchedule(schedule: ScheduleSettings) {
        show(Settings(schedule = schedule))
        press(Key.DirectionDown)
    }

    @Test
    fun aTickListSaysAllWhenEverythingIsTicked() {
        showSchedule(ScheduleSettings())

        compose.onAllNodesWithText("All").assertCountEquals(2)
    }

    @Test
    fun aTickListNamesOneOrTwoTicked() {
        showSchedule(ScheduleSettings(rawEpisodes = false, formats = setOf(ShowFormat.TV, ShowFormat.MOVIE)))

        compose.onNodeWithText("Dub").assertIsDisplayed()
        compose.onNodeWithText("TV, Movie").assertIsDisplayed()
    }

    @Test
    fun aTickListCountsMoreThanTwo() {
        showSchedule(ScheduleSettings(formats = ShowFormat.entries.toSet() - ShowFormat.TV - ShowFormat.SPECIAL))

        compose.onNodeWithText("4 of 6").assertIsDisplayed()
    }

    @Test
    fun releasesWithNothingTickedReadsSubOnly() {
        showSchedule(ScheduleSettings(rawEpisodes = false, dubs = false))

        compose.onNodeWithText("Sub only").assertIsDisplayed()
    }

    @Test
    fun releasesOpensItsTickListWithRawEpisodesFocused() {
        showSchedule(ScheduleSettings(rawEpisodes = false))

        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        panel().assertIsDisplayed().assertLeftPositionInRootIsEqualTo(584.dp).assertWidthIsEqualTo(376.dp)
        compose.onNodeWithText("Sub always shows. Tick what else to list.").assertIsDisplayed()
        tickItem(TickList.RELEASES, ScheduleRelease.RAW).assertIsFocused().assertIsOff().assertHeightIsEqualTo(56.dp)
        tickItem(TickList.RELEASES, ScheduleRelease.DUB).assertIsOn()
        compose.onNodeWithText("Japanese audio, before subtitles are out", useUnmergedTree = true).assertIsDisplayed()
    }

    // OK ticks or unticks and saves at once; the panel stays open until Back.
    @Test
    fun okTicksAndThePanelStaysOpenUntilBack() {
        showSchedule(ScheduleSettings())

        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertEquals(listOf(Triple<TickList, Any, Boolean>(TickList.RELEASES, ScheduleRelease.DUB, false)), ticks)
        tickItem(TickList.RELEASES, ScheduleRelease.DUB).assertIsFocused()
        back()
        panel().assertDoesNotExist()
        tickList(TickList.RELEASES).assertIsFocused()
    }

    @Test
    fun theLastTickedFormatCantBeUnticked() {
        showSchedule(ScheduleSettings(formats = setOf(ShowFormat.TV)))

        press(Key.DirectionRight)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        tickItem(TickList.FORMATS, ShowFormat.TV).assertIsFocused().assertIsOn()
        compose.onNodeWithText("Keep at least one ticked", useUnmergedTree = true).assertIsDisplayed()
        // Formats are just their names, with no help lines.
        tickItem(TickList.FORMATS, ShowFormat.ONA).assertHeightIsEqualTo(56.dp)
        compose.onNodeWithText("Made for streaming", useUnmergedTree = true).assertDoesNotExist()
        press(Key.DirectionCenter)

        assertEquals(emptyList<Triple<TickList, Any, Boolean>>(), ticks)
        tickItem(TickList.FORMATS, ShowFormat.TV).assertIsOn()
    }

    @Test
    fun chineseAnimationOffersOffAndOn() {
        showSchedule(ScheduleSettings())

        press(Key.DirectionRight)
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionCenter)
        choice(Setting.CHINESE_ANIMATION, false).assertIsFocused().assertIsSelected()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertEquals(listOf(Setting.CHINESE_ANIMATION to true), chosen)
        setting(Setting.CHINESE_ANIMATION).assertIsFocused()
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
            press(Key.DirectionDown, times = 4)
            press(Key.DirectionRight)
        }

        setting(Setting.SLOW_TV).assertIsFocused()
    }

    @Test
    fun focusStaysOnACategoryAfterRecreation() {
        recreate { press(Key.DirectionDown, times = 4) }

        category(SettingsCategory.DISPLAY).assertIsFocused()
    }

    @Test
    fun anOpenPanelKeepsFocusAfterRecreation() {
        recreate {
            openFirstSetting()
            press(Key.DirectionDown)
        }

        choice(Setting.UNAIRED_ANIME, false).assertIsFocused()
    }
}
