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
private val starter = InstalledSource("com.example.starter", "Starter source", "1.2.0")
private val demo = InstalledSource("com.example.demo", "Demo source", "1.0.0")
private val newerStarter = starter.copy(problem = SourceProblem.BUILT_FOR_NEWER_APP)
private val testSource = InstalledSource("com.example.test", "Test source", "1.0.0")

private fun listed(
    source: InstalledSource,
    apk: String,
    api: Int = 1,
) = ListedSource(source.id, source.name, source.version, api, "https://example.com/$apk")

private fun entry(
    source: InstalledSource,
    apk: String,
    api: Int = 1,
) = """{ "id": "${source.id}", "name": "${source.name}", "version": "${source.version}", "api": $api, "apk": "https://example.com/$apk" }"""

private fun list(vararg entries: String) = """{ "sources": [ ${entries.joinToString()} ] }"""

/** Installs any APK whose text is a source's name, and keeps the list in memory. API version 1 is the only one it runs. */
private class FakeStore(
    private val sources: Map<String, InstalledSource> =
        mapOf("EXAMPLE" to example, "STARTER" to starter, "DEMO" to demo, "NEWER_STARTER" to newerStarter),
) : SourceStore {
    override val installed = MutableStateFlow<List<InstalledSource>?>(emptyList())
    val removed = mutableListOf<String>()
    val installs = mutableListOf<String>()
    var waitFor: CompletableDeferred<Unit>? = null

    override fun problemFor(builtFor: Int) =
        when {
            builtFor < 1 -> SourceProblem.BUILT_FOR_OLDER_APP
            builtFor > 1 -> SourceProblem.BUILT_FOR_NEWER_APP
            else -> null
        }

    override suspend fun install(
        apk: ByteArray,
        id: String?,
    ): InstalledSource? {
        waitFor?.await()
        val source = sources[apk.decodeToString()]?.takeIf { id == null || it.id == id } ?: return null
        installs += source.id
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
            "https://v.gd/forward.php?format=json&shorturl=Mine" to """{ "url": "https://example.com/mine.json" }""",
            "https://example.com/starter.apk" to "STARTER",
            "https://example.com/demo.apk" to "DEMO",
            "https://example.com/newer-starter.apk" to "NEWER_STARTER",
            "https://example.com/mine.json" to list(entry(starter, "starter.apk")),
            "https://example.com/broken.json" to """{ "sources": [ { "id": """,
            "https://example.com/empty.json" to list(),
            "https://example.com/newer.json" to list(entry(starter, "starter.apk", api = 2)),
            "https://example.com/all.json" to
                list(
                    entry(example, "source.apk"),
                    entry(testSource, "test.apk", api = 2),
                    entry(starter, "starter.apk"),
                    entry(demo, "demo.apk"),
                ),
            "https://example.com/two.json" to list(entry(starter, "starter.apk"), entry(demo, "demo.apk")),
            "https://example.com/mixed.json" to
                list(entry(starter, "starter.apk"), entry(demo, "gone.apk"), entry(example, "starter.apk")),
            "https://example.com/stale.json" to list(entry(starter, "newer-starter.apk"), entry(demo, "demo.apk")),
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

    private val SourcesViewModel.choosing get() = state.value.adding as Adding.Choosing

    @Test
    fun aListShowsItsSourcesWithNoneTicked() =
        test {
            val store = FakeStore().apply { installed.value = listOf(example, demo.copy(version = "0.9.0")) }
            val viewModel = viewModel(store)

            viewModel.add("https://example.com/all.json")

            assertEquals(
                Adding.Choosing(
                    listOf(
                        Choice(listed(starter, "starter.apk"), ChoiceStatus.New),
                        Choice(listed(demo, "demo.apk"), ChoiceStatus.Update(from = "0.9.0")),
                        Choice(listed(example, "source.apk"), ChoiceStatus.Installed),
                        Choice(listed(testSource, "test.apk", api = 2), ChoiceStatus.CantUse(SourceProblem.BUILT_FOR_NEWER_APP)),
                    ),
                ),
                viewModel.state.value.adding,
            )
            assertEquals(emptyList(), store.installs)
        }

    @Test
    fun aCodeCanLeadToAList() =
        test {
            val viewModel = viewModel()

            viewModel.add("Mine")

            assertEquals(listOf(Choice(listed(starter, "starter.apk"), ChoiceStatus.New)), viewModel.choosing.choices)
        }

    @Test
    fun aSourceInstalledAtTheSameOrANewerVersionCannotBeTicked() =
        test {
            val store = FakeStore().apply { installed.value = listOf(starter.copy(version = "1.10.0")) }
            val viewModel = viewModel(store)
            viewModel.add("https://example.com/mine.json")

            viewModel.toggle(starter.id)

            assertEquals(listOf(Choice(listed(starter, "starter.apk"), ChoiceStatus.Installed)), viewModel.choosing.choices)
        }

    @Test
    fun togglingTicksAndUnticks() =
        test {
            val viewModel = viewModel()
            viewModel.add("https://example.com/two.json")

            viewModel.toggle(demo.id)
            assertEquals(listOf(false, true), viewModel.choosing.choices.map { it.ticked })

            viewModel.toggle(demo.id)
            assertEquals(listOf(false, false), viewModel.choosing.choices.map { it.ticked })
        }

    @Test
    fun addingInstallsTheTickedSourcesInOrder() =
        test {
            val store = FakeStore()
            val viewModel = viewModel(store)
            viewModel.add("https://example.com/all.json")
            viewModel.toggle(demo.id)
            viewModel.toggle(starter.id)

            viewModel.addTicked()

            assertEquals(listOf(starter.id, demo.id), store.installs)
            assertEquals(Adding.AddedAll(listOf(starter, demo)), viewModel.state.value.adding)
        }

    @Test
    fun nothingTickedAddsNothing() =
        test {
            val store = FakeStore()
            val viewModel = viewModel(store)
            viewModel.add("https://example.com/two.json")

            viewModel.addTicked()

            assertEquals(emptyList(), store.installs)
            assertEquals(null, viewModel.choosing.progress)
        }

    @Test
    fun whileAddingItSaysHowFarItHasGot() =
        test {
            val store = FakeStore()
            val viewModel = viewModel(store)
            viewModel.add("https://example.com/two.json")
            viewModel.toggle(starter.id)
            viewModel.toggle(demo.id)
            store.waitFor = CompletableDeferred()

            viewModel.addTicked()

            assertEquals(Progress(done = 0, total = 2), viewModel.choosing.progress)
            viewModel.toggle(starter.id)
            assertEquals(listOf(true, true), viewModel.choosing.choices.map { it.ticked })
            store.waitFor!!.complete(Unit)
            assertEquals(Adding.AddedAll(listOf(starter, demo)), viewModel.state.value.adding)
        }

    @Test
    fun whenSomeFailTheListStaysWithWhatHappenedToEach() =
        test {
            val viewModel = viewModel()
            viewModel.add("https://example.com/mixed.json")
            viewModel.choosing.choices.forEach { viewModel.toggle(it.source.id) }

            viewModel.addTicked()

            assertEquals(
                Adding.Choosing(
                    listOf(
                        Choice(listed(starter, "starter.apk"), ChoiceStatus.Installed),
                        Choice(listed(demo, "gone.apk"), ChoiceStatus.Failed, ticked = true),
                        // Its APK is another source, so it isn't installed.
                        Choice(listed(example, "starter.apk"), ChoiceStatus.Failed, ticked = true),
                    ),
                ),
                viewModel.state.value.adding,
            )
        }

    @Test
    fun aSourceWhoseApkIsBuiltForAnotherVersionSaysWhy() =
        test {
            val viewModel = viewModel()
            viewModel.add("https://example.com/stale.json")
            viewModel.toggle(starter.id)
            viewModel.toggle(demo.id)

            viewModel.addTicked()

            assertEquals(
                listOf(
                    Choice(listed(starter, "newer-starter.apk"), ChoiceStatus.CantUse(SourceProblem.BUILT_FOR_NEWER_APP)),
                    Choice(listed(demo, "demo.apk"), ChoiceStatus.Installed),
                ),
                viewModel.choosing.choices,
            )
        }

    @Test
    fun aListThatCannotBeReadSaysSo() =
        test {
            val viewModel = viewModel()

            viewModel.add("https://example.com/broken.json")

            assertEquals(Adding.Failed(AddError.BROKEN_LIST), viewModel.state.value.adding)
        }

    @Test
    fun aListWithNothingThisAppCanUseSaysSo() =
        test {
            val viewModel = viewModel()

            viewModel.add("https://example.com/empty.json")
            assertEquals(Adding.Failed(AddError.NOTHING_IN_LIST), viewModel.state.value.adding)

            viewModel.add("https://example.com/newer.json")
            assertEquals(Adding.Failed(AddError.NOTHING_IN_LIST), viewModel.state.value.adding)
        }
}
