package ani.saikou.neo.tv.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.sources.CheckStatus
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.shared.sources.Progress
import ani.saikou.neo.shared.sources.SourceCheck
import ani.saikou.neo.shared.sources.SourceUpdate
import ani.saikou.neo.shared.sources.SourcesState
import ani.saikou.neo.shared.sources.UpdateStatus
import ani.saikou.neo.shared.sources.UpdatesState
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val LIST = "https://example.com/list.json"
private val example = InstalledSource("ani.saikou.neo.example", "Example source", "1.4.0", list = LIST)
private val demo = InstalledSource("ani.saikou.neo.demo", "Demo source", "0.9.0", list = LIST)
private val sample = InstalledSource("ani.saikou.neo.sample", "Sample source", "2.1.0", list = LIST)
private val starter = InstalledSource("ani.saikou.neo.starter", "Starter source", "1.0.0")

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SourceUpdatesSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val updates = mutableStateOf(UpdatesState())
    private val checks = mutableStateOf<Map<String, SourceCheck>>(emptyMap())
    private var checked = 0
    private val updated = mutableListOf<String>()
    private var updatedAll = 0

    private val twoOut =
        UpdatesState(
            listOf(
                SourceUpdate(example, UpdateStatus.Available("1.5.0")),
                SourceUpdate(demo, UpdateStatus.Available("1.0.0")),
                SourceUpdate(sample, UpdateStatus.NeedsNewerApp("2.2.0")),
                SourceUpdate(starter, UpdateStatus.NotChecked),
            ),
        )

    private fun show(
        state: UpdatesState,
        intoSources: Boolean = true,
    ) {
        updates.value = state
        compose.setContent {
            NeoTheme {
                SettingsScreen(
                    Settings(),
                    onChoose = { _, _ -> },
                    sources = SourcesState(listOf(example, demo, sample, starter)),
                    sourceActions =
                        SourceActions(
                            onCheckUpdates = { checked++ },
                            onUpdate = { updated += it },
                            onUpdateAll = { updatedAll++ },
                        ),
                    checks = checks.value,
                    updates = updates.value,
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

    private fun text(text: String) = compose.onNodeWithText(text, useUnmergedTree = true)

    private fun described(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))

    @Test
    fun openingSourcesChecksForUpdates() {
        show(UpdatesState())

        assertEquals(1, checked)
    }

    @Test
    fun settingsShowsHowManyUpdatesThereAreBesideSources() {
        show(twoOut, intoSources = false)

        compose.onNodeWithContentDescription("2 updates", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun withNoUpdatesThereIsNoCountOrUpdateAll() {
        show(UpdatesState(listOf(SourceUpdate(example, UpdateStatus.UpToDate))))

        compose.onNodeWithContentDescription("1 update", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(UPDATE_ALL_TAG).assertDoesNotExist()
        compose.onNodeWithTag(updateTag(example.id)).assertDoesNotExist()
    }

    @Test
    fun eachSourceSaysWhatItsCheckFound() {
        show(twoOut)

        text("Version 1.4.0 · 1.5.0 is out").assertIsDisplayed()
        text("Version 2.1.0 · 2.2.0 needs a newer app").assertIsDisplayed()
        text("No updates: add it again from its list").assertExists()
        compose.onNodeWithTag(updateTag(sample.id)).assertDoesNotExist()
        compose.onNodeWithTag(updateTag(starter.id)).assertDoesNotExist()
    }

    @Test
    fun updateOnARowUpdatesThatSource() {
        show(twoOut)
        // Add a source, Add from my phone, then Update all, then the first row's Update.
        press(Key.DirectionDown, times = 3)

        compose
            .onNodeWithTag(updateTag(example.id))
            .assertIsFocused()
            .assert(described("Example source, Version 1.4.0 · 1.5.0 is out. Update"))
        press(Key.DirectionCenter)

        assertEquals(listOf(example.id), updated)
    }

    @Test
    fun updateAllSaysHowManyAndUpdatesThemAll() {
        show(twoOut)
        press(Key.DirectionDown, times = 2)

        compose.onNodeWithTag(UPDATE_ALL_TAG).assertIsFocused()
        text("Update all (2)").assertIsDisplayed()
        press(Key.DirectionCenter)

        assertEquals(1, updatedAll)
    }

    @Test
    fun whileUpdatingTheHeadingSaysHowFarAndEachRowWhereItIs() {
        show(
            UpdatesState(
                listOf(
                    SourceUpdate(example, UpdateStatus.Available("1.5.0"), installing = true),
                    SourceUpdate(demo, UpdateStatus.Available("1.0.0"), waiting = true),
                ),
                progress = Progress(0, 2),
            ),
        )

        text("Updating 1 of 2").assertIsDisplayed()
        text("Updating to 1.5.0").assertIsDisplayed()
        text("Waiting to update to 1.0.0").assertIsDisplayed()
        compose.onNodeWithTag(UPDATE_ALL_TAG).assertDoesNotExist()
        compose.onNodeWithTag(updateTag(demo.id)).assertDoesNotExist()
    }

    @Test
    fun aNewerVersionOutAfterAnUpdateShowsAsAnUpdate() {
        show(UpdatesState(listOf(SourceUpdate(example, UpdateStatus.Available("1.6.0"), updatedTo = "1.4.0"))))

        text("Version 1.4.0 · 1.6.0 is out").assertIsDisplayed()
    }

    @Test
    fun aFailedUpdateSaysTheOldVersionIsStillThereAndCanBeTriedAgain() {
        show(
            UpdatesState(
                listOf(
                    SourceUpdate(example, UpdateStatus.UpToDate, updatedTo = "1.5.0"),
                    SourceUpdate(demo, UpdateStatus.Available("1.0.0"), failed = true),
                ),
            ),
        )

        text("Updated to 1.5.0").assertIsDisplayed()
        text("Didn't update. Still on 0.9.0").assertIsDisplayed()
        compose.onNodeWithTag(updateTag(demo.id)).assertIsDisplayed()
    }

    // Example installing and demo waiting, as after Update all.
    private val installing =
        UpdatesState(
            listOf(
                SourceUpdate(example, UpdateStatus.Available("1.5.0"), installing = true),
                SourceUpdate(demo, UpdateStatus.Available("1.0.0"), waiting = true),
                SourceUpdate(sample, UpdateStatus.NeedsNewerApp("2.2.0")),
                SourceUpdate(starter, UpdateStatus.NotChecked),
            ),
            progress = Progress(0, 2),
        )

    private val finished =
        UpdatesState(
            listOf(
                SourceUpdate(example, UpdateStatus.UpToDate, updatedTo = "1.5.0"),
                SourceUpdate(demo, UpdateStatus.UpToDate, updatedTo = "1.0.0"),
                SourceUpdate(sample, UpdateStatus.NeedsNewerApp("2.2.0")),
                SourceUpdate(starter, UpdateStatus.NotChecked),
            ),
        )

    private fun set(state: UpdatesState) {
        updates.value = state
        compose.waitForIdle()
    }

    @Test
    fun whileUpdatesInstallFocusWaitsOnTheProgressAndTheRowsRest() {
        show(twoOut)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        set(installing)

        compose.onNodeWithTag(UPDATE_PROGRESS_TAG).assertIsFocused()
        // Check and Remove rest on every row, so Down has nowhere to go.
        press(Key.DirectionDown, times = 3)
        compose.onNodeWithTag(UPDATE_PROGRESS_TAG).assertIsFocused()
        compose.onNodeWithTag(checkTag(example.id)).assertIsNotFocused()
        // Once they finish, focus goes to Check all, where the heading's pills are.
        set(finished)
        compose.onNodeWithTag(CHECK_ALL_TAG).assertIsFocused()
        // And the rows can be reached again: Down goes to the first row's Remove, under Check all.
        press(Key.DirectionDown)
        compose.onNodeWithTag(removeTag(example.id)).assertIsFocused()
    }

    @Test
    fun updatingOneSourceAlsoMovesFocusToTheProgress() {
        show(twoOut)
        press(Key.DirectionDown, times = 3)
        compose.onNodeWithTag(updateTag(example.id)).assertIsFocused()
        press(Key.DirectionCenter)

        set(installing.copy(progress = Progress(0, 1)))

        compose.onNodeWithTag(UPDATE_PROGRESS_TAG).assertIsFocused()
        // Up goes to Add a source, and Down comes back.
        press(Key.DirectionUp)
        compose.onNodeWithTag(UPDATE_PROGRESS_TAG).assertIsNotFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(UPDATE_PROGRESS_TAG).assertIsFocused()
    }

    @Test
    fun passingOverSourcesWhileUpdatesInstallLeavesFocusOnTheCategories() {
        show(installing, intoSources = false)

        // Down to Sources, which shows its pane.
        press(Key.DirectionDown, times = 5)

        text("Updating 1 of 2").assertIsDisplayed()
        compose.onNodeWithTag(UPDATE_PROGRESS_TAG).assertIsNotFocused()
    }

    @Test
    fun whileChecksRunUpdateAllAndUpdateRest() {
        checks.value = mapOf(example.id to SourceCheck(CheckStatus.RUNNING))
        show(twoOut)

        // Past Add a source and Add from my phone, Update all rests, so Down goes to Stop checks.
        press(Key.DirectionDown, times = 2)
        compose.onNodeWithTag(CHECK_ALL_TAG).assertIsFocused()
        // The rows' Update rests with their Check and Remove.
        press(Key.DirectionDown)
        compose.onNodeWithTag(CHECK_ALL_TAG).assertIsFocused()
        compose.onNodeWithTag(updateTag(example.id)).assertIsNotFocused()
    }
}
