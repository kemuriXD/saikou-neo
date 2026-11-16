package ani.saikou.neo.shared.pairing

import ani.saikou.neo.shared.account.AniListToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PairTvViewModelTest {
    private val main = StandardTestDispatcher()
    private val invite = PairingInvite("192.168.1.20", 4000, ByteArray(65) { if (it == 0) 0x04 else 1 }, ByteArray(32))
    private val scanned = PairingRequest.Scanned(invite)
    private val token = AniListToken("the-token", 9_000)
    private val livingRoom = PairedTvReply("tv-1", "Living room TV")

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private class FakeTv(
        private val reply: () -> PairedTvReply,
    ) : TvLink {
        override val checkCode = "482913"
        val sent = mutableListOf<AniListToken>()
        var closed = false

        override suspend fun sendAccount(token: AniListToken): PairedTvReply {
            sent += token
            return reply()
        }

        override fun close() {
            closed = true
        }
    }

    private class MemoryTvStore(
        var tvs: List<PairedTv> = emptyList(),
    ) : PairedTvStore {
        override fun readTvs() = tvs

        override fun saveTvs(tvs: List<PairedTv>) {
            this.tvs = tvs
        }
    }

    private fun TestScope.viewModel(
        store: PairedTvStore = MemoryTvStore(),
        account: AniListToken? = token,
        connect: suspend (PairingRequest) -> TvLink,
    ) = PairTvViewModel(connect, account = { account }, store = store, io = main, now = { 1_761_868_800 })
        .also { advanceUntilIdle() }

    @Test
    fun connectingShowsUntilTheTvAnswersThenTheCheckCode() =
        runTest(main) {
            val answer = CompletableDeferred<TvLink>()
            val model = viewModel { answer.await() }

            model.start(scanned)
            advanceUntilIdle()
            assertEquals(PairTvState.Connecting, model.state.value)

            answer.complete(FakeTv { livingRoom })
            advanceUntilIdle()
            assertEquals(PairTvState.CheckCode("482913"), model.state.value)
        }

    @Test
    fun nothingIsSentUntilItMatchesIsTapped() =
        runTest(main) {
            val tv = FakeTv { livingRoom }
            val model = viewModel { tv }

            model.start(scanned)
            advanceUntilIdle()
            assertTrue(tv.sent.isEmpty())

            model.confirm()
            advanceUntilIdle()
            assertEquals(listOf(token), tv.sent)
            assertEquals(PairTvState.SignedIn("Living room TV"), model.state.value)
            assertTrue(tv.closed)
        }

    @Test
    fun itDoesNotMatchStopsPairingAndSendsNothing() =
        runTest(main) {
            val tv = FakeTv { livingRoom }
            val store = MemoryTvStore()
            val model = viewModel(store) { tv }

            model.start(scanned)
            advanceUntilIdle()
            model.reject()
            advanceUntilIdle()

            assertEquals(PairTvState.Stopped, model.state.value)
            assertTrue(tv.sent.isEmpty())
            assertTrue(tv.closed)
            assertTrue(store.tvs.isEmpty())
        }

    @Test
    fun cancelStopsConnecting() =
        runTest(main) {
            val never = CompletableDeferred<TvLink>()
            val model = viewModel { never.await() }

            model.start(scanned)
            advanceUntilIdle()
            model.cancel()
            advanceUntilIdle()

            assertEquals(PairTvState.Idle, model.state.value)
            never.complete(FakeTv { livingRoom })
            advanceUntilIdle()
            assertEquals(PairTvState.Idle, model.state.value)
        }

    @Test
    fun aConnectionThatFinishesAfterCancelIsClosed() =
        runTest(main) {
            val answer = CompletableDeferred<TvLink>()
            val tv = FakeTv { livingRoom }
            val model = viewModel { withContext(NonCancellable) { answer.await() } }

            model.start(scanned)
            advanceUntilIdle()
            model.cancel()
            answer.complete(tv)
            advanceUntilIdle()

            assertEquals(PairTvState.Idle, model.state.value)
            assertTrue(tv.closed)
        }

    @Test
    fun cancelOnTheCheckCodeClosesTheConnectionWithoutSending() =
        runTest(main) {
            val tv = FakeTv { livingRoom }
            val model = viewModel { tv }

            model.start(scanned)
            advanceUntilIdle()
            model.cancel()
            advanceUntilIdle()

            assertTrue(tv.closed)
            assertTrue(tv.sent.isEmpty())
        }

    @Test
    fun aTvThatCannotBeReachedOffersTryAgain() =
        runTest(main) {
            var attempts = 0
            val model =
                viewModel {
                    attempts++
                    if (attempts == 1) throw PairingException("No route") else FakeTv { livingRoom }
                }

            model.start(scanned)
            advanceUntilIdle()
            assertEquals(PairTvState.Unreachable, model.state.value)

            model.retry()
            advanceUntilIdle()
            assertEquals(PairTvState.CheckCode("482913"), model.state.value)
        }

    @Test
    fun aTvThatDropsTheAccountEndsOnCouldNotReach() =
        runTest(main) {
            val store = MemoryTvStore()
            val model = viewModel(store) { FakeTv { throw PairingException("Closed") } }

            model.start(scanned)
            advanceUntilIdle()
            model.confirm()
            advanceUntilIdle()

            assertEquals(PairTvState.Unreachable, model.state.value)
            assertTrue(store.tvs.isEmpty())
        }

    @Test
    fun withoutAnAccountNothingIsSent() =
        runTest(main) {
            val tv = FakeTv { livingRoom }
            val model = viewModel(account = null) { tv }

            model.start(scanned)
            advanceUntilIdle()
            model.confirm()
            advanceUntilIdle()

            assertEquals(PairTvState.Unreachable, model.state.value)
            assertTrue(tv.sent.isEmpty())
        }

    @Test
    fun theTvIsAddedToYourTvsWithTheDateItWasPaired() =
        runTest(main) {
            val store = MemoryTvStore(listOf(PairedTv("tv-2", "Bedroom Fire TV", 100)))
            val model = viewModel(store) { FakeTv { livingRoom } }

            model.start(scanned)
            advanceUntilIdle()
            model.confirm()
            advanceUntilIdle()

            val expected = listOf(PairedTv("tv-2", "Bedroom Fire TV", 100), PairedTv("tv-1", "Living room TV", 1_761_868_800))
            assertEquals(expected, store.tvs)
            assertEquals(expected, model.tvs.value)
        }

    @Test
    fun pairingTheSameTvAgainReplacesIt() =
        runTest(main) {
            val store = MemoryTvStore(listOf(PairedTv("tv-1", "Old name", 100)))
            val model = viewModel(store) { FakeTv { livingRoom } }

            model.start(scanned)
            advanceUntilIdle()
            model.confirm()
            advanceUntilIdle()

            assertEquals(listOf(PairedTv("tv-1", "Living room TV", 1_761_868_800)), store.tvs)
        }

    @Test
    fun aTypedCodeThatIsNotFoundCannotBeReached() =
        runTest(main) {
            val model = viewModel { connectForPairing(it, lookup = { null }) { _, _, _, _ -> error("not called") } }

            model.start(PairingRequest.Typed("K7P2QX"))
            advanceUntilIdle()

            assertEquals(PairTvState.Unreachable, model.state.value)
        }

    @Test
    fun aTypedCodeConnectsToTheTvItLooksUp() =
        runTest(main) {
            var reached: String? = null
            val model =
                viewModel {
                    connectForPairing(it, lookup = { code -> invite.takeIf { code == "K7P2QX" } }) { host, _, _, _ ->
                        reached = host
                        FakeTv { livingRoom }
                    }
                }

            model.start(PairingRequest.Typed("K7P2QX"))
            advanceUntilIdle()

            assertEquals("192.168.1.20", reached)
            assertEquals(PairTvState.CheckCode("482913"), model.state.value)
        }

    @Test
    fun confirmDoesNothingBeforeTheCheckCode() =
        runTest(main) {
            val never = CompletableDeferred<TvLink>()
            val model = viewModel { never.await() }

            model.start(scanned)
            advanceUntilIdle()
            model.confirm()
            advanceUntilIdle()

            assertEquals(PairTvState.Connecting, model.state.value)
            assertNull(model.tvs.value.firstOrNull())
        }
}
