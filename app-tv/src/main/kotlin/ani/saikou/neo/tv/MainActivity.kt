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
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.home.HomeViewModel
import ani.saikou.neo.tv.home.HomeScreen
import ani.saikou.neo.tv.theme.NeoTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val home =
                        viewModel {
                            val today = Calendar.getInstance()
                            HomeViewModel(
                                client = AniListClient(),
                                season = AnimeSeason.forMonth(today.get(Calendar.MONTH) + 1),
                                year = today.get(Calendar.YEAR),
                            )
                        }
                    val state by home.state.collectAsStateWithLifecycle()
                    // Home is the start screen, so Back leaves the app.
                    HomeScreen(state)
                }
            }
        }
    }
}
