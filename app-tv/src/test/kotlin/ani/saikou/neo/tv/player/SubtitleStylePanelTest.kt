package ani.saikou.neo.tv.player

import androidx.activity.ComponentActivity
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
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.shared.settings.SubtitleSize
import ani.saikou.neo.shared.settings.SubtitleStyle
import ani.saikou.neo.shared.settings.SubtitleStylePart
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val demo = InstalledSource("com.example.demo", "Demo source", "1.0.0")

private fun server(edition: ServerEdition) = PlayServer(demo, FoundServer("1", "Demo", edition, 24), 2)

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

private val answered = FoundStream(Stream(listOf(Quality(1080, "https://example.com/1080.m3u8", StreamFormat.Hls))), 1080)

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubtitleStylePanelTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val playback = FakePlayback()
    private val controls = ControlsViewModel()
    private val steps = mutableListOf<Pair<SubtitleStylePart, Boolean>>()

    private fun show(
        edition: ServerEdition = ServerEdition.SUB,
        style: SubtitleStyle = SubtitleStyle(),
    ) {
        val server = server(edition)
        compose.setContent {
            NeoTheme {
                PlayerScreen(
                    PlayerState(server, PlayerPhase.Playing(answered)),
                    PlayRequest(show, 7, "Rebirth", listOf(server), picked = 0),
                    PlayerActions(),
                    playback,
                    controls,
                    video = {},
                    styling = SubtitleStyling(style) { part, forward -> steps += part to forward },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    // From the picked subtitle: past French and the timing row.
    private fun toStyleRow() {
        press(Key.Menu)
        repeat(3) { press(Key.DirectionDown) }
        compose.onNodeWithTag(PLAYER_PANEL_STYLE_TAG).assertIsFocused()
    }

    @Test
    fun okOpensTheStyleAndLeftAndRightChangeItAtOnce() {
        show(style = SubtitleStyle(size = SubtitleSize.LARGE))
        toStyleRow()
        press(Key.DirectionCenter)

        compose.onNodeWithTag(playerPanelStyleTag(SubtitleStylePart.SIZE)).assertIsFocused()
        compose.onNodeWithText("Large").assertExists()
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        press(Key.DirectionLeft)

        assertEquals(listOf(SubtitleStylePart.SIZE to true, SubtitleStylePart.COLOUR to false), steps)
        assertTrue(playback.calls.isEmpty())
    }

    @Test
    fun backFromTheStyleGoesBackToItsRow() {
        show()
        toStyleRow()
        press(Key.DirectionCenter)
        back()

        compose.onNodeWithTag(PLAYER_PANEL_STYLE_TAG).assertIsFocused()
        assertEquals(1, compose.onAllNodes(hasTestTag(PLAYER_PANEL_TAG)).fetchSemanticsNodes().size)
    }

    @Test
    fun withSubtitlesOffTheRowSaysToTurnThemOn() {
        playback.subtitles = playback.subtitles.map { it.copy(selected = false) }
        show()
        // Focus starts on Off, above English and French.
        press(Key.Menu)
        repeat(4) { press(Key.DirectionDown) }
        compose.onNodeWithTag(PLAYER_PANEL_STYLE_TAG).assertIsFocused()
        compose.onNodeWithText("Turn on subtitles to style them").assertExists()

        press(Key.DirectionCenter)

        compose.onNodeWithTag(PLAYER_PANEL_STYLE_TAG).assertIsFocused()
    }

    @Test
    fun withOnlyBurnedInSubtitlesTheRowSaysTheyArePartOfTheVideo() {
        playback.subtitles = emptyList()
        show(ServerEdition.HARD_SUB)
        press(Key.Menu)

        compose.onNodeWithText("These subtitles are part of the video").assertExists()
    }
}
