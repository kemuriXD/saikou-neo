package ani.saikou.neo.phone

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import ani.saikou.neo.phone.theme.PhoneTheme
import ani.saikou.neo.shared.account.AccountState
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.account.SignInProblem
import ani.saikou.neo.shared.pairing.PairedTv
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
    fun aNewSignInShowsTheAccountAndPairsATvNext() {
        var pairs = 0
        compose.setContent { PhoneTheme { SignedInScreen(kemuri, onPair = { pairs++ }) } }

        compose.onNodeWithText("Signed in as Kemuri").assertIsDisplayed()
        compose.onNodeWithText("Your AniList list and progress are ready. Pair a TV next so it signs in too.").assertIsDisplayed()
        compose.onNodeWithText("Pair a TV").performClick()

        assertEquals(1, pairs)
    }

    @Test
    fun theFirstLetterShowsUntilThePictureLoads() {
        val withPicture = kemuri.copy(avatarUrl = "https://example.invalid/avatar.png")
        compose.setContent { PhoneTheme { SignedInScreen(withPicture, onPair = {}) } }

        compose.onNodeWithText("K").assertIsDisplayed()
    }

    @Test
    fun aFailedSignInSaysWhatWentWrongAndHowToFixIt() {
        var tries = 0
        compose.setContent { PhoneTheme { SignInProblemScreen(SignInProblem.NO_NETWORK, onTryAgain = { tries++ }) } }

        compose.onNodeWithText("Couldn't reach AniList").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Your phone looks offline. Check Wi-Fi or mobile data, then try again. Nothing was saved, so you start the sign-in from the top.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()

        assertEquals(1, tries)
    }

    @Test
    fun everySignInProblemHasItsOwnHeading() {
        var problem by mutableStateOf(SignInProblem.NO_NETWORK)
        compose.setContent { PhoneTheme { SignInProblemScreen(problem, onTryAgain = {}) } }
        val headings =
            SignInProblem.entries.map {
                problem = it
                compose.waitForIdle()
                compose
                    .onNodeWithTag(SIGN_IN_PROBLEM_TITLE)
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.Text]
                    .joinToString()
            }

        assertEquals(SignInProblem.entries.size, headings.toSet().size)
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
    fun removingATvAsksFirstThenSaysItWasRemoved() {
        val livingRoom = PairedTv("tv-1", "Living room TV", 1_761_868_800)
        var tvs by mutableStateOf(listOf(livingRoom, PairedTv("tv-2", "Bedroom Fire TV", 1_762_041_600)))
        val removed = mutableListOf<PairedTv>()
        compose.setContent {
            PhoneTheme {
                HomeScreen(kemuri, onAccount = {}, tvs = tvs, onRemoveTv = {
                    removed += it
                    tvs = tvs - it
                })
            }
        }

        compose.onNodeWithContentDescription("Options for Living room TV").performClick()
        compose.onNodeWithText("Remove this TV").performClick()
        compose.onNodeWithText("Remove Living room TV?").assertIsDisplayed()
        assertTrue(removed.isEmpty())

        compose.onNodeWithText("Remove").performClick()

        assertEquals(listOf(livingRoom), removed)
        compose.onNodeWithText("Living room TV removed").assertIsDisplayed()
    }

    @Test
    fun cancelKeepsTheTv() {
        val removed = mutableListOf<PairedTv>()
        compose.setContent {
            PhoneTheme {
                HomeScreen(kemuri, onAccount = {}, tvs = listOf(PairedTv("tv-1", "Living room TV", 1)), onRemoveTv = { removed += it })
            }
        }

        compose.onNodeWithText("Living room TV").performClick()
        compose.onNodeWithText("Remove this TV").performClick()
        compose.onNodeWithText("Cancel").performClick()

        assertTrue(removed.isEmpty())
        compose.onNodeWithText("Remove Living room TV?").assertDoesNotExist()
    }

    @Test
    fun signOutAsksFirst() {
        var signOuts = 0
        compose.setContent {
            PhoneTheme { AccountScreen(kemuri, version = "0.0.5-alpha", onBack = {}, onOpen = {}, onSignOut = { signOuts++ }) }
        }

        compose.onNodeWithText("Sign out").performClick()
        compose.onNodeWithText("Sign out on this phone?").assertIsDisplayed()
        compose.onNodeWithText("Your TVs stay signed in. To sign one out, choose Sign out in its Settings > Account.").assertIsDisplayed()
        assertEquals(0, signOuts)

        // The dialog's Sign out, not the page's.
        compose.onAllNodesWithText("Sign out").onLast().performClick()

        assertEquals(1, signOuts)
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
