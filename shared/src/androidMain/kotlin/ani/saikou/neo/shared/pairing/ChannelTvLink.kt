package ani.saikou.neo.shared.pairing

import ani.saikou.neo.shared.account.AniListToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * The messages sent over a [PairingChannel] once both screens show the check code. All are JSON, sealed by the
 * channel like any other message:
 *
 * - Phone to TV, only after It matches: `{"v":1,"type":"account","token":"<AniList token>","expires":<epoch seconds>}`
 * - TV to phone once it's signed in: `{"v":1,"type":"signed-in","id":"<TV ID>","name":"<TV name>"}`. The ID stays the
 *   same each time that TV pairs. ID and name are 1 to 64 characters.
 * - Either way, when one side stops pairing (#414): `{"v":1,"type":"stopped","reason":"<reason>"}`, then it closes the
 *   connection. The reason is `no-match` (It doesn't match on the phone), `cancelled` (closed or cancelled on either
 *   side), `refused` (AniList didn't accept the account, TV to phone) or `timed-out` (the TV stopped waiting, TV to
 *   phone). A connection that closes without one was lost. Anything else from the TV means it didn't sign in.
 *
 * Add from my phone uses the same connection with its own messages, listed in [SourcesMessages].
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

    /** Says pairing stopped, and why. */
    public fun stopped(reason: PairingStop): ByteArray =
        message("stopped")
            .put("reason", STOP_REASONS.getValue(reason))
            .toString()
            .encodeToByteArray()

    /** Why the other side stopped, or null if [bytes] isn't a stop message with a known reason. */
    public fun readStopped(bytes: ByteArray): PairingStop? =
        read(bytes, "stopped")?.let { json ->
            val reason = json.optString("reason")
            STOP_REASONS.entries.firstOrNull { it.value == reason }?.key
        }

    private val STOP_REASONS =
        mapOf(
            PairingStop.NoMatch to "no-match",
            PairingStop.Cancelled to "cancelled",
            PairingStop.Refused to "refused",
            PairingStop.TimedOut to "timed-out",
        )

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

    internal fun message(type: String): JSONObject = JSONObject().put("v", VERSION).put("type", type)

    internal fun read(
        bytes: ByteArray,
        type: String,
    ): JSONObject? {
        val text = bytes.decodeToString()
        // The parser recurses, so deep nesting from the other side could overflow the stack and crash the app.
        if (nesting(text) > MAX_NESTING) return null
        return try {
            JSONObject(text).takeIf { it.optInt("v") == VERSION && it.optString("type") == type }
        } catch (_: JSONException) {
            null
        }
    }

    private const val MAX_NESTING = 8

    /** How deep the arrays and objects in [json] go, not counting brackets inside strings. */
    private fun nesting(json: String): Int {
        var depth = 0
        var deepest = 0
        var inString = false
        var escaped = false
        for (char in json) {
            when {
                escaped -> escaped = false
                inString && char == '\\' -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' || char == '[' -> deepest = maxOf(deepest, ++depth)
                char == '}' || char == ']' -> depth--
            }
        }
        return deepest
    }
}

/**
 * The phone's side of pairing (#249): connects to the TV with [connectToTv]. The account goes over only in
 * [TvLink.sendAccount]. [TvLink.answer] waits up to [answerTimeoutMillis], as long as a TV code lasts; the TV stops
 * waiting well before that and says so.
 */
public suspend fun connectPhoneToTv(
    host: String,
    port: Int,
    publicKey: ByteArray,
    sessionId: ByteArray,
    answerTimeoutMillis: Int = TV_CODE_SECONDS * 1_000,
): TvLink = ChannelTvLink(connectToTv(host, port, publicKey, sessionId), answerTimeoutMillis)

private class ChannelTvLink(
    private val channel: PairingChannel,
    private val answerTimeoutMillis: Int,
) : TvLink {
    override val checkCode: String get() = channel.checkCode

    override suspend fun answer(): TvAnswer =
        withContext(Dispatchers.IO) {
            val bytes = channel.receive(answerTimeoutMillis)
            PairingMessages.readSignedIn(bytes)?.let { TvAnswer.SignedIn(it) }
                ?: PairingMessages.readStopped(bytes)?.let { TvAnswer.Stopped(it) }
                ?: throw PairingException("The TV didn't sign in").also { channel.close() }
        }

    override suspend fun sendAccount(token: AniListToken) = withContext(Dispatchers.IO) { channel.send(PairingMessages.account(token)) }

    override suspend fun stop(reason: PairingStop) = withContext(Dispatchers.IO) { channel.sendStop(reason) }

    override fun close() = channel.close()
}

/** Sends the stop message if the connection is still there, then closes it. */
internal fun PairingChannel.sendStop(reason: PairingStop) {
    try {
        send(PairingMessages.stopped(reason))
    } catch (_: PairingException) {
        // Already gone: the other side sees a lost connection, which is as near as it can get.
    } finally {
        close()
    }
}
