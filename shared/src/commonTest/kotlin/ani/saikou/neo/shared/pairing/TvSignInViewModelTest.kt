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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        val stops = mutableListOf<PairingStop>()
        var closed = false

        override suspend fun receiveAccount(): AniListToken = account.await()

        override suspend fun replySignedIn(reply: PairedTvReply) {
            replies += reply
        }

        override suspend fun stop(reason: PairingStop) {
            stops += reason
            closed = true
        }

        var turnedAway = false

        override fun turnAway() {
            turnedAway = true
            closed = true
        }

        override fun close() {
            closed = true
        }
    }

    private inner class FakeSession : TvPairingSession {
        override val invite = this@TvSignInViewModelTest.invite
        private val phones = mutableListOf<CompletableDeferred<PhoneLink>>()
        val phone get() = phoneFor(wait = 0)
        var onAttempt: ((Boolean) -> Unit)? = null
        var closed = false
        private var waits = 0

        /** The phone the [wait]th call to [accept] gets, counting from 0. */
        fun phoneFor(wait: Int): CompletableDeferred<PhoneLink> {
            while (phones.size <= wait) phones += CompletableDeferred()
            return phones[wait]
        }

        override suspend fun accept(onAttempt: (inProgress: Boolean) -> Unit): PhoneLink {
            this.onAttempt = onAttempt
            return phoneFor(waits++).await()
        }

        override fun close() {
            closed = true
        }
    }

    /** A session whose wait fails with [PairingException] once it's closed, as the real listener's does. */
    private inner class ClosingSession : TvPairingSession {
        override val invite = this@TvSignInViewModelTest.invite
        private val phone = CompletableDeferred<PhoneLink>()

        override suspend fun accept(onAttempt: (inProgress: Boolean) -> Unit): PhoneLink = withContext(NonCancellable) { phone.await() }

        override fun close() {
            phone.completeExceptionally(PairingException("The listener closed"))
        }
    }

    /**
     * Like the real listener: once cancelled, its wait takes a while to wind down, and closing it fails the wait with
     * [PairingException].
     */
    private inner class SlowToStopSession : TvPairingSession {
        override val invite = this@TvSignInViewModelTest.invite
        private val phone = CompletableDeferred<PhoneLink>()
        var closed = false

        override suspend fun accept(onAttempt: (inProgress: Boolean) -> Unit): PhoneLink =
            try {
                phone.await()
            } finally {
                withContext(NonCancellable) { delay(500) }
            }

        override fun close() {
            closed = true
            phone.completeExceptionally(PairingException("The listener closed"))
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
    fun getANewCodeGoesStraightToTheNewCode() =
        runTest(main) {
            // Closing the real listener fails its wait with an ordinary error, not a cancellation.
            val model =
                TvSignInViewModel(
                    open = { ClosingSession() },
                    viewer = { AniListResult.Success(hikari) },
                    store = accounts,
                    tv = thisTv,
                    io = main,
                    makeCode = { listOf("K74Q9M", "ABC234")[codes++ % 2] },
                )
            runCurrent()
            val states = mutableListOf<TvSignInState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.toList(states) }

            model.newCode()
            runCurrent()

            assertEquals(listOf("Waiting", "Starting", "Waiting"), states.map { it::class.simpleName })
            model.cancel()
        }

    // Seen on a Fire TV: the old code's wait wound down after the new code's listener opened, then closed it.
    @Test
    fun theOldCodeWindingDownLeavesTheNewCodesListenerOpen() =
        runTest(main) {
            val slow = mutableListOf<SlowToStopSession>()
            val model =
                TvSignInViewModel(
                    open = { SlowToStopSession().also { slow += it } },
                    viewer = { AniListResult.Success(hikari) },
                    store = accounts,
                    tv = thisTv,
                    io = main,
                    makeCode = { listOf("K74Q9M", "ABC234")[codes++ % 2] },
                )
            runCurrent()
            model.newCode()
            advanceTimeBy(2_000)

            assertFalse(slow[1].closed)
            assertEquals("ABC234", (model.state.value as TvSignInState.Waiting).code)
            model.cancel()
        }

    @Test
    fun aNewCodeIsNeverTheOneBefore() =
        runTest(main) {
            val model =
                TvSignInViewModel(
                    open = { FakeSession().also { sessions += it } },
                    viewer = { AniListResult.Success(hikari) },
                    store = accounts,
                    tv = thisTv,
                    io = main,
                    makeCode = { listOf("K74Q9M", "K74Q9M", "ABC234")[codes++ % 3] },
                )
            runCurrent()
            model.newCode()
            runCurrent()
            assertEquals("ABC234", (model.state.value as TvSignInState.Waiting).code)
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
            assertFalse(sessions.single().closed)

            phone.account.complete(token)
            advanceUntilIdle()
            assertEquals(TvSignInState.SignedIn(hikari), model.state.value)
            assertTrue(sessions.single().closed)
            assertEquals("the-token", accounts.account?.token?.accessToken)
            assertEquals(hikari, accounts.account?.profile)
            assertEquals(listOf(thisTv), phone.replies)
            assertTrue(phone.closed)
        }

    @Test
    fun thePhoneLeavingAtTheCheckCodeGoesBackToTheSameCode() =
        runTest(main) {
            val model = viewModel()
            val first = FakePhone()
            sessions.single().phone.complete(first)
            runCurrent()
            advanceTimeBy(30_000)

            first.account.completeExceptionally(PairingException("closed"))
            runCurrent()
            val waiting = model.state.value as TvSignInState.Waiting
            assertEquals("K74Q9M", waiting.code)
            assertEquals(invite.toQrText(), waiting.qrText)
            assertTrue(waiting.secondsLeft in 569..571)
            assertEquals(TvPairingNote.LostConnection, waiting.note)
            assertTrue(first.closed)
            assertFalse(sessions.single().closed)
            assertNull(accounts.account)

            val second = FakePhone()
            sessions.single().phoneFor(wait = 1).complete(second)
            runCurrent()
            assertEquals(TvSignInState.CheckCode("482913"), model.state.value)
            second.account.completeExceptionally(PairingStoppedException(PairingStop.NoMatch))
            runCurrent()
            assertEquals(TvPairingNote.StoppedOnPhone, (model.state.value as TvSignInState.Waiting).note)

            val third = FakePhone()
            sessions.single().phoneFor(wait = 2).complete(third)
            runCurrent()
            assertEquals(TvSignInState.CheckCode("482913"), model.state.value)
            advanceTimeBy(1_000)
            assertEquals(TvSignInState.CheckCode("482913"), model.state.value)
            third.account.complete(token)
            second.account.complete(token)
            advanceUntilIdle()
            assertEquals(TvSignInState.SignedIn(hikari), model.state.value)
            assertEquals(1, sessions.size)
        }

    @Test
    fun theTvSaysWhyThePhoneLeft() =
        runTest(main) {
            val model = viewModel()
            val reasons =
                listOf(
                    PairingStoppedException(PairingStop.NoMatch) to TvPairingNote.StoppedOnPhone,
                    PairingStoppedException(PairingStop.Cancelled) to TvPairingNote.CancelledOnPhone,
                    PairingStoppedException(PairingStop.TimedOut) to TvPairingNote.TookTooLong,
                    // Only the TV refuses an account, so a phone saying so is treated as a lost connection.
                    PairingStoppedException(PairingStop.Refused) to TvPairingNote.LostConnection,
                    PairingException("Connection reset") to TvPairingNote.LostConnection,
                )
            reasons.forEachIndexed { wait, (left, note) ->
                val phone = FakePhone()
                sessions.single().phoneFor(wait).complete(phone)
                runCurrent()
                phone.account.completeExceptionally(left)
                runCurrent()
                val waiting = model.state.value as TvSignInState.Waiting
                assertEquals(note, waiting.note)
                assertEquals("K74Q9M", waiting.code)
            }
        }

    @Test
    fun theNoteStaysWhileTheCodeCountsDown() =
        runTest(main) {
            val model = viewModel()
            val phone = FakePhone()
            sessions.single().phone.complete(phone)
            runCurrent()
            phone.account.completeExceptionally(PairingStoppedException(PairingStop.NoMatch))
            runCurrent()
            advanceTimeBy(5_000)

            val waiting = model.state.value as TvSignInState.Waiting
            assertEquals(TvPairingNote.StoppedOnPhone, waiting.note)
            assertTrue(waiting.secondsLeft < TV_CODE_SECONDS)
        }

    @Test
    fun aPhoneLeavingAfterTheCodeRanOutShowsItRanOut() =
        runTest(main) {
            val model = viewModel()
            val phone = FakePhone()
            sessions.single().phone.complete(phone)
            runCurrent()
            advanceTimeBy(601_000)

            phone.account.completeExceptionally(PairingException("closed"))
            runCurrent()
            assertEquals(TvSignInState.Expired("K74Q9M"), model.state.value)
            assertTrue(sessions.single().closed)
        }

    @Test
    fun aTokenAniListRefusesTellsThePhoneAndGoesBackToTheSameCode() =
        runTest(main) {
            val model = viewModel(viewer = AniListResult.Failure(AniListError.SignedOut))
            val phone = FakePhone()
            sessions.single().phone.complete(phone)
            runCurrent()

            phone.account.complete(token)
            runCurrent()
            val waiting = model.state.value as TvSignInState.Waiting
            assertEquals("K74Q9M", waiting.code)
            assertEquals(TvPairingNote.AniListRefused, waiting.note)
            assertEquals(listOf(PairingStop.Refused), phone.stops)
            assertFalse(sessions.single().closed)
            assertNull(accounts.account)
            assertTrue(phone.replies.isEmpty())
        }

    @Test
    fun aniListOutOfReachIsNotCalledARefusal() =
        runTest(main) {
            val model = viewModel(viewer = AniListResult.Failure(AniListError.Network(null)))
            val phone = FakePhone()
            sessions.single().phone.complete(phone)
            runCurrent()

            phone.account.complete(token)
            runCurrent()
            val waiting = model.state.value as TvSignInState.Waiting
            assertEquals(TvPairingNote.AniListUnreachable, waiting.note)
            assertTrue(phone.stops.isEmpty())
            assertTrue(phone.turnedAway)
            assertNull(accounts.account)
        }

    @Test
    fun aTokenRefusedAfterTheCodeRanOutShowsItRanOut() =
        runTest(main) {
            val model = viewModel(viewer = AniListResult.Failure(AniListError.SignedOut))
            val phone = FakePhone()
            sessions.single().phone.complete(phone)
            runCurrent()
            advanceTimeBy(601_000)

            phone.account.complete(token)
            runCurrent()
            assertEquals(TvSignInState.Expired("K74Q9M"), model.state.value)
            assertTrue(sessions.single().closed)
        }

    @Test
    fun aTvWithNoNetworkSaysSoAndTriesAgain() =
        runTest(main) {
            var online = false
            val model =
                TvSignInViewModel(
                    open = { if (online) FakeSession() else throw NoNetworkException() },
                    viewer = { AniListResult.Success(hikari) },
                    store = accounts,
                    tv = thisTv,
                    io = main,
                )
            runCurrent()
            assertEquals(TvSignInState.NoNetwork, model.state.value)

            online = true
            model.newCode()
            runCurrent()
            assertTrue(model.state.value is TvSignInState.Waiting)
        }

    @Test
    fun aListenerThatWontOpenForAnotherReasonStopsPairing() =
        runTest(main) {
            val model =
                TvSignInViewModel(
                    open = { throw PairingException("Port in use") },
                    viewer = { AniListResult.Success(hikari) },
                    store = accounts,
                    tv = thisTv,
                    io = main,
                )
            runCurrent()
            assertEquals(TvSignInState.Stopped, model.state.value)
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
    fun cancelTellsThePhoneWithoutSigningIn() =
        runTest(main) {
            val model = viewModel()
            val phone = FakePhone()
            sessions.single().phone.complete(phone)
            runCurrent()

            model.cancel()
            phone.account.complete(token)
            advanceUntilIdle()
            assertEquals(listOf(PairingStop.Cancelled), phone.stops)
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
