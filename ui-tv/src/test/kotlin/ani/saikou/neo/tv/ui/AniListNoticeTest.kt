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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.account.ListEntry
import ani.saikou.neo.shared.account.ListNotice
import ani.saikou.neo.shared.account.ListStatus
import ani.saikou.neo.shared.account.NoticeChange
import ani.saikou.neo.shared.account.ShownNotice
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // Leaving the player, the screen underneath arrives with the notice and takes focus a few frames later. Undo still
    // ends up with it, and moving away goes to what the screen picked.
    @Test
    fun undoKeepsFocusFromAScreenThatArrivesWithIt() {
        shown.value = notice(NoticeChange.Updated(ListStatus.WATCHING, 5, byEpisode = true))
        compose.setContent {
            NeoTheme {
                val screen = remember { FocusRequester() }
                val first = remember { FocusRequester() }
                Box(Modifier.fillMaxSize().focusRequester(screen).focusGroup()) {
                    NeoSurface({}, CircleShape, 1f, Modifier.focusRequester(first).testTag("first")) { Text("First") }
                    LaunchedEffect(Unit) {
                        repeat(3) { withFrameNanos {} }
                        first.requestFocus()
                    }
                    NoticeOverScreens(shown.value, NoticeActions(), screen)
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag(NOTICE_UNDO_TAG).assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("first").assertIsFocused()
    }

    // Leaving the player, what had focus as the notice came can be the player itself, gone a moment later. Moving away
    // from Undo then still lands on the screen, not nowhere.
    @Test
    fun withWhatHadFocusGoneMovingAwayStillLandsOnTheScreen() {
        val playerShowing = mutableStateOf(true)
        compose.setContent {
            NeoTheme {
                val screen = remember { FocusRequester() }
                val player = remember { FocusRequester() }
                Box(Modifier.fillMaxSize().focusRequester(screen).focusGroup()) {
                    if (playerShowing.value) {
                        NeoSurface({}, CircleShape, 1f, Modifier.focusRequester(player).testTag("player")) { Text("Player") }
                        LaunchedEffect(Unit) { player.requestFocus() }
                    } else {
                        NeoSurface({}, CircleShape, 1f, Modifier.testTag("server")) { Text("Server") }
                    }
                    NoticeOverScreens(shown.value, NoticeActions(), screen)
                }
            }
        }
        compose.waitForIdle()
        shown.value = notice(NoticeChange.Updated(ListStatus.WATCHING, 5, byEpisode = true))
        compose.waitForIdle()
        playerShowing.value = false
        compose.waitForIdle()
        compose.onNodeWithTag(NOTICE_UNDO_TAG).assertIsFocused()

        press(Key.Back)

        compose.onNodeWithTag("server").assertIsFocused()
    }

    // Once pressed, Undo has done its job, so focus goes back to the screen straight away.
    @Test
    fun pressingUndoGivesFocusBack() {
        show()
        shown.value = notice(NoticeChange.Updated(ListStatus.WATCHING, 5, byEpisode = true))
        compose.waitForIdle()

        press(Key.DirectionCenter)

        compose.onNodeWithTag("first").assertIsFocused()
        assertEquals(listOf("undo"), pressed)
    }

    @Test
    fun undoTakesFocusAndAnyMoveGivesItBack() {
        show()
        shown.value = notice(NoticeChange.Updated(ListStatus.WATCHING, 5, byEpisode = true))
        compose.waitForIdle()

        compose.onNodeWithTag(NOTICE_UNDO_TAG).assertIsFocused()
        press(Key.DirectionRight)

        compose.onNodeWithTag("first").assertIsFocused()
        compose.onNodeWithTag(NOTICE_TAG).assertExists()
        assertEquals(emptyList<String>(), pressed)
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

    // The app's root Surface stretches its children to the screen; the notice keeps its own size, bottom left.
    @Test
    fun insideTheAppsSurfaceTheNoticeKeepsItsOwnSize() {
        compose.setContent {
            NeoTheme {
                Surface(Modifier.fillMaxSize()) {
                    NoticeOverScreens(notice(NoticeChange.Removed), NoticeActions(), remember { FocusRequester() })
                }
            }
        }
        compose.waitForIdle()

        val bounds = compose.onNodeWithTag(NOTICE_TAG).getUnclippedBoundsInRoot()
        assertEquals(48.dp, bounds.left)
        assertTrue(bounds.right - bounds.left < 600.dp && bounds.bottom - bounds.top < 100.dp)
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
