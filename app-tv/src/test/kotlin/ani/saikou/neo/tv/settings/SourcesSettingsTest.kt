package ani.saikou.neo.tv.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.sources.AddError
import ani.saikou.neo.shared.sources.Adding
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.shared.sources.SourceProblem
import ani.saikou.neo.shared.sources.SourcesState
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val example = InstalledSource("ani.saikou.neo.example", "Example source", "1.4.0")
private val demo = InstalledSource("ani.saikou.neo.demo", "Demo source", "0.9.0")

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SourcesSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = mutableStateOf(SourcesState(emptyList()))
    private val added = mutableListOf<String>()
    private val removed = mutableListOf<String>()
    private var dismissed = 0

    private fun show(sources: SourcesState) {
        state.value = sources
        compose.setContent {
            NeoTheme {
                SettingsScreen(
                    Settings(),
                    onChoose = { _, _ -> },
                    sources = state.value,
                    sourceActions = SourceActions(onAdd = { added += it }, onDismiss = { dismissed++ }, onRemove = { removed += it }),
                )
            }
        }
        // Down to Sources, after Browsing and Display, then into it.
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionRight)
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

    private fun addSource() = compose.onNodeWithTag(ADD_SOURCE_TAG)

    private fun row(source: InstalledSource) = compose.onNodeWithTag(sourceTag(source.id))

    private fun update(change: SourcesState.() -> SourcesState) {
        state.value = state.value.change()
        compose.waitForIdle()
    }

    @Test
    fun withNoSourcesAddASourceHasFocus() {
        show(SourcesState(emptyList()))

        addSource().assertIsFocused()
        compose.onNodeWithText("No sources yet").assertIsDisplayed()
        compose.onNodeWithText("Only add sources you trust. They run with the app's permissions.").assertIsDisplayed()
    }

    @Test
    fun listsEachSourceWithItsVersionUnderAddASource() {
        show(SourcesState(listOf(example, demo)))

        addSource().assertIsFocused().assertLeftPositionInRootIsEqualTo(306.dp).assertHeightIsEqualTo(60.dp)
        compose.onNodeWithText("INSTALLED").assertIsDisplayed()
        row(example)
            .assertHeightIsEqualTo(68.dp)
            .assert(described("Example source, Version 1.4.0. Remove"))
        row(demo).assert(described("Demo source, Version 0.9.0. Remove"))
    }

    @Test
    fun aSourceThatNoLongerFitsSaysWhyInTheList() {
        val older = example.copy(problem = SourceProblem.BUILT_FOR_OLDER_APP)
        show(SourcesState(listOf(older)))

        row(older).assert(
            described(
                "Example source, This source was built for an older version of Saikou Neo. Get a newer version of the source, " +
                    "then add it again.. Remove",
            ),
        )
    }

    @Test
    fun addingSendsTheLinkTyped() {
        show(SourcesState(emptyList()))

        press(Key.DirectionCenter)
        compose.onNodeWithTag(SETTINGS_PANEL_TAG).assertLeftPositionInRootIsEqualTo(584.dp).assertWidthIsEqualTo(376.dp)
        compose.onNodeWithTag(SOURCE_FIELD_TAG).assertIsFocused().performTextInput("https://example.com/source.apk")
        compose.onNodeWithText("Add").performClickByKey()

        assertEquals(listOf("https://example.com/source.apk"), added)
    }

    @Test
    fun whileCheckingAndAfterAFailureThePanelSaysSoAndKeepsTheText() {
        show(SourcesState(emptyList()))
        press(Key.DirectionCenter)
        compose.onNodeWithTag(SOURCE_FIELD_TAG).performTextInput("nothing")

        update { copy(adding = Adding.Checking) }
        compose.onNodeWithText("Checking…").assertIsDisplayed()

        update { copy(adding = Adding.Failed(AddError.NO_SUCH_CODE)) }
        compose.onNodeWithText("No source has that code. Check it and try again.").assertIsDisplayed()
        compose.onNodeWithTag(SOURCE_FIELD_TAG).assertTextEquals("nothing", includeEditableText = true)
    }

    @Test
    fun backClosesTheAddPanelOntoAddASource() {
        show(SourcesState(listOf(example)))
        press(Key.DirectionCenter)

        back()

        compose.onNodeWithTag(SETTINGS_PANEL_TAG).assertDoesNotExist()
        addSource().assertIsFocused()
        assertEquals(1, dismissed)
    }

    @Test
    fun anAddedSourceClosesThePanel() {
        show(SourcesState(emptyList()))
        press(Key.DirectionCenter)

        update { SourcesState(listOf(example), Adding.Added(example)) }

        compose.onNodeWithTag(SETTINGS_PANEL_TAG).assertDoesNotExist()
        row(example).assertIsDisplayed()
        addSource().assertIsFocused()
    }

    @Test
    fun aSourceForAnotherVersionSaysWhyAndCloses() {
        show(SourcesState(emptyList()))
        press(Key.DirectionCenter)

        update { copy(adding = Adding.CantUse(example.copy(version = "2.0.0", problem = SourceProblem.BUILT_FOR_NEWER_APP))) }

        compose.onNodeWithText("Can't use this source").assertIsDisplayed()
        compose.onNodeWithText("Version 2.0.0").assertIsDisplayed()
        compose
            .onNodeWithText("This source was built for a newer version of Saikou Neo. Update the app, then add it again.")
            .assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsFocused()
        compose.onNodeWithText("Add").assertDoesNotExist()

        press(Key.DirectionCenter)
        assertEquals(1, dismissed)
    }

    @Test
    fun removeAsksFirstWithKeepFocused() {
        show(SourcesState(listOf(example, demo)))
        press(Key.DirectionDown)

        press(Key.DirectionCenter)

        compose.onNodeWithText("Remove Example source?").assertIsDisplayed()
        compose.onNodeWithText("Keep").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(emptyList<String>(), removed)
        row(example).assertIsFocused()
    }

    @Test
    fun removingMovesFocusToTheNextSource() {
        show(SourcesState(listOf(demo, example)))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        update { copy(installed = listOf(example)) }

        assertEquals(listOf(demo.id), removed)
        row(example).assertIsFocused()
    }

    @Test
    fun removingTheLastSourceMovesFocusToAddASource() {
        show(SourcesState(listOf(example)))
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        update { copy(installed = emptyList()) }

        addSource().assertIsFocused()
        compose.onNodeWithText("No sources yet").assertIsDisplayed()
    }

    private fun described(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))

    // Select on a focused button, as the remote does.
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickByKey() {
        press(Key.DirectionDown)
        assertIsFocused()
        press(Key.DirectionCenter)
    }
}
