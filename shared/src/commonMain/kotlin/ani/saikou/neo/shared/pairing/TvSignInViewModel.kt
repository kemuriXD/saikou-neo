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
import kotlinx.coroutines.ensureActive
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
private const val TV_CODE_LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZ"
private const val TV_CODE_DIGITS = "23456789"

/** A new code for the TV to show: six different characters, with at least one letter and one digit. */
public fun newTvCode(random: Random = Random.Default): String {
    val letter = TV_CODE_LETTERS[random.nextInt(TV_CODE_LETTERS.length)]
    val digit = TV_CODE_DIGITS[random.nextInt(TV_CODE_DIGITS.length)]
    val rest = (TV_CODE_LETTERS + TV_CODE_DIGITS).filter { it != letter && it != digit }.toList().shuffled(random)
    return (rest.take(TV_CODE_LENGTH - 2) + letter + digit).shuffled(random).joinToString("")
}

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

    /**
     * Waits for the account the phone sends once It matches is chosen. Throws [PairingStoppedException] when the phone
     * stops pairing, or when the TV stops waiting (after telling the phone), and [PairingException] if the connection
     * drops. Either way the phone counts as turned away.
     */
    public suspend fun receiveAccount(): AniListToken

    /** Tells the phone this TV is signed in. */
    public suspend fun replySignedIn(reply: PairedTvReply)

    /** Tells the phone why pairing stopped, then drops it. It counts as turned away. */
    public suspend fun stop(reason: PairingStop)

    public fun close()
}

/** Where Sign in on the TV is, board by board (#245). */
public sealed interface TvSignInState {
    /** Opening the listener. */
    public data object Starting : TvSignInState

    /**
     * The QR code and [code], with [secondsLeft] until they run out. [note] says why the last phone left without
     * signing the TV in, so another try can use the same code (#410, #414).
     */
    public data class Waiting(
        val qrText: String,
        val code: String,
        val secondsLeft: Int,
        val note: TvPairingNote? = null,
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

    /** Pairing couldn't go on: too many failed attempts, or the account couldn't be saved. Nothing was saved. */
    public data object Stopped : TvSignInState

    /** Can't sign in yet: this TV isn't on a network, so no phone could reach it. Try again opens a new code. */
    public data object NoNetwork : TvSignInState
}

/** Why the last phone left the check code, shown under the code it can try again with. */
public enum class TvPairingNote {
    /** It doesn't match on the phone. */
    StoppedOnPhone,

    /** Pairing was closed on the phone before it sent an account. */
    CancelledOnPhone,

    /** AniList didn't accept the account the phone sent. */
    AniListRefused,

    /** The connection dropped without a word. */
    LostConnection,

    /** The phone didn't choose It matches in time. */
    TookTooLong,
}

/**
 * Signs the TV in to AniList from a phone (#116, ADR 0004). It makes a code, opens a pairing session under it at once
 * (so a phone can find the TV by typing the code, #381), shows the code for [TV_CODE_SECONDS], and once a phone connects shows the check code. If
 * that phone leaves without signing the TV in, the TV goes back to the same code (#410) and says why (#414). The account the phone sends
 * is checked with [viewer], saved in [store], and the phone hears back with [tv], this TV's stable ID and name. If AniList refuses it, the
 * phone hears that instead, and it counts against the phone like any other attempt that didn't finish.
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
    private var lastCode: String? = null

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
     * Not now, Cancel or Back, or the app going to the background: stops pairing, and a phone at the check code hears
     * it was cancelled. Unless the TV already signed in, Pairing stopped then shows, so coming back offers a new code.
     */
    public fun cancel() {
        val leaving = phone
        phone = null
        stop()
        leaving?.let { viewModelScope.launch(io) { it.stop(PairingStop.Cancelled) } }
        mutableState.update { if (it is TvSignInState.SignedIn) it else TvSignInState.Stopped }
    }

