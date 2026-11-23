package ani.saikou.neo.shared.sources

import ani.saikou.neo.shared.pairing.PairingException
import ani.saikou.neo.shared.pairing.PairingInvite
import ani.saikou.neo.shared.pairing.PairingStop
import ani.saikou.neo.shared.pairing.PairingStoppedException
import ani.saikou.neo.shared.pairing.TV_CODE_SECONDS
import ani.saikou.neo.shared.pairing.TvPairingNote
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val LIST = "https://example.com/sources.json"

private fun entry(
    id: String,
    apk: String,
    version: String = "1.2.0",
    api: Int = 1,
) = ListedSource(id, "Source $id", version, api, "https://example.com/$apk")

/** The TV's side of Add from my phone (#405, ADR 0042): what arrives, the confirm, and the installs. */
@OptIn(ExperimentalCoroutinesApi::class)
class SourcesFromPhoneViewModelTest {
    private val main = UnconfinedTestDispatcher()
    private val invite = PairingInvite("192.168.1.20", 4000, ByteArray(65) { if (it == 0) 0x04 else 1 }, ByteArray(32))

    init {
        Dispatchers.setMain(main)
    }

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(main) { body() }

    private class FakePhone : SourcesPhone {
        override val checkCode = "482913"
        val sentTv = mutableListOf<TvSources>()
        val match = CompletableDeferred<Unit>()
        val picked = CompletableDeferred<PickedSources>()
        val stops = mutableListOf<PairingStop>()
        var received = false
        var closed = false

        override suspend fun receiveMatch() = match.await()

        override suspend fun sendTvSources(tv: TvSources) {
            sentTv += tv
        }

        override suspend fun receivePicked(): PickedSources = picked.await()

        override suspend fun replyReceived() {
            received = true
        }

        override suspend fun stop(reason: PairingStop) {
            stops += reason
            closed = true
        }

        override fun close() {
            closed = true
        }
    }

    private inner class FakeSession : TvSourcesSession {
        override val invite = this@SourcesFromPhoneViewModelTest.invite
        val phones = ArrayDeque<CompletableDeferred<SourcesPhone>>()
        var closed = false

        fun next(): CompletableDeferred<SourcesPhone> = CompletableDeferred<SourcesPhone>().also { phones += it }

        override suspend fun accept(onAttempt: (inProgress: Boolean) -> Unit): SourcesPhone =
            (phones.firstOrNull() ?: next()).await().also {
                phones.removeFirst()
            }

        override fun close() {
            closed = true
        }
    }

    private val pages =
        mapOf(
            "https://example.com/starter.apk" to "STARTER",
            "https://example.com/demo.apk" to "DEMO",
            "https://example.com/source.apk" to "EXAMPLE",
            "https://example.com/newer-starter.apk" to "NEWER_STARTER",
        )

    private fun viewModel(
        session: FakeSession,
        store: FakeStore = FakeStore(),
    ) = SourcesFromPhoneViewModel({ session }, store, SourceDownloads(PagesEngine(pages), main), 1..1, main, makeCode = { "K7P2QX" })

    /** A board with a phone that has connected, and unless not to [confirm], the codes matched on both. */
    private fun connected(
        session: FakeSession = FakeSession(),
        store: FakeStore = FakeStore(),
        confirm: Boolean = true,
    ): Triple<SourcesFromPhoneViewModel, FakePhone, FakeStore> {
        val phone = FakePhone()
        session.next().complete(phone)
        val viewModel = viewModel(session, store)
        if (confirm) {
            phone.match.complete(Unit)
            viewModel.confirmCodes()
        }
        return Triple(viewModel, phone, store)
    }

    @Test
    fun noTvStateBeforeBothSayTheCodesMatch() =
        test {
            val (viewModel, phone) = connected(confirm = false)
            assertEquals(FromPhoneState.CheckCode("482913"), viewModel.state.value)

            phone.match.complete(Unit)
            assertEquals(FromPhoneState.CheckCode("482913"), viewModel.state.value)
            assertEquals(emptyList(), phone.sentTv)

            viewModel.confirmCodes()
            assertEquals(FromPhoneState.Picking("482913"), viewModel.state.value)
            assertEquals(1, phone.sentTv.size)
        }

