package ani.saikou.neo.shared.pairing

import android.content.Context
import ani.saikou.neo.shared.sources.PickedSources
import ani.saikou.neo.shared.sources.SourcesPhone
import ani.saikou.neo.shared.sources.SourcesTv
import ani.saikou.neo.shared.sources.TvSource
import ani.saikou.neo.shared.sources.TvSources
import ani.saikou.neo.shared.sources.TvSourcesSession
import ani.saikou.neo.shared.sources.isCleanSourceLink
import ani.saikou.neo.shared.sources.isHiddenChar
import ani.saikou.neo.shared.sources.readSourceList
import ani.saikou.neo.shared.sources.sourceIdFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * The messages of Add from my phone (ADR 0042). The TV opens a board like Sign in's, and the phone connects with the
 * same handshake, encryption and frames ([PairingChannel]). Both screens then show [PairingChannel.checkCode], and
 * nothing else is sent until both people confirm it matches, on the phone and on the TV. All messages are JSON, sealed
 * by the channel like any other message:
 *
 * - Phone to TV, once It matches is chosen on the phone: `{"v":1,"type":"codes-match"}`. Anything else first, such as
 *   picks, drops the phone.
 * - TV to phone, once it has that and It matches is chosen on the TV too:
 *   `{"v":1,"type":"tv-sources","api":{"min":1,"max":4},"installed":[...]}`.
 *   `api` is the extension API versions the TV runs (ADR 0023). Each installed source is
 *   `{"id":"<id>","version":"<version>","list":true}`, where `list` says whether a list link is saved with it (ADR
 *   0046). At most 200 sources.
 * - Phone to TV, with the ticked entries of a list: `{"v":1,"type":"sources","list":"<link>","sources":[...]}`. Each
 *   entry has `id`, `name`, `version`, `api` and `apk`, as in a list (ADR 0025). `list` is the link the list came from;
 *   a short code is sent as the link it led to. For a link that isn't a list, the phone sends
 *   `{"v":1,"type":"sources","apk":"<link>"}` instead.
 * - TV to phone once it has them: `{"v":1,"type":"sources-received"}`. Both sides then close the connection.
 * - Either way, a stop message as in [PairingMessages]: `no-match` when the codes don't match on either side,
 *   `cancelled` from either side, `timed-out` when the TV stops waiting for the phone's It matches or picks, or
 *   `refused` from the TV when it can't use the picks.
 *
 * The phone reads the TV's messages as they come, from the handshake on, so it hears a stop or a dropped connection
 * while the user is still on the check code or picking, and sends nothing after it.
 *
 * The TV never trusts the phone's checks. A message over 65,000 bytes or with more than 256 brackets is refused. So is
 * anything but 1 to 50 valid list entries with distinct ids, and a list or APK link the TV wouldn't take typed, longer
 * than 2,000 characters or with hidden characters. Any of these drops the phone. Install on the TV checks each entry
 * again before it downloads anything.
 */
internal object SourcesMessages {
    private const val MAX_MESSAGE = 65_000
    private const val MAX_INSTALLED = 200
    private const val MAX_TEXT = 60

    fun tvSources(tv: TvSources): ByteArray {
        val installed = JSONArray()
        tv.installed.take(MAX_INSTALLED).forEach {
            installed.put(JSONObject().put("id", it.id).put("version", it.version.take(MAX_TEXT)).put("list", it.hasList))
        }
        val api = JSONObject().put("min", tv.api.first).put("max", tv.api.last)
        return PairingMessages
            .message("tv-sources")
            .put("api", api)
            .put("installed", installed)
            .toString()
            .encodeToByteArray()
    }

