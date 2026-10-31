package ani.saikou.neo.tv

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.search.SearchGenre
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoRail
import ani.saikou.neo.tv.ui.Section
import ani.saikou.neo.tv.ui.SectionPlaceholder

/**
 * The app's screens. Home is always at the bottom of the back stack. A show can be opened again further up, such as a
 * season from its prequel's Related row, so each visit has its own [Visited.visit] number to keep its state apart.
 */
sealed interface Screen {
    data object Home : Screen

    data class SeeMore(
        val row: HomeRow,
    ) : Screen

    /** One of the rail's sections other than Home, opened from Home. */
    data class Section(
        val section: ani.saikou.neo.tv.ui.Section,
    ) : Screen

    /** Settings, on Sources, opened from an episode with no sources to play it. */
    data object Sources : Screen

    /** The most popular anime of one genre, opened from Search. */
    data class Genre(
        val genre: SearchGenre,
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
// position, a section as "t" and its position, a genre as "g" and its position, Details as "d" and its anime id and visit, and recommendations as "r" and theirs.
// Settings opened on Sources is "o".
private val BackStackSaver =
    listSaver<SnapshotStateList<Screen>, String>(
        save = { stack ->
            stack.mapNotNull { screen ->
                when (screen) {
                    Screen.Home -> null
                    is Screen.SeeMore -> "s${screen.row.ordinal}"
                    is Screen.Section -> "t${screen.section.ordinal}"
                    Screen.Sources -> "o"
                    is Screen.Genre -> "g${screen.genre.ordinal}"
                    is Screen.Details -> "d${screen.animeId}.${screen.visit}"
                    is Screen.Recommendations -> "r${screen.animeId}.${screen.visit}"
                }
            }
        },
        restore = { saved ->
            mutableStateListOf<Screen>(Screen.Home).apply {
                addAll(
                    saved.map {
                        if (it == "o") return@map Screen.Sources
                        val (number, visit) = it.drop(1).split('.').map(String::toInt) + 0
                        when (it.first()) {
                            's' -> Screen.SeeMore(HomeRow.entries[number])
                            't' -> Screen.Section(Section.entries[number])
                            'g' -> Screen.Genre(SearchGenre.entries[number])
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
 * [recommendations], or Settings on [sources]. The rail down the left of Home opens each [section]; Back from a section goes to
 * Home. [search]
 * opens Details or a [genre]'s page, and [discover] opens Details. Each screen
 * keeps its saved state and ViewModels while it's on the back stack, so Back finds Home as it was left. Back on Home
 * leaves the app.
 */
@Composable
fun NeoNavigation(
    home: @Composable (openDetails: (animeId: Int) -> Unit, openSeeMore: (row: HomeRow) -> Unit) -> Unit,
    seeMore: @Composable (row: HomeRow, openDetails: (animeId: Int) -> Unit) -> Unit,
    details: @Composable (
        animeId: Int,
        openDetails: (animeId: Int) -> Unit,
        openRecommendations: (animeId: Int) -> Unit,
        openSources: () -> Unit,
    ) -> Unit,
    recommendations: @Composable (animeId: Int, openDetails: (animeId: Int) -> Unit) -> Unit = { _, _ -> },
    section: @Composable (section: Section) -> Unit = { SectionPlaceholder(it) },
    search: @Composable (openDetails: (animeId: Int) -> Unit, openGenre: (genre: SearchGenre) -> Unit) -> Unit = { _, _ -> },
    genre: @Composable (genre: SearchGenre, openDetails: (animeId: Int) -> Unit) -> Unit = { _, _ -> },
    discover: @Composable (openDetails: (animeId: Int) -> Unit) -> Unit = { SectionPlaceholder(Section.DISCOVER) },
    sources: @Composable () -> Unit = { section(Section.SETTINGS) },
) {
    val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf<Screen>(Screen.Home) }
    // Takes focus back from the rail to what last had it on Home.
    val homeFocus = remember { FocusRequester() }
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

                // A screen fading out keeps its focus until the next one takes it, which can wait for that screen to load.
                // Only the top screen takes the remote's keys, so a quick press can't move focus on the one left behind.
                @Composable
                fun Screen.TakesKeysOnTop(content: @Composable () -> Unit) =
                    Box(Modifier.onPreviewKeyEvent { it.key != Key.Back && this != backStack.lastOrNull() }) { content() }
                entry<Screen.Home> {
                    // Home remembers what had focus, for the rail to send it back to. The restorer sets how focus
                    // enters the group after it. The rail is part of Home, so it fades in and out with it. When a section that
                    // had focus closes, focus enters Home again from the top left, which is the rail's corner, so it's sent past.
                    it.TakesKeysOnTop {
                        Box(Modifier.focusProperties { onEnter = { homeFocus.requestFocus() } }.focusGroup()) {
                            Box(Modifier.focusRequester(homeFocus).focusRestorer().focusGroup()) {
                                home(openDetails) { row -> backStack.add(Screen.SeeMore(row)) }
                            }
                            NeoRail(onSelect = { backStack.add(Screen.Section(it)) }, onClose = { homeFocus.requestFocus() })
                        }
                    }
                }
                entry<Screen.Section> {
                    it.TakesKeysOnTop {
                        when (it.section) {
                            Section.SEARCH -> search(openDetails) { genre -> backStack.add(Screen.Genre(genre)) }
                            Section.DISCOVER -> discover(openDetails)
                            else -> section(it.section)
                        }
                    }
                }
                entry<Screen.Genre> { it.TakesKeysOnTop { genre(it.genre, openDetails) } }
                entry<Screen.SeeMore> { it.TakesKeysOnTop { seeMore(it.row, openDetails) } }
                entry<Screen.Details> {
                    it.TakesKeysOnTop {
                        details(
                            it.animeId,
                            openDetails,
                            { animeId -> backStack.add(Screen.Recommendations(animeId, nextVisit(backStack))) },
                            { backStack.add(Screen.Sources) },
                        )
                    }
                }
                entry<Screen.Sources> { it.TakesKeysOnTop { sources() } }
                entry<Screen.Recommendations> { it.TakesKeysOnTop { recommendations(it.animeId, openDetails) } }
            },
    )
}
