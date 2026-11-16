package ani.saikou.neo.shared.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.account.AniListToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** A pairing step failed: a bad key, a message that didn't decrypt, a broken connection, or a TV that wasn't found. */
public class PairingException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** A connected TV, after the handshake. Nothing goes to it until [sendAccount]. */
public interface TvLink {
    /** The 6-digit code both screens show. */
    public val checkCode: String

    /** Sends the account and waits for the TV to say it's signed in. Throws [PairingException] if it doesn't. */
    public suspend fun sendAccount(token: AniListToken): PairedTvReply

    /** Drops the connection. The TV takes that as pairing stopped. */
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

    /** 3.8 The codes didn't match, so nothing was sent. */
    public data object Stopped : PairTvState
}

/**
 * Pairs the phone with a TV (#249). [start] connects; the account from [account] goes to the TV only in [confirm],
 * after the user says the check codes match. The TV is then saved in Your TVs ([tvs]).
 */
public class PairTvViewModel(
    private val connect: suspend (PairingRequest) -> TvLink,
    private val account: suspend () -> AniListToken?,
    private val store: PairedTvStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = { Clock.System.now().epochSeconds },
) : ViewModel() {
    private val mutableState = MutableStateFlow<PairTvState>(PairTvState.Idle)
    public val state: StateFlow<PairTvState> = mutableState.asStateFlow()

    private val mutableTvs = MutableStateFlow<List<PairedTv>>(emptyList())
    public val tvs: StateFlow<List<PairedTv>> = mutableTvs.asStateFlow()

    private var request: PairingRequest? = null
    private var link: TvLink? = null
    private var job: Job? = null

    init {
        viewModelScope.launch { mutableTvs.value = withContext(io) { store.readTvs() } }
    }

    /** Connects to the TV in [request], replacing any pairing in progress. */
    public fun start(request: PairingRequest) {
        this.request = request
        retry()
    }

    /** Try again on Couldn't reach the TV: connects to the same TV again. */
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
            }
    }

    /** It matches: sends the account, then saves the TV once it says it's signed in. */
    public fun confirm() {
        val shown = state.value as? PairTvState.CheckCode ?: return
        if (shown.sending) return
        val tv = link ?: return
        mutableState.value = shown.copy(sending = true)
        job =
            viewModelScope.launch {
                try {
                    val token = account() ?: throw PairingException("The phone isn't signed in")
                    val reply = tv.sendAccount(token)
                    val saved = mutableTvs.value.filterNot { it.id == reply.id } + PairedTv(reply.id, reply.name, now())
                    withContext(io) { store.saveTvs(saved) }
                    mutableTvs.value = saved
                    mutableState.value = PairTvState.SignedIn(reply.name)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    mutableState.value = PairTvState.Unreachable
                } finally {
                    // Close this attempt's connection only; a newer one may have taken its place.
                    tv.close()
                    if (link === tv) link = null
                }
            }
    }

    /** It doesn't match: drops the connection without sending anything. */
    public fun reject() {
        if (state.value !is PairTvState.CheckCode) return
        stop(PairTvState.Stopped)
    }

    /**
     * Cancel or Close: stops whatever is happening and sends nothing. Once the account is on its way it can't be
     * called back, so then it waits for the TV, to keep a TV that signed in from going missing from Your TVs.
     */
    public fun cancel() {
        if ((state.value as? PairTvState.CheckCode)?.sending == true) return
        request = null
        stop(PairTvState.Idle)
    }

    private fun stop(next: PairTvState) {
        job?.cancel()
        job = null
        closeLink()
        mutableState.value = next
    }

    private fun closeLink() {
        link?.close()
        link = null
    }

    override fun onCleared() {
        job?.cancel()
        closeLink()
    }
}
