package ani.saikou.neo.shared.pairing

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairingInviteTest {
    private val key = ByteArray(65) { if (it == 0) 0x04 else it.toByte() }
    private val session = ByteArray(32) { (it * 7).toByte() }
    private val invite = PairingInvite(host = "192.168.1.20", port = 40123, publicKey = key, sessionId = session)

    private fun b64(bytes: ByteArray) = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)

    private fun qr(
        host: String = "192.168.1.20",
        port: String = "40123",
        key: String = b64(this.key),
        session: String = b64(this.session),
        version: String = "1",
    ) = "saikouneo://pair?v=$version&host=$host&port=$port&key=$key&session=$session"

    @Test
    fun anInviteSurvivesTheQrCode() {
        assertEquals(invite, PairingInvite.parse(invite.toQrText()))
    }

    @Test
    fun theQrTextHasTheDocumentedShape() {
        assertEquals(qr(), invite.toQrText())
    }

    @Test
    fun everyPrivateRangeIsAccepted() {
        for (host in listOf("10.0.0.2", "172.16.0.9", "172.31.255.1", "192.168.0.1", "169.254.3.4")) {
            assertEquals(host, PairingInvite.parse(qr(host = host))?.host, host)
        }
    }

    @Test
    fun aWebsiteIsNotAnInvite() {
        assertNull(PairingInvite.parse("https://anilist.co/"))
        assertNull(PairingInvite.parse("K7P2QX"))
        assertNull(PairingInvite.parse(""))
    }

    @Test
    fun aPublicAddressOrANameIsRefused() {
        for (host in listOf(
            "8.8.8.8",
            "172.32.0.1",
            "example.com",
            "localhost",
            "127.0.0.1",
            "192.168.1",
            "192.168.1.256",
            "0300.168.1.1",
        )) {
            assertNull(PairingInvite.parse(qr(host = host)), host)
        }
    }

    @Test
    fun aPortOutsideTheRangeIsRefused() {
        for (port in listOf("0", "65536", "-1", "port", "")) {
            assertNull(PairingInvite.parse(qr(port = port)), port)
        }
    }

    @Test
    fun aKeyOfTheWrongShapeIsRefused() {
        assertNull(PairingInvite.parse(qr(key = b64(key.copyOf(64)))))
        assertNull(PairingInvite.parse(qr(key = b64(key.copyOf().also { it[0] = 0x02 }))))
        assertNull(PairingInvite.parse(qr(key = "not base64!")))
    }

    @Test
    fun aSessionOfTheWrongSizeIsRefused() {
        assertNull(PairingInvite.parse(qr(session = b64(session.copyOf(31)))))
    }

    @Test
    fun anotherVersionIsRefused() {
        assertNull(PairingInvite.parse(qr(version = "2")))
    }

    @Test
    fun aMissingOrRepeatedFieldIsRefused() {
        assertNull(PairingInvite.parse(qr().replace("&port=40123", "")))
        assertNull(PairingInvite.parse(qr() + "&host=10.0.0.1"))
    }
}
