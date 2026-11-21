package ani.saikou.neo.tv.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.playback.FoundServer
import ani.saikou.neo.shared.playback.PlayRequest
import ani.saikou.neo.shared.playback.PlayServer
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.tv.NeoNavigation
import ani.saikou.neo.tv.home.HomeScreen
import ani.saikou.neo.tv.home.card
import ani.saikou.neo.tv.home.loaded
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val request =
    PlayRequest(
        AnimeDetails(card(0), null, null, 23, emptyList(), emptyList(), null),
        episode = 21,
        episodeTitle = null,
        servers =
            listOf(
                PlayServer(
                    InstalledSource("com.example.demo", "Demo source", "1.0.0"),
                    FoundServer("1", "Server 1", ServerEdition.SUB, 24),
                    1,
                ),
            ),
        picked = 0,
    )

/** The AniList notice follows the player on screen, also when the next episode's player takes the place of the last one. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class PlayersOnScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val told = mutableListOf<Boolean>()
    private val players = PlayersOnScreen { told += it }

    private fun show() =
        compose.setContent {
            NeoTheme {
                NeoNavigation(
                    home = { openDetails, openSeeMore -> HomeScreen(loaded, onOpen = openDetails, onSeeMore = openSeeMore) },
                    seeMore = { _, _ -> },
                    details = { _, _, _, _, openPlayer ->
                        val play = remember { FocusRequester() }
                        LaunchedEffect(Unit) { play.requestFocus() }
                        Button(onClick = { openPlayer(request) }, modifier = Modifier.focusRequester(play).testTag("play")) {
                            Text("Play")
                        }
                    },
                    player = { playing, _, playNext ->
                        OnScreen(players, fullScreen = true)
                        val next = remember { FocusRequester() }
                        LaunchedEffect(Unit) { next.requestFocus() }
                        Column {
                            Text("Playing E${playing.episode}")
                            Button(
                                onClick = { playNext(playing.copy(episode = playing.episode + 1)) },
                                modifier = Modifier.focusRequester(next),
                            ) { Text("Next") }
                        }
                    },
                )
            }
        }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun openPlayer() {
        show()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("play").assertExists()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Playing E21").assertExists()
    }

    @Test
    fun thePlayerIsFullScreenWhileItPlays() {
        openPlayer()

        assertTrue(players.inPlayer)
        assertEquals(true, told.last())
    }

    @Test
    fun theNextEpisodesPlayerKeepsTheNoticeWaiting() {
        openPlayer()

        press(Key.DirectionCenter)
        compose.onNodeWithText("Playing E22").assertExists()
        compose.onNodeWithText("Playing E21").assertDoesNotExist()

        assertTrue(players.inPlayer)
        assertEquals(true, told.last())
    }

    @Test
    fun leavingThePlayerAfterItMovedOnLetsTheNoticeShow() {
        openPlayer()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Playing E22").assertExists()

        back()

        compose.onNodeWithText("Playing E22").assertDoesNotExist()
        assertFalse(players.inPlayer)
        assertEquals(false, told.last())
    }
}
