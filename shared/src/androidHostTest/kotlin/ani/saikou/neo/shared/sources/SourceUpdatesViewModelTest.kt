package ani.saikou.neo.shared.sources

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

private const val LIST = "https://example.com/list.json"
private const val OTHER = "https://example.com/other.json"

private val alpha = InstalledSource("com.example.alpha", "Alpha", "1.0.0", list = LIST)
private val beta = InstalledSource("com.example.beta", "Beta", "1.0.0", list = LIST)
private val gamma = InstalledSource("com.example.gamma", "Gamma", "1.0.0", list = LIST)

private fun entry(
    source: InstalledSource,
    version: String,
    apk: String,
    api: Int = 1,
): String = """{ "id": "${source.id}", "name": "${source.name}", "version": "$version", "api": $api, "apk": "https://example.com/$apk" }"""

private fun list(vararg entries: String) = """{ "sources": [ ${entries.joinToString()} ] }"""

@OptIn(ExperimentalCoroutinesApi::class)
class SourceUpdatesViewModelTest {
    private val main = UnconfinedTestDispatcher()

    init {
        Dispatchers.setMain(main)
    }

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(main) { body() }

    // The APKs each install the newer version of their source.
    private val store =
        FakeStore(
            mapOf(
                "ALPHA" to alpha.copy(version = "1.1.0"),
                "BETA" to beta.copy(version = "1.1.0"),
                "GAMMA" to gamma.copy(version = "1.1.0"),
            ),
        )

    private var pages =
        mapOf(
            LIST to list(entry(alpha, "1.1.0", "alpha.apk"), entry(beta, "1.1.0", "beta.apk"), entry(gamma, "1.1.0", "gamma.apk")),
            "https://example.com/alpha.apk" to "ALPHA",
            "https://example.com/beta.apk" to "BETA",
            "https://example.com/gamma.apk" to "GAMMA",
        )
    private lateinit var engine: PagesEngine

    private val times = FakeCheckTimes()
    private var now = 1_000L

    private fun viewModel(vararg installed: InstalledSource): SourceUpdatesViewModel {
        store.installed.value = installed.toList()
        engine = PagesEngine(pages)
        return SourceUpdatesViewModel(store, SourceDownloads(engine, main), times, main) { now }
    }

    private val SourceUpdatesViewModel.statuses get() = state.value.sources.associate { it.source.id to it.status }

    @Test
    fun beforeACheckEachSourceWithAListIsUnknown() =
        test {
            val viewModel = viewModel(alpha, beta.copy(list = null))

            assertEquals(mapOf(alpha.id to UpdateStatus.Unknown, beta.id to UpdateStatus.NotChecked), viewModel.statuses)
            assertNull(viewModel.state.value.lastChecked)
        }

    @Test
    fun aCheckFindsUpdatesSourcesUpToDateAndOnesNeedingANewerApp() =
        test {
            pages =
                pages +
                (
                    LIST to
                        list(
                            entry(alpha, "1.1.0", "alpha.apk"),
                            entry(beta, "1.0.0", "beta.apk"),
                            entry(gamma, "2.0.0", "gamma.apk", api = 2),
                        )
                )
            val viewModel = viewModel(alpha, beta, gamma)

            viewModel.check()

            assertEquals(
                mapOf(
                    alpha.id to UpdateStatus.Available("1.1.0"),
                    beta.id to UpdateStatus.UpToDate,
                    gamma.id to UpdateStatus.NeedsNewerApp("2.0.0"),
                ),
                viewModel.statuses,
            )
            assertEquals(1, viewModel.state.value.updates)
            assertEquals(1_000L, viewModel.state.value.lastChecked)
            assertFalse(viewModel.state.value.checking)
        }

    @Test
    fun aSourceWithNoListIsNotChecked() =
        test {
            // Added from a single APK, or installed before lists were kept.
            val viewModel = viewModel(alpha.copy(list = null))

            viewModel.check()

            assertEquals(mapOf(alpha.id to UpdateStatus.NotChecked), viewModel.statuses)
            assertEquals(emptyList(), engine.urls)
        }

    @Test
    fun sourcesSharingAListFetchItOnce() =
        test {
            val viewModel = viewModel(alpha, beta, gamma)

            viewModel.check()

            assertEquals(listOf(LIST), engine.urls)
            assertEquals(3, viewModel.state.value.updates)
        }

    @Test
    fun aListThatCannotBeReadIsSkipped() =
        test {
            pages = pages + ("https://example.com/broken.json" to """{ "sources": [ { "id": """)
            val viewModel =
                viewModel(alpha.copy(list = "https://example.com/broken.json"), beta.copy(list = "https://example.com/gone.json"), gamma)

            viewModel.check()

            assertEquals(
                mapOf(
                    alpha.id to UpdateStatus.Unknown,
                    beta.id to UpdateStatus.Unknown,
                    gamma.id to UpdateStatus.Available("1.1.0"),
                ),
                viewModel.statuses,
            )
            assertFalse(viewModel.state.value.checking)
        }

    @Test
    fun aSourceItsListNoLongerNamesIsUnknown() =
        test {
            pages = pages + (OTHER to list(entry(beta, "1.1.0", "beta.apk")))
            val viewModel = viewModel(alpha.copy(list = OTHER))

            viewModel.check()

            assertEquals(mapOf(alpha.id to UpdateStatus.Unknown), viewModel.statuses)
        }

