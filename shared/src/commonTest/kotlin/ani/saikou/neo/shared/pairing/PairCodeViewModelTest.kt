package ani.saikou.neo.shared.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairCodeViewModelTest {
    private val model = PairCodeViewModel()

    @Test
    fun whatIsTypedIsTidiedUp() {
        model.onCodeChange("k7p 2")
        assertEquals("K7P2", model.state.value.code)
        assertFalse(model.state.value.complete)
    }

    @Test
    fun connectWaitsForAllSix() {
        model.onCodeChange("K7P2Q")
        assertNull(model.connect())

        model.onCodeChange("K7P2QX")
        assertTrue(model.state.value.complete)
        assertEquals(PairingRequest.Typed("K7P2QX"), model.connect())
    }

    @Test
    fun aScannedInviteBecomesARequest() {
        val invite = PairingInvite("10.0.0.5", 4000, ByteArray(65) { if (it == 0) 0x04 else 1 }, ByteArray(32))
        assertEquals(PairingRequest.Scanned(invite), model.scanned(invite.toQrText()))
    }

    @Test
    fun anyOtherQrCodeIsIgnored() {
        assertNull(model.scanned("https://example.com"))
    }
}
