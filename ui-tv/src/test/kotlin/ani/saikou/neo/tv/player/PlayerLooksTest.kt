package ani.saikou.neo.tv.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
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
import ani.saikou.neo.shared.settings.BuiltInLook
import ani.saikou.neo.shared.settings.LookId
import ani.saikou.neo.shared.settings.PlayerSubtitles
import ani.saikou.neo.shared.settings.SubtitleSettings
import ani.saikou.neo.shared.settings.drawingFor
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

private val source = InstalledSource("com.example.demo", "Demo source", "1.0.0")
private val subServer = PlayServer(source, FoundServer("1", "Demo, Sub", ServerEdition.SUB, 24), 2)
private val hardSubServer = PlayServer(source, FoundServer("2", "Demo, Hard sub", ServerEdition.HARD_SUB, 24), 2)
private val card = AnimeCard(1535, AnimeTitle("Death Note", null, null), null, "#e4a15d", null, "TV", 37, null, null, null)
private val details = AnimeDetails(card, null, null, 23, emptyList(), emptyList(), null)
private val stream = FoundStream(Stream(listOf(Quality(1080, "https://example.com/1080.m3u8", StreamFormat.Hls))), 1080)

private val boxed = LookId.BuiltIn(BuiltInLook.BOXED)
private val softShadow = LookId.BuiltIn(BuiltInLook.SOFT_SHADOW)

private fun subtitles(
    styledTrack: Boolean = false,
    myLookOnTrack: Boolean = false,
): PlayerSubtitles {
    val settings = SubtitleSettings()
    return PlayerSubtitles(settings, settings.pickFor(card.id), null, styledTrack, myLookOnTrack, settings.drawingFor(card.id))
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerLooksTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val playback = FakePlayback()
    private val controls = ControlsViewModel()
    private val calls = mutableListOf<String>()
    private val actions =
        LookActions(
            preview = { calls += "preview $it" },
            keep = { calls += "keep" },
            changeSize = { calls += "size $it" },
            changePosition = { calls += "position $it" },
            useMyLook = { calls += "my look $it" },
        )

    private fun show(
        server: PlayServer = subServer,
        looks: PlayerSubtitles = subtitles(),
    ) {
        val request = PlayRequest(details, 7, null, listOf(server), picked = 0)
        compose.setContent {
            NeoTheme {
                PlayerScreen(
                    PlayerState(server, PlayerPhase.Playing(stream)),
                    request,
                    PlayerActions(),
                    playback,
                    controls,
                    video = {},
                    looks = Looks(looks, actions),
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

    private fun openLooks() {
        press(Key.Menu)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag(PLAYER_PANEL_LOOK_TAG).assertIsFocused()
        press(Key.DirectionCenter)
    }

    @Test
    fun theLookRowNamesTheLookAndOpensTheLooksOnIt() {
        show()
        openLooks()
        back()
        compose.onNodeWithText("Classic").assertExists()

        press(Key.DirectionCenter)

        assertTrue(controls.state.value.looks)
        compose.onNodeWithTag(lookTileTag(LookId.BuiltIn(BuiltInLook.CLASSIC))).assertIsFocused()
    }

    @Test
    fun aFocusedTileShowsOnTheVideoAndOkKeepsIt() {
        show()
        openLooks()

        press(Key.DirectionRight)
        compose.onNodeWithTag(lookTileTag(softShadow)).assertIsFocused()
        assertEquals("preview $softShadow", calls.last())
        compose.onNodeWithText("use Soft shadow").assertExists()

        press(Key.DirectionCenter)
        assertEquals("keep", calls.last())
    }

    @Test
    fun backPutsTheSavedLookBackAndReturnsToTheRow() {
        show()
        openLooks()
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        assertEquals("preview $boxed", calls.last())

        back()

        assertEquals("preview null", calls.last())
        assertFalse(controls.state.value.looks)
        compose.onNodeWithTag(PLAYER_PANEL_LOOK_TAG).assertIsFocused()
    }

    @Test
    fun leftAndRightChangeTheSizeAndThePosition() {
        show()
        openLooks()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag(PLAYER_LOOK_SIZE_TAG).assertIsFocused()
        assertEquals("preview null", calls.last())

        press(Key.DirectionRight)
        press(Key.DirectionLeft)
        press(Key.DirectionDown)
        press(Key.DirectionLeft)

        assertEquals(listOf("size 1", "size -1", "position -1"), calls.filterNot { it.startsWith("preview") })
    }

    @Test
    fun withSubtitlesOffTheRowIsGreyedAndSaysWhy() {
        playback.subtitles = playback.subtitles.map { it.copy(selected = false) }
        show()
        press(Key.Menu)

        compose.onNodeWithText("Turn on subtitles to style them").assertExists()
        compose.onNodeWithTag(PLAYER_PANEL_LOOK_TAG).assertIsNotEnabled()
    }

    @Test
    fun subtitlesInThePictureGreyTheLookAndTheTiming() {
        playback.subtitles = emptyList()
        show(server = hardSubServer)
        press(Key.Menu)

        compose.onNodeWithText("These subtitles are part of the video").assertExists()
        compose.onNodeWithText("Subtitles in the video can’t be moved").assertExists()
        compose.onNodeWithTag(PLAYER_PANEL_LOOK_TAG).assertIsNotEnabled()
    }

    @Test
    fun aStyledTrackGreysTheTilesStartsOnSizeAndCanTakeMyLook() {
        show(looks = subtitles(styledTrack = true))
        openLooks()

        compose
            .onNodeWithText(
                "This track has its own styling, including signs. It keeps its look. Your size still applies.",
            ).assertExists()
        compose.onNodeWithTag(lookTileTag(boxed)).assertIsNotEnabled()
        compose.onNodeWithTag(PLAYER_LOOK_SIZE_TAG).assertIsFocused()

        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals("my look true", calls.last())
    }
}
