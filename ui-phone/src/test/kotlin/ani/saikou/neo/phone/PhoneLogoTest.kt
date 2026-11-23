package ani.saikou.neo.phone

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import ani.saikou.neo.phone.theme.PhoneTheme
import ani.saikou.neo.shared.account.AniListProfile
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

// The round Saikou mark keeps its own colours. A tint would flatten it to one pink and lose the light play button.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h917dp-xhdpi")
class PhoneLogoTest {
    @get:Rule
    val compose = createComposeRule()

    // The play button is #E9E7FF, a light lilac that neither the pink tint nor the screen behind it has.
    private fun assertLogoShowsThePlayButton() {
        val pixels = compose.onNodeWithTag(PHONE_LOGO_TAG, useUnmergedTree = true).captureToImage().toPixelMap()
        val play =
            (0 until pixels.width).flatMap { x -> (0 until pixels.height).map { y -> pixels[x, y] } }.filter {
                abs(it.red - 0xE9 / 255f) < 0.03f && abs(it.green - 0xE7 / 255f) < 0.03f && it.blue > 0.97f
            }
        assertTrue("no play button pixels in the logo", play.isNotEmpty())
    }

    @Test
    fun welcomeDrawsTheMarkInItsOwnColours() {
        compose.setContent { PhoneTheme { WelcomeScreen(onSignIn = {}) } }

        assertLogoShowsThePlayButton()
    }

    @Test
    fun homeDrawsTheMarkInItsOwnColours() {
        compose.setContent { PhoneTheme { HomeScreen(AniListProfile(1, "Kemuri", "https://anilist.co/user/1"), onAccount = {}) } }

        assertLogoShowsThePlayButton()
    }
}
