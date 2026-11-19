package ani.saikou.neo.shared.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.account.AniListToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** A pairing step failed: a bad key, a message that didn't decrypt, a broken connection, or a TV that wasn't found. */
public open class PairingException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** A connected TV, after the handshake. Nothing goes to it until [sendAccount]. */
public interface TvLink {
    /** The 6-digit code both screens show. */
    public val checkCode: String

    /**
     * Waits for the TV's next word: signed in, or stopped and why. Throws [PairingException] if the connection drops
     * or the TV says anything else. [close] ends the wait.
     */
    public suspend fun answer(): TvAnswer

    /** Sends the account. The TV's [answer] says how it went. */
    public suspend fun sendAccount(token: AniListToken)

    /** Tells the TV why pairing stopped, then drops the connection. */
    public suspend fun stop(reason: PairingStop)

    /** Drops the connection without a word. The TV takes that as a lost connection. */
    public fun close()
}

/** What the TV says once it's signed in: an ID that stays the same across pairings, and its name. */
public data class PairedTvReply(
    val id: String,
    val name: String,
)

/** A TV in Your TVs. */
public data class PairedTv(
    val id: String,
    val name: String,
    val pairedAtEpochSeconds: Long,
)

/** Where the phone keeps Your TVs. Calls block, so make them off the main thread. */
public interface PairedTvStore {
    public fun readTvs(): List<PairedTv>

    public fun saveTvs(tvs: List<PairedTv>)
}

/**
 * Connects to the TV in [request]. A typed code is found with [lookup] first; null means no TV on this network is
 * showing it. [connect] makes the encrypted connection (on Android, [connectToTv]).
 */
public suspend fun connectForPairing(
    request: PairingRequest,
    lookup: suspend (code: String) -> PairingInvite?,
    connect: suspend (host: String, port: Int, publicKey: ByteArray, sessionId: ByteArray) -> TvLink,
): TvLink {
    val invite =
        when (request) {
            is PairingRequest.Scanned -> request.invite
            is PairingRequest.Typed -> lookup(request.code) ?: throw PairingException("No TV is showing that code")
        }
    return connect(invite.host, invite.port, invite.publicKey, invite.sessionId)
}

/** Where pairing is, screen by screen (3.4 to 3.8). */
public sealed interface PairTvState {
    /** Not pairing. */
    public data object Idle : PairTvState

    /** 3.4 Connecting, until the TV answers. */
    public data object Connecting : PairTvState

    /** 3.5 Check the codes match. [sending] once It matches is tapped, until the TV answers. */
    public data class CheckCode(
        val code: String,
        val sending: Boolean = false,
    ) : PairTvState

    /** 3.6 The TV is signed in and in Your TVs. */
    public data class SignedIn(
        val tvName: String,
    ) : PairTvState

    /** 3.7 Couldn't reach the TV, or it dropped the connection. */
    public data object Unreachable : PairTvState

    /**
     * 3.8a to 3.8d: pairing stopped at the check code, and why. [PairingStop.NoMatch] is It doesn't match on this
     * phone; the others come from the TV.
     */
    public data class Stopped(
        val reason: PairingStop,
    ) : PairTvState
}

/**
 * Pairs the phone with a TV (#249). [start] connects; the account from [account] goes to the TV only in [confirm],
 * after the user says the check codes match. The TV is then saved in Your TVs ([tvs]). From the check code on, the
 * phone listens to the TV, so a TV that stops pairing shows why straight away (#414).
 */
