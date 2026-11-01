package ani.saikou.neo.tv.player

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
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
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.playback.FoundServer
import ani.saikou.neo.shared.playback.FoundStream
import ani.saikou.neo.shared.playback.PlayRequest
import ani.saikou.neo.shared.playback.PlayServer
import ani.saikou.neo.shared.playback.PlayerPhase
import ani.saikou.neo.shared.playback.PlayerState
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val demo = InstalledSource("com.example.demo", "Demo source", "1.0.0")
private val one = PlayServer(demo, FoundServer("1", "Server 1", ServerEdition.SUB, 24), 2)
private val two = PlayServer(demo, FoundServer("2", "Server 2", ServerEdition.DUB, 24), 2)

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

private val request = PlayRequest(show, 7, "Rebirth", listOf(one, two), picked = 0)
private val answered = FoundStream(Stream(listOf(Quality(1080, "https://example.com/1080.m3u8", StreamFormat.Hls))), 1080)

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = mutableStateOf(PlayerState<Stream>(one, PlayerPhase.Starting(null), willTryNext = true))
    private val pressed = mutableListOf<String>()
    private val playback = FakePlayback()
    private val controls = ControlsViewModel()

    private fun show() {
        compose.setContent {
            NeoTheme {
                PlayerScreen(
                    state.value,
                    request,
                    PlayerActions(
                        onTryNext = { pressed += "next" },
                        onTryAgain = { pressed += "again" },
                        onChooseAnother = { pressed += "choose" },
                    ),
                    playback,
                    controls,
                    video = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    @Test
    fun startingSaysWhereItFoundTheServerAndWhoItsAsking() {
        show()

        compose.onNodeWithText("DEATH NOTE").assertExists()
        compose.onNodeWithText("E7 · Rebirth").assertExists()
        compose.onNodeWithText("Found on Demo source · 2 servers").assertExists()
        compose.onNodeWithText("Asking Server 1").assertExists()
        compose.onNodeWithText("If Server 1 fails before the video starts, the next server is tried once.").assertExists()
        compose.onNodeWithTag(PLAYER_CHOOSE_TAG).assertIsFocused()
    }

    @Test
    fun onceTheServerAnswersItBuffers() {
        state.value = state.value.copy(phase = PlayerPhase.Starting(answered))
        show()

        compose.onNodeWithText("Server 1 answered · 1080p Sub").assertExists()
        compose.onNodeWithText("Buffering").assertExists()
    }

    @Test
    fun theNoteGoesOnceTheNextServerWontBeTried() {
        state.value = state.value.copy(willTryNext = false)
        show()

        compose.onNodeWithText("If Server 1 fails before the video starts, the next server is tried once.").assertDoesNotExist()
    }

    @Test
    fun chooseAnotherServerGoesBack() {
        show()

        press(Key.DirectionCenter)

        assertEquals(listOf("choose"), pressed)
    }

    @Test
    fun playingShowsNothingOverTheVideo() {
        state.value = state.value.copy(phase = PlayerPhase.Playing(answered))
        show()

        compose.onNodeWithTag(PLAYER_STARTING_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PLAYER_STOPPED_TAG).assertDoesNotExist()
    }

    @Test
    fun aQuietStartShowsNothingOverTheVideo() {
        state.value = state.value.copy(phase = PlayerPhase.Starting(null, quiet = true))
        show()

        compose.onNodeWithTag(PLAYER_STARTING_TAG).assertDoesNotExist()
    }

    @Test
    fun stoppedSaysWhyAndOffersTheNextServerFirst() {
        state.value = state.value.copy(phase = PlayerPhase.Stopped(FailReason.SourceDown, 503, 724_000, two))
        show()

        compose.onNodeWithText("Server 1 stopped answering").assertExists()
        compose.onNodeWithText("The site isn’t working right now. E7 stopped at 12:04.").assertExists()
        compose.onNodeWithText("Demo source · Server 1 · error 503").assertExists()
        compose.onNodeWithTag(PLAYER_TRY_NEXT_TAG).assertIsFocused()
        compose.onNodeWithText("Try Server 2").assertExists()
        compose.onNodeWithText("Choose another server").assertExists()
        compose.onNodeWithText("Try Server 1 again").assertExists()
    }

    @Test
    fun eachStoppedButtonDoesItsJob() {
        state.value = state.value.copy(phase = PlayerPhase.Stopped(FailReason.SourceDown, null, null, two))
        show()

        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        compose.onNodeWithTag(PLAYER_CHOOSE_TAG).assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        compose.onNodeWithTag(PLAYER_TRY_AGAIN_TAG).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf("next", "choose", "again"), pressed)
    }

    @Test
    fun withoutANextServerChooseAnotherComesFirst() {
        state.value = state.value.copy(phase = PlayerPhase.Stopped(FailReason.NoAnswer, null, null, null))
        show()

        compose.onNodeWithTag(PLAYER_TRY_NEXT_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PLAYER_CHOOSE_TAG).assertIsFocused()
        compose.onNodeWithText("Didn’t answer in 25 s.").assertExists()
        compose.onNodeWithText("Demo source · Server 1").assertExists()
    }

    @Test
    fun aServerSharingItsNameIsToldApartByEdition() {
        val sub = PlayServer(demo, FoundServer("sub", "Demo", ServerEdition.SUB, 24), 2)
        val dub = PlayServer(demo, FoundServer("dub", "Demo", ServerEdition.DUB, 24), 2)
        state.value = PlayerState(sub, PlayerPhase.Stopped(FailReason.Blocked, null, null, dub))
        show()

        compose.onNodeWithText("Try Demo, Dub").assertExists()
    }
}
