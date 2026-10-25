package ani.saikou.neo.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Surface
import ani.saikou.neo.shared.catalogue.AniListClient
import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.FanartTvClient
import ani.saikou.neo.shared.catalogue.TmdbClient
import ani.saikou.neo.shared.details.DetailsViewModel
import ani.saikou.neo.shared.home.HomeViewModel
import ani.saikou.neo.shared.home.SeeMoreViewModel
import ani.saikou.neo.tv.details.DetailsScreen
import ani.saikou.neo.tv.home.HomeScreen
import ani.saikou.neo.tv.home.SeeMoreScreen
import ani.saikou.neo.tv.theme.NeoTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private val client by lazy { AniListClient() }
    private val aniZip by lazy { AniZipClient() }
    private val fanartTv by lazy { FanartTvClient(BuildConfig.FANART_TV_API_KEY) }
    private val tmdb by lazy { TmdbClient(BuildConfig.TMDB_API_KEY) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NeoNavigation(
                        home = { openDetails, openSeeMore ->
                            val home = viewModel { HomeViewModel(client, aniZip, fanartTv, tmdb, season(), year()) }
                            val state by home.state.collectAsStateWithLifecycle()
                            HomeScreen(
                                state = state,
                                onOpen = openDetails,
                                onPick = home::pick,
                                onSpotlightInView = home::watchSpotlight,
                                onSeeMore = openSeeMore,
                            )
                        },
                        seeMore = { row, openDetails ->
                            val seeMore = viewModel { SeeMoreViewModel(client, row, season(), year()) }
                            val state by seeMore.state.collectAsStateWithLifecycle()
                            SeeMoreScreen(row, state, onOpen = openDetails, onLoadMore = seeMore::loadMore, onRetry = seeMore::retry)
                        },
                        details = { animeId ->
                            val details = viewModel { DetailsViewModel(client, aniZip, fanartTv, tmdb, animeId) }
                            val state by details.state.collectAsStateWithLifecycle()
                            DetailsScreen(state)
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
