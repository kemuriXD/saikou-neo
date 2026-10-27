package ani.saikou.neo.tv

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.TabPlaceholder
import ani.saikou.neo.tv.ui.TopTab

/**
 * The app's screens. Home is always at the bottom of the back stack. A show can be opened again further up, such as a
 * season from its prequel's Related row, so each visit has its own [Visited.visit] number to keep its state apart.
 */
sealed interface Screen {
    data object Home : Screen

    data class SeeMore(
        val row: HomeRow,
    ) : Screen

    /** One of the top bar's tabs other than Home, always straight on top of Home. */
    data class Tab(
        val tab: TopTab,
    ) : Screen

    data class Details(
        val animeId: Int,
        override val visit: Int = 0,
    ) : Visited

    /** All the anime recommended for one. */
    data class Recommendations(
        val animeId: Int,
        override val visit: Int = 0,
    ) : Visited

    /** A screen that can be on the back stack more than once. */
    sealed interface Visited : Screen {
        val visit: Int
    }
}

/** A number no screen on [stack] has, for the next one opened. */
private fun nextVisit(stack: List<Screen>) = (stack.filterIsInstance<Screen.Visited>().maxOfOrNull { it.visit } ?: 0) + 1

// Home is implied, so only the screens above it are saved, each as a letter and numbers: See more as "s" and its row's
// position, a tab as "t" and its position, Details as "d" and its anime id and visit, and recommendations as "r" and theirs.
private val BackStackSaver =
    listSaver<SnapshotStateList<Screen>, String>(
        save = { stack ->
            stack.mapNotNull { screen ->
                when (screen) {
                    Screen.Home -> null
                    is Screen.SeeMore -> "s${screen.row.ordinal}"
                    is Screen.Tab -> "t${screen.tab.ordinal}"
                    is Screen.Details -> "d${screen.animeId}.${screen.visit}"
                    is Screen.Recommendations -> "r${screen.animeId}.${screen.visit}"
                }
            }
        },
        restore = { saved ->
            mutableStateListOf<Screen>(Screen.Home).apply {
                addAll(
                    saved.map {
                        val (number, visit) = it.drop(1).split('.').map(String::toInt) + 0
                        when (it.first()) {
                            's' -> Screen.SeeMore(HomeRow.entries[number])
                            't' -> Screen.Tab(TopTab.entries[number])
                            'r' -> Screen.Recommendations(number, visit)
                            else -> Screen.Details(number, visit)
                        }
                    },
                )
            }
        },
    )

/**
 * Moves between [home], a row's [seeMore] page and [details], which can open another show's Details or all of its
 * [recommendations]. Home's top bar opens a [tab], and each tab's bar can open another; Back from any tab goes to Home.
 * Each screen keeps its saved state and ViewModels while it's on the back stack, so Back finds Home as it was left.
 * Back on Home leaves the app.
 */
@Composable
fun NeoNavigation(
    home: @Composable (
        openDetails: (animeId: Int) -> Unit,
        openSeeMore: (row: HomeRow) -> Unit,
        openTab: (tab: TopTab) -> Unit,
    ) -> Unit,
    seeMore: @Composable (row: HomeRow, openDetails: (animeId: Int) -> Unit) -> Unit,
    details: @Composable (animeId: Int, openDetails: (animeId: Int) -> Unit, openRecommendations: (animeId: Int) -> Unit) -> Unit,
    recommendations: @Composable (animeId: Int, openDetails: (animeId: Int) -> Unit) -> Unit = { _, _ -> },
    tab: @Composable (tab: TopTab, openTab: (tab: TopTab) -> Unit) -> Unit = { tab, openTab -> TabPlaceholder(tab, openTab) },
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
                val openDetails: (Int) -> Unit = { animeId -> backStack.add(Screen.Details(animeId, nextVisit(backStack))) }
                // A tab replaces the one open, so moving along the bar never leaves a trail for Back. Home is under them all.
                val openTab: (TopTab) -> Unit = { tab ->
                    if (backStack.lastOrNull() != Screen.Tab(tab)) {
                        if (backStack.lastOrNull() is Screen.Tab) backStack.removeLastOrNull()
                        if (tab != TopTab.HOME) backStack.add(Screen.Tab(tab))
                    }
                }

                // A screen fading out keeps its focus until the next one takes it, which can wait for that screen to load.
                // Only the top screen takes the remote's keys, so a quick press can't move focus on the one left behind.
                @Composable
                fun Screen.TakesKeysOnTop(content: @Composable () -> Unit) =
                    Box(Modifier.onPreviewKeyEvent { it.key != Key.Back && this != backStack.lastOrNull() }) { content() }
                entry<Screen.Home> { it.TakesKeysOnTop { home(openDetails, { row -> backStack.add(Screen.SeeMore(row)) }, openTab) } }
                entry<Screen.Tab> { it.TakesKeysOnTop { tab(it.tab, openTab) } }
                entry<Screen.SeeMore> { it.TakesKeysOnTop { seeMore(it.row, openDetails) } }
                entry<Screen.Details> {
                    it.TakesKeysOnTop {
                        details(
                            it.animeId,
                            openDetails,
                        ) { animeId -> backStack.add(Screen.Recommendations(animeId, nextVisit(backStack))) }
                    }
                }
                entry<Screen.Recommendations> { it.TakesKeysOnTop { recommendations(it.animeId, openDetails) } }
            },
    )
}
