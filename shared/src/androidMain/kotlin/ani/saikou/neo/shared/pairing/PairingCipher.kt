package ani.saikou.neo.shared.pairing

import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Which end of a pairing this device is. The TV listens; the phone connects. */
public enum class PairingRole { TV, PHONE }

/** Size in bytes of a pairing session ID, which the TV picks at random each time pairing opens. */
public const val PAIRING_SESSION_ID_SIZE: Int = 32

/** A fresh random session ID. The TV shares it with the phone along with its public key (QR or lookup). */
public fun newPairingSessionId(): ByteArray = randomBytes(PAIRING_SESSION_ID_SIZE)

/** Size in bytes of the nonce each side adds to every connection attempt. */
public const val PAIRING_NONCE_SIZE: Int = 32

/** A fresh random nonce for one connection attempt. */
public fun newPairingNonce(): ByteArray = randomBytes(PAIRING_NONCE_SIZE)

/**
 * The phone's commitment to its key and nonce: SHA-256(`"saikou-neo/commit/v1"` || phone public key || phone
 * nonce). The phone sends it before it sees the TV's nonce, so it can't pick values that steer the check code.
 */
public fun pairingCommitment(
    phonePublicKey: ByteArray,
    phoneNonce: ByteArray,
): ByteArray = sha256("saikou-neo/commit/v1".encodeToByteArray() + phonePublicKey + phoneNonce)

private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

/** A P-256 key pair made for one pairing session. It's never stored. */
public class PairingKeyPair private constructor(
    internal val keyPair: KeyPair,
) {
    internal val ecPublic: ECPublicKey get() = keyPair.public as ECPublicKey

    /** The public key as an uncompressed SEC 1 point: `0x04 || X || Y`, 65 bytes. */
    public val publicKey: ByteArray get() = encodePoint(ecPublic.w)

    public companion object {
        public fun generate(): PairingKeyPair =
            PairingKeyPair(
                KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair(),
            )
    }
}

/**
 * The keys for one paired connection, and the short code both screens show.
 *
 * This is the crypto half of the pairing protocol (ADR 0004). [PairingChannel] describes how it's carried over
 * TCP. Another companion can interoperate by following these steps exactly.
 *
 * 1. Each side has a P-256 key pair. Public keys travel as 65-byte uncompressed points (`0x04 || X || Y`). A
 *    received key must be that size, start with `0x04`, and be on the curve, or pairing stops.
 * 2. `Z` = the 32-byte X coordinate from ECDH between your private key and the peer's public key.
 * 3. `PRK` = HMAC-SHA256(key = session ID, message = `Z`). The session ID is the TV's 32 random bytes.
 * 4. `T` = SHA-256(`"saikou-neo/pair/v1"` || session ID || TV public key || phone public key || TV nonce || phone
 *    nonce). The values are always in that order, whichever side you are. The nonces are 32 random bytes each
 *    side picks per attempt; [PairingChannel] covers how they're exchanged.
 * 5. `expand(label)` = HMAC-SHA256(key = `PRK`, message = UTF-8 `label` || `T` || `0x01`). This is one block of
 *    HKDF-Expand with `label || T` as the info.
 * 6. Phone-to-TV key = `expand("phone-to-tv")`. TV-to-phone key = `expand("tv-to-phone")`. Both are AES-256 keys.
 * 7. Check code = the first 4 bytes of `expand("check-code")` as an unsigned big-endian number, modulo 1,000,000,
 *    written as 6 digits with leading zeros. Both screens show it and the user confirms they match before any
 *    account is sent. A device in the middle has a different `Z` on each leg. The phone commits to its nonce
 *    before it sees the TV's ([pairingCommitment]), so neither leg's code can be chosen in advance and a device
 *    in the middle has a one in a million chance of the codes matching.
 * 8. Each message is AES-256-GCM with a 128-bit tag and no associated data. The 12-byte nonce is 4 zero bytes
 *    and then a 64-bit big-endian counter. Each direction has its own counter starting at 0 and adding 1 per
 *    message. A sealed message is the ciphertext followed by the tag; the nonce isn't sent.
 * 9. If a message doesn't decrypt, the connection is over: this cipher refuses everything after it.
 */
