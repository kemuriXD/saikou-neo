package ani.saikou.neo.tv.player

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.extension.api.Quality
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.extension.api.StreamFormat
import ani.saikou.neo.shared.catalogue.AniZipEpisode
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.catalogue.EpisodeKind
import ani.saikou.neo.shared.details.EpisodeList
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.shared.playback.ControlsViewModel
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.playback.FoundServer
import ani.saikou.neo.shared.playback.FoundStream
import ani.saikou.neo.shared.playback.PanelTab
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
import kotlin.time.Duration.Companion.minutes

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
    override val audio = listOf(TrackOption(0, 0, "Japanese", selected = true), TrackOption(1, 0, null, selected = false))
    override val subtitles = listOf(TrackOption(2, 0, "English", selected = true), TrackOption(3, 0, "French", selected = false))
    override var speed by mutableFloatStateOf(1f)

    override var subtitleDelayMs by mutableIntStateOf(0)

    override fun changeSubtitleDelay(delayMs: Int) {
        subtitleDelayMs = delayMs
    }

    override fun selectTrack(option: TrackOption) {
        calls += "track ${option.label}"
    }

    override fun subtitlesOff() {
        calls += "subtitles off"
    }

    override fun changeSpeed(speed: Float) {
        calls += "speed $speed"
        this.speed = speed
    }

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

    private fun panelOpen() = compose.onAllNodes(hasTestTag(PLAYER_PANEL_TAG)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun menuOpensThePanelOnThePickedSubtitleWithoutPausing() {
        show()
        press(Key.Menu)

        assertTrue(panelOpen())
        assertFalse(controlsShown())
        assertEquals(emptyList<String>(), playback.calls)
        compose.onNodeWithText("English").assertIsFocused()
    }

    private fun episodes(
        count: Int = 30,
        kinds: Map<Int, EpisodeKind> = emptyMap(),
    ) = EpisodeList(
        count = count,
        countSoFar = false,
        film = false,
        episodes = (1..count).map { AniZipEpisode(it, "Title $it", null, null, null, 24) },
        extras = emptyList(),
        kinds = kinds,
    )

    private fun showDrawer(
        list: EpisodeList? = episodes(),
        progress: WatchProgress? = null,
        picks: MutableList<Int> = mutableListOf(),
    ) {
        compose.setContent {
            NeoTheme {
                PlayerScreen(
                    PlayerState(sub, PlayerPhase.Playing(answered)),
                    request.copy(episodes = list, progress = progress),
                    PlayerActions(onEpisode = { picks += it }),
                    playback,
                    controls,
                    video = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun drawerOpen() = compose.onAllNodes(hasTestTag(PLAYER_EPISODES_TAG)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun upOpensTheDrawerOnThePlayingEpisodeAndBackClosesIt() {
        showDrawer()
        press(Key.DirectionUp)

        assertTrue(drawerOpen())
        compose.onNodeWithTag(drawerEpisodeTag(7)).assertIsFocused()
        assertEquals(emptyList<String>(), playback.calls)

        back()
        assertFalse(drawerOpen())
        assertFalse(controlsShown())
    }

    @Test
    fun upFromTheBarOpensTheDrawerAndBackReturnsToTheBar() {
        showDrawer()
        press(Key.DirectionDown)
        compose.onNodeWithTag(PLAYER_BAR_TAG).assertIsFocused()
        press(Key.DirectionUp)
        assertTrue(drawerOpen())

        back()
        assertTrue(controlsShown())
        compose.onNodeWithTag(PLAYER_BAR_TAG).assertIsFocused()
    }

    @Test
    fun theEpisodesButtonOpensTheDrawerAndGetsFocusBack() {
        showDrawer()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        compose.onNodeWithText("Episodes").assertIsFocused()
        press(Key.DirectionCenter)
        assertTrue(drawerOpen())

        back()
        compose.onNodeWithText("Episodes").assertIsFocused()
    }

    @Test
    fun theDrawerOpensOnTheRangeOfThePlayingEpisode() {
        showDrawer()
        press(Key.DirectionUp)

        compose.onNodeWithText("1–12").assertIsSelected()
        compose.onNodeWithTag(drawerEpisodeTag(7)).assertIsFocused()
    }

    @Test
    fun theDrawerMarksWatchedPlayingAndFiller() {
        val progress = WatchProgress(5, 12.minutes, 24.minutes, null)
        showDrawer(episodes(kinds = mapOf(8 to EpisodeKind.Filler)), progress)
        press(Key.DirectionUp)

        compose.onNodeWithTag(drawerEpisodeTag(7)).assert(hasText("Playing now", substring = true))
        compose.onNodeWithTag(drawerEpisodeTag(8)).assert(hasText("Filler · 24 min"))
        compose.onNodeWithTag(drawerEpisodeTag(6)).assert(!hasText("Filler", substring = true))
    }

    @Test
    fun pickingAnotherEpisodeAsksForItAndPickingThePlayingOneJustCloses() {
        val picks = mutableListOf<Int>()
        showDrawer(picks = picks)
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertFalse(drawerOpen())
        assertEquals(emptyList<Int>(), picks)

        press(Key.DirectionUp)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(listOf(8), picks)
    }

    @Test
    fun withNoOtherEpisodeUpDoesNothing() {
        showDrawer(episodes(count = 1))
        press(Key.DirectionUp)
        assertFalse(drawerOpen())
    }

    private fun showForPicking(
        calls: MutableList<String>,
        stream: FoundStream<Stream> = answered.copy(heights = listOf(1080, 720, 480)),
    ) {
        val other = PlayServer(demo, FoundServer("2", "Demo, Dub", ServerEdition.DUB, 24), 2)
        compose.setContent {
            NeoTheme {
                PlayerScreen(
                    PlayerState(sub, PlayerPhase.Playing(stream)),
                    request.copy(servers = listOf(sub, other)),
                    PlayerActions(
                        onQuality = { height, at -> calls += "quality $height at $at" },
                        onAudio = { audio, at -> calls += "audio $audio at $at" },
                        onServer = { server, at -> calls += "server ${server.server.id} at $at" },
                    ),
                    playback,
                    controls,
                    video = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun qualityListsEveryHeightWithNoAutoAndPicksFromTheSameSpot() {
        val calls = mutableListOf<String>()
        showForPicking(calls)
        controls.openPanel(PanelTab.Quality)
        compose.waitForIdle()

        compose.onNodeWithText("1080p").assertExists()
        compose.onNodeWithText("720p").assertExists()
        compose.onNodeWithText("480p").assertExists()
        assertEquals(0, compose.onAllNodesWithText("Auto").fetchSemanticsNodes().size)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertEquals(listOf("quality 720 at 724000"), calls)
    }

    @Test
    fun serverListsEachOneAndPicksFromTheSameSpot() {
        val calls = mutableListOf<String>()
        showForPicking(calls)
        controls.openPanel(PanelTab.Server)
        compose.waitForIdle()

        compose.onNodeWithText("Demo source · Demo, Sub · Sub").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertEquals(listOf("server 2 at 724000"), calls)
    }

    @Test
    fun audioServedApartIsListedFromTheStream() {
        val calls = mutableListOf<String>()
        showForPicking(calls, answered.copy(audio = listOf("Korean", "Hindi")))
        controls.openPanel(PanelTab.Tracks)
        compose.waitForIdle()

        assertEquals(0, compose.onAllNodesWithText("Japanese").fetchSemanticsNodes().size)
        press(Key.DirectionLeft)
        compose.onNodeWithText("Hindi").assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf("audio 1 at 724000"), calls)
    }

    @Test
    fun subtitleTimingMovesEarlierAndLaterAndOkPutsItBack() {
        show()
        press(Key.Menu)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag(PLAYER_PANEL_TIMING_TAG).assertIsFocused()
        compose.onNodeWithText("0.0 s").assertExists()

        press(Key.DirectionRight)
        press(Key.DirectionRight)
        assertEquals(200, playback.subtitleDelayMs)
        compose.onNodeWithText("+0.2 s").assertExists()

        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        assertEquals(-100, playback.subtitleDelayMs)
        compose.onNodeWithTag(PLAYER_PANEL_TIMING_TAG).assertIsFocused()

        press(Key.DirectionCenter)
        assertEquals(0, playback.subtitleDelayMs)
    }

    @Test
    fun aServerStoppingClosesThePanel() {
        var phase by mutableStateOf<PlayerPhase<Stream>>(PlayerPhase.Playing(answered))
        compose.setContent {
            NeoTheme {
                PlayerScreen(PlayerState(sub, phase), request, PlayerActions(), playback, controls, video = {})
            }
        }
        compose.waitForIdle()
        press(Key.Menu)
        assertTrue(panelOpen())

        phase = PlayerPhase.Stopped(FailReason.SourceDown, null, 1_000, null)
        compose.waitForIdle()

        assertEquals(null, controls.state.value.panel)
    }

    @Test
    fun menuAgainClosesThePanel() {
        show()
        press(Key.Menu)
        press(Key.Menu)

        assertFalse(panelOpen())
    }

    @Test
    fun backClosesThePanelLeavingTheVideo() {
        show()
        press(Key.Menu)
        back()

        assertFalse(panelOpen())
        assertFalse(controlsShown())
    }

    @Test
    fun picksASubtitleAndStaysOpen() {
        show()
        press(Key.Menu)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        assertEquals(listOf("track French"), playback.calls)
        assertTrue(panelOpen())
    }

    @Test
    fun offTurnsSubtitlesOff() {
        show()
        press(Key.Menu)
        press(Key.DirectionUp)
        compose.onNodeWithText("Off").assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf("subtitles off"), playback.calls)
    }

    @Test
    fun leftMovesToAudioWithoutSeekingAndNamesAnUnnamedTrack() {
        show()
        press(Key.Menu)
        press(Key.DirectionLeft)
        compose.onNodeWithText("Track 2").assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf("track null"), playback.calls)
    }

    @Test
    fun leftAndRightPastTheColumnsMoveBetweenTabs() {
        show()
        press(Key.Menu)
        press(Key.DirectionRight)
        compose.onNodeWithText("1080p").assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithText("English").assertIsFocused()
        assertEquals(emptyList<String>(), playback.calls)
    }

    @Test
    fun upFromTheTopOfAListGoesToItsTabWithoutChangingIt() {
        show()
        press(Key.Menu)
        press(Key.DirectionUp)
        press(Key.DirectionUp)

        compose.onNodeWithTag(playerPanelTabTag(PanelTab.Tracks)).assertIsFocused()
        compose.onNodeWithText("Off").assertExists()
    }

    @Test
    fun changingSpeedShowsOnTheButton() {
        show()
        press(Key.Menu)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(listOf("speed 1.25"), playback.calls)
        back()
        press(Key.DirectionDown)

        compose.onNodeWithText("1.25×").assertExists()
    }

    @Test
    fun aButtonOpensItsTabAndBackReturnsToIt() {
        show()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithText("1×").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(PanelTab.Speed, controls.state.value.panel)

        back()
        assertFalse(panelOpen())
        assertTrue(controlsShown())
        compose.onNodeWithText("1×").assertIsFocused()
    }

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
    fun downFromTheBarLandsOnPlayAndFocusPassesOverDimmedButtons() {
        show()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag(PLAYER_PLAY_PAUSE_TAG).assertIsFocused()

        press(Key.DirectionRight)
        compose.onNodeWithText("Episodes").assertExists()
        compose.onNodeWithText("Audio and subtitles").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithText("1080p").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithText("1×").assertIsFocused()
    }

    @Test
    fun upFromPlayAndPauseGoesBackToTheBar() {
        show()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionUp)

        compose.onNodeWithTag(PLAYER_BAR_TAG).assertIsFocused()
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
