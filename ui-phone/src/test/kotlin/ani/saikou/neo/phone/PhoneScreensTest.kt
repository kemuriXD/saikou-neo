package ani.saikou.neo.phone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import ani.saikou.neo.phone.theme.PhoneTheme
import ani.saikou.neo.shared.account.AccountState
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.account.SignInProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The design's phone frames are 412 × 917 dp.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h917dp")
class PhoneScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val kemuri = AniListProfile(5412345, "Kemuri", "https://anilist.co/user/5412345")

    @Test
    fun welcomeSignsInWithAniList() {
        var signIns = 0
        compose.setContent { PhoneTheme { WelcomeScreen(AccountState.SignedOut(), onSignIn = { signIns++ }) } }

        compose.onNodeWithText("Sign in here. Watch on your TV.").assertIsDisplayed()
        compose.onNodeWithText("Sign in with AniList").performClick()

        assertEquals(1, signIns)
    }

    @Test
    fun welcomeSaysWhyTheLastSignInDidNotWork() {
        compose.setContent { PhoneTheme { WelcomeScreen(AccountState.SignedOut(SignInProblem.NO_NETWORK), onSignIn = {}) } }

        compose.onNodeWithText("Couldn't reach AniList. Check your connection and try again.").assertIsDisplayed()
    }

    @Test
    fun homeAsksToPairTheFirstTvAndHasNoTvsYet() {
        var accountOpened = false
        compose.setContent { PhoneTheme { HomeScreen(kemuri, onAccount = { accountOpened = true }) } }

        compose.onNodeWithText("Pair your first TV").assertIsDisplayed()
        compose.onNodeWithText("Scan the TV code").assertIsDisplayed()
        compose.onNodeWithText("Type the code instead").assertIsDisplayed()
        compose.onNodeWithText("Your TVs").assertIsDisplayed()
        compose.onNodeWithText("No TVs yet. A TV you pair shows up here, ready to send things to or remove.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Account").performClick()

        assertTrue(accountOpened)
    }

    @Test
    fun accountShowsTheAniListProfile() {
        var opened: String? = null
        var back = false
        compose.setContent {
            PhoneTheme { AccountScreen(kemuri, version = "0.0.5-alpha", onBack = { back = true }, onOpen = { opened = it }) }
        }

        compose.onNodeWithText("Kemuri").assertIsDisplayed()
        compose.onNodeWithText("Signed in with AniList").assertIsDisplayed()
        compose.onNodeWithText("0.0.5-alpha").assertIsDisplayed()
        compose.onNodeWithText("AniList profile").performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals("https://anilist.co/user/5412345", opened)
        assertTrue(back)
    }

    @Test
    fun aPhoneUsesTheWholeWidth() {
        compose.setContent { Box(Modifier.requiredSize(412.dp, 800.dp)) { PhoneColumn { Box(Modifier.testTagContent()) } } }

        compose.onNodeWithTag(CONTENT).assertWidthIsEqualTo(412.dp)
    }

    @Test
    fun aTabletCentresA480DpColumn() {
        compose.setContent { Box(Modifier.requiredSize(800.dp, 1280.dp)) { PhoneColumn { Box(Modifier.testTagContent()) } } }

        compose.onNodeWithTag(CONTENT).assertWidthIsEqualTo(480.dp)
    }
}

private const val CONTENT = "content"

private fun Modifier.testTagContent() = fillMaxSize().testTag(CONTENT)
