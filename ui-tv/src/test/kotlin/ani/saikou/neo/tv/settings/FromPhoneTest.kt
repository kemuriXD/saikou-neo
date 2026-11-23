package ani.saikou.neo.tv.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.sources.FromPhoneState
import ani.saikou.neo.shared.sources.InstallOutcome
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.shared.sources.ListedSource
import ani.saikou.neo.shared.sources.PickedSources
import ani.saikou.neo.shared.sources.SourcesState
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val demo = InstalledSource("ani.saikou.neo.demo", "Demo source", "0.9.0")
private const val LIST = "https://example.com/list.json"
private val picked =
    PickedSources.FromList(
        LIST,
        listOf(
            ListedSource("ani.saikou.neo.sample", "Sample source", "2.1.0", 1, "https://example.com/sample.apk"),
            ListedSource(demo.id, demo.name, "1.0.0", 1, "https://example.com/demo.apk"),
        ),
    )

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class FromPhoneTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = mutableStateOf<FromPhoneState?>(null)
    private var opened = 0
    private var matched = 0
    private var installed = 0
    private var closed = 0

    private fun show(
        installedSources: List<InstalledSource> = listOf(demo),
        intoSources: Boolean = true,
    ) {
        compose.setContent {
            NeoTheme {
                SettingsScreen(
                    Settings(),
                    onChoose = { _, _ -> },
                    sources = SourcesState(installedSources),
                    sourceActions = SourceActions(onFromPhone = { opened++ }),
                    fromPhone =
                        FromPhone(
                            state.value,
                            onMatch = { matched++ },
                            onInstall = { installed++ },
                            onClose = {
                                closed++
                                state.value = null
                            },
                        ),
                )
            }
        }
        if (intoSources) {
            // Down to Sources, after Browsing, Schedule, Playback, Subtitles and Display, then into it.
            press(Key.DirectionDown, times = 5)
            press(Key.DirectionRight)
        }
    }

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun set(next: FromPhoneState?) {
        state.value = next
        compose.waitForIdle()
    }

    private fun text(text: String) = compose.onNodeWithText(text, useUnmergedTree = true)

    @Test
    fun addFromMyPhoneSitsUnderAddASourceAndOpensTheBoard() {
        show()
        press(Key.DirectionDown)

        compose.onNodeWithTag(FROM_PHONE_ENTRY_TAG).assertIsFocused()
        text("Pick sources on your phone and send them here").assertIsDisplayed()
        press(Key.DirectionCenter)

        assertEquals(1, opened)
    }

    @Test
    fun withNoSourcesItIsAButtonBesideAddASource() {
        show(installedSources = emptyList())
        press(Key.DirectionRight)

        compose.onNodeWithTag(FROM_PHONE_ENTRY_TAG).assertIsFocused()
    }

    @Test
    fun theBoardShowsTheCodeAndBackLeaves() {
        show()
        set(FromPhoneState.Waiting("qr", "K74Q9M", 590))

        text("Pick sources on your phone").assertIsDisplayed()
        text("Get a new code").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertEquals(1, closed)
    }

    @Test
    fun itMatchesOnTheTvIsFocusedAndConfirms() {
        show()
        set(FromPhoneState.CheckCode("482913"))

        text("482 913").assertIsDisplayed()
        text("Choose It matches on your phone too").assertIsDisplayed()
        press(Key.DirectionCenter)

        assertEquals(1, matched)
    }

    @Test
    fun onceConfirmedItWaitsForThePhone() {
        show()
        set(FromPhoneState.CheckCode("482913", confirmed = true))

        text("Waiting for your phone to confirm too").assertIsDisplayed()
    }

    @Test
    fun whatArrivedShowsNewAndUpdateWithInstallFocused() {
        show()
        set(FromPhoneState.Confirm(picked))

        text("From your phone").assertIsDisplayed()
        text("Your phone sent 2 sources. Nothing installs until you press Install.").assertIsDisplayed()
        text("New, version 2.1.0").assertIsDisplayed()
        text("Update from 0.9.0 to 1.0.0").assertIsDisplayed()
        press(Key.DirectionCenter)

        assertEquals(1, installed)
    }

    @Test
    fun whileInstallingEachSourceSaysWhereItIs() {
        show()
        set(FromPhoneState.Installing(picked, listOf(null, null)))

        text("Installing 1 of 2. Keep this screen open.").assertIsDisplayed()
        text("Waiting").assertIsDisplayed()
    }

    @Test
    fun aFailureNamesTheSourceAndSaysWhyThenDoneCloses() {
        show()
        set(
            FromPhoneState.Installing(
                picked,
                listOf(InstallOutcome.Installed(demo.copy(id = "ani.saikou.neo.sample", version = "2.1.0")), InstallOutcome.NotTheSource),
            ),
        )

        text("1 of 2 sources installed.").assertIsDisplayed()
        text("Its file isn't the source the list names").assertIsDisplayed()
        press(Key.DirectionCenter)

        assertEquals(1, closed)
    }
}
