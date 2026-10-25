package ani.saikou.neo.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Surface
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.ArtLibrary
import ani.saikou.neo.shared.catalogue.FanartTvClient
import ani.saikou.neo.shared.catalogue.TmdbClient
import ani.saikou.neo.shared.details.DetailsViewModel
import ani.saikou.neo.shared.home.HomeViewModel
import ani.saikou.neo.shared.home.SeeMoreViewModel
import ani.saikou.neo.tv.details.DetailsScreen
import ani.saikou.neo.tv.home.HomeScreen
import ani.saikou.neo.tv.home.SeeMoreScreen
import ani.saikou.neo.tv.home.displayTitle
import ani.saikou.neo.tv.theme.NeoTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private val client by lazy { AniListClient() }
    private val aniZip by lazy { AniZipClient() }
    private val library by lazy {
        ArtLibrary(client, aniZip, FanartTvClient(BuildConfig.FANART_TV_API_KEY), TmdbClient(BuildConfig.TMDB_API_KEY))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NeoNavigation(
                        home = { openDetails, openSeeMore ->
                            val home = viewModel { HomeViewModel(client, library, season(), year()) }
                            val state by home.state.collectAsStateWithLifecycle()
                            HomeScreen(
                                state = state,
                                onOpen = openDetails,
                                onPick = home::pick,
                                onSpotlightInView = home::watchSpotlight,
                                onSeeMore = openSeeMore,
                                onRest = home::rest,
                            )
                        },
                        seeMore = { row, openDetails ->
                            val seeMore = viewModel { SeeMoreViewModel(client, library, row, season(), year()) }
                            val state by seeMore.state.collectAsStateWithLifecycle()
                            SeeMoreScreen(
                                row,
                                state,
                                onOpen = openDetails,
                                onLoadMore = seeMore::loadMore,
                                onRetry = seeMore::retry,
                                onRest = seeMore::rest,
                            )
                        },
                        details = { animeId, openDetails, openRecommendations ->
                            val details = viewModel { DetailsViewModel(client, library, aniZip, animeId) }
                            val state by details.state.collectAsStateWithLifecycle()
                            DetailsScreen(state, onOpen = openDetails, onRecommendations = { openRecommendations(animeId) })
                        },
                        recommendations = { animeId, openDetails ->
                            val recommendations = viewModel { SeeMoreViewModel.recommendations(client, library, animeId) }
                            val state by recommendations.state.collectAsStateWithLifecycle()
                            SeeMoreScreen(
                                title = stringResource(R.string.details_recommendations),
                                state = state,
                                onOpen = openDetails,
                                onLoadMore = recommendations::loadMore,
                                onRetry = recommendations::retry,
                                onRest = recommendations::rest,
                                eyebrow = state.show?.displayTitle(),
                            )
                        },
                    )
                }
            }
        }
    }

    // This season, for the This season row and its page.
    private fun season() = AnimeSeason.forMonth(Calendar.getInstance().get(Calendar.MONTH) + 1)

    private fun year() = Calendar.getInstance().get(Calendar.YEAR)
}
