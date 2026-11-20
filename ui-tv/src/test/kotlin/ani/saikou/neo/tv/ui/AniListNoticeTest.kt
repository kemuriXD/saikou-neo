package ani.saikou.neo.tv.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.Text
import ani.saikou.neo.shared.account.ListEntry
import ani.saikou.neo.shared.account.ListNotice
import ani.saikou.neo.shared.account.ListStatus
import ani.saikou.neo.shared.account.NoticeChange
import ani.saikou.neo.shared.account.ShownNotice
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// ADR 0056: over every screen but the player, the notice takes focus to Undo and gives it back on any move.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AniListNoticeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val shown = mutableStateOf<ShownNotice?>(null)
    private val pressed = mutableListOf<String>()
    private val planning = ListEntry(ListStatus.PLANNING, 0, hasStartDate = false)

    private fun notice(
        change: NoticeChange,
        before: ListEntry? = planning,
    ) = ShownNotice(ListNotice(101, 1, AnimeTitle("Romaji", "English", null), change, before))

    private fun show() {
        compose.setContent {
            NeoTheme {
                val screen = remember { FocusRequester() }
                val first = remember { FocusRequester() }
                Box(Modifier.fillMaxSize().focusRequester(screen).focusGroup()) {
                    BackHandler { pressed += "back" }
                    Row {
                        NeoSurface(
                            { pressed += "first" },
                            CircleShape,
                            1f,
                            Modifier.focusRequester(first).testTag("first"),
                        ) { Text("First") }
                        NeoSurface({ pressed += "second" }, CircleShape, 1f, Modifier.testTag("second")) { Text("Second") }
                    }
                    LaunchedEffect(Unit) { first.requestFocus() }
                    NoticeOverScreens(shown.value, NoticeActions(onUndo = { pressed += "undo" }), screen)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun undoTakesFocusAndAnyMoveGivesItBack() {
        show()
        shown.value = notice(NoticeChange.Updated(ListStatus.WATCHING, 5, byEpisode = true))
        compose.waitForIdle()

        compose.onNodeWithTag(NOTICE_UNDO_TAG).assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionRight)

        compose.onNodeWithTag("first").assertIsFocused()
        compose.onNodeWithTag(NOTICE_TAG).assertExists()
        assertEquals(listOf("undo"), pressed)
    }

    @Test
    fun backOnUndoOnlyGivesFocusBack() {
        show()
        shown.value = notice(NoticeChange.Updated(ListStatus.WATCHING, 5, byEpisode = true))
        compose.waitForIdle()

        press(Key.Back)

        compose.onNodeWithTag("first").assertIsFocused()
        assertEquals(emptyList<String>(), pressed)
    }

    @Test
    fun theNoticeClosingGivesFocusBack() {
        show()
        shown.value = notice(NoticeChange.Removed)
        compose.waitForIdle()

        shown.value = null
        compose.waitForIdle()

        compose.onNodeWithTag(NOTICE_TAG).assertDoesNotExist()
        compose.onNodeWithTag("first").assertIsFocused()
    }

    @Test
    fun aStatusChangeSaysTheNewAndOldStatus() {
        show()
        shown.value = notice(NoticeChange.Updated(ListStatus.WATCHING, 0, byEpisode = false))
        compose.waitForIdle()

        compose.onNodeWithText("English · Watching on AniList").assertExists()
        compose.onNodeWithText("It was Planning").assertExists()
    }

    @Test
    fun watchingAShowNotOnTheListSaysSo() {
        show()
        shown.value = notice(NoticeChange.Updated(ListStatus.WATCHING, 1, byEpisode = true), before = null)
        compose.waitForIdle()

        compose.onNodeWithText("English · episode 1 on AniList").assertExists()
        compose.onNodeWithText("It wasn\'t on your list").assertExists()
    }

    @Test
    fun theLastEpisodeSaysCompletedAndARemoveSaysWhatItWas() {
        show()
        shown.value =
            notice(NoticeChange.Updated(ListStatus.COMPLETED, 12, byEpisode = true), before = ListEntry(ListStatus.WATCHING, 11, true))
        compose.waitForIdle()
        compose.onNodeWithText("English · Completed on AniList").assertExists()
        compose.onNodeWithText("It was Watching, episode 11").assertExists()

        shown.value = notice(NoticeChange.Removed, before = ListEntry(ListStatus.PAUSED, 4, true))
        compose.waitForIdle()
        compose.onNodeWithText("English · off your list").assertExists()
        compose.onNodeWithText("It was Paused, episode 4").assertExists()
    }
}
