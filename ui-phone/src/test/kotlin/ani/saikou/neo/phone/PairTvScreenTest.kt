package ani.saikou.neo.phone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ani.saikou.neo.phone.theme.PhoneTheme
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.pairing.PairTvState
import ani.saikou.neo.shared.pairing.PairedTv
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rGB-w412dp-h917dp")
class PairTvScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val taps = mutableListOf<String>()
    private val actions =
        PairTvActions(
            close = { taps += "close" },
            confirm = { taps += "confirm" },
            reject = { taps += "reject" },
            retry = { taps += "retry" },
            scanNew = { taps += "scan" },
            done = { taps += "done" },
        )

    private fun show(state: PairTvState) = compose.setContent { PhoneTheme { PairTvScreen(state, actions) } }

    @Test
    fun connectingSaysToKeepTheTvOnAndCancelStopsIt() {
        show(PairTvState.Connecting)

        compose.onNodeWithText("Connecting to your TV…").assertIsDisplayed()
        compose.onNodeWithText("Keep Saikou Neo open on the TV with the code showing.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Close").performClick()

        assertEquals(listOf("close", "close"), taps)
    }

    @Test
    fun theCheckCodeShowsInTwoGroupsAndIsReadAsOneLabel() {
        show(PairTvState.CheckCode("482913"))

        compose.onNodeWithText("Does your TV show this code?").assertIsDisplayed()
        compose.onNodeWithContentDescription("Check code 4 8 2 9 1 3").assertIsDisplayed()
        compose.onNodeWithText("482 913", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("It matches").performClick()
        compose.onNodeWithText("It doesn’t match").performClick()

        assertEquals(listOf("confirm", "reject"), taps)
    }

    @Test
    fun whileTheAccountIsGoingTheButtonsWait() {
        show(PairTvState.CheckCode("482913", sending = true))

        compose.onNodeWithText("It matches").assertIsNotEnabled()
        compose.onNodeWithText("It doesn’t match").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Close").assertIsNotEnabled()
    }

    @Test
    fun aSignedInTvIsNamedAndDoneLeaves() {
        show(PairTvState.SignedIn("Living room TV"))

        compose.onNodeWithText("Living room TV is signed in").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()

        assertEquals(listOf("done"), taps)
    }

    @Test
    fun couldNotReachOffersTryAgainAndANewCode() {
        show(PairTvState.Unreachable)

        compose.onNodeWithText("Couldn’t reach the TV").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Scan a new code").performClick()

        assertEquals(listOf("retry", "scan"), taps)
    }

    @Test
    fun pairingStoppedSaysNothingWasSent() {
        show(PairTvState.Stopped)

        compose.onNodeWithText("Pairing stopped").assertIsDisplayed()
        compose
            .onNodeWithText(
                "The codes didn’t match, so nothing was sent. Choose Sign in on the TV again to get a new code.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Start again").performClick()
        compose.onNodeWithText("Back to Home").performClick()

        assertEquals(listOf("scan", "close"), taps)
    }

    @Test
    fun homeListsYourTvsWithTheDayEachWasPaired() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.UK)
        val tvs = listOf(PairedTv("tv-1", "Living room TV", 1_761_868_800), PairedTv("tv-2", "Bedroom Fire TV", 1_762_041_600))
        var scans = 0
        compose.setContent {
            PhoneTheme { HomeScreen(AniListProfile(1, "Kemuri", null), onAccount = {}, tvs = tvs, onScan = { scans++ }) }
        }

        compose.onNodeWithText("Living room TV").assertIsDisplayed()
        compose.onNodeWithText("Signed in · Paired 31 Oct").assertIsDisplayed()
        compose.onNodeWithText("Bedroom Fire TV").assertIsDisplayed()
        compose.onNodeWithText("Signed in · Paired 2 Nov").assertIsDisplayed()
        compose.onNodeWithText("Pair your first TV").assertDoesNotExist()
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Pair a TV")), useUnmergedTree = true).performClick()

        assertEquals(1, scans)
    }
}