    @Test
    fun theTvConfirmingFirstWaitsForThePhone() =
        test {
            val (viewModel, phone) = connected(confirm = false)

            viewModel.confirmCodes()
            assertEquals(FromPhoneState.CheckCode("482913", confirmed = true), viewModel.state.value)
            assertEquals(emptyList(), phone.sentTv)

            phone.match.complete(Unit)
            assertEquals(FromPhoneState.Picking("482913"), viewModel.state.value)
        }

    @Test
    fun codesThatDoNotMatchOnTheTvStopTheBoard() =
        test {
            val (viewModel, phone) = connected(confirm = false)
            phone.match.complete(Unit)

            viewModel.codesDontMatch()

            assertEquals(FromPhoneState.Stopped, viewModel.state.value)
            assertEquals(listOf(PairingStop.NoMatch), phone.stops)
            assertEquals(emptyList(), phone.sentTv)
        }

    @Test
    fun codesThatDoNotMatchOnThePhoneSendTheBoardBack() =
        test {
            val (viewModel, phone) = connected(confirm = false)

            phone.match.completeExceptionally(PairingStoppedException(PairingStop.NoMatch))

            assertEquals(TvPairingNote.StoppedOnPhone, (viewModel.state.value as FromPhoneState.Waiting).note)
            assertEquals(emptyList(), phone.sentTv)
        }

    @Test
    fun aTvThatNeverConfirmsStopsWaiting() =
        test {
            val (viewModel, phone) = connected(confirm = false)
            phone.match.complete(Unit)

            advanceTimeBy(90_001)

            assertEquals(listOf(PairingStop.TimedOut), phone.stops)
            assertEquals(TvPairingNote.TookTooLong, (viewModel.state.value as FromPhoneState.Waiting).note)
            assertEquals(emptyList(), phone.sentTv)
        }

    @Test
    fun cancelOnTheConfirmScreenDropsWhatArrived() =
        test {
            val (viewModel, phone, store) = connected()
            phone.picked.complete(PickedSources.OneApk("https://example.com/source.apk"))

            viewModel.cancel()
            viewModel.install()

            assertEquals(FromPhoneState.Stopped, viewModel.state.value)
            assertEquals(emptyList(), store.installs)
        }

    @Test
    fun theBoardShowsItsCode() =
        test {
            val viewModel = viewModel(FakeSession())

            assertEquals(FromPhoneState.Waiting(invite.toQrText(), "K7P2QX", TV_CODE_SECONDS), viewModel.state.value)
        }

    @Test
    fun aPhoneHearsWhatTheTvHas() =
        test {
            val store =
                FakeStore().apply {
                    installed.value =
                        listOf(InstalledSource("com.example.starter", "Starter", "1.0.0", list = LIST))
                }
            val (viewModel, phone) = connected(store = store)

            assertEquals(FromPhoneState.Picking("482913"), viewModel.state.value)
            assertEquals(listOf(TvSources(listOf(TvSource("com.example.starter", "1.0.0", hasList = true)), 1..1)), phone.sentTv)
        }

    @Test
    fun nothingInstallsBeforeInstall() =
        test {
            val session = FakeSession()
            val (viewModel, phone, store) = connected(session)
            val picked = PickedSources.FromList(LIST, listOf(entry("com.example.starter", "starter.apk")))

            phone.picked.complete(picked)

            assertEquals(FromPhoneState.Confirm(picked), viewModel.state.value)
            assertTrue(phone.received)
            assertTrue(phone.closed)
            assertTrue(session.closed)
            assertEquals(emptyList(), store.installs)
        }

    @Test
    fun installSavesTheListWithEachSource() =
        test {
            val (viewModel, phone, store) = connected()
            val picked =
                PickedSources.FromList(
                    LIST,
                    listOf(entry("com.example.starter", "starter.apk"), entry("com.example.demo", "demo.apk", "1.0.0")),
                )
            phone.picked.complete(picked)

            viewModel.install()

            val state = viewModel.state.value as FromPhoneState.Installing
            assertTrue(state.done)
            assertEquals(listOf(LIST, LIST), state.outcomes.map { (it as InstallOutcome.Installed).source.list })
            assertEquals(listOf("com.example.starter", "com.example.demo"), store.installs)
        }

