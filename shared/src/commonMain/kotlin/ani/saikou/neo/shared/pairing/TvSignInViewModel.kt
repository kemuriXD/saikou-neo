package ani.saikou.neo.shared.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.account.AccountStore
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.account.AniListToken
import ani.saikou.neo.shared.account.SignedInAccount
import ani.saikou.neo.shared.catalogue.AniListResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** How long a TV code and its QR code last. */
public const val TV_CODE_SECONDS: Int = 600

// Letters and digits that can't be read as one another: no 0, O, 1, I or L.
private const val TV_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

/** A new code for the TV to show. */
public fun newTvCode(random: Random = Random.Default): String =
    String(CharArray(TV_CODE_LENGTH) { TV_CODE_ALPHABET[random.nextInt(TV_CODE_ALPHABET.length)] })

/** The TV listening for a phone: what goes in its QR code, and the phones that connect. */
public interface TvPairingSession {
    public val invite: PairingInvite

    /**
     * Waits for a phone to finish the handshake. [onAttempt] says when a phone starts one (true) and when every
     * attempt has failed again (false). Throws [PairingException] once pairing can't go on.
     */
    public suspend fun accept(onAttempt: (inProgress: Boolean) -> Unit): PhoneLink

    /** Stops listening and stops advertising the code. A phone already connected keeps its link. */
    public fun close()
}

/** A phone connected to the TV, after the handshake. */
public interface PhoneLink {
    /** The 6-digit code both screens show. */
    public val checkCode: String

    /** Waits for the account the phone sends once It matches is chosen. Throws [PairingException] if it doesn't come. */
    public suspend fun receiveAccount(): AniListToken

    /** Tells the phone this TV is signed in. */
    public suspend fun replySignedIn(reply: PairedTvReply)

    public fun close()
}

/** Where Sign in on the TV is, board by board (#245). */
public sealed interface TvSignInState {
    /** Opening the listener. */
    public data object Starting : TvSignInState

    /** The QR code and [code], with [secondsLeft] until they run out. */
    public data class Waiting(
        val qrText: String,
        val code: String,
        val secondsLeft: Int,
    ) : TvSignInState

    /** A phone is making the encrypted link. */
    public data object Connecting : TvSignInState

    /** The check code, until the phone sends the account. Only the phone has It matches. */
    public data class CheckCode(
        val code: String,
    ) : TvSignInState

    public data class SignedIn(
        val profile: AniListProfile,
    ) : TvSignInState

    /** [code] ran out before a phone used it. */
    public data class Expired(
        val code: String,
    ) : TvSignInState

    /** The connection closed or failed, or AniList refused the account. Nothing was saved. */
    public data object Stopped : TvSignInState
}

/**
 * Signs the TV in to AniList from a phone (#116, ADR 0004). It makes a code, opens a pairing session under it at once
 * (so a phone can find the TV by typing the code, #381), shows the code for [TV_CODE_SECONDS], and once a phone connects shows the check code. The account the phone sends is checked with
 * [viewer], saved in [store], and the phone hears back with [tv], this TV's stable ID and name.
 */
public class TvSignInViewModel(
    private val open: suspend (code: String) -> TvPairingSession,
    private val viewer: suspend (AniListToken) -> AniListResult<AniListProfile>,
    private val store: AccountStore,
    private val tv: PairedTvReply,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val makeCode: () -> String = { newTvCode() },
) : ViewModel() {
    private val mutableState = MutableStateFlow<TvSignInState>(TvSignInState.Starting)
    public val state: StateFlow<TvSignInState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var session: TvPairingSession? = null
    private var phone: PhoneLink? = null

    init {
        newCode()
    }

    /** Get a new code: drops this code and any phone on it, and starts again. */
    public fun newCode() {
        stop()
        mutableState.value = TvSignInState.Starting
        job = viewModelScope.launch { pair() }
    }

    /**
     * Not now, Cancel or Back, or the app going to the background: stops pairing and sends the phone nothing. Unless
     * the TV already signed in, Pairing stopped then shows, so coming back offers a new code.
     */
    public fun cancel() {
        stop()
        mutableState.update { if (it is TvSignInState.SignedIn) it else TvSignInState.Stopped }
    }

    private suspend fun pair() {
        val code = makeCode()
        val opened =
            try {
                // Opening isn't interrupted, so a listener opened just as pairing is cancelled is closed here.
                val caller = currentCoroutineContext()
                withContext(io + NonCancellable) { open(code).also { if (!caller.isActive) it.close() } }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.value = TvSignInState.Stopped
                return
            }
        session = opened
        var waiting = TvSignInState.Waiting(opened.invite.toQrText(), code, TV_CODE_SECONDS)
        mutableState.value = waiting
        val countdown =
            viewModelScope.launch {
                for (left in TV_CODE_SECONDS downTo 1) {
                    waiting = waiting.copy(secondsLeft = left)
                    mutableState.update { if (it is TvSignInState.Waiting) waiting else it }
                    delay(SECOND_MILLIS)
                }
            }
        val link =
            try {
                withTimeoutOrNull(TV_CODE_SECONDS * SECOND_MILLIS) {
                    // Called on another thread, so each change only applies to the board it expects.
                    opened.accept { inProgress ->
                        mutableState.update {
                            when {
                                inProgress && it is TvSignInState.Waiting -> TvSignInState.Connecting
                                !inProgress && it == TvSignInState.Connecting -> waiting
                                else -> it
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null.also { mutableState.value = TvSignInState.Stopped }
            } finally {
                countdown.cancel()
                closeSession()
            }
        if (link == null) {
            if (mutableState.value != TvSignInState.Stopped) mutableState.value = TvSignInState.Expired(waiting.code)
            return
        }
        phone = link
        mutableState.value = TvSignInState.CheckCode(link.checkCode)
        mutableState.value = signIn(link)
    }

    private suspend fun signIn(link: PhoneLink): TvSignInState =
        try {
            val token = link.receiveAccount()
            val profile = (viewer(token) as? AniListResult.Success)?.value ?: throw PairingException("AniList refused the account")
            withContext(io) { store.saveAccount(SignedInAccount(profile, token)) }
            try {
                link.replySignedIn(tv)
            } catch (_: PairingException) {
                // Signed in once saved. The phone not hearing back only means it shows Couldn't reach the TV.
            }
            TvSignInState.SignedIn(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            TvSignInState.Stopped
        } finally {
            link.close()
            if (phone === link) phone = null
        }

    private fun stop() {
        job?.cancel()
        job = null
        closeSession()
        phone?.close()
        phone = null
    }

    private fun closeSession() {
        session?.close()
        session = null
    }

    override fun onCleared() {
        stop()
    }

    private companion object {
        const val SECOND_MILLIS = 1_000L
    }
}
