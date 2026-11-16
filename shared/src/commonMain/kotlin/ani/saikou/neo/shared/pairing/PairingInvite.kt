package ani.saikou.neo.shared.pairing

import kotlin.io.encoding.Base64

/**
 * What the TV's QR code tells the phone: where the TV listens and the key and session ID pairing uses (ADR 0004).
 *
 * The QR code holds this text, with the fields in this order:
 *
 * `saikouneo://pair?v=1&host=<IPv4>&port=<port>&key=<public key>&session=<session ID>`
 *
 * - `host` is the TV's private or link-local IPv4 address in dotted form (10/8, 172.16/12, 192.168/16, 169.254/16).
 *   Names and public addresses are refused, so a QR code can't send the phone off the local network.
 * - `port` is 1 to 65,535.
 * - `key` is the TV's 65-byte uncompressed P-256 public key, and `session` its 32-byte session ID, both in URL-safe
 *   Base64 without padding.
 *
 * A phone reads only version 1, and refuses a code with any field missing, repeated or extra.
 */
public class PairingInvite(
    public val host: String,
    public val port: Int,
    public val publicKey: ByteArray,
    public val sessionId: ByteArray,
) {
    /** The text the TV puts in its QR code. */
    public fun toQrText(): String =
        "$PREFIX$VERSION&host=$host&port=$port&key=${base64.encode(publicKey)}&session=${base64.encode(sessionId)}"

    override fun equals(other: Any?): Boolean =
        other is PairingInvite &&
            host == other.host &&
            port == other.port &&
            publicKey.contentEquals(other.publicKey) &&
            sessionId.contentEquals(other.sessionId)

    override fun hashCode(): Int = ((host.hashCode() * 31 + port) * 31 + publicKey.contentHashCode()) * 31 + sessionId.contentHashCode()

    override fun toString(): String = "PairingInvite($host:$port)"

    public companion object {
        private const val PREFIX = "saikouneo://pair?v="
        private const val VERSION = "1"
        private val FIELDS = setOf("v", "host", "port", "key", "session")
        private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        /** The invite in a scanned QR code, or null if the code is anything else. */
        public fun parse(text: String): PairingInvite? {
            if (!text.startsWith(PREFIX)) return null
            val pairs = text.substringAfter('?').split('&').map { it.substringBefore('=', "") to it.substringAfter('=', "") }
            val fields = pairs.toMap()
            if (pairs.size != FIELDS.size || fields.keys != FIELDS || fields["v"] != VERSION) return null

            val host = fields.getValue("host").takeIf(::isLocalIpv4) ?: return null
            val port = fields.getValue("port").toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
            val key = decode(fields.getValue("key"))?.takeIf { it.size == PUBLIC_KEY_SIZE && it[0] == UNCOMPRESSED } ?: return null
            val session = decode(fields.getValue("session"))?.takeIf { it.size == PAIRING_SESSION_ID_BYTES } ?: return null
            return PairingInvite(host, port, key, session)
        }

        private fun decode(value: String): ByteArray? =
            try {
                base64.decode(value)
            } catch (_: IllegalArgumentException) {
                null
            }

        /** Dotted decimal with no leading zeros, in a private or link-local range. */
        private fun isLocalIpv4(host: String): Boolean {
            val parts = host.split('.')
            if (parts.size != 4) return false
            val octets =
                parts.map { part ->
                    if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return false
                    if (part.length > 1 && part[0] == '0') return false
                    part.toInt().takeIf { it <= 255 } ?: return false
                }
            val (a, b) = octets
            return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
        }

        private const val PUBLIC_KEY_SIZE = 65
        private const val UNCOMPRESSED: Byte = 0x04
        private const val PAIRING_SESSION_ID_BYTES = 32
    }
}
