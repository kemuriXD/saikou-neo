package ani.saikou.neo.shared.sources

import ani.saikou.neo.shared.pairing.PairingException
import ani.saikou.neo.shared.pairing.PairingInvite
import ani.saikou.neo.shared.pairing.PairingRequest
import ani.saikou.neo.shared.pairing.PairingStop
import ani.saikou.neo.shared.pairing.PairingStoppedException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

private fun listed(
    id: String,
    version: String = "1.0.0",
) = ListedSource(id, "Source $id", version, 1, "https://example.com/$id.apk")

/** Sending sources from the phone to a TV's Add from my phone board (#406, ADR 0042 and 0058). */
@OptIn(ExperimentalCoroutinesApi::class)
class SendSourcesViewModelTest {
    private val main = StandardTestDispatcher()
    private val scanned =
        PairingRequest.Scanned(
            PairingInvite(
                "192.168.1.20",
                4000,
                ByteArray(65) {
                    if (it ==
                        0
                    ) {
                        0x04
                    } else {
                        1
                    }
                },
                ByteArray(32),
            ),
        )
    private val tvSources = TvSources(listOf(TvSource("com.example.old", "1.0.0", hasList = true)), 1..4)
    private val list = "https://example.com/sources.json"
    private val choices =
        listOf(
            Choice(listed("com.example.fresh"), ChoiceStatus.New),
            Choice(listed("com.example.old", "2.0.0"), ChoiceStatus.Update("1.0.0")),
            Choice(listed("com.example.same"), ChoiceStatus.Installed),
            Choice(listed("com.example.newer"), ChoiceStatus.CantUse(SourceProblem.BUILT_FOR_NEWER_APP)),
        )

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    /** A TV board that sends what it has once [hasConfirmed], and takes picks unless [drops]. */
    private class FakeTv(
        private val tv: TvSources,
    ) : SourcesTv {
        override val checkCode = "482913"
        var confirmed = false
        val sent = mutableListOf<PickedSources>()
        val stops = mutableListOf<PairingStop>()
        var closed = false
        var drops: Exception? = null
        val tvConfirms = CompletableDeferred<Unit>()

        override suspend fun confirmCodes() {
            confirmed = true
        }

        override suspend fun receiveTvSources(): TvSources {
            check(confirmed) { "Asked before the codes were confirmed" }
            tvConfirms.await()
            return tv
        }

        override suspend fun send(picked: PickedSources) {
            drops?.let { throw it }
            sent += picked
        }

        override suspend fun stop(reason: PairingStop) {
            stops += reason
            closed = true
        }

        override fun close() {
            closed = true
        }
    }

    private fun TestScope.viewModel(
        read: suspend (String, TvSources) -> LinkForTv = { _, _ -> LinkForTv.Listed(list, choices) },
        connect: suspend (PairingRequest) -> SourcesTv,
    ) = SendSourcesViewModel(connect, read, io = main).also { advanceUntilIdle() }

    /** Connected, codes confirmed on both sides, on the link screen. */
    private fun TestScope.onLink(
        tv: FakeTv = FakeTv(tvSources),
        read: suspend (String, TvSources) -> LinkForTv = { _, _ -> LinkForTv.Listed(list, choices) },
    ): SendSourcesViewModel {
        val model = viewModel(read) { tv }
        model.start(scanned)
        advanceUntilIdle()
        model.confirm()
        tv.tvConfirms.complete(Unit)
        advanceUntilIdle()
        return model
    }

    private fun TestScope.picking(
        tv: FakeTv = FakeTv(tvSources),
        read: suspend (String, TvSources) -> LinkForTv = { _, _ -> LinkForTv.Listed(list, choices) },
    ): SendSourcesViewModel =
        onLink(tv, read).apply {
            onLinkChange(list)
            check()
            advanceUntilIdle()
        }

