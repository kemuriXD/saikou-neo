package ani.saikou.neo.shared.pairing

import android.content.Context
import android.os.Build
import ani.saikou.neo.shared.account.AniListToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.UUID

/**
 * Opens pairing on the TV (#116): a listener on the TV's local network address with a new key and session ID,
 * advertised under [code] so a phone can find it by the code alone (#381). Throws [PairingException] when the TV has no
 * private or link-local IPv4 address, so a phone couldn't reach it.
 */
public fun openTvPairing(
    context: Context,
    code: String,
    accountTimeoutMillis: Int = ACCOUNT_TIMEOUT_MILLIS,
): TvPairingSession {
    val address = localAddress() ?: throw PairingException("This TV isn't on a local network")
    val keyPair = PairingKeyPair.generate()
    val sessionId = newPairingSessionId()
    val listener = PairingListener(keyPair, sessionId, ServerSocket(0, BACKLOG, address))
    val invite = PairingInvite(address.hostAddress!!, listener.port, keyPair.publicKey, sessionId)
    val advert = advertisePairing(context, code, invite)
    return ListenerSession(listener, advert, invite, accountTimeoutMillis)
}

private class ListenerSession(
    private val listener: PairingListener,
    private val advert: AutoCloseable,
    override val invite: PairingInvite,
    private val accountTimeoutMillis: Int,
) : TvPairingSession {
    override suspend fun accept(onAttempt: (inProgress: Boolean) -> Unit): PhoneLink =
        ChannelPhoneLink(listener, listener.accept(onAttempt), accountTimeoutMillis)

    override fun close() {
        advert.close()
        listener.close()
    }
}

private class ChannelPhoneLink(
    private val listener: PairingListener,
    private val channel: PairingChannel,
    private val accountTimeoutMillis: Int,
) : PhoneLink {
    override val checkCode: String get() = channel.checkCode

    override suspend fun receiveAccount(): AniListToken =
        withContext(Dispatchers.IO) {
            try {
                PairingMessages.readAccount(channel.receive(accountTimeoutMillis))
                    ?: throw PairingException("The phone didn't send an account")
            } catch (e: PairingException) {
                // Left at the check code. A device that keeps doing this gets cut off (#410).
                listener.turnAway(channel)
                throw e
            }
        }

    override suspend fun replySignedIn(reply: PairedTvReply) = withContext(Dispatchers.IO) { channel.send(PairingMessages.signedIn(reply)) }

    override fun close() = channel.close()
}

/**
 * A private or link-local IPv4 address the phone can reach, the kind a [PairingInvite] accepts. VPN and other tunnel
 * interfaces are skipped, so the port never opens on a VPN provider's network.
 */
private fun localAddress(): InetAddress? =
    NetworkInterface
        .getNetworkInterfaces()
        ?.toList()
        .orEmpty()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint && TUNNELS.none(it.name::startsWith) }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .sortedBy { it.isLinkLocalAddress }
        .firstOrNull { it.isSiteLocalAddress || it.isLinkLocalAddress }

/**
 * What this TV tells a phone it paired with: an ID made once and kept, so the phone replaces this TV rather than
 * listing it twice, and its make and model, such as "Amazon AFTSSS".
 */
public fun thisTv(context: Context): PairedTvReply {
    val preferences = context.getSharedPreferences(IDENTITY_FILE, Context.MODE_PRIVATE)
    val id =
        preferences.getString(TV_ID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(TV_ID, it).commit()
        }
    return PairedTvReply(id, tvName(Build.MANUFACTURER, Build.MODEL))
}

/** "Make Model", without the make twice when the model already starts with it. */
internal fun tvName(
    make: String,
    model: String,
): String {
    val maker = make.trim().replaceFirstChar { it.uppercase() }
    val name = if (maker.isEmpty() || model.trim().startsWith(maker, ignoreCase = true)) model.trim() else "$maker ${model.trim()}"
    return name.ifEmpty { "TV" }.take(MAX_NAME)
}

// Long enough to compare the codes and choose It matches on the phone. Kept short because the TV takes no other phone
// while it shows the check code, so a device that connects and stays silent holds it only this long (#410).
private const val ACCOUNT_TIMEOUT_MILLIS = 90 * 1_000
private const val BACKLOG = 8
private val TUNNELS = listOf("tun", "ppp", "wg", "ipsec")
private const val IDENTITY_FILE = "tv_identity"
private const val TV_ID = "id"
private const val MAX_NAME = 64
