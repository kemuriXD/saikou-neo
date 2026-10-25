package ani.saikou.neo.tv

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.tv.theme.NeoTheme

/** The app's screens. Home is always at the bottom of the back stack. */
sealed interface Screen {
    data object Home : Screen

    data class SeeMore(
        val row: HomeRow,
    ) : Screen

    data class Details(
        val animeId: Int,
    ) : Screen
}

// Home is implied, so only the screens above it are saved: Details as its anime id, which AniList keeps above 0, and
// See more as minus its row's position, less one.
private val BackStackSaver =
    listSaver<SnapshotStateList<Screen>, Int>(
        save = { stack ->
            stack.mapNotNull { screen ->
                when (screen) {
                    Screen.Home -> null
                    is Screen.SeeMore -> -(screen.row.ordinal + 1)
                    is Screen.Details -> screen.animeId
                }
            }
        },
        restore = { saved ->
            mutableStateListOf<Screen>(Screen.Home).apply {
                addAll(saved.map { if (it < 0) Screen.SeeMore(HomeRow.entries[-it - 1]) else Screen.Details(it) })
            }
        },
    )

/**
 * Moves between [home], a row's [seeMore] page and [details]. Each screen keeps its saved state and ViewModels while it's on the back stack,
 * so Back finds Home as it was left. Back on Home leaves the app.
 */
@Composable
fun NeoNavigation(
    home: @Composable (openDetails: (animeId: Int) -> Unit, openSeeMore: (row: HomeRow) -> Unit) -> Unit,
    seeMore: @Composable (row: HomeRow, openDetails: (animeId: Int) -> Unit) -> Unit,
    details: @Composable (animeId: Int) -> Unit,
) {
    val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<Screen>(Screen.Home) }
    // One crossfade each way. The default Back preview shrinks the screen as for a phone's back swipe, which a remote doesn't have.
    val dimensions = NeoTheme.dimensions
    val fade = tween<Float>(dimensions.durationBackdropMillis, easing = dimensions.easeStandard)
    val crossfade = fadeIn(fade) togetherWith fadeOut(fade)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        transitionSpec = { crossfade },
        popTransitionSpec = { crossfade },
        predictivePopTransitionSpec = { crossfade },
        entryProvider =
            entryProvider {
                val openDetails: (Int) -> Unit = { animeId -> backStack.add(Screen.Details(animeId)) }
                entry<Screen.Home> { home(openDetails) { row -> backStack.add(Screen.SeeMore(row)) } }
                entry<Screen.SeeMore> { seeMore(it.row, openDetails) }
                entry<Screen.Details> { details(it.animeId) }
            },
    )
}
