package ani.saikou.neo.shared.sources

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val example = InstalledSource("ani.saikou.neo.example", "Example source", "1.4.0")

/** Installs any APK whose text is a source's name, and keeps the list in memory. */
private class FakeStore(
    private val sources: Map<String, InstalledSource> = mapOf("EXAMPLE" to example),
) : SourceStore {
    override val installed = MutableStateFlow<List<InstalledSource>?>(emptyList())
    val removed = mutableListOf<String>()
    var waitFor: CompletableDeferred<Unit>? = null

    override suspend fun install(apk: ByteArray): InstalledSource? {
        waitFor?.await()
        val source = sources[apk.decodeToString()] ?: return null
        if (source.problem == null) installed.value = installed.value.orEmpty().filter { it.id != source.id } + source
        return source
    }

    override suspend fun remove(id: String) {
        removed += id
        installed.value = installed.value.orEmpty().filter { it.id != id }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SourcesViewModelTest {
    private val main = UnconfinedTestDispatcher()

    init {
        Dispatchers.setMain(main)
    }

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(main) { body() }

    private val pages =
        mapOf(
            "https://example.com/source.apk" to "EXAMPLE",
            "https://example.com/newer.apk" to "NEWER",
            "https://example.com/readme.txt" to "README",
            "https://v.gd/forward.php?format=json&shorturl=Example_1" to """{ "url": "https://example.com/source.apk" }""",
            "https://v.gd/forward.php?format=json&shorturl=missing" to """{ "errorcode": 1, "errormessage": "No" }""",
            "https://v.gd/forward.php?format=json&shorturl=public_http" to """{ "url": "http://example.com/source.apk" }""",
        )

    private fun viewModel(store: SourceStore = FakeStore()): SourcesViewModel {
        val engine = PagesEngine(pages)
        return SourcesViewModel(store, ShortCodes(engine, main), SourceDownloads(engine, main))
    }

    @Test
    fun showsTheInstalledSources() =
        test {
            val store = FakeStore().apply { installed.value = listOf(example) }

            assertEquals(listOf(example), viewModel(store).state.value.installed)
        }

    @Test
    fun aLinkInstallsItsSource() =
        test {
            val store = FakeStore()
            val viewModel = viewModel(store)

            viewModel.add(" https://example.com/source.apk ")

            assertEquals(Adding.Added(example), viewModel.state.value.adding)
            assertEquals(listOf(example), viewModel.state.value.installed)
        }

    @Test
    fun aCodeInstallsTheSourceItLinksTo() =
        test {
            val viewModel = viewModel()

            viewModel.add("Example_1")

            assertEquals(Adding.Added(example), viewModel.state.value.adding)
        }

    @Test
    fun whileCheckingItSaysSo() =
        test {
            val store = FakeStore().apply { waitFor = CompletableDeferred() }
            val viewModel = viewModel(store)

            viewModel.add("https://example.com/source.apk")

            assertEquals(Adding.Checking, viewModel.state.value.adding)
            store.waitFor!!.complete(Unit)
            assertEquals(Adding.Added(example), viewModel.state.value.adding)
        }

    @Test
    fun anUnknownCodeSaysSo() =
        test {
            val viewModel = viewModel()

            viewModel.add("missing")

            assertEquals(Adding.Failed(AddError.NO_SUCH_CODE), viewModel.state.value.adding)
        }

    @Test
    fun textThatIsNeitherALinkNorACodeSaysSo() =
        test {
            val viewModel = viewModel()

            viewModel.add("not a code")

            assertEquals(Adding.Failed(AddError.NO_SUCH_CODE), viewModel.state.value.adding)
        }

    @Test
    fun plainHttpOutsideHomeIsRefused() =
        test {
            val viewModel = viewModel()

            viewModel.add("http://example.com/source.apk")
            assertEquals(Adding.Failed(AddError.NOT_SECURE), viewModel.state.value.adding)

            viewModel.add("public_http")
            assertEquals(Adding.Failed(AddError.NOT_SECURE), viewModel.state.value.adding)
        }

    @Test
    fun aFileThatIsNotASourceOrCannotBeFetchedSaysSo() =
        test {
            val viewModel = viewModel()

            viewModel.add("https://example.com/readme.txt")
            assertEquals(Adding.Failed(AddError.NOT_A_SOURCE), viewModel.state.value.adding)

            viewModel.add("https://example.com/gone.apk")
            assertEquals(Adding.Failed(AddError.NOT_A_SOURCE), viewModel.state.value.adding)
        }

    @Test
    fun aSourceBuiltForAnotherVersionIsNotInstalledAndSaysWhy() =
        test {
            val newer = InstalledSource("ani.saikou.neo.example", "Example source", "2.0.0", SourceProblem.BUILT_FOR_NEWER_APP)
            val viewModel = viewModel(FakeStore(mapOf("NEWER" to newer)))

            viewModel.add("https://example.com/newer.apk")

            assertEquals(Adding.CantUse(newer), viewModel.state.value.adding)
            assertEquals(emptyList(), viewModel.state.value.installed)
        }

    @Test
    fun dismissingStopsTheCheckAndClearsTheResult() =
        test {
            val store = FakeStore().apply { waitFor = CompletableDeferred() }
            val viewModel = viewModel(store)
            viewModel.add("https://example.com/source.apk")

            viewModel.dismiss()
            store.waitFor!!.complete(Unit)

            assertEquals(Adding.Idle, viewModel.state.value.adding)
            assertEquals(emptyList(), viewModel.state.value.installed)
        }

    @Test
    fun removeTakesASourceOffTheList() =
        test {
            val store = FakeStore().apply { installed.value = listOf(example) }
            val viewModel = viewModel(store)

            viewModel.remove(example.id)

            assertEquals(listOf(example.id), store.removed)
            assertEquals(emptyList(), viewModel.state.value.installed)
        }

    @Test
    fun beforeTheListIsReadThereIsNone() =
        test {
            val store =
                object : SourceStore by FakeStore() {
                    override val installed: StateFlow<List<InstalledSource>?> = MutableStateFlow(null)
                }

            assertNull(viewModel(store).state.value.installed)
        }
}