public class PairingCipher private constructor(
    private val sendKey: SecretKeySpec,
    private val receiveKey: SecretKeySpec,
    /** The 6-digit code to show and compare. */
    public val checkCode: String,
) {
    private var sent = 0L
    private var received = 0L
    private var broken = false

    /** Encrypts [plaintext] as the next message in this side's direction. */
    @Synchronized
    public fun seal(plaintext: ByteArray): ByteArray {
        if (broken) throw PairingException("The pairing connection was rejected")
        return aesGcm(Cipher.ENCRYPT_MODE, sendKey, sent++, plaintext)
    }

    /** Decrypts the peer's next message. Throws [PairingException] and stops the cipher if it was altered. */
    @Synchronized
    public fun open(sealed: ByteArray): ByteArray {
        if (broken) throw PairingException("The pairing connection was rejected")
        return try {
            aesGcm(Cipher.DECRYPT_MODE, receiveKey, received, sealed).also { received++ }
        } catch (e: GeneralSecurityException) {
            broken = true
            throw PairingException("A pairing message didn't decrypt", e)
        } catch (e: IllegalArgumentException) {
            // Some providers reject a too-short input this way instead.
            broken = true
            throw PairingException("A pairing message didn't decrypt", e)
        }
    }

    public companion object {
        /** Agrees keys between [own] and [peerPublicKey] for the session [sessionId] and this attempt's nonces. */
        public fun create(
            role: PairingRole,
            own: PairingKeyPair,
            peerPublicKey: ByteArray,
            sessionId: ByteArray,
            tvNonce: ByteArray,
            phoneNonce: ByteArray,
        ): PairingCipher {
            if (sessionId.size != PAIRING_SESSION_ID_SIZE) throw PairingException("Wrong session ID size")
            if (tvNonce.size != PAIRING_NONCE_SIZE || phoneNonce.size != PAIRING_NONCE_SIZE) throw PairingException("Wrong nonce size")
            val peer = decodePoint(peerPublicKey, own.ecPublic)
            val shared =
                try {
                    KeyAgreement.getInstance("ECDH").run {
                        init(own.keyPair.private)
                        doPhase(peer, true)
                        generateSecret()
                    }
                } catch (e: GeneralSecurityException) {
                    throw PairingException("Key agreement failed", e)
                }
            val prk = hmac(sessionId, shared)
            val (tvKey, phoneKey) = if (role == PairingRole.TV) own.publicKey to peerPublicKey else peerPublicKey to own.publicKey
            val transcript = sha256(PROTOCOL_LABEL.encodeToByteArray() + sessionId + tvKey + phoneKey + tvNonce + phoneNonce)

            fun expand(label: String) = hmac(prk, label.encodeToByteArray() + transcript + byteArrayOf(1))

            val phoneToTv = SecretKeySpec(expand("phone-to-tv"), "AES")
            val tvToPhone = SecretKeySpec(expand("tv-to-phone"), "AES")
            val code = BigInteger(1, expand("check-code").copyOf(4)).toLong() % 1_000_000
            return if (role == PairingRole.TV) {
                PairingCipher(tvToPhone, phoneToTv, code.toString().padStart(6, '0'))
            } else {
                PairingCipher(phoneToTv, tvToPhone, code.toString().padStart(6, '0'))
            }
        }

        private const val PROTOCOL_LABEL = "saikou-neo/pair/v1"
    }
}

private fun hmac(
    key: ByteArray,
    message: ByteArray,
): ByteArray =
    Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(message)
    }

private fun aesGcm(
    mode: Int,
    key: SecretKeySpec,
    counter: Long,
    input: ByteArray,
): ByteArray {
    val nonce = ByteArray(12)
    for (i in 0 until 8) nonce[11 - i] = (counter ushr (8 * i)).toByte()
    return Cipher.getInstance("AES/GCM/NoPadding").run {
        init(mode, key, GCMParameterSpec(128, nonce))
        doFinal(input)
    }
}

private const val COORDINATE_SIZE = 32

private fun encodePoint(point: ECPoint): ByteArray = byteArrayOf(0x04) + fixed(point.affineX) + fixed(point.affineY)

private fun fixed(value: BigInteger): ByteArray {
    val bytes = value.toByteArray().takeLast(COORDINATE_SIZE).toByteArray()
    return ByteArray(COORDINATE_SIZE - bytes.size) + bytes
}

/** Reads a peer key, checking it's a real P-256 point so a crafted key can't leak our private key. */
private fun decodePoint(
    bytes: ByteArray,
    sameCurveAs: ECPublicKey,
): ECPublicKey {
    if (bytes.size != 1 + 2 * COORDINATE_SIZE || bytes[0] != 0x04.toByte()) throw PairingException("Not a P-256 public key")
    val x = BigInteger(1, bytes.copyOfRange(1, 1 + COORDINATE_SIZE))
    val y = BigInteger(1, bytes.copyOfRange(1 + COORDINATE_SIZE, bytes.size))
    val params = sameCurveAs.params
    val p = (params.curve.field as ECFieldFp).p
    val onCurve =
        x < p && y < p &&
            y.multiply(y).mod(p) ==
            x
                .pow(3)
                .add(params.curve.a.multiply(x))
                .add(params.curve.b)
                .mod(p)
    if (!onCurve) throw PairingException("The public key isn't on P-256")
    return try {
        KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), params)) as ECPublicKey
    } catch (e: GeneralSecurityException) {
        throw PairingException("Not a P-256 public key", e)
    }
}
