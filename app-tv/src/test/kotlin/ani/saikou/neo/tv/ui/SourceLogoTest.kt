package ani.saikou.neo.tv.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
            NeoTheme { SourceLogo("demo source", icon, size = 40.dp, radius = 10.dp, background = Tile) }
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

    @Test
    fun theLetterSitsOnATile() {
        show(null)

        assertTrue("the tile is drawn", tileShows())
    }

    @Test
    fun aSeeThroughLogoHasNoTileBehindIt() {
        // Clear, with a blue dot in the middle, like a logo shipped as a PNG with no background.
        val bitmap =
            Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.TRANSPARENT)
                for (x in 40 until 56) for (y in 40 until 56) setPixel(x, y, Color.BLUE)
            }
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        show(png)
        compose.waitUntilDoesNotExist(hasText("D"), timeoutMillis = 5_000)
        compose.waitForIdle()

        assertFalse("no tile behind the logo", tileShows())
    }

    private fun tileShows(): Boolean {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return (0 until pixels.width).any { x -> (0 until pixels.height).any { y -> pixels[x, y] == Tile } }
    }

    private companion object {
        val Tile = ComposeColor(0xFFFF0000)
    }
}
