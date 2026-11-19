package ani.saikou.neo.shared.pairing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How a TV in Sign in can be found by its code (#381). While pairing is open the TV advertises itself on the local
 * network with DNS service discovery (mDNS):
 *
 * - service type [PAIRING_SERVICE_TYPE], with the service named by the code the TV shows, such as `K74Q9M`
 * - the port it listens on, and its address, as the service gives them
 * - TXT attributes `v=1`, `key` and `session`, written exactly as in the QR code ([PairingInvite])
 *
 * The phone browses for the typed code and reads the same fields a QR code carries, with the same checks: only
 * private and link-local IPv4 addresses. Anyone on the network could advertise a TV's code, so the check code both
 * screens show afterwards is what stops an impostor (ADR 0004).
 */
public const val PAIRING_SERVICE_TYPE: String = "_saikouneo._tcp"

/** How long the phone looks for a typed code before it says Couldn't reach the TV. */
public const val TV_LOOKUP_MILLIS: Long = 8_000

private val ADVERT_FIELDS = setOf("v", "key", "session")

/** The TXT attributes the TV advertises for this invite. Its address and port go in the service itself. */
public fun PairingInvite.advertAttributes(): Map<String, String> =
    mapOf(
        "v" to PairingInvite.VERSION,
        "key" to PairingInvite.base64.encode(publicKey),
        "session" to PairingInvite.base64.encode(sessionId),
    )

/** A service found on the network, before it's checked: its name, an address it gave, its port and TXT attributes. */
public data class AdvertisedTv(
    val name: String,
    val host: String,
    val port: Int,
    val attributes: Map<String, String>,
) {
    /** The TV to connect to, or null if this isn't a version 1 advert on the local network. */
    public fun toInvite(): PairingInvite? {
        if (attributes.keys != ADVERT_FIELDS || attributes["v"] != PairingInvite.VERSION) return null
        return PairingInvite.checked(host, port, attributes.getValue("key"), attributes.getValue("session"))
    }
}

/**
 * The first TV in [adverts] showing [code] whose advert passes [AdvertisedTv.toInvite], or null if none turns up within
 * [timeoutMillis].
 */
public suspend fun findAdvertisedTv(
    code: String,
    adverts: Flow<AdvertisedTv>,
    timeoutMillis: Long = TV_LOOKUP_MILLIS,
): PairingInvite? =
    withTimeoutOrNull(timeoutMillis) {
        adverts.mapNotNull { advert -> advert.takeIf { it.name.equals(code, ignoreCase = true) }?.toInvite() }.firstOrNull()
    }
