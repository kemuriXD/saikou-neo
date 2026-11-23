package ani.saikou.neo.phone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ani.saikou.neo.phone.theme.PhoneTheme
import ani.saikou.neo.shared.sources.AddError
import ani.saikou.neo.shared.sources.Choice
import ani.saikou.neo.shared.sources.ChoiceStatus
import ani.saikou.neo.shared.sources.ListedSource
import ani.saikou.neo.shared.sources.SendEnd
import ani.saikou.neo.shared.sources.SendSourcesState
import ani.saikou.neo.shared.sources.SourceProblem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rGB-w412dp-h917dp")
class SendSourcesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val taps = mutableListOf<String>()
    private val actions =
        SendSourcesActions(
            close = { taps += "close" },
            confirm = { taps += "confirm" },
            reject = { taps += "reject" },
            linkChange = { taps += "type $it" },
            check = { taps += "check" },
            toggle = { taps += "toggle $it" },
            back = { taps += "back" },
            send = { taps += "send" },
            scanAgain = { taps += "scan" },
            retry = { taps += "retry" },
        )

    private fun source(
        id: String,
        name: String,
        version: String,
    ) = ListedSource(id, name, version, 1, "https://example.org/$id.apk")

    private val choices =
        listOf(
            Choice(source("sample", "Sample source", "2.1.0"), ChoiceStatus.New),
            Choice(source("demo", "Demo source", "1.0.0"), ChoiceStatus.Update("0.9.0")),
            Choice(source("example", "Example source", "1.4.0"), ChoiceStatus.Installed),
            Choice(source("test", "Test source", "3.0.0"), ChoiceStatus.CantUse(SourceProblem.BUILT_FOR_NEWER_APP)),
        )

    private fun show(state: SendSourcesState) = compose.setContent { PhoneTheme { SendSourcesScreen(state, "Living room TV", actions) } }

    @Test
    fun theCheckCodeAsksForItMatchesOnTheTvToo() {
        show(SendSourcesState.CheckCode("482913"))

        compose.onNodeWithText("Send a source").assertIsDisplayed()
        compose.onNodeWithContentDescription("Check code 4 8 2 9 1 3").assertIsDisplayed()
        compose.onNodeWithText("choose It matches on the TV too", substring = true).assertIsDisplayed()
        compose.onNodeWithText("It matches").performClick()
        compose.onNodeWithText("It doesn’t match").performClick()

        assertEquals(listOf("confirm", "reject"), taps)
    }

    @Test
    fun theLinkScreenNamesTheTvAndChecksTheLink() {
        show(SendSourcesState.Link("https://example.org/sources.json"))

        compose.onNodeWithText("To Living room TV").assertIsDisplayed()
        compose.onNodeWithText("A list lets you pick which sources to send.").assertIsDisplayed()
        compose.onNodeWithText("Sources run inside the app. Only add ones you trust.").assertIsDisplayed()
        compose.onNodeWithText("Check link").performClick()

        assertEquals(listOf("check"), taps)
    }

    @Test
    fun whileCheckingTheButtonWaits() {
        show(SendSourcesState.Link("https://example.org/sources.json", checking = true))

        compose.onNodeWithText("Checking the link…").assertIsDisplayed()
        compose.onNodeWithText("Check link").assertIsNotEnabled()
    }

    @Test
    fun aLinkThatCantBeReadSaysSo() {
        show(SendSourcesState.Link("https://example.org/about.html", error = AddError.NOT_A_SOURCE))

        compose.onNodeWithText("This link isn’t a source or a list of sources.").assertIsDisplayed()
    }

    @Test
    fun nothingTickedLeavesSendOff() {
        show(SendSourcesState.Pick("https://example.org/sources.json", choices))

        compose.onNodeWithText("From example.org/sources.json. Tick the ones to send.").assertIsDisplayed()
        compose.onNodeWithText("Version 2.1.0").assertIsDisplayed()
        compose.onNodeWithText("Update from 0.9.0 to 1.0.0").assertIsDisplayed()
        compose.onNodeWithText("Installed, version 1.4.0").assertIsDisplayed()
        compose.onNodeWithText("Needs a newer version of the app").assertIsDisplayed()
        compose.onNode(hasText("Sample source").and(hasClickAction()), useUnmergedTree = false).assertIsOff()
        compose.onNodeWithText("Send to TV").assertIsNotEnabled()

        compose.onNodeWithText("Sample source").performClick()
        // Installed rows can't be ticked.
        compose.onNodeWithText("Example source").performClick()
        assertEquals(listOf("toggle sample"), taps)
    }

    @Test
    fun tickedSourcesAreCountedOnSend() {
        show(
            SendSourcesState.Pick(
                "https://example.org/sources.json",
                choices.map {
                    if (it.status.let { s ->
                            s is ChoiceStatus.New ||
                                s is ChoiceStatus.Update
                        }
                    ) {
                        it.copy(ticked = true)
                    } else {
                        it
                    }
                },
            ),
        )

        compose.onNode(hasText("Demo source").and(hasClickAction())).assertIsOn()
        compose.onNodeWithText("Send 2 sources").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("send", "back"), taps)
    }

    @Test
    fun aLinkToOneApkIsOneRowNamedAfterTheFile() {
        val apk = "https://example.org/source-1.2.0.apk"
        show(SendSourcesState.Pick(apk, apk = apk))

        compose.onNodeWithText("From example.org/source-1.2.0.apk.").assertIsDisplayed()
        compose.onNodeWithText("One source, checked on the TV").assertIsDisplayed()
        compose.onNodeWithText("source-1.2.0.apk").performClick()

        assertEquals(listOf("toggle $apk"), taps)
    }

    @Test
    fun aListWhereNothingFitsSaysWhy() {
        show(
            SendSourcesState.Pick(
                "https://example.org/sources.json",
                choices.filter {
                    !it.status.let { s ->
                        s is ChoiceStatus.New ||
                            s is ChoiceStatus.Update
                    }
                },
            ),
        )

        compose.onNodeWithText("From example.org/sources.json.").assertIsDisplayed()
        compose.onNodeWithText("Nothing in this list can be added", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Send to TV").assertIsNotEnabled()
    }

    @Test
    fun sentNamesTheTv() {
        show(SendSourcesState.Sent)

        compose.onNodeWithText("Living room TV has the list").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()

        assertEquals(listOf("close"), taps)
    }

    @Test
    fun aLostTvOffersToScanAgain() {
        show(SendSourcesState.Ended(SendEnd.Lost))

        compose.onNodeWithText("Lost the connection to Living room TV").assertIsDisplayed()
        compose.onNodeWithText("Scan again").performClick()

        assertEquals(listOf("scan"), taps)
    }

    @Test
    fun aTvThatCouldNotBeReachedOffersTryAgainAndANewCode() {
        show(SendSourcesState.Ended(SendEnd.Unreachable))

        compose.onNodeWithText("Couldn’t reach the TV").assertIsDisplayed()
        compose.onNodeWithText("Check the phone and TV are on the same Wi-Fi", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Scan a new code").performClick()

        assertEquals(listOf("retry", "scan"), taps)
    }

    @Test
    fun itDoesNotMatchHereSaysNothingWasSent() {
        show(SendSourcesState.Ended(SendEnd.NoMatch))

        compose.onNodeWithText("The codes don’t match").assertIsDisplayed()
        compose.onNodeWithText("Nothing was sent.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Scan again").performClick()
        compose.onNodeWithText("Back to Home").performClick()

        assertEquals(listOf("scan", "close"), taps)
    }

    @Test
    fun codesThatDoNotMatchOnTheTvSaySo() {
        show(SendSourcesState.Ended(SendEnd.NoMatchOnTv))

        compose.onNodeWithText("Living room TV says the codes don’t match").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Someone else may be connecting. Open Add from my phone on the TV again and check the code before you tap It matches.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Scan again").performClick()

        assertEquals(listOf("scan"), taps)
    }

    @Test
    fun cancelOnTheTvSaysItStoppedThere() {
        show(SendSourcesState.Ended(SendEnd.StoppedOnTv))

        compose.onNodeWithText("Stopped on Living room TV").assertIsDisplayed()
        compose.onNodeWithText("Open Add from my phone on the TV again to send sources.").assertIsDisplayed()
        compose.onNodeWithText("Scan again").performClick()

        assertEquals(listOf("scan"), taps)
    }

    @Test
    fun aTvThatStoppedWaitingSaysSo() {
        show(SendSourcesState.Ended(SendEnd.TimedOut))

        compose.onNodeWithText("Living room TV stopped waiting").assertIsDisplayed()
        compose.onNodeWithText("Nothing was sent in time.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Scan again").performClick()

        assertEquals(listOf("scan"), taps)
    }

    @Test
    fun picksTheTvCouldNotUseSaySo() {
        show(SendSourcesState.Ended(SendEnd.Refused))

        compose.onNodeWithText("Living room TV didn’t take the sources").assertIsDisplayed()
        compose.onNodeWithText("Nothing will install.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Scan again").performClick()

        assertEquals(listOf("scan"), taps)
    }
}