    @Test
    fun installsGoOneAtATime() =
        test {
            val gate = CompletableDeferred<Unit>()
            val store = FakeStore().apply { gates += gate }
            val (viewModel, phone) = connected(store = store)
            val picked =
                PickedSources.FromList(
                    LIST,
                    listOf(entry("com.example.starter", "starter.apk"), entry("com.example.demo", "demo.apk", "1.0.0")),
                )
            phone.picked.complete(picked)

            viewModel.install()

            assertEquals(FromPhoneState.Installing(picked, listOf(null, null)), viewModel.state.value)
            gate.complete(Unit)
            assertEquals(2, (viewModel.state.value as FromPhoneState.Installing).outcomes.count { it is InstallOutcome.Installed })
        }

    @Test
    fun oneApkInstallsWithNoList() =
        test {
            val (viewModel, phone) = connected()
            phone.picked.complete(PickedSources.OneApk("https://example.com/source.apk"))

            viewModel.install()

            val outcome = (viewModel.state.value as FromPhoneState.Installing).outcomes.single() as InstallOutcome.Installed
            assertEquals("ani.saikou.neo.example", outcome.source.id)
            assertEquals(null, outcome.source.list)
        }

    @Test
    fun eachFailureSaysWhy() =
        test {
            val store = FakeStore().apply { installed.value = listOf(InstalledSource("com.example.demo", "Demo", "2.0.0", list = LIST)) }
            val (viewModel, phone) = connected(store = store)
            phone.picked.complete(
                PickedSources.FromList(
                    LIST,
                    listOf(
                        entry("com.example.gone", "gone.apk"),
                        entry("com.example.other", "starter.apk"),
                        entry("com.example.starter", "newer-starter.apk"),
                        entry("com.example.later", "starter.apk", api = 2),
                        entry("com.example.demo", "demo.apk", "1.0.0"),
                    ),
                ),
            )

            viewModel.install()

            assertEquals(
                listOf(
                    InstallOutcome.NotDownloaded,
                    InstallOutcome.NotTheSource,
                    InstallOutcome.CantUse(SourceProblem.BUILT_FOR_NEWER_APP),
                    InstallOutcome.CantUse(SourceProblem.BUILT_FOR_NEWER_APP),
                    InstallOutcome.AlreadyInstalled,
                ),
                (viewModel.state.value as FromPhoneState.Installing).outcomes,
            )
            assertEquals(listOf("com.example.starter"), store.installs)
        }

    @Test
    fun aPhoneThatDropsSendsTheBoardBackWithANote() =
        test {
            val session = FakeSession()
            val (viewModel, phone) = connected(session)

            phone.picked.completeExceptionally(PairingException("Gone"))

            assertEquals(
                FromPhoneState.Waiting(invite.toQrText(), "K7P2QX", TV_CODE_SECONDS, TvPairingNote.LostConnection),
                viewModel.state.value,
            )
            assertTrue(phone.closed)
        }

    @Test
    fun aPhoneThatTookTooLongSaysSo() =
        test {
            val (viewModel, phone) = connected()

            phone.picked.completeExceptionally(PairingStoppedException(PairingStop.TimedOut))

            assertEquals(TvPairingNote.TookTooLong, (viewModel.state.value as FromPhoneState.Waiting).note)
        }

    @Test
    fun theBoardRunsOut() =
        test {
            val viewModel = viewModel(FakeSession())

            advanceTimeBy(TV_CODE_SECONDS * 1_000L + 1)

            assertEquals(FromPhoneState.Expired("K7P2QX"), viewModel.state.value)
        }

    @Test
    fun cancelTellsThePhoneAndInstallsNothing() =
        test {
            val (viewModel, phone, store) = connected()

            viewModel.cancel()

            assertEquals(FromPhoneState.Stopped, viewModel.state.value)
            assertEquals(listOf(PairingStop.Cancelled), phone.stops)
            viewModel.install()
            assertEquals(emptyList(), store.installs)
        }
}
