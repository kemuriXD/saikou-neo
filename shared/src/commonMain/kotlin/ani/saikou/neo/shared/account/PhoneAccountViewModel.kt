package ani.saikou.neo.shared.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** Where the phone's AniList account and a sign-in in progress are kept. Calls block, so make them off the main thread. */
public interface AccountStore {
    public fun readAccount(): SignedInAccount?

    public fun saveAccount(account: SignedInAccount)

    public fun clearAccount()

    public fun readAttempt(): SignInAttempt?

    public fun saveAttempt(attempt: SignInAttempt)

    public fun clearAttempt()
}

/** Whether the phone is signed in to AniList. */
public sealed interface AccountState {
    /** Reading the saved account. */
    public data object Loading : AccountState

    /** Welcome shows, with [problem] when the last sign-in didn't work. */
    public data class SignedOut(
        val problem: SignInProblem? = null,
    ) : AccountState

    /** Back from the browser, checking the token with AniList. */
    public data object Verifying : AccountState

    public data class SignedIn(
        val profile: AniListProfile,
    ) : AccountState
}

/** Why the phone isn't signed in, as Welcome tells it. */
public enum class SignInProblem {
    /** This build has no AniList client ID. */
    NOT_SET_UP,

    /** The sign-in took too long; start again. */
    EXPIRED,

    /** No connection while checking the token. */
    NO_NETWORK,

    /** AniList stopped accepting the saved token, or it ran out. */
    SESSION_ENDED,

    /** Anything else. */
    FAILED,
}

/**
 * Signs the phone in to AniList (#247). [signIn] sends anilist.co to [browser]; the browser comes back to
 * [redirectUri], which the app passes to [finishSignIn]. The token is checked with [viewer] before it's saved.
 */
public class PhoneAccountViewModel(
    private val clientId: String,
    private val redirectUri: String,
    private val store: AccountStore,
    private val viewer: suspend (AniListToken) -> AniListResult<AniListProfile>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = { Clock.System.now().epochSeconds },
    private val newState: () -> String = AniListSignIn::newState,
) : ViewModel() {
    private val mutableState = MutableStateFlow<AccountState>(AccountState.Loading)
    public val state: StateFlow<AccountState> = mutableState.asStateFlow()

    private val browserUrls = Channel<String>(Channel.BUFFERED)

    /** Addresses to open in the browser, each once. */
    public val browser: Flow<String> = browserUrls.receiveAsFlow()

    init {
        viewModelScope.launch {
            val saved = withContext(io) { store.readAccount() }
            when {
                saved == null -> {
                    mutableState.value = AccountState.SignedOut()
                }

                saved.token.expiresAtEpochSeconds <= now() -> {
                    forget(SignInProblem.SESSION_ENDED)
                }

                else -> {
                    mutableState.value = AccountState.SignedIn(saved.profile)
                    check(saved.token, signingIn = false)
                }
            }
        }
    }

    public fun signIn() {
        if (clientId.isBlank()) {
            mutableState.value = AccountState.SignedOut(SignInProblem.NOT_SET_UP)
            return
        }
        viewModelScope.launch {
            val attempt = SignInAttempt(newState(), now())
            withContext(io) { store.saveAttempt(attempt) }
            browserUrls.send(AniListSignIn.authorizeUrl(clientId, attempt.state))
        }
    }

    /** Reads the browser's answer at [uri]. An answer to some other sign-in changes nothing. */
    public fun finishSignIn(uri: String) {
        viewModelScope.launch {
            val attempt = withContext(io) { store.readAttempt() }
            val result = AniListSignIn.parseCallback(uri, redirectUri, attempt, now())
            if (result == CallbackResult.Unrelated) return@launch
            withContext(io) { store.clearAttempt() }
            when (result) {
                is CallbackResult.Token -> {
                    mutableState.value = AccountState.Verifying
                    check(result.token, signingIn = true)
                }

                CallbackResult.Declined -> {
                    mutableState.value = AccountState.SignedOut()
                }

                CallbackResult.Expired -> {
                    mutableState.value = AccountState.SignedOut(SignInProblem.EXPIRED)
                }

                CallbackResult.Invalid, CallbackResult.Unrelated -> {
                    mutableState.value = AccountState.SignedOut(SignInProblem.FAILED)
                }
            }
        }
    }

    /**
     * Asks AniList who [token] belongs to. A new sign-in is saved once AniList answers. A saved one stays signed in
     * through a network problem, and is forgotten only when AniList refuses the token.
     */
    private suspend fun check(
        token: AniListToken,
        signingIn: Boolean,
    ) {
        when (val answer = viewer(token)) {
            is AniListResult.Success -> {
                val saved = withContext(io) { runCatching { store.saveAccount(SignedInAccount(answer.value, token)) } }.isSuccess
                mutableState.value =
                    when {
                        saved || !signingIn -> AccountState.SignedIn(answer.value)

                        // Signed in now but not after a restart would be a surprise, so say it didn't work.
                        else -> AccountState.SignedOut(SignInProblem.FAILED)
                    }
            }

            is AniListResult.Failure -> {
                val refused = answer.error.let { it is AniListError.Http && it.statusCode in REFUSED }
                when {
                    !signingIn -> if (refused) forget(SignInProblem.SESSION_ENDED)
                    answer.error is AniListError.Network -> mutableState.value = AccountState.SignedOut(SignInProblem.NO_NETWORK)
                    else -> mutableState.value = AccountState.SignedOut(SignInProblem.FAILED)
                }
            }
        }
    }

    private suspend fun forget(problem: SignInProblem) {
        withContext(io) { store.clearAccount() }
        mutableState.value = AccountState.SignedOut(problem)
    }

    private companion object {
        /** AniList answers 400 "Invalid token" for a token it no longer accepts; 401 and 403 mean the same. */
        val REFUSED = setOf(400, 401, 403)
    }
}