    @Test
    fun connectingShowsUntilTheHandshakeThenTheCheckCode() =
        runTest(main) {
            val answer = CompletableDeferred<SourcesTv>()
            val model = viewModel { answer.await() }

            model.start(scanned)
            advanceUntilIdle()
            assertEquals(SendSourcesState.Connecting, model.state.value)

            answer.complete(FakeTv(tvSources))
            advanceUntilIdle()
            assertEquals(SendSourcesState.CheckCode("482913"), model.state.value)
        }

    @Test
    fun theTvListIsOnlyAskedForOnceTheCodesAreConfirmedOnBothSides() =
        runTest(main) {
            val tv = FakeTv(tvSources)
            val model = viewModel { tv }
            model.start(scanned)
            advanceUntilIdle()
            assertTrue(!tv.confirmed)

            model.confirm()
            advanceUntilIdle()
            assertTrue(tv.confirmed)
            // Waiting for It matches on the TV too.
            assertEquals(SendSourcesState.CheckCode("482913", waiting = true), model.state.value)

            tv.tvConfirms.complete(Unit)
            advanceUntilIdle()
            assertEquals(SendSourcesState.Link(), model.state.value)
        }

    @Test
    fun itDoesNotMatchTellsTheTvAndSendsNothing() =
        runTest(main) {
            val tv = FakeTv(tvSources)
            val model = viewModel { tv }
            model.start(scanned)
            advanceUntilIdle()

            model.reject()
            advanceUntilIdle()

            assertEquals(listOf(PairingStop.NoMatch), tv.stops)
            assertTrue(!tv.confirmed)
            assertEquals(SendSourcesState.Idle, model.state.value)
        }

    @Test
    fun aTvThatCantBeReachedIsLost() =
        runTest(main) {
            val model = viewModel { throw PairingException("No route") }

            model.start(scanned)
            advanceUntilIdle()

            assertEquals(SendSourcesState.Lost, model.state.value)
        }

    @Test
    fun aTvThatStopsAtTheCheckCodeIsLost() =
        runTest(main) {
            val tv = FakeTv(tvSources)
            val model = viewModel { tv }
            model.start(scanned)
            advanceUntilIdle()

            model.confirm()
            tv.tvConfirms.completeExceptionally(PairingStoppedException(PairingStop.NoMatch))
            advanceUntilIdle()

            assertEquals(SendSourcesState.Lost, model.state.value)
            assertTrue(tv.closed)
        }

    @Test
    fun aListShowsItsSourcesWithNothingTicked() =
        runTest(main) {
            var asked: Pair<String, TvSources>? = null
            val model =
                picking(read = { text, tv ->
                    asked = text to tv
                    LinkForTv.Listed(list, choices)
                })

            assertEquals(list to tvSources, asked)
            val pick = model.state.value as SendSourcesState.Pick
            assertEquals(choices, pick.choices)
            assertTrue(pick.choices.none { it.ticked })
            assertNull(pick.picked)
            assertTrue(!pick.nothingFits)
        }

    @Test
    fun checkingShowsWhileTheLinkIsRead() =
        runTest(main) {
            val answer = CompletableDeferred<LinkForTv>()
            val model = onLink(read = { _, _ -> answer.await() })

            model.onLinkChange("  $list ")
            model.check()
            advanceUntilIdle()
            assertEquals(SendSourcesState.Link("  $list ", checking = true), model.state.value)

            // Typing waits while it checks.
            model.onLinkChange("something else")
            assertEquals(SendSourcesState.Link("  $list ", checking = true), model.state.value)

            answer.complete(LinkForTv.Listed(list, choices))
            advanceUntilIdle()
            assertTrue(model.state.value is SendSourcesState.Pick)
        }

    @Test
    fun anEmptyLinkIsNotChecked() =
        runTest(main) {
            val model = onLink()

            model.onLinkChange("   ")
            model.check()
            advanceUntilIdle()

            assertEquals(SendSourcesState.Link("   "), model.state.value)
        }