    private suspend fun pair() {
        var code = makeCode()
        while (code == lastCode) code = makeCode()
        lastCode = code
        val opened =
            try {
                // Opening isn't interrupted, so a listener opened just as pairing is cancelled is closed here.
                val caller = currentCoroutineContext()
                withContext(io + NonCancellable) { open(code).also { if (!caller.isActive) it.close() } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = if (e is NoNetworkException) TvSignInState.NoNetwork else TvSignInState.Stopped
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
        try {
            // The code stays open until a phone sends an account, so a phone that leaves at the check code (#410)
            // only sends the TV back to the same code.
            while (true) {
                val link = nextPhone(opened) { waiting } ?: return
                phone = link
                waiting = waiting.copy(note = null)
                mutableState.value = TvSignInState.CheckCode(link.checkCode)
                val note =
                    when (val heard = receiveAccount(link)) {
                        is Heard.Left -> {
                            heard.note
                        }

                        is Heard.Account -> {
                            val profile = check(heard.token)
                            if (profile == null) {
                                refuse(link)
                                TvPairingNote.AniListRefused
                            } else {
                                closeListener(opened, countdown)
                                mutableState.value = signIn(link, heard.token, profile)
                                return
                            }
                        }
                    }
                if (countdown.isCompleted) {
                    mutableState.value = TvSignInState.Expired(code)
                    return
                }
                waiting = waiting.copy(note = note)
                mutableState.value = waiting
            }
        } finally {
            closeListener(opened, countdown)
        }
    }

    /** Waits for a phone until the code runs out. Null, with the board set, when there won't be one. */
    private suspend fun nextPhone(
        opened: TvPairingSession,
        waiting: () -> TvSignInState.Waiting,
    ): PhoneLink? {
        val link =
            try {
                withTimeoutOrNull(waiting().secondsLeft * SECOND_MILLIS) {
                    // Called on another thread, so each change only applies to the board it expects.
                    opened.accept { inProgress ->
                        mutableState.update {
                            when {
                                inProgress && it is TvSignInState.Waiting -> TvSignInState.Connecting
                                !inProgress && it == TvSignInState.Connecting -> waiting()
                                else -> it
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Closing the listener for a new code also fails this wait. That's not Pairing stopped.
                currentCoroutineContext().ensureActive()
                mutableState.value = TvSignInState.Stopped
                return null
            }
        if (link == null) mutableState.value = TvSignInState.Expired(waiting().code)
        return link
    }

    /** What the phone at the check code did: sent an account, or left. */
    private sealed interface Heard {
        data class Account(
            val token: AniListToken,
        ) : Heard

        data class Left(
            val note: TvPairingNote,
        ) : Heard
    }

    private suspend fun receiveAccount(link: PhoneLink): Heard =
        try {
            Heard.Account(link.receiveAccount())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            link.close()
            if (phone === link) phone = null
            Heard.Left(
                when ((e as? PairingStoppedException)?.reason) {
                    PairingStop.NoMatch -> TvPairingNote.StoppedOnPhone
                    PairingStop.Cancelled -> TvPairingNote.CancelledOnPhone
                    PairingStop.TimedOut -> TvPairingNote.TookTooLong
                    // Only the TV refuses an account, so a phone saying so is as good as gone.
                    PairingStop.Refused, null -> TvPairingNote.LostConnection
                },
            )
        }

    /** The AniList profile for [token], or null if AniList doesn't accept it. */
    private suspend fun check(token: AniListToken): AniListProfile? =
        try {
            (viewer(token) as? AniListResult.Success)?.value
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    /** Tells the phone AniList refused its account. Any device could send a bad one, so it counts against it. */
    private suspend fun refuse(link: PhoneLink) {
        try {
            link.stop(PairingStop.Refused)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            link.close()
        }
        if (phone === link) phone = null
    }

    private fun closeListener(
        opened: TvPairingSession,
        countdown: Job,
    ) {
        countdown.cancel()
        // Close this code's listener only. After Get a new code, the next one may already be open.
        opened.close()
        if (session === opened) session = null
    }

    private suspend fun signIn(
        link: PhoneLink,
        token: AniListToken,
        profile: AniListProfile,
    ): TvSignInState =
        try {
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
            currentCoroutineContext().ensureActive()
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
