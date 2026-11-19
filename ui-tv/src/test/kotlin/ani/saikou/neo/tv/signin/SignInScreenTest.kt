package ani.saikou.neo.tv.signin

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.pairing.TvPairingNote
import ani.saikou.neo.shared.pairing.TvSignInState
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SignInScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = mutableStateOf<TvSignInState>(TvSignInState.Waiting("saikouneo://pair?v=1", "K74Q9M", 581))
    private var newCodes = 0
    private var left = 0
    private var home = 0

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    private fun show() =
        compose.setContent {
            NeoTheme {
                SignInScreen(state.value, onNewCode = { newCodes++ }, onLeave = { left++ }, onHome = { home++ })
            }
        }

    @Test
    fun waitingShowsTheStepsTheCodeAndHowLongItLasts() {
        show()
        compose.onNodeWithText("Use your phone to sign in to AniList").assertExists()
        compose.onNodeWithContentDescription("Code K 7 4 Q 9 M").assertExists()
        compose.onNodeWithText("Waiting for your phone · code lasts 9:41").assertExists()
        compose.onNodeWithText("Get a new code").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertEquals(1, newCodes)
        assertEquals(1, left)
    }

    @Test
    fun theCheckCodeShowsInTwoGroupsAndOnlyCancelIsOffered() {
        state.value = TvSignInState.CheckCode("482913")
        show()
        compose.onNodeWithContentDescription("Check code 4 8 2 9 1 3").assertExists()
        compose.onNodeWithText("Get a new code").assertDoesNotExist()
        compose.onNodeWithText("Cancel").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, left)
    }

    @Test
    fun anExpiredCodeSaysSoAndOffersANewOne() {
        state.value = TvSignInState.Expired("K74Q9M")
        show()
        compose.onNodeWithText("This code has expired").assertExists()
        compose.onNodeWithText("K74Q9M no longer works on the phone.").assertExists()
        compose.onNodeWithText("Get a new code").assertIsFocused()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun focusMovesToGoToHomeWhenThePhoneConfirms() {
        state.value = TvSignInState.CheckCode("482913")
        show()
        compose.onNodeWithText("Cancel").assertIsFocused()

        state.value = TvSignInState.SignedIn(AniListProfile(7, "Hikari", null))
        compose.onNodeWithText("Go to Home").assertIsFocused()
        // Drawn focused too: the paper fill, not the tonal one (seen on the Fire TV).
        val bounds = compose.onNodeWithText("Go to Home").fetchSemanticsNode().boundsInRoot
        val fill = compose.onRoot().captureToImage().toPixelMap()[bounds.left.toInt() + 16, bounds.center.y.toInt()]
        assertTrue("Go to Home isn't drawn focused: $fill", fill.red > 0.8f && fill.green > 0.8f)
    }

    @Test
    fun focusMovesToGetANewCodeWhenPairingStops() {
        state.value = TvSignInState.CheckCode("482913")
        show()

        state.value = TvSignInState.Stopped
        compose.onNodeWithText("Get a new code").assertIsFocused()
    }

    @Test
    fun aPhoneThatLeftLeavesANoteUnderTheSameCode() {
        state.value = TvSignInState.CheckCode("482913")
        show()

        state.value = TvSignInState.Waiting("saikouneo://pair?v=1", "K74Q9M", 512, TvPairingNote.StoppedOnPhone)
        compose.onNodeWithContentDescription("Code K 7 4 Q 9 M").assertExists()
        compose.onNodeWithText("Pairing was stopped on your phone.").assertExists()
        compose.onNodeWithText("This code still works.").assertExists()
        compose.onNodeWithText("Get a new code").assertIsFocused()
    }

    @Test
    fun eachNoteSaysWhyThePhoneLeft() {
        show()
        val notes =
            mapOf(
                TvPairingNote.StoppedOnPhone to "Pairing was stopped on your phone.",
                TvPairingNote.CancelledOnPhone to "Pairing was cancelled on your phone.",
                TvPairingNote.AniListRefused to "AniList didn’t accept the account.",
                TvPairingNote.LostConnection to "Lost the connection to your phone.",
                TvPairingNote.TookTooLong to "Your phone took too long.",
            )
        notes.forEach { (note, text) ->
            state.value = TvSignInState.Waiting("saikouneo://pair?v=1", "K74Q9M", 512, note)
            compose.onNodeWithText(text).assertExists()
        }
    }

    @Test
    fun noNoteBeforeAPhoneLeaves() {
        show()
        compose.onNodeWithText("This code still works.").assertDoesNotExist()
    }

    @Test
    fun aTvWithNoNetworkSaysSoAndTriesAgain() {
        state.value = TvSignInState.NoNetwork
        show()
        compose.onNodeWithText("Can’t sign in yet").assertExists()
        compose.onNodeWithText("This TV isn’t on a network").assertExists()
        compose.onNodeWithText("Try again").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, newCodes)
    }

    @Test
    fun signedInShowsTheNameAndGoesHome() {
        state.value = TvSignInState.SignedIn(AniListProfile(7, "Hikari", null))
        show()
        compose.onNodeWithText("Hikari").assertExists()
        compose.onNodeWithText("Go to Home").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, home)
    }
}
