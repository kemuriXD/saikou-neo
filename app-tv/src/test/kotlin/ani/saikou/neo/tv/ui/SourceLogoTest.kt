package ani.saikou.neo.tv.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import androidx.compose.ui.graphics.Color as ComposeColor

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SourceLogoTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(icon: Any?) =
        compose.setContent {
            NeoTheme { SourceLogo("demo source", icon, size = 40.dp, radius = 10.dp, background = ComposeColor.DarkGray) }
        }

    @Test
    fun withNoLogoTheTileShowsTheNamesFirstLetter() {
        show(null)

        compose.onNodeWithText("D").assertExists()
    }

    @Test
    fun aLogoTakesTheLettersPlaceOnceItIsDrawn() {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        show(png)

        compose.waitUntilDoesNotExist(hasText("D"), timeoutMillis = 5_000)
    }
}
