package ani.saikou.neo.phone

import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ani.saikou.neo.phone.theme.PhoneTheme
import ani.saikou.neo.shared.pairing.PairCodeState
import ani.saikou.neo.shared.pairing.PairCodeViewModel
import ani.saikou.neo.shared.pairing.PairingRequest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h917dp")
class PairScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun scanningShowsTheCameraAndOffersTypingInstead() {
        val taps = mutableListOf<String>()
        compose.setContent {
            PhoneTheme {
                PairScanScreen(
                    cameraAllowed = true,
                    onClose = { taps += "close" },
                    onType = { taps += "type" },
                    onOpenSettings = { taps += "settings" },
                    viewfinder = {},
                )
            }
        }

        compose.onNodeWithText("Pair a TV").assertIsDisplayed()
        compose.onNodeWithText("Looking for the code…").assertIsDisplayed()
        compose.onNodeWithText("Point your camera at the code on your TV. It scans on its own.").assertIsDisplayed()
        compose.onNodeWithText("Type the code instead").performClick()
        compose.onNodeWithContentDescription("Close").performClick()

        assertEquals(listOf("type", "close"), taps)
    }

    @Test
    fun withoutTheCameraItSaysSoAndOffersSettingsOrTyping() {
        val taps = mutableListOf<String>()
        compose.setContent {
            PhoneTheme {
                PairScanScreen(
                    cameraAllowed = false,
                    onClose = { taps += "close" },
                    onType = { taps += "type" },
                    onOpenSettings = { taps += "settings" },
                    viewfinder = {},
                )
            }
        }

        compose.onNodeWithText("Camera access is off").assertIsDisplayed()
        compose.onNodeWithText("Looking for the code…").assertDoesNotExist()
        compose.onNodeWithText("Open settings").performClick()
        compose.onNodeWithText("Type the code instead").performClick()

        assertEquals(listOf("settings", "type"), taps)
    }

    @Test
    fun connectWaitsForAllSixCharactersInCapitals() {
        val model = PairCodeViewModel()
        val requests = mutableListOf<PairingRequest>()
        compose.setContent {
            val state by model.state.collectAsStateWithLifecycle()
            PhoneTheme {
                PairCodeScreen(
                    state = state,
                    onCodeChange = model::onCodeChange,
                    onConnect = { model.connect()?.let(requests::add) },
                    onBack = {},
                )
            }
        }

        val field = compose.onNodeWithContentDescription("TV code")
        field.performTextInput("k7p2")
        compose.onNodeWithText("Connect").assertIsNotEnabled()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "4 of 6 typed"))
        field.assert(hasText("K7P2"))

        field.performTextReplacement("k7p2qx")
        compose.onNodeWithText("Connect").assertIsEnabled().performClick()

        assertEquals(listOf(PairingRequest.Typed("K7P2QX")), requests)
    }

    @Test
    fun keysTypedBeforeTheStateCatchesUpAreKept() {
        // The screen's state lags a frame behind the field; here it never catches up at all.
        val typed = mutableListOf<String>()
        compose.setContent {
            PhoneTheme { PairCodeScreen(state = PairCodeState(), onCodeChange = { typed += it }, onConnect = {}, onBack = {}) }
        }

        val field = compose.onNodeWithContentDescription("TV code")
        field.performTextInput("k7p")
        field.performTextInput("-2")

        field.assert(hasText("K7P2"))
        compose.waitForIdle()
        assertEquals("K7P2", typed.last())
    }

    @Test
    fun backLeavesTyping() {
        var back = false
        compose.setContent {
            PhoneTheme { PairCodeScreen(state = PairCodeState(), onCodeChange = {}, onConnect = {}, onBack = { back = true }) }
        }

        compose.onNodeWithText("Type the code").assertIsDisplayed()
        compose.onNodeWithText("Enter the 6-character code under the QR code on your TV.").assertIsDisplayed()
        compose.onNodeWithText("Letters and numbers only. Capitals are added for you.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(true, back)
    }
}
