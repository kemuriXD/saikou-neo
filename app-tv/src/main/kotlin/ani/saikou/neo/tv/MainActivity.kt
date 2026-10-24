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
import ani.saikou.neo.shared.details.DetailsViewModel
import ani.saikou.neo.shared.home.HomeViewModel
import ani.saikou.neo.tv.details.DetailsScreen
import ani.saikou.neo.tv.home.HomeScreen
import ani.saikou.neo.tv.theme.NeoTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private val client by lazy { AniListClient() }
    private val aniZip by lazy { AniZipClient() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NeoNavigation(
                        home = { openDetails ->
                            val home =
                                viewModel {
                                    val today = Calendar.getInstance()
                                    HomeViewModel(
                                        client = client,
                                        aniZip = aniZip,
                                        season = AnimeSeason.forMonth(today.get(Calendar.MONTH) + 1),
                                        year = today.get(Calendar.YEAR),
                                    )
                                }
                            val state by home.state.collectAsStateWithLifecycle()
                            HomeScreen(state, onOpen = openDetails)
                        },
                        details = { animeId ->
                            val details = viewModel { DetailsViewModel(client, animeId) }
                            val state by details.state.collectAsStateWithLifecycle()
                            DetailsScreen(state)
                        },
                    )
                }
            }
        }
    }
}
