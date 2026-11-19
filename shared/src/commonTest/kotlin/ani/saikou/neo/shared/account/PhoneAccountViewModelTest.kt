package ani.saikou.neo.shared.account

import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneAccountViewModelTest {
    private val main = StandardTestDispatcher()
    private val redirect = "ani.saikou.neo.preview://oauth/anilist"
    private val kemuri = AniListProfile(5412345, "Kemuri", "https://anilist.co/user/5412345")
    private var now = 1_000L

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        store: AccountStore = MemoryAccountStore(),
        clientId: String = "12345",
        viewer: suspend (AniListToken) -> AniListResult<AniListProfile> = { AniListResult.Success(kemuri) },
    ): PhoneAccountViewModel =
        PhoneAccountViewModel(
            clientId = clientId,
            redirectUri = redirect,
            store = store,
            viewer = viewer,
            io = main,
            now = { now },
            newState = { "state-1" },
        ).also { advanceUntilIdle() }

    private fun callback(state: String = "state-1") = "$redirect#access_token=tok&token_type=Bearer&expires_in=3600&state=$state"

    @Test
    fun withNothingSavedThePhoneIsSignedOut() =
        runTest(main) {
            assertEquals(AccountState.SignedOut(), viewModel().state.value)
        }

    @Test
    fun signInOpensAniListInTheBrowser() =
        runTest(main) {
            val store = MemoryAccountStore()
            val model = viewModel(store)

            model.signIn()
            advanceUntilIdle()

            assertEquals(
                "https://anilist.co/api/v2/oauth/authorize?client_id=12345&response_type=token&state=state-1",
                model.browser.first(),
            )
            assertEquals(SignInAttempt("state-1", 1_000), store.attempt)
        }

    @Test
    fun withoutAClientIdSignInSaysItIsNotSetUp() =
        runTest(main) {
            val model = viewModel(clientId = "")

            model.signIn()
            advanceUntilIdle()

            assertEquals(AccountState.SignedOut(SignInProblem.NOT_SET_UP), model.state.value)
        }

    @Test
    fun comingBackFromTheBrowserChecksTheTokenThenSavesTheAccount() =
        runTest(main) {
            val store = MemoryAccountStore()
            val seen = mutableListOf<String>()
            val model =
                viewModel(store) { token ->
                    seen += token.accessToken
                    AniListResult.Success(kemuri)
                }
            model.signIn()
            advanceUntilIdle()

            model.finishSignIn(callback())
            advanceUntilIdle()

            assertEquals(AccountState.SignedIn(kemuri, justSignedIn = true), model.state.value)
            assertEquals(listOf("tok"), seen)
            assertEquals(kemuri, store.account?.profile)
            assertEquals(1_000 + 3_600L, store.account?.token?.expiresAtEpochSeconds)
            assertNull(store.attempt)
        }

    @Test
    fun confirmingTheSignInMovesOnFromTheSignedInScreen() =
        runTest(main) {
            val model = viewModel()
            model.signIn()
            advanceUntilIdle()
            model.finishSignIn(callback())
            advanceUntilIdle()

            model.confirmSignIn()

            assertEquals(AccountState.SignedIn(kemuri), model.state.value)
        }

    @Test
    fun confirmingWhileSignedOutChangesNothing() =
        runTest(main) {
            val model = viewModel()

            model.confirmSignIn()

            assertEquals(AccountState.SignedOut(), model.state.value)
        }

    @Test
    fun dismissingAProblemGoesBackToPlainWelcome() =
        runTest(main) {
            val model = viewModel(clientId = "")
            model.signIn()
            advanceUntilIdle()

            model.dismissProblem()

            assertEquals(AccountState.SignedOut(), model.state.value)
        }

    @Test
    fun tryingAgainClearsTheLastProblem() =
        runTest(main) {
            val model = viewModel { AniListResult.Failure(AniListError.Network("offline")) }
            model.signIn()
            advanceUntilIdle()
            model.finishSignIn(callback())
            advanceUntilIdle()

            model.signIn()
            advanceUntilIdle()

            assertEquals(AccountState.SignedOut(), model.state.value)
        }

    @Test
    fun aSavedAccountSignsInStraightAwayAfterARestart() =
        runTest(main) {
            val store = MemoryAccountStore(account = SignedInAccount(kemuri, AniListToken("tok", 5_000)))

            val model = viewModel(store) { AniListResult.Failure(AniListError.Network("offline")) }

            assertEquals(AccountState.SignedIn(kemuri), model.state.value)
            assertTrue(store.account != null)
        }

    @Test
    fun aRenamedAccountShowsItsNewName() =
        runTest(main) {
            val store = MemoryAccountStore(account = SignedInAccount(kemuri, AniListToken("tok", 5_000)))
            val renamed = kemuri.copy(name = "Kemuri2")

            val model = viewModel(store) { AniListResult.Success(renamed) }

            assertEquals(AccountState.SignedIn(renamed), model.state.value)
            assertEquals(renamed, store.account?.profile)
        }

    @Test
    fun aTokenAniListNoLongerAcceptsSignsThePhoneOut() =
        runTest(main) {
            val store = MemoryAccountStore(account = SignedInAccount(kemuri, AniListToken("tok", 5_000)))

            val model = viewModel(store) { AniListResult.Failure(AniListError.Http(400)) }

            assertEquals(AccountState.SignedOut(SignInProblem.SESSION_ENDED), model.state.value)
            assertNull(store.account)
        }

    @Test
    fun anExpiredTokenIsForgotten() =
        runTest(main) {
            now = 6_000
            val store = MemoryAccountStore(account = SignedInAccount(kemuri, AniListToken("tok", 5_000)))

            val model = viewModel(store)

            assertEquals(AccountState.SignedOut(SignInProblem.SESSION_ENDED), model.state.value)
            assertNull(store.account)
        }

    @Test
    fun sayingNoOnAniListGoesBackQuietly() =
        runTest(main) {
            val model = viewModel()
            model.signIn()
            advanceUntilIdle()

            model.finishSignIn("$redirect?error=access_denied&state=state-1")
            advanceUntilIdle()

            assertEquals(AccountState.SignedOut(), model.state.value)
        }

    @Test
    fun aCallbackForAnotherSignInChangesNothing() =
        runTest(main) {
            val store = MemoryAccountStore()
            val model = viewModel(store)
            model.signIn()
            advanceUntilIdle()

            model.finishSignIn(callback(state = "someone-else"))
            advanceUntilIdle()

            assertEquals(AccountState.SignedOut(), model.state.value)
            assertNull(store.account)
            assertEquals(SignInAttempt("state-1", 1_000), store.attempt)
        }

    @Test
    fun noConnectionWhileCheckingTheTokenSaysSo() =
        runTest(main) {
            val store = MemoryAccountStore()
            val model = viewModel(store) { AniListResult.Failure(AniListError.Network("offline")) }
            model.signIn()
            advanceUntilIdle()

            model.finishSignIn(callback())
            advanceUntilIdle()

            assertEquals(AccountState.SignedOut(SignInProblem.NO_NETWORK), model.state.value)
            assertNull(store.account)
        }

    @Test
    fun aBrokenAnswerSaysSignInFailed() =
        runTest(main) {
            val model = viewModel()
            model.signIn()
            advanceUntilIdle()

            model.finishSignIn("$redirect#error=server_error&state=state-1")
            advanceUntilIdle()

            assertEquals(AccountState.SignedOut(SignInProblem.FAILED), model.state.value)
        }

    @Test
    fun anAccountThatCanNotBeSavedSaysSo() =
        runTest(main) {
            val store =
                object : AccountStore by MemoryAccountStore() {
                    override fun saveAccount(account: SignedInAccount): Unit = throw IllegalStateException("Keystore failed")
                }
            val model = viewModel(store)
            model.signIn()
            advanceUntilIdle()

            model.finishSignIn(callback())
            advanceUntilIdle()

            assertEquals(AccountState.SignedOut(SignInProblem.NOT_SAVED), model.state.value)
        }

    @Test
    fun aTokenAniListRefusesWhileSigningInSaysSignInFailed() =
        runTest(main) {
            val store = MemoryAccountStore()
            val model = viewModel(store) { AniListResult.Failure(AniListError.Http(401)) }
            model.signIn()
            advanceUntilIdle()

            model.finishSignIn(callback())
            advanceUntilIdle()

            assertEquals(AccountState.SignedOut(SignInProblem.FAILED), model.state.value)
            assertNull(store.account)
        }

    @Test
    fun theAttemptSurvivesTheAppClosingWhileTheBrowserIsOpen() =
        runTest(main) {
            val store = MemoryAccountStore()
            viewModel(store).signIn()
            advanceUntilIdle()

            val reopened = viewModel(store)
            reopened.finishSignIn(callback())
            advanceUntilIdle()

            assertEquals(AccountState.SignedIn(kemuri, justSignedIn = true), reopened.state.value)
        }

    @Test
    fun signOutForgetsTheAccountAndGoesBackToWelcome() =
        runTest(main) {
            val store = MemoryAccountStore()
            store.saveAccount(SignedInAccount(kemuri, AniListToken("tok", now + 3_600)))
            val model = viewModel(store)
            assertEquals(AccountState.SignedIn(kemuri), model.state.value)

            model.signOut()
            advanceUntilIdle()

            assertNull(store.account)
            assertEquals(AccountState.SignedOut(), model.state.value)
        }

    @Test
    fun aBrowserAnswerAfterSigningOutChangesNothing() =
        runTest(main) {
            val store = MemoryAccountStore()
            store.saveAccount(SignedInAccount(kemuri, AniListToken("tok", now + 3_600)))
            val model = viewModel(store)
            model.signIn()
            advanceUntilIdle()

            model.signOut()
            advanceUntilIdle()
            model.finishSignIn(callback())
            advanceUntilIdle()

            assertNull(store.account)
            assertEquals(AccountState.SignedOut(), model.state.value)
        }

    @Test
    fun aCheckStillRunningWhenSigningOutDoesNotSignBackIn() =
        runTest(main) {
            val store = MemoryAccountStore()
            store.saveAccount(SignedInAccount(kemuri, AniListToken("tok", now + 3_600)))
            val answer = CompletableDeferred<AniListResult<AniListProfile>>()
            val model = viewModel(store) { answer.await() }

            model.signOut()
            advanceUntilIdle()
            answer.complete(AniListResult.Success(kemuri))
            advanceUntilIdle()

            assertNull(store.account)
            assertEquals(AccountState.SignedOut(), model.state.value)
        }
}

/** An [AccountStore] in memory. */
internal class MemoryAccountStore(
    var account: SignedInAccount? = null,
    var attempt: SignInAttempt? = null,
) : AccountStore {
    override fun readAccount(): SignedInAccount? = account

    override fun saveAccount(account: SignedInAccount) {
        this.account = account
    }

    override fun clearAccount() {
        account = null
    }

    override fun readAttempt(): SignInAttempt? = attempt

    override fun saveAttempt(attempt: SignInAttempt) {
        this.attempt = attempt
    }

    override fun clearAttempt() {
        attempt = null
    }
}
