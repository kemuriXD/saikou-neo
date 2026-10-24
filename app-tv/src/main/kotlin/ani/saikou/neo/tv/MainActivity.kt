package ani.saikou.neo.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.tv.material3.Surface
import ani.saikou.neo.tv.sample.SampleRow
import ani.saikou.neo.tv.theme.NeoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeoTheme {
                // Test tags double as resource IDs so :benchmark-tv can find the cards.
                Surface(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    Box(contentAlignment = Alignment.CenterStart) { SampleRow() }
                }
            }
        }
    }
}
