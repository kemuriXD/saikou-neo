package ani.saikou.neo.shared.pairing

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PairingCipherTest {
    private val tv = PairingKeyPair.generate()
    private val phone = PairingKeyPair.generate()
    private val session = newPairingSessionId()
    private val tvNonce = newPairingNonce()
    private val phoneNonce = newPairingNonce()

    private fun tvCipher(
        peer: ByteArray = phone.publicKey,
        id: ByteArray = session,
        tvRandom: ByteArray = tvNonce,
    ) = PairingCipher.create(PairingRole.TV, tv, peer, id, tvRandom, phoneNonce)

    private fun phoneCipher(
        own: PairingKeyPair = phone,
        tvKey: ByteArray = tv.publicKey,
        id: ByteArray = session,
    ) = PairingCipher.create(PairingRole.PHONE, own, tvKey, id, tvNonce, phoneNonce)

    @Test
    fun aPublicKeyIsAnUncompressedP256Point() {
        assertEquals(65, tv.publicKey.size)
        assertEquals(0x04, tv.publicKey[0].toInt())
        assertEquals(32, session.size)
    }

    @Test
    fun bothSidesDeriveTheSameSixDigitCheckCode() {
        val code = tvCipher().checkCode

        assertEquals(code, phoneCipher().checkCode)
        assertTrue(Regex("[0-9]{6}").matches(code), code)
    }

    @Test
    fun aDifferentSecretGivesADifferentCheckCode() {
        val stranger = PairingKeyPair.generate()

        assertNotEquals(tvCipher().checkCode, tvCipher(peer = stranger.publicKey).checkCode)
        assertNotEquals(tvCipher().checkCode, tvCipher(id = newPairingSessionId()).checkCode)
    }

    @Test
    fun theTvNonceChangesTheCheckCode() {
        // The phone commits to its nonce before it sees this one, so nobody can pick keys to match a code.
        val codes = List(20) { tvCipher(tvRandom = newPairingNonce()).checkCode }.toSet()

        assertTrue(codes.size > 15, codes.toString())
    }

    @Test
    fun aCommitmentOnlyMatchesItsOwnKeyAndNonce() {
        val commitment = pairingCommitment(phone.publicKey, phoneNonce)

        assertContentEquals(commitment, pairingCommitment(phone.publicKey, phoneNonce))
        assertEquals(32, commitment.size)
        assertFalse(commitment.contentEquals(pairingCommitment(phone.publicKey, newPairingNonce())))
        assertFalse(commitment.contentEquals(pairingCommitment(tv.publicKey, phoneNonce)))
    }

    @Test
    fun aNonceOfTheWrongSizeIsRejected() {
        assertFailsWith<PairingException> { tvCipher(tvRandom = ByteArray(8)) }
    }

    @Test
    fun messagesTravelBothWays() {
        val tvSide = tvCipher()
        val phoneSide = phoneCipher()

        assertContentEquals(byteArrayOf(1, 2, 3), tvSide.open(phoneSide.seal(byteArrayOf(1, 2, 3))))
        assertContentEquals(byteArrayOf(4, 5), phoneSide.open(tvSide.seal(byteArrayOf(4, 5))))
        assertContentEquals(byteArrayOf(6), tvSide.open(phoneSide.seal(byteArrayOf(6))))
    }

    @Test
    fun aTamperedMessageIsRejected() {
        val sealed = phoneCipher().seal("token".encodeToByteArray())
        sealed[sealed.size / 2] = (sealed[sealed.size / 2].toInt() xor 1).toByte()

        assertFailsWith<PairingException> { tvCipher().open(sealed) }
    }

    @Test
    fun aRejectedMessageEndsTheChannel() {
        val tvSide = tvCipher()
        val phoneSide = phoneCipher()
        val good = phoneSide.seal(byteArrayOf(1))

        assertFailsWith<PairingException> { tvSide.open(byteArrayOf(0, 1, 2)) }
        assertFailsWith<PairingException> { tvSide.open(good) }
        assertFailsWith<PairingException> { tvSide.seal(byteArrayOf(2)) }
    }

    @Test
    fun aReplayedMessageIsRejected() {
        val tvSide = tvCipher()
        val sealed = phoneCipher().seal(byteArrayOf(1))
        tvSide.open(sealed)

        assertFailsWith<PairingException> { tvSide.open(sealed) }
    }

    @Test
    fun aSideCannotOpenItsOwnMessages() {
        val phoneSide = phoneCipher()

        assertFailsWith<PairingException> { phoneSide.open(phoneSide.seal(byteArrayOf(1))) }
    }

    @Test
    fun aMessageSealedForAnotherTvIsRejected() {
        val other = PairingKeyPair.generate()
        val sealed = phoneCipher(tvKey = other.publicKey).seal(byteArrayOf(1))

        assertFailsWith<PairingException> { tvCipher().open(sealed) }
    }

    @Test
    fun aKeyThatIsNotOnTheCurveIsRejected() {
        val offCurve = phone.publicKey.copyOf().also { it[64] = (it[64].toInt() xor 1).toByte() }
        val wrongPrefix = phone.publicKey.copyOf().also { it[0] = 0x02 }

        assertFailsWith<PairingException> { tvCipher(peer = offCurve) }
        assertFailsWith<PairingException> { tvCipher(peer = wrongPrefix) }
        assertFailsWith<PairingException> { tvCipher(peer = phone.publicKey.copyOf(33)) }
    }

    @Test
    fun aSessionIdOfTheWrongSizeIsRejected() {
        assertFailsWith<PairingException> { tvCipher(id = ByteArray(8)) }
    }
}
