package ani.saikou.neo.tv.details

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.details.DetailsState
import ani.saikou.neo.tv.NeoNavigation
import ani.saikou.neo.tv.home.HomeScreen
import ani.saikou.neo.tv.home.card
import ani.saikou.neo.tv.home.loaded
import ani.saikou.neo.tv.theme.NeoColors
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(state: DetailsState) {
        compose.setContent { NeoTheme { DetailsScreen(state) } }
    }

    /** Inside the app's navigation, opened from Home, as on the TV. */
    private fun showFromHome(state: DetailsState) {
        compose.setContent {
            NeoTheme {
                NeoNavigation(
                    home = { openDetails -> HomeScreen(loaded, onOpen = openDetails) },
                    details = { DetailsScreen(state) },
                )
            }
        }
        press(Key.DirectionCenter)
        compose.waitForIdle()
    }

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    private fun back() {
        compose.waitForIdle()
        // Through the window, as the remote's Back key arrives.
        Espresso.pressBack()
        compose.waitForIdle()
    }

    @Test
    fun theTopSaysWhatTheAnimeIs() {
        show(DetailsState.Loaded(details))

        compose.onNodeWithText("TV · FALL 2026").assertExists()
        compose.onNodeWithText("Show 7").assertExists()
        compose.onNodeWithText("Drama · Mystery · 12 episodes").assertExists()
    }

    @Test
    fun theOverviewHasASynopsisPreviewAndTheFacts() {
        show(DetailsState.Loaded(details))

        compose.onNodeWithText("Overview").assertExists()
        compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).assertIsFocused()
        compose.onNodeWithText("Fall 2026").assertExists()
        compose.onNodeWithText("OLM").assertExists()
        compose.onNodeWithText("8.5").assertExists()
        // The editors' note waits for the reader.
        compose.onNodeWithText("Streamed early.").assertDoesNotExist()
    }

    @Test
    fun selectingTheSynopsisOpensTheWholeOfIt() {
        show(DetailsState.Loaded(details))

        press(Key.DirectionCenter)

        compose.onNodeWithTag(DETAILS_READER_TAG).assertIsFocused()
        // The preview stays underneath, so look for the story inside the reader.
        compose
            .onNode(
                hasText("Maomao returns to the pleasure district.\n\nOne day, she senses an anomaly.") and
                    hasAnyAncestor(hasTestTag(DETAILS_READER_TAG)),
            ).assertExists()
        compose.onNodeWithText("Streamed early.").assertExists()
        compose.onNodeWithText("Source: Crunchyroll").assertExists()
    }

    @Test
    fun backClosesTheReaderAndReturnsToTheSynopsis() {
        showFromHome(DetailsState.Loaded(details))

        press(Key.DirectionCenter)
        back()

        compose.onNodeWithTag(DETAILS_READER_TAG).assertDoesNotExist()
        compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).assertIsFocused()
        // Focused is drawn in paper. It once had focus but drew unfocused, as it missed the focus event.
        val preview = compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).captureToImage().toPixelMap()
        assertEquals(NeoColors().paper, preview[preview.width / 2, 4])
    }

    @Test
    fun leavesOutWhatAniListDoesNotKnow() {
        val bare = card(7).copy(format = null, episodes = null, season = null, averageScore = null)
        show(DetailsState.Loaded(details.copy(card = bare, genres = emptyList(), studios = emptyList(), description = null)))

        // The year alone, both at the top and in the facts.
        compose.onAllNodesWithText("2026").assertCountEquals(2)
        compose.onNodeWithTag(DETAILS_META_TAG).assertDoesNotExist()
        compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).assertDoesNotExist()
        compose.onNodeWithText("Score").assertDoesNotExist()
    }

    @Test
    fun theLayoutFollowsTheDesign() {
        show(DetailsState.Loaded(details))

        compose
            .onNodeWithText("TV · FALL 2026")
            .assertTopPositionInRootIsEqualTo(90.dp)
            .assertLeftPositionInRootIsEqualTo(58.dp)
        compose
            .onNodeWithText("Overview")
            .assertTopPositionInRootIsEqualTo(300.dp)
            .assertLeftPositionInRootIsEqualTo(58.dp)
    }

    @Test
    fun loadingShowsPlaceholdersInsteadOfText() {
        show(DetailsState.Loading)

        compose.onNodeWithTag(DETAILS_LOADING_TAG).assertExists()
        compose.onNodeWithText("Show 7").assertDoesNotExist()
    }

    @Test
    fun aFailedLoadSaysSo() {
        show(DetailsState.Failed)

        compose.onNodeWithText("Couldn't load this anime.").assertExists()
    }

    private companion object {
        val details =
            AnimeDetails(
                card = card(7).copy(averageScore = 85, season = AnimeSeason.FALL),
                description =
                    "Maomao returns to the <i>pleasure district</i>.\n<br><br>\nOne day, she senses an anomaly." +
                        "<br><br><i>Note: Streamed early.</i><br><br>(Source: Crunchyroll)",
                status = "RELEASING",
                episodeMinutes = 24,
                genres = listOf("Drama", "Mystery"),
                studios = listOf("OLM"),
                nextEpisode = null,
            )
    }
}
