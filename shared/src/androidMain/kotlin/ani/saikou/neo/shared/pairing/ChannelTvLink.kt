package ani.saikou.neo.shared.pairing

import ani.saikou.neo.shared.account.AniListToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * The messages sent over a [PairingChannel] once the user says the check codes match. Both are JSON, sealed by the
 * channel like any other message:
 *
 * - Phone to TV, only after It matches: `{"v":1,"type":"account","token":"<AniList token>","expires":<epoch seconds>}`
 * - TV to phone once it's signed in: `{"v":1,"type":"signed-in","id":"<TV ID>","name":"<TV name>"}`. The ID stays the
 *   same each time that TV pairs. ID and name are 1 to 64 characters. Anything else means the TV didn't sign in.
 *
 * If the codes don't match, or the user cancels, the phone closes the connection without sending anything.
 */
public object PairingMessages {
    private const val VERSION = 1
    private const val MAX_FIELD = 64

    /** The phone's message carrying [token]. */
    public fun account(token: AniListToken): ByteArray =
        message("account")
            .put("token", token.accessToken)
            .put("expires", token.expiresAtEpochSeconds)
            .toString()
            .encodeToByteArray()

    /** The token in the phone's message, or null if [bytes] isn't one. */
    public fun readAccount(bytes: ByteArray): AniListToken? =
        read(bytes, "account")?.let { json ->
            val token = json.optString("token").takeIf { it.isNotEmpty() } ?: return null
            val expires = json.optLong("expires", 0).takeIf { it > 0 } ?: return null
            AniListToken(token, expires)
        }

    /** The TV's answer once it's signed in. */
    public fun signedIn(reply: PairedTvReply): ByteArray =
        message("signed-in")
            .put("id", reply.id)
            .put("name", reply.name)
            .toString()
            .encodeToByteArray()

    /** The TV's answer, or null if [bytes] isn't a valid one. */
    public fun readSignedIn(bytes: ByteArray): PairedTvReply? =
        read(bytes, "signed-in")?.let { json ->
            val id = json.optString("id").takeIf { it.length in 1..MAX_FIELD } ?: return null
            val name = cleanName(json.optString("name")).takeIf { it.length in 1..MAX_FIELD } ?: return null
            PairedTvReply(id, name)
        }

    /**
     * The TV's name as the phone shows it: line breaks and other control characters become spaces, and invisible
     * format characters such as direction overrides go, so a name can't break or reorder the screen around it.
     */
    private fun cleanName(name: String): String =
        name
            .map { if (Character.getType(it) == Character.CONTROL.toInt()) ' ' else it }
            .filterNot { Character.getType(it) == Character.FORMAT.toInt() }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    private fun message(type: String) = JSONObject().put("v", VERSION).put("type", type)

    private fun read(
        bytes: ByteArray,
        type: String,
    ): JSONObject? =
        try {
            JSONObject(bytes.decodeToString()).takeIf { it.optInt("v") == VERSION && it.optString("type") == type }
        } catch (_: JSONException) {
            null
        }
}

/**
 * The phone's side of pairing (#249): connects to the TV with [connectToTv]. The account goes over only in
 * [TvLink.sendAccount], and the TV has [replyTimeoutMillis] to say it's signed in.
 */
public suspend fun connectPhoneToTv(
    host: String,
    port: Int,
    publicKey: ByteArray,
    sessionId: ByteArray,
    replyTimeoutMillis: Int = 30_000,
): TvLink = ChannelTvLink(connectToTv(host, port, publicKey, sessionId), replyTimeoutMillis)

private class ChannelTvLink(
    private val channel: PairingChannel,
    private val replyTimeoutMillis: Int,
) : TvLink {
    override val checkCode: String get() = channel.checkCode

    override suspend fun sendAccount(token: AniListToken): PairedTvReply =
        withContext(Dispatchers.IO) {
            channel.send(PairingMessages.account(token))
            PairingMessages.readSignedIn(channel.receive(replyTimeoutMillis))
                ?: throw PairingException("The TV didn't sign in")
        }

    override fun close() = channel.close()
}