    /** What the TV has, or null if [bytes] isn't a valid report. */
    fun readTvSources(bytes: ByteArray): TvSources? =
        try {
            readJson(bytes, "tv-sources")?.let { json ->
                val api = json.getJSONObject("api")
                val range = api.getInt("min")..api.getInt("max")
                val array = json.getJSONArray("installed")
                if (range.first < 1 || range.isEmpty() || array.length() > MAX_INSTALLED) return null
                val installed =
                    List(array.length()) { i ->
                        val source = array.getJSONObject(i)
                        val id = source.getString("id").takeIf { it.matches(sourceIdFormat) } ?: return null
                        // The phone shows it in "update from", so it can't hide or reorder text.
                        val version =
                            source.getString("version").takeIf { it.length in 1..MAX_TEXT && it.none(::isHiddenChar) } ?: return null
                        TvSource(id, version, source.optBoolean("list"))
                    }
                TvSources(installed, range)
            }
        } catch (_: JSONException) {
            null
        }

    /** The phone's picks. Throws [PairingException] when they're too big to send in one message. */
    fun picked(picked: PickedSources): ByteArray {
        val json = PairingMessages.message("sources")
        when (picked) {
            is PickedSources.OneApk -> {
                json.put("apk", picked.apk)
            }

            is PickedSources.FromList -> {
                val sources = JSONArray()
                picked.sources.forEach {
                    sources.put(
                        JSONObject()
                            .put("id", it.id)
                            .put("name", it.name)
                            .put("version", it.version)
                            .put("api", it.api)
                            .put("apk", it.apk),
                    )
                }
                json.put("list", picked.list).put("sources", sources)
            }
        }
        return json.toString().encodeToByteArray().takeIf { it.size <= MAX_MESSAGE }
            ?: throw PairingException("Too many sources to send at once")
    }

    /** The phone's picks, checked as the TV checks a typed list, or null if [bytes] isn't a valid message. */
    fun readPicked(bytes: ByteArray): PickedSources? =
        try {
            readJson(bytes, "sources")?.let { json ->
                if (json.has("apk")) {
                    val apk = json.getString("apk")
                    return if (json.has("list") || json.has("sources") || !isCleanSourceLink(apk)) null else PickedSources.OneApk(apk)
                }
                val list = json.getString("list").takeIf(::isCleanSourceLink) ?: return null
                val sent = json.getJSONArray("sources").length()
                // Read with the list rules. An entry they leave out, or one past the limit, refuses the whole message.
                val read =
                    readSourceList(bytes)?.takeIf { sent > 0 && it.size == sent && it.all { source -> isCleanSourceLink(source.apk) } }
                        ?: return null
                PickedSources.FromList(list, read.map { it.copy(icon = null) })
            }
        } catch (_: JSONException) {
            null
        }

    fun codesMatch(): ByteArray = PairingMessages.message("codes-match").toString().encodeToByteArray()

    fun isCodesMatch(bytes: ByteArray): Boolean = readJson(bytes, "codes-match") != null

    fun received(): ByteArray = PairingMessages.message("sources-received").toString().encodeToByteArray()

    fun isReceived(bytes: ByteArray): Boolean = readJson(bytes, "sources-received") != null

    private fun readJson(
        bytes: ByteArray,
        type: String,
    ): JSONObject? = if (bytes.size > MAX_MESSAGE) null else PairingMessages.read(bytes, type)
}

/**
 * Opens Add from my phone on the TV: a listener advertised under [code], as [openTvPairing] opens Sign in. A phone has
 * [matchTimeoutMillis] to say the codes match, then [pickTimeoutMillis] to send its picks. After either, the TV tells
 * it so and stops waiting.
 */
public fun openTvSourcesPairing(
    context: Context,
    code: String,
    matchTimeoutMillis: Int = MATCH_TIMEOUT_MILLIS,
    pickTimeoutMillis: Int = PICK_TIMEOUT_MILLIS,
): TvSourcesSession {
    val open = listen(context, code)
    return object : TvSourcesSession {
        override val invite = open.invite

        override suspend fun accept(onAttempt: (inProgress: Boolean) -> Unit): SourcesPhone =
            ChannelSourcesPhone(open.listener, open.listener.accept(onAttempt), matchTimeoutMillis, pickTimeoutMillis)

        override fun close() = open.close()
    }
}

