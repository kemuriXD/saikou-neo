package ani.saikou.neo.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Whether the player is on screen, and full screen, for the AniList notice (ADR 0056). [playing] tells the notice. When
 * the player moves on, the next episode's player takes the place of the last one, and the two crossfade, so the old one
 * leaves after the new one came. Only the newest speaks for the screen, and the player has left once the last one has.
 */
@Stable
internal class PlayersOnScreen(
    private val playing: (fullScreen: Boolean) -> Unit,
) {
    private val players = mutableListOf<Any>()

    var inPlayer by mutableStateOf(false)
        private set

    fun entered(player: Any) {
        players += player
        inPlayer = true
    }

    fun fullScreen(
        player: Any,
        fullScreen: Boolean,
    ) {
        if (players.lastOrNull() === player) playing(fullScreen)
    }

    fun left(player: Any) {
        players.remove(player)
        if (players.isNotEmpty()) return
        inPlayer = false
        playing(false)
    }
}

/** Tells [players] this player is on screen while it's composed, and whether its video is [fullScreen]. */
@Composable
internal fun OnScreen(
    players: PlayersOnScreen,
    fullScreen: Boolean,
) {
    val player = remember { Any() }
    DisposableEffect(players) {
        players.entered(player)
        onDispose { players.left(player) }
    }
    LaunchedEffect(players, fullScreen) { players.fullScreen(player, fullScreen) }
}