    @Test
    fun aLinkThatCantBeReadSaysWhyAndClearsOnTyping() =
        runTest(main) {
            AddError.entries.forEach { error ->
                val model = onLink(read = { _, _ -> LinkForTv.Failed(error) })

                model.onLinkChange("https://example.com/about.html")
                model.check()
                advanceUntilIdle()
                assertEquals(SendSourcesState.Link("https://example.com/about.html", error = error), model.state.value)

                model.onLinkChange("https://example.com/about.htm")
                assertEquals(SendSourcesState.Link("https://example.com/about.htm"), model.state.value)
            }
        }

    @Test
    fun aListWhereNothingFitsSaysSo() =
        runTest(main) {
            val none = choices.filter { !it.status.canPick }
            val model = picking(read = { _, _ -> LinkForTv.Listed(list, none) })

            val pick = model.state.value as SendSourcesState.Pick
            assertTrue(pick.nothingFits)
            assertEquals(none, pick.choices)
        }

    @Test
    fun onlyTickedSourcesThatCanBePickedAreSent() =
        runTest(main) {
            val tv = FakeTv(tvSources)
            val model = picking(tv)

            model.toggle("com.example.fresh")
            model.toggle("com.example.same")
            model.toggle("com.example.newer")
            val pick = model.state.value as SendSourcesState.Pick
            assertEquals(listOf("com.example.fresh"), pick.choices.filter { it.ticked }.map { it.source.id })

            model.send()
            advanceUntilIdle()

            assertEquals(listOf<PickedSources>(PickedSources.FromList(list, listOf(listed("com.example.fresh")))), tv.sent)
            assertEquals(SendSourcesState.Sent, model.state.value)
        }

    @Test
    fun sendDoesNothingWithNothingTicked() =
        runTest(main) {
            val tv = FakeTv(tvSources)
            val model = picking(tv)

            model.toggle("com.example.fresh")
            model.toggle("com.example.fresh")
            model.send()
            advanceUntilIdle()

            assertTrue(tv.sent.isEmpty())
            assertTrue(model.state.value is SendSourcesState.Pick)
        }

    @Test
    fun aLinkToOneApkIsOneRowSentOnlyOnceTicked() =
        runTest(main) {
            val apk = "https://example.com/source-1.2.0.apk"
            val tv = FakeTv(tvSources)
            val model = picking(tv, read = { _, _ -> LinkForTv.OneApk(apk) })

            val pick = model.state.value as SendSourcesState.Pick
            assertEquals(apk, pick.apk)
            assertNull(pick.picked)

            model.toggle(apk)
            model.send()
            advanceUntilIdle()

            assertEquals(listOf<PickedSources>(PickedSources.OneApk(apk)), tv.sent)
            assertEquals(SendSourcesState.Sent, model.state.value)
        }

    @Test
    fun backFromThePicksKeepsTheLink() =
        runTest(main) {
            val model = picking()

            model.backToLink()

            assertEquals(SendSourcesState.Link(list), model.state.value)
        }

    @Test
    fun aTvLostWhileSendingIsLost() =
        runTest(main) {
            val tv = FakeTv(tvSources).apply { drops = PairingException("Connection reset") }
            val model = picking(tv)

            model.toggle("com.example.fresh")
            model.send()
            advanceUntilIdle()

            assertEquals(SendSourcesState.Lost, model.state.value)
            assertTrue(tv.closed)
        }

    @Test
    fun cancelTellsTheTvOnceConnected() =
        runTest(main) {
            val tv = FakeTv(tvSources)
            val model = onLink(tv)

            model.cancel()
            advanceUntilIdle()

            assertEquals(listOf(PairingStop.Cancelled), tv.stops)
            assertEquals(SendSourcesState.Idle, model.state.value)
        }

    @Test
    fun cancelWhileConnectingDropsTheConnectionWhenItComes() =
        runTest(main) {
            val answer = CompletableDeferred<SourcesTv>()
            val tv = FakeTv(tvSources)
            val model = viewModel { answer.await() }
            model.start(scanned)
            advanceUntilIdle()

            model.cancel()
            answer.complete(tv)
            advanceUntilIdle()

            assertEquals(SendSourcesState.Idle, model.state.value)
        }
}
