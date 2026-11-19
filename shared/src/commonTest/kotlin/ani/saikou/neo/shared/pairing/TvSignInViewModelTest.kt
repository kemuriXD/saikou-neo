package ani.saikou.neo.shared.pairing

import ani.saikou.neo.shared.account.AccountStore
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.account.AniListToken
import ani.saikou.neo.shared.account.SignInAttempt
import ani.saikou.neo.shared.account.SignedInAccount
import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TvSignInViewModelTest {
    private val main = StandardTestDispatcher()
    private val invite = PairingInvite("192.168.1.20", 4000, ByteArray(65) { if (it == 0) 0x04 else 1 }, ByteArray(32))
    private val token = AniListToken("the-token", 9_000)
    private val hikari = AniListProfile(7, "Hikari", null)
    private val thisTv = PairedTvReply("tv-1", "Living room TV")

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private class FakePhone : PhoneLink {
        override val checkCode = "482913"
        val account = CompletableDeferred<AniListToken>()
        val replies = mutableListOf<PairedTvReply>()
        var closed = false

        override suspend fun receiveAccount(): AniListToken = account.await()

        override suspend fun replySignedIn(reply: PairedTvReply) {
            replies += reply
        }

        override fun close() {
            closed = true
        }
    }

    private inner class FakeSession : TvPairingSession {
        override val invite = this@TvSignInViewModelTest.invite
        val phone = CompletableDeferred<PhoneLink>()
        var onAttempt: ((Boolean) -> Unit)? = null
        var closed = false

        override suspend fun accept(onAttempt: (inProgress: Boolean) -> Unit): PhoneLink {
            this.onAttempt = onAttempt
            return phone.await()
        }

        override fun close() {
            closed = true
        }
    }

    private class MemoryAccounts : AccountStore {
        var account: SignedInAccount? = null

        override fun readAccount() = account

        override fun saveAccount(account: SignedInAccount) {
            this.account = account
        }

        override fun clearAccount() {
            account = null
        }

        override fun readAttempt(): SignInAttempt? = null

        override fun saveAttempt(attempt: SignInAttempt) = Unit

        override fun clearAttempt() = Unit
    }

    private val sessions = mutableListOf<FakeSession>()
    private val advertised = mutableListOf<String>()
    private val accounts = MemoryAccounts()
    private var codes = 0

    private fun TestScope.viewModel(viewer: AniListResult<AniListProfile> = AniListResult.Success(hikari)) =
        TvSignInViewModel(
            open = { code ->
                FakeSession().also {
                    advertised += code
                    sessions += it
                }
            },
            viewer = { viewer },
            store = accounts,
            tv = thisTv,
            io = main,
            makeCode = { listOf("K74Q9M", "ABC234")[codes++ % 2] },
        ).also { runCurrent() }

    @Test
    fun showsTheQrCodeAndTheCodeWithTenMinutesLeft() =
        runTest(main) {
            val model = viewModel()
            assertEquals(TvSignInState.Waiting(invite.toQrText(), "K74Q9M", 600), model.state.value)

            advanceTimeBy(19_500)
            assertEquals(581, (model.state.value as TvSignInState.Waiting).secondsLeft)
            model.cancel()
        }

    @Test
    fun aCodeThatRunsOutSaysSoAndStopsListening() =
        runTest(main) {
            val model = viewModel()
            advanceTimeBy(600_001)
            assertEquals(TvSignInState.Expired("K74Q9M"), model.state.value)
            assertTrue(sessions.single().closed)
        }

    @Test
    fun getANewCodeReplacesTheCodeAndTheListener() =
        runTest(main) {
            val model = viewModel()
            model.newCode()
            runCurrent()
            assertTrue(sessions[0].closed)
            assertEquals(TvSignInState.Waiting(invite.toQrText(), "ABC234", 600), model.state.value)
            model.cancel()
        }

    @Test
    fun pairingOpensUnderTheCodeTheTvShows() =
        runTest(main) {
            val model = viewModel()
            model.newCode()
            runCurrent()
            assertEquals(listOf("K74Q9M", "ABC234"), advertised)
            model.cancel()
        }

    @Test
    fun connectingShowsWhileAPhoneIsMidHandshakeAndGoesBackIfItFails() =
        runTest(main) {
            val model = viewModel()
            advanceTimeBy(2_500)
            sessions.single().onAttempt!!(true)
            assertEquals(TvSignInState.Connecting, model.state.value)
            sessions.single().onAttempt!!(false)
            assertEquals("K74Q9M", (model.state.value as TvSignInState.Waiting).code)
            model.cancel()
        }

    @Test
    fun thePhoneConfirmingSignsTheTvInAndRepliesWithItsId() =
        runTest(main) {
            val model = viewModel()
            val phone = FakePhone()
            sessions.single().phone.complete(phone)
            runCurrent()
            assertEquals(TvSignInState.CheckCode("482913"), model.state.value)
            assertTrue(sessions.single().closed)

            phone.account.complete(token)
            advanceUntilIdle()
            assertEquals(TvSignInState.SignedIn(hikari), model.state.value)
            assertEquals("the-token", accounts.account?.token?.accessToken)
            assertEquals(hikari, accounts.account?.profile)
            assertEquals(listOf(thisTv), phone.replies)
            assertTrue(phone.closed)
        }

    @Test
    fun thePhoneClosingAfterTheCheckCodeStopsPairingAndSavesNothing() =
        runTest(main) {
            val model = viewModel()
            val phone = FakePhone()
            sessions.single().phone.complete(phone)
            runCurrent()

            phone.account.completeExceptionally(PairingException("closed"))
            advanceUntilIdle()
            assertEquals(TvSignInState.Stopped, model.state.value)
            assertNull(accounts.account)
            assertTrue(phone.closed)
        }

    @Test
    fun aTokenAniListRefusesStopsPairingAndSavesNothing() =
        runTest(main) {
            val model = viewModel(viewer = AniListResult.Failure(AniListError.Http(401)))
            val phone = FakePhone()
            sessions.single().phone.complete(phone)
            runCurrent()

            phone.account.complete(token)
            advanceUntilIdle()
            assertEquals(TvSignInState.Stopped, model.state.value)
            assertNull(accounts.account)
            assertTrue(phone.replies.isEmpty())
        }

    @Test
    fun tooManyFailedAttemptsStopPairing() =
        runTest(main) {
            val model = viewModel()
            sessions.single().phone.completeExceptionally(PairingException("Too many"))
            advanceUntilIdle()
            assertEquals(TvSignInState.Stopped, model.state.value)
        }

    @Test
    fun cancelClosesTheConnectionWithoutSigningIn() =
        runTest(main) {
            val model = viewModel()
            val phone = FakePhone()
            sessions.single().phone.complete(phone)
            runCurrent()

            model.cancel()
            phone.account.complete(token)
            advanceUntilIdle()
            assertTrue(phone.closed)
            assertNull(accounts.account)
            assertEquals(TvSignInState.Stopped, model.state.value)
        }

    @Test
    fun cancelWhileTheListenerOpensStillClosesIt() =
        runTest(main) {
            val opening = CompletableDeferred<Unit>()
            val session = FakeSession()
            val model =
                TvSignInViewModel(
                    open = { session.also { opening.await() } },
                    viewer = { AniListResult.Success(hikari) },
                    store = accounts,
                    tv = thisTv,
                    io = main,
                )
            runCurrent()
            model.cancel()
            opening.complete(Unit)
            advanceUntilIdle()
            assertTrue(session.closed)
        }
}
