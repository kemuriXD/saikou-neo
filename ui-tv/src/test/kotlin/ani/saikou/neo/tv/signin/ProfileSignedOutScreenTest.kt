package ani.saikou.neo.tv.signin

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class ProfileSignedOutScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var signIns = 0
    private var homes = 0

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    @Test
    fun signInWithAniListHasFocusAndBackToHomeIsBesideIt() {
        compose.setContent { NeoTheme { ProfileSignedOutScreen(onSignIn = { signIns++ }, onHome = { homes++ }) } }

        compose.onNodeWithText("Your anime lists, on the big screen").assertExists()
        compose.onNodeWithText("Sign in with AniList").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        assertEquals(1, signIns)
        assertEquals(1, homes)
    }
}