    @Test
    fun updateInstallsTheApkTheListNamesAndKeepsTheList() =
        test {
            val viewModel = viewModel(alpha, beta)
            viewModel.check()

            viewModel.update(alpha.id)

            assertEquals(listOf(alpha.id), store.installs)
            assertEquals(listOf(alpha.copy(version = "1.1.0"), beta), store.installed.value?.sortedBy { it.id })
            assertEquals(mapOf(alpha.id to UpdateStatus.UpToDate, beta.id to UpdateStatus.Available("1.1.0")), viewModel.statuses)
            assertNull(viewModel.state.value.progress)
        }

    @Test
    fun aSourceWithNoUpdateIsNotInstalled() =
        test {
            val viewModel = viewModel(alpha.copy(version = "1.1.0"))
            viewModel.check()

            viewModel.update(alpha.id)
            viewModel.updateAll()

            assertEquals(emptyList(), store.installs)
        }

    @Test
    fun anApkThatIsNotTheSourceItsEntryNamesFailsAndKeepsTheOldVersion() =
        test {
            pages = pages + (LIST to list(entry(alpha, "1.1.0", "beta.apk")))
            val viewModel = viewModel(alpha)
            viewModel.check()

            viewModel.update(alpha.id)

            assertEquals(listOf(alpha), store.installed.value)
            val row =
                viewModel.state.value.sources
                    .single()
            assertEquals(UpdateStatus.Available("1.1.0"), row.status)
            assertEquals(true, row.failed)
        }

    @Test
    fun updateAllInstallsOneAtATimeAndCarriesOnPastAFailure() =
        test {
            pages = pages - "https://example.com/beta.apk"
            val viewModel = viewModel(alpha, beta, gamma)
            viewModel.check()
            val first = CompletableDeferred<Unit>()
            val second = CompletableDeferred<Unit>()
            store.gates += listOf(first, second)

            viewModel.updateAll()

            assertEquals(Progress(done = 0, total = 3), viewModel.state.value.progress)
            assertEquals(emptyList(), store.installs)
            first.complete(Unit)
            // Beta's APK can't be fetched, so gamma is next.
            assertEquals(Progress(done = 2, total = 3), viewModel.state.value.progress)
            assertEquals(listOf(alpha.id), store.installs)
            second.complete(Unit)
            assertNull(viewModel.state.value.progress)
            assertEquals(listOf(alpha.id, gamma.id), store.installs)
            assertEquals(
                setOf(beta.id),
                viewModel.state.value.sources
                    .filter { it.failed }
                    .map { it.source.id }
                    .toSet(),
            )
            assertEquals(
                listOf(alpha.copy(version = "1.1.0"), beta, gamma.copy(version = "1.1.0")),
                store.installed.value?.sortedBy { it.id },
            )
        }

    @Test
    fun tryingAgainClearsTheFailure() =
        test {
            pages = pages + (LIST to list(entry(alpha, "1.1.0", "beta.apk")))
            val viewModel = viewModel(alpha)
            viewModel.check()
            viewModel.update(alpha.id)
            assertEquals(
                true,
                viewModel.state.value.sources
                    .single()
                    .failed,
            )
            val gate = CompletableDeferred<Unit>()
            store.gates += gate

            viewModel.update(alpha.id)

            assertEquals(
                false,
                viewModel.state.value.sources
                    .single()
                    .failed,
            )
            gate.complete(Unit)
            assertEquals(
                true,
                viewModel.state.value.sources
                    .single()
                    .failed,
            )
        }

    @Test
    fun whileUpdatingAllTheFirstIsInstallingAndTheRestWait() =
        test {
            val viewModel = viewModel(alpha, beta, gamma)
            viewModel.check()
            val first = CompletableDeferred<Unit>()
            store.gates += first

            viewModel.updateAll()

            val rows =
                viewModel.state.value.sources
                    .associateBy { it.source.id }
            assertEquals(listOf(true, false, false), listOf(alpha, beta, gamma).map { rows.getValue(it.id).installing })
            assertEquals(listOf(false, true, true), listOf(alpha, beta, gamma).map { rows.getValue(it.id).waiting })
            first.complete(Unit)
            assertEquals(
                listOf(false, false, false),
                viewModel.state.value.sources
                    .map { it.installing || it.waiting },
            )
        }

    @Test
    fun anUpdatedSourceSaysWhichVersionItGot() =
        test {
            val viewModel = viewModel(alpha, beta)
            viewModel.check()

            viewModel.update(alpha.id)

            val rows =
                viewModel.state.value.sources
                    .associateBy { it.source.id }
            assertEquals("1.1.0", rows.getValue(alpha.id).updatedTo)
            assertEquals(UpdateStatus.UpToDate, rows.getValue(alpha.id).status)
            assertNull(rows.getValue(beta.id).updatedTo)
        }

    @Test
    fun aDueCheckRunsWhenThereHasBeenNoneAndSavesTheTime() =
        test {
            val viewModel = viewModel(alpha)

            viewModel.checkIfDue()

            assertEquals(listOf(LIST), engine.urls)
            assertEquals(1_000L, times.saved)
        }

    @Test
    fun aDueCheckWaitsTwelveHoursFromTheLastOne() =
        test {
            times.saved = 1_000L
            now = 1_000L + 12 * 60 * 60 - 1
            val viewModel = viewModel(alpha)

            viewModel.checkIfDue()
            assertEquals(emptyList(), engine.urls)

            now += 1
            viewModel.checkIfDue()
            assertEquals(listOf(LIST), engine.urls)
            assertEquals(now, times.saved)
        }
}

/** Keeps when sources were last checked in memory. */
private class FakeCheckTimes : UpdateCheckTimes {
    var saved: Long? = null

    override suspend fun lastChecked(): Long? = saved

    override suspend fun checked(at: Long) {
        saved = at
    }
}
