package ani.saikou.neo.tv.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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

// ADR 0056: over every screen but the player, Undo shows focused as the notice comes, without taking focus from the
// screen. OK presses it, and any D-pad key or Back takes the focus off it, leaving focus where it was.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AniListNoticeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val shown = mutableStateOf<ShownNotice?>(null)
    private val pressed = mutableListOf<String>()
    private val undoFocus = mutableListOf<Boolean>()
    private val planning = ListEntry(ListStatus.PLANNING, 0, hasStartDate = false)

    private fun notice(
        change: NoticeChange,
        before: ListEntry? = planning,
    ) = ShownNotice(ListNotice(101, 1, AnimeTitle("Romaji", "English", null), change, before))

    private fun show() {
        compose.setContent {
            NeoTheme {
                val keys = remember { NoticeKeys() }
                val first = remember { FocusRequester() }
                Box(Modifier.fillMaxSize().onPreviewKeyEvent { keys.onKey(it) }) {
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
                    NoticeOverScreens(
                        shown.value,
                        NoticeActions(onUndo = { pressed += "undo" }, onUndoFocused = { undoFocus += it }),
                        keys,
                    )
                }
            }
        }
        compose.waitForIdle()
        shown.value = notice(NoticeChange.Updated(ListStatus.WATCHING, 5, byEpisode = true))
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun undoShowsFocusedAndTheScreenKeepsItsFocus() {
        show()

        compose.onNodeWithTag(NOTICE_UNDO_TAG).assertIsSelected()
        compose.onNodeWithTag("first").assertIsFocused()
        assertEquals(true, undoFocus.last())
    }

    @Test
    fun okPressesUndoAndFocusIsStillWhereItWas() {
        show()

        press(Key.DirectionCenter)

        assertEquals(listOf("undo"), pressed)
        compose.onNodeWithTag("first").assertIsFocused()
        compose.onNodeWithTag(NOTICE_UNDO_TAG).assertIsNotSelected()
    }

    // The key only takes the focus off Undo, so focus doesn't jump along with it. The notice stays until it times out.
    @Test
    fun aDpadKeyTakesTheFocusOffUndoAndGoesNoFurther() {
        show()

        press(Key.DirectionRight)

        compose.onNodeWithTag("first").assertIsFocused()
        compose.onNodeWithTag(NOTICE_UNDO_TAG).assertIsNotSelected()
        compose.onNodeWithTag(NOTICE_TAG).assertExists()
        assertEquals(false, undoFocus.last())

        press(Key.DirectionRight)
        compose.onNodeWithTag("second").assertIsFocused()
        assertEquals(emptyList<String>(), pressed)
    }

    @Test
    fun backTakesTheFocusOffUndoWithoutGoingBack() {
        show()

        press(Key.Back)

        compose.onNodeWithTag(NOTICE_UNDO_TAG).assertIsNotSelected()
        assertEquals(emptyList<String>(), pressed)
    }

    @Test
    fun aFailedUndoShowsNoUndo() {
        show()
        shown.value = shown.value?.copy(failed = true)
        compose.waitForIdle()

        compose.onNodeWithText("Couldn\'t undo English").assertExists()
        compose.onNodeWithTag(NOTICE_UNDO_TAG).assertDoesNotExist()
        press(Key.DirectionRight)
        compose.onNodeWithTag("second").assertIsFocused()
    }

    // #512: a mark that went back says it wasn't saved, with nothing to undo.
    @Test
    fun aMarkThatWasntSavedSaysSoWithNoUndo() {
        show()
        shown.value = notice(NoticeChange.NotSaved(5, watched = true), before = null)
        compose.waitForIdle()

        compose.onNodeWithText("English · episode 5 not marked watched").assertExists()
        compose.onNodeWithText("Not saved. AniList changed since, or can\'t be reached").assertExists()
        compose.onNodeWithTag(NOTICE_UNDO_TAG).assertDoesNotExist()
        press(Key.DirectionRight)
        compose.onNodeWithTag("second").assertIsFocused()

        shown.value = notice(NoticeChange.NotSaved(3, watched = false), before = null)
        compose.waitForIdle()
        compose.onNodeWithText("English · episode 3 not marked unwatched").assertExists()
    }

    // The app's root Surface stretches its children to the screen; the notice keeps its own size, bottom left.
    @Test
    fun insideTheAppsSurfaceTheNoticeKeepsItsOwnSize() {
        compose.setContent {
            NeoTheme {
                Surface(Modifier.fillMaxSize()) {
                    NoticeOverScreens(notice(NoticeChange.Removed), NoticeActions(), remember { NoticeKeys() })
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

        compose.onNodeWithText("English · Watching, episode 1 on AniList").assertExists()
        compose.onNodeWithText("It wasn\'t on your list").assertExists()
    }

    // #482: watching episode 1 of a completed show starts a rewatch, and the notice says so.
    @Test
    fun anEpisodeThatChangesTheStatusNamesTheNewOne() {
        show()
        shown.value =
            notice(NoticeChange.Updated(ListStatus.REWATCHING, 1, byEpisode = true), before = ListEntry(ListStatus.COMPLETED, 26, true))
        compose.waitForIdle()

        compose.onNodeWithText("English · Rewatching, episode 1 on AniList").assertExists()
        compose.onNodeWithText("It was Completed, episode 26").assertExists()
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