/** A phone on the TV's board. One that leaves without sending valid picks counts as turned away. */
internal class ChannelSourcesPhone(
    private val listener: PairingListener,
    private val channel: PairingChannel,
    private val matchTimeoutMillis: Int,
    private val pickTimeoutMillis: Int,
) : SourcesPhone {
    @Volatile private var matched = false

    @Volatile private var told = false

    override val checkCode: String get() = channel.checkCode

    override suspend fun receiveMatch() {
        listener.receiveFrom(channel, matchTimeoutMillis, "The phone didn't say the codes match") { bytes ->
            Unit.takeIf { SourcesMessages.isCodesMatch(bytes) }
        }
        matched = true
    }

    override suspend fun sendTvSources(tv: TvSources) =
        withContext(Dispatchers.IO) {
            if (!matched) throw PairingException("The phone hasn't said the codes match")
            try {
                channel.send(SourcesMessages.tvSources(tv))
                told = true
            } catch (e: PairingException) {
                listener.turnAway(channel)
                throw e
            }
        }

    override suspend fun receivePicked(): PickedSources {
        if (!told) throw PairingException("The phone hasn't heard what the TV has")
        // Picks it can't use are refused out loud, so the phone says the TV didn't take them, not that it lost the TV.
        return listener.receiveFrom(
            channel,
            pickTimeoutMillis,
            "The phone sent sources this TV can't use",
            PairingStop.Refused,
            SourcesMessages::readPicked,
        )
    }

    override suspend fun replyReceived() = withContext(Dispatchers.IO) { channel.send(SourcesMessages.received()) }

    override suspend fun stop(reason: PairingStop) =
        withContext(Dispatchers.IO) {
            try {
                channel.sendStop(reason)
            } finally {
                listener.turnAway(channel)
            }
        }

    override fun close() = channel.close()
}

/**
 * The phone's side of Add from my phone: connects to the TV's board with [connectToTv]. The wait for what the TV has
 * lasts up to [answerTimeoutMillis], and the wait for the TV to take the picks up to [receivedTimeoutMillis].
 */
public suspend fun connectPhoneForSources(
    host: String,
    port: Int,
    publicKey: ByteArray,
    sessionId: ByteArray,
    answerTimeoutMillis: Int = ANSWER_TIMEOUT_MILLIS,
    receivedTimeoutMillis: Int = RECEIVED_TIMEOUT_MILLIS,
): SourcesTv = ChannelSourcesTv(connectToTv(host, port, publicKey, sessionId), answerTimeoutMillis, receivedTimeoutMillis)

/**
 * The TV's board on the phone. From the handshake on, one reader takes the TV's messages in order: what the TV has,
 * then that it took the picks, each only in its turn. A stop, a dropped connection or anything out of turn ends the
 * session there and then, so [awaitEnd] hears it while the user is still on the check code or picking, and nothing more
 * is sent.
 */
