package ani.saikou.neo.tv.player

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.extension.api.Quality
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.extension.api.StreamFormat
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.playback.ControlsViewModel
import ani.saikou.neo.shared.playback.FoundServer
import ani.saikou.neo.shared.playback.FoundStream
import ani.saikou.neo.shared.playback.PlayRequest
import ani.saikou.neo.shared.playback.PlayServer
import ani.saikou.neo.shared.playback.PlayerPhase
import ani.saikou.neo.shared.playback.PlayerState
import ani.saikou.neo.shared.playback.Segment
import ani.saikou.neo.shared.playback.SegmentKind
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val demo = InstalledSource("com.example.demo", "Demo source", "1.0.0")
private val sub = PlayServer(demo, FoundServer("1", "Demo, Sub", ServerEdition.SUB, 24), 2)

private val show =
    AnimeDetails(
        card = AnimeCard(1535, AnimeTitle("Death Note", null, null), null, "#e4a15d", null, "TV", 37, null, null, null),
        description = null,
        status = null,
        episodeMinutes = 23,
        genres = emptyList(),
        studios = emptyList(),
        nextEpisode = null,
    )

private val request = PlayRequest(show, 7, "Rebirth", listOf(sub), picked = 0)
private val answered = FoundStream(Stream(listOf(Quality(1080, "https://example.com/1080.m3u8", StreamFormat.Hls))), 1080)

/** A video at 12:04 of 23:40 that notes what it's told. */
internal class FakePlayback : Playback {
    val calls = mutableListOf<String>()
    override var positionMs by mutableLongStateOf(724_000L)
    override val durationMs = 1_420_000L
    override var playing by mutableStateOf(true)

    override fun play() {
        calls += "play"
        playing = true
    }

    override fun pause() {
        calls += "pause"
        playing = false
    }

    override fun seekTo(positionMs: Long) {
        calls += "seek $positionMs"
        this.positionMs = positionMs
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerControlsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val playback = FakePlayback()
    private val controls = ControlsViewModel()

    private fun show(segments: List<Segment> = emptyList()) {
        compose.setContent {
            NeoTheme {
                PlayerScreen(
                    PlayerState(sub, PlayerPhase.Playing(answered), segments = segments),
                    request,
                    PlayerActions(),
                    playback,
                    controls,
                    video = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun hold(key: Key) {
        compose.onRoot().performKeyInput {
            keyDown(key)
            advanceEventTime(1_500)
            keyUp(key)
        }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun controlsShown() = compose.onAllNodes(hasTestTag(PLAYER_CONTROLS_TAG)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun nothingShowsOverTheVideoAtFirst() {
        show()
        assertFalse(controlsShown())
    }

    @Test
    fun okPausesAndShowsTheControlsOnTheBar() {
        show()
        press(Key.DirectionCenter)

        assertEquals(listOf("pause"), playback.calls)
        assertTrue(controlsShown())
        compose.onNodeWithTag(PLAYER_BAR_TAG).assertIsFocused()
    }

    @Test
    fun okOnTheBarPlaysAgain() {
        show()
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)

        assertEquals(listOf("pause", "play"), playback.calls)
    }

    @Test
    fun downShowsTheControlsWithoutPausing() {
        show()
        press(Key.DirectionDown)

        assertTrue(controlsShown())
        assertEquals(emptyList<String>(), playback.calls)
    }

    @Test
    fun leftAndRightSeekTenSecondsAndAddUp() {
        show()
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)

        assertEquals(listOf("seek 714000", "seek 704000"), playback.calls)
        assertFalse(controlsShown())
        compose.onNodeWithText("−20 s").assertExists()

        press(Key.DirectionRight)
        assertEquals("seek 714000", playback.calls.last())
    }

    @Test
    fun theBarShowsTheTitleTimeAndWhenItEnds() {
        show()
        press(Key.DirectionDown)

        compose.onNodeWithText("DEATH NOTE").assertExists()
        compose.onNodeWithText("E7 · Rebirth").assertExists()
        compose.onNodeWithText("Demo source · Demo, Sub · 1080p · Sub").assertExists()
        compose.onNodeWithText("12:04 / 23:40").assertExists()
        compose.onNodeWithText("Ends ", substring = true).assertExists()
    }

    @Test
    fun knownSegmentsAreNamedStretches() {
        show(listOf(Segment(SegmentKind.Recap, 0, 70_000), Segment(SegmentKind.Opening, 70_000, 160_000)))
        press(Key.DirectionDown)

        compose.onNodeWithText("Recap").assertExists()
        compose.onNodeWithText("Opening").assertExists()
        compose.onNodeWithText("12:04 / 23:40 · Episode").assertExists()
    }

    @Test
    fun onlyPlayAndPauseTakesFocusInTheRow() {
        show()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag(PLAYER_PLAY_PAUSE_TAG).assertIsFocused()

        press(Key.DirectionRight)
        compose.onNodeWithTag(PLAYER_PLAY_PAUSE_TAG).assertIsFocused()
        compose.onNodeWithText("Episodes").assertExists()
        compose.onNodeWithText("Audio and subtitles").assertExists()
    }

    @Test
    fun backHidesTheControls() {
        show()
        press(Key.DirectionDown)
        back()

        assertFalse(controlsShown())
    }

    @Test
    fun holdingRightScrubsAndOkJumpsThere() {
        show()
        hold(Key.DirectionRight)

        assertTrue(controlsShown())
        compose.onNodeWithTag(PLAYER_SCRUB_TAG).assertExists()
        val target =
            controls.state.value.scrub!!
                .targetMs
        assertTrue(target > 734_000)

        press(Key.DirectionCenter)
        assertEquals("seek $target", playback.calls.last())
        compose.onNodeWithTag(PLAYER_SCRUB_TAG).assertDoesNotExist()
    }

    @Test
    fun backWhileScrubbingStaysWhereYouWere() {
        show()
        hold(Key.DirectionLeft)
        val before = playback.calls.toList()
        compose.onNodeWithText("stay at 12:04", substring = true).assertExists()

        back()

        assertEquals(before, playback.calls)
        compose.onNodeWithTag(PLAYER_SCRUB_TAG).assertDoesNotExist()
        assertTrue(controlsShown())
    }
}
