package ani.saikou.neo.tv.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.sources.CheckStatus
import ani.saikou.neo.shared.sources.CheckStep
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.shared.sources.SourceCheck
import ani.saikou.neo.shared.sources.SourcesState
import ani.saikou.neo.shared.sources.StepOutcome
import ani.saikou.neo.shared.sources.StepResult
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds

private val example = InstalledSource("ani.saikou.neo.example", "Example source", "1.4.0")
private val demo = InstalledSource("ani.saikou.neo.demo", "Demo source", "0.9.0")

private fun passed(
    step: CheckStep,
    millis: Int,
) = StepResult(step, StepOutcome.Passed, millis.milliseconds)

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SourceChecksTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val checks = mutableStateOf<Map<String, SourceCheck>>(emptyMap())
    private val checked = mutableListOf<String>()
    private var checkedAll = 0
    private var stopped = 0

    private fun show() {
        compose.setContent {
            NeoTheme {
                SettingsScreen(
                    Settings(),
                    onChoose = { _, _ -> },
                    sources = SourcesState(listOf(example, demo)),
                    sourceActions =
                        SourceActions(
                            onCheck = { checked += it.id },
                            onCheckAll = { checkedAll++ },
                            onStopChecks = { stopped++ },
                        ),
                    checks = checks.value,
                )
            }
        }
        // Down to Sources, then into it.
        press(Key.DirectionDown, times = 4)
        press(Key.DirectionRight)
    }

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun set(vararg check: Pair<InstalledSource, SourceCheck>) {
        checks.value = check.associate { (source, it) -> source.id to it }
        compose.waitForIdle()
    }

    private fun described(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))

    private fun text(text: String) = compose.onNodeWithText(text, useUnmergedTree = true)

    private fun checkExample() {
        compose.onNodeWithTag(checkTag(example.id)).assertExists()
        // Down to Check all, Down to the first source's Remove, Left to its Check.
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionLeft)
        compose.onNodeWithTag(checkTag(example.id)).assertIsFocused()
        press(Key.DirectionCenter)
    }

    @Test
    fun checkOnASourceShowsEachStepWithItsTime() {
        show()
        checkExample()
        set(example to SourceCheck(CheckStatus.RUNNING, listOf(passed(CheckStep.START, 200))))

        assertEquals(listOf(example.id), checked)
        text("Checking a source").assertIsDisplayed()
        text("Started").assertIsDisplayed()
        text("0.2 s").assertIsDisplayed()
        compose.onNodeWithText("Stop").assertIsFocused()

        set(
            example to
                SourceCheck(
                    CheckStatus.DONE,
                    listOf(
                        passed(CheckStep.START, 200),
                        passed(CheckStep.SITE, 1100),
                        passed(CheckStep.SERVERS, 4800),
                        passed(CheckStep.STREAM, 2300),
                    ),
                ),
        )
        text("This source works").assertIsDisplayed()
        text("Got a stream from the first server").assertIsDisplayed()
        text("Checked in 8.4 s.").assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsFocused()
    }

    @Test
    fun aFailedStepSaysWhyAndCheckAgainRunsItAgain() {
        show()
        checkExample()
        set(
            example to
                SourceCheck(
                    CheckStatus.DONE,
                    listOf(
                        passed(CheckStep.START, 300),
                        StepResult(CheckStep.SITE, StepOutcome.Failed(FailReason.Blocked), 900.milliseconds),
                    ),
                ),
        )

        text("This source failed").assertIsDisplayed()
        // In the panel and in the row behind it.
        compose.onAllNodesWithText("Failed: the site blocked the request", useUnmergedTree = true).onFirst().assertIsDisplayed()
        compose.onNodeWithText("Check again").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf(example.id, example.id), checked)
    }

    @Test
    fun checkAllShowsEachSourceAndStopChecksStopsThem() {
        show()
        press(Key.DirectionDown)
        text("Check all").assertIsDisplayed()
        press(Key.DirectionCenter)
        assertEquals(1, checkedAll)

        set(
            example to SourceCheck(CheckStatus.DONE, listOf(passed(CheckStep.START, 200))),
            demo to SourceCheck(CheckStatus.RUNNING, listOf(passed(CheckStep.START, 200))),
        )
        compose.onNodeWithTag(checkTag(example.id)).assert(described("Example source, Works, checked in 0.2 s. Check"))
        compose.onNodeWithTag(checkTag(demo.id)).assert(described("Demo source, Checking: reaching its site. Check"))
        // The rows rest while checks run, so focus stays on Stop checks.
        press(Key.DirectionDown)
        compose.onNodeWithTag(CHECK_ALL_TAG).assertIsFocused()
        text("Stop checks").assertIsDisplayed()

        press(Key.DirectionCenter)
        assertEquals(1, stopped)
    }

    @Test
    fun leavingSourcesStopsChecks() {
        show()
        press(Key.DirectionDown)
        press(Key.DirectionLeft)
        press(Key.DirectionUp)

        assertTrue(stopped > 0)
    }
}