public class PairTvViewModel(
    private val connect: suspend (PairingRequest) -> TvLink,
    private val account: suspend () -> AniListToken?,
    private val store: PairedTvStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = { Clock.System.now().epochSeconds },
    private val replyTimeoutMillis: Long = 30_000,
) : ViewModel() {
    private val mutableState = MutableStateFlow<PairTvState>(PairTvState.Idle)
    public val state: StateFlow<PairTvState> = mutableState.asStateFlow()

    private val mutableTvs = MutableStateFlow<List<PairedTv>>(emptyList())
    public val tvs: StateFlow<List<PairedTv>> = mutableTvs.asStateFlow()

    private var request: PairingRequest? = null
    private var link: TvLink? = null
    private var job: Job? = null
    private var sending: Job? = null

    init {
        viewModelScope.launch { mutableTvs.value = withContext(io) { store.readTvs() } }
    }

    /** Connects to the TV in [request], replacing any pairing in progress. */
    public fun start(request: PairingRequest) {
        this.request = request
        retry()
    }

    /** Try again or Start again: connects to the same TV again. */
    public fun retry() {
        val request = request ?: return
        stop(PairTvState.Connecting)
        job =
            viewModelScope.launch {
                val connected =
                    try {
                        connect(request)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        mutableState.value = PairTvState.Unreachable
                        return@launch
                    }
                if (!isActive) {
                    // Cancelled while the handshake finished: drop it rather than show its code.
                    connected.close()
                    return@launch
                }
                link = connected
                mutableState.value = PairTvState.CheckCode(connected.checkCode)
                listen(connected)
            }
    }

    /** Waits for the TV's answer, from the check code until it signs in or stops. */
    private suspend fun listen(tv: TvLink) {
        val answer =
            try {
                tv.answer()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        sending?.cancel()
        sending = null
        // Close this attempt's connection only; a newer one may have taken its place.
        tv.close()
        if (link !== tv) return
        link = null
        val sent = (state.value as? PairTvState.CheckCode)?.sending == true
        mutableState.value =
            when {
                answer is TvAnswer.SignedIn && sent -> save(answer.reply)

                // Only the phone has It doesn't match, so the TV saying it means something's wrong.
                answer is TvAnswer.Stopped && answer.reason != PairingStop.NoMatch -> PairTvState.Stopped(answer.reason)

                else -> PairTvState.Unreachable
            }
    }

    private suspend fun save(reply: PairedTvReply): PairTvState {
        val saved = mutableTvs.value.filterNot { it.id == reply.id } + PairedTv(reply.id, reply.name, now())
        try {
            withContext(io) { store.saveTvs(saved) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return PairTvState.Unreachable
        }
        mutableTvs.value = saved
        return PairTvState.SignedIn(reply.name)
    }

    /** It matches: sends the account. The TV then has [replyTimeoutMillis] to answer. */
    public fun confirm() {
        val shown = state.value as? PairTvState.CheckCode ?: return
        if (shown.sending) return
        val tv = link ?: return
        mutableState.value = shown.copy(sending = true)
        sending =
            viewModelScope.launch {
                try {
                    val token = account() ?: throw PairingException("The phone isn't signed in")
                    tv.sendAccount(token)
                    delay(replyTimeoutMillis)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Not sent: closing ends the wait below with Couldn't reach the TV.
                }
                // No answer in time, or nothing to send.
                tv.close()
            }
    }

    /**
     * Remove: forgets the TV with [id] on this phone. The TV keeps its account until it signs out in its own Settings
     * (ADR 0040).
     */
    public fun removeTv(id: String) {
        viewModelScope.launch {
            val kept = mutableTvs.value.filterNot { it.id == id }
            if (kept.size == mutableTvs.value.size) return@launch
            withContext(io) { store.saveTvs(kept) }
            mutableTvs.value = kept
        }
    }

    /** It doesn't match: tells the TV, without sending the account. */
    public fun reject() {
        val shown = state.value as? PairTvState.CheckCode ?: return
        if (shown.sending) return
        stop(PairTvState.Stopped(PairingStop.NoMatch), PairingStop.NoMatch)
    }

    /**
     * Cancel or Close: stops whatever is happening and sends no account; at the check code the TV hears it was
     * cancelled. Once the account is on its way it can't be called back, so then it waits for the TV, to keep a TV
     * that signed in from going missing from Your TVs.
     */
    public fun cancel() {
        val shown = state.value
        if ((shown as? PairTvState.CheckCode)?.sending == true) return
        request = null
        stop(PairTvState.Idle, PairingStop.Cancelled.takeIf { shown is PairTvState.CheckCode })
    }

    /** Stops listening and drops the connection, telling the TV [reason] first if there is one. */
    private fun stop(
        next: PairTvState,
        reason: PairingStop? = null,
    ) {
        job?.cancel()
        job = null
        sending?.cancel()
        sending = null
        val leaving = link
        link = null
        if (leaving != null) {
            // Not cancellable, so the TV is closed even if this screen goes away straight after.
            if (reason == null) leaving.close() else viewModelScope.launch(io + NonCancellable) { leaving.stop(reason) }
        }
        mutableState.value = next
    }

    override fun onCleared() {
        job?.cancel()
        sending?.cancel()
        link?.close()
        link = null
    }
}
