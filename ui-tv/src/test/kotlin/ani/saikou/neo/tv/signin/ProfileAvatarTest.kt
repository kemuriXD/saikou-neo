package ani.saikou.neo.tv.signin

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.tv.theme.NeoTheme
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class ProfileAvatarTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val kemuri = AniListProfile(5412345, "Kemuri", "https://anilist.co/user/5412345", "https://example.invalid/a.png")

    @Test
    fun theFirstLetterShowsUntilThePictureLoads() {
        compose.setContent { NeoTheme { ProfileAvatar(kemuri, 96.dp, NeoTheme.typography.display) } }

        compose.onNodeWithText("K").assertExists()
    }

    @Test
    fun aLoadedPictureHasNothingBehindIt() {
        val picture = AsyncImagePreviewHandler { ColorDrawable(Color.TRANSPARENT).asImage() }
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true, LocalAsyncImagePreviewHandler provides picture) {
                NeoTheme { ProfileAvatar(kemuri, 96.dp, NeoTheme.typography.display) }
            }
        }

        compose.onNodeWithText("K").assertDoesNotExist()
    }
}