internal class ChannelSourcesTv(
    internal val channel: PairingChannel,
    private val answerTimeoutMillis: Int,
    private val receivedTimeoutMillis: Int = RECEIVED_TIMEOUT_MILLIS,
) : SourcesTv {
    /** A message from the TV, in its turn. */
    private sealed interface FromTv {
        class Sources(
            val tv: TvSources,
        ) : FromTv

        data object Received : FromTv
    }

    @Volatile private var confirmed = false

    @Volatile private var heard = false

    @Volatile private var sentPicks = false

    // This phone stopped or closed, so the reader's end is its own doing, not the TV's.
    @Volatile private var closedHere = false

    // How the TV ended the session, once it has.
    @Volatile private var ended: PairingException? = null

    private val messages = Channel<FromTv>(Channel.UNLIMITED)
    private val end = CompletableDeferred<PairingException>()

    override val checkCode: String get() = channel.checkCode

    init {
        thread(isDaemon = true, name = "Add from my phone") { read() }
    }

    override suspend fun confirmCodes() =
        withContext(Dispatchers.IO) {
            ended?.let { throw it }
            // Before sending, so an answer that comes straight back is in its turn.
            confirmed = true
            channel.send(SourcesMessages.codesMatch())
        }

    override suspend fun receiveTvSources(): TvSources {
        if (!confirmed) throw PairingException("The codes haven't been confirmed")
        val sources = next("The TV didn't say what it has", answerTimeoutMillis) as FromTv.Sources
        heard = true
        return sources.tv
    }

    override suspend fun send(picked: PickedSources) {
        if (!heard) throw PairingException("The TV hasn't said what it has")
        val message = SourcesMessages.picked(picked)
        withContext(Dispatchers.IO) {
            ended?.let { throw it }
            sentPicks = true
            channel.send(message)
        }
        next("The TV didn't take the sources", receivedTimeoutMillis)
        close()
    }

    override suspend fun awaitEnd(): PairingException = end.await()

    override suspend fun stop(reason: PairingStop) =
        withContext(Dispatchers.IO) {
            closedHere = true
            // A TV that has gone hears nothing more.
            if (ended == null) channel.sendStop(reason) else channel.close()
        }

    override fun close() {
        closedHere = true
        channel.close()
    }

    /** The TV's next message, within [timeoutMillis]. Throws how the TV ended the session, or [missing] if it's silent. */
    private suspend fun next(
        missing: String,
        timeoutMillis: Int,
    ): FromTv {
        val result = withTimeoutOrNull(timeoutMillis.toLong()) { messages.receiveCatching() }
        result?.getOrNull()?.let { return it }
        if (result == null) close()
        throw ended ?: PairingException(missing)
    }

    /** The only reader of [channel], on its own thread for as long as the connection lasts. */
    private fun read() {
        val failure =
            try {
                readUntilReceived()
                return
            } catch (e: PairingException) {
                e
            } catch (e: RuntimeException) {
                PairingException("The TV sent something that couldn't be read", e)
            }
        channel.close()
        ended = failure
        messages.close()
        if (!closedHere) end.complete(failure)
    }

    /** Hands on each of the TV's messages in its turn, until it takes the picks. Throws when the session ends first. */
    private fun readUntilReceived() {
        var gotSources = false
        while (true) {
            val bytes = channel.receive(0)
            PairingMessages.readStopped(bytes)?.let { throw PairingStoppedException(it) }
            if (!gotSources) {
                // What the TV has only counts once the phone confirmed the codes (ADR 0058).
                val tv = SourcesMessages.readTvSources(bytes)?.takeIf { confirmed } ?: throw PairingException(OUT_OF_TURN)
                gotSources = true
                messages.trySend(FromTv.Sources(tv))
            } else if (sentPicks && SourcesMessages.isReceived(bytes)) {
                messages.trySend(FromTv.Received)
                // Done. The phone closes the connection itself.
                messages.close()
                return
            } else {
                throw PairingException(OUT_OF_TURN)
            }
        }
    }

    private companion object {
        const val OUT_OF_TURN = "The TV sent something out of turn"
    }
}

// As long as Sign in gives a phone at the check code. The board takes no other phone meanwhile, and a device that keeps
// holding it gets cut off (#410).
private const val MATCH_TIMEOUT_MILLIS = 90 * 1_000

// Long enough to paste a link and tick a few sources.
private const val PICK_TIMEOUT_MILLIS = 5 * 60 * 1_000

// The TV sends what it has only once It matches is chosen there too, so the phone waits as long as a code lasts.
private const val ANSWER_TIMEOUT_MILLIS = TV_CODE_SECONDS * 1_000

// The TV answers as soon as it has the picks, so a phone left waiting longer than this has lost it.
private const val RECEIVED_TIMEOUT_MILLIS = 30 * 1_000
