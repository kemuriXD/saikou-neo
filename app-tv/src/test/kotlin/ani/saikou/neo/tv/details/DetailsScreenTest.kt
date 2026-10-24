package ani.saikou.neo.tv.details

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.details.DetailsState
import ani.saikou.neo.tv.home.card
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class DetailsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(state: DetailsState) {
        compose.setContent { NeoTheme { DetailsScreen(state) } }
    }

    @Test
    fun showsWhatTheAnimeIs() {
        show(DetailsState.Loaded(details))

        compose.onNodeWithText("Show 7").assertExists()
        compose.onNodeWithText("TV · FALL 2026").assertExists()
        compose.onNodeWithText("Drama · Mystery · 12 episodes · Score 8.5").assertExists()
        compose.onNodeWithText("Maomao returns to the pleasure district.\n\nOne day, she senses an anomaly.").assertExists()
    }

    @Test
    fun leavesOutWhatAniListDoesNotKnow() {
        val bare = card(7).copy(format = null, episodes = null, season = null, averageScore = null)
        show(DetailsState.Loaded(details.copy(card = bare, genres = emptyList(), description = null)))

        compose.onNodeWithText("2026").assertExists()
        compose.onNodeWithTag(DETAILS_META_TAG).assertDoesNotExist()
        compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).assertDoesNotExist()
    }

    @Test
    fun theAboutColumnSitsWhereTheDesignPutsIt() {
        show(DetailsState.Loaded(details))

        compose
            .onNodeWithText("TV · FALL 2026")
            .assertTopPositionInRootIsEqualTo(90.dp)
            .assertLeftPositionInRootIsEqualTo(58.dp)
        compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).assertWidthIsEqualTo(340.dp)
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
                description = "Maomao returns to the <i>pleasure district</i>.\n<br><br>\nOne day, she senses an anomaly.",
                status = "RELEASING",
                episodeMinutes = 24,
                genres = listOf("Drama", "Mystery"),
                studios = listOf("OLM"),
                nextEpisode = null,
            )
    }
}
