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
import kotlin.test.assertContentEquals
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
    icon: String? = null,
): String {
    val logo = icon?.let { """, "icon": "https://example.com/$it"""" }.orEmpty()
    val fields = """"id": "${source.id}", "name": "${source.name}", "version": "${source.version}", "api": $api"""
    return """{ $fields, "apk": "https://example.com/$apk"$logo }"""
}

private val starterLogo = png(96, 96)

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

    // Each install waits for the next of these, if there is one.
    val gates = ArrayDeque<CompletableDeferred<Unit>>()

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
        gates.removeFirstOrNull()?.await()
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
            "https://example.com/beta.json" to
                list(entry(starter.copy(version = "1.3.0-beta.1"), "starter.apk"), entry(demo.copy(version = "v1.1.0"), "demo.apk")),
            "https://example.com/logos.json" to
                list(
                    entry(starter, "starter.apk", icon = "starter.png"),
                    entry(demo, "demo.apk", icon = "huge.png"),
                    entry(example, "source.apk"),
                ),
        )

    private val files = mapOf("https://example.com/starter.png" to starterLogo, "https://example.com/huge.png" to png(4096, 4096))

    private fun viewModel(store: SourceStore = FakeStore()): SourcesViewModel {
        val engine = PagesEngine(pages, files)
        return SourcesViewModel(store, ShortCodes(engine, main), SourceDownloads(engine, main), main)
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
    fun aListShowsTheLogosItCanFetch() =
        test {
            val viewModel = viewModel()

            viewModel.add("https://example.com/logos.json")

            // The demo's logo is too big to draw, so it has none, but it can still be picked.
            assertEquals(setOf(starter.id), viewModel.state.value.icons.keys)
            assertContentEquals(starterLogo, viewModel.state.value.icons[starter.id])
            assertEquals(3, viewModel.choosing.choices.count { it.status.canPick })
        }

    @Test
    fun closingTheListOrAddingAnotherLinkDropsItsLogos() =
        test {
            val viewModel = viewModel()
            viewModel.add("https://example.com/logos.json")

            viewModel.dismiss()
            assertEquals(emptyMap(), viewModel.state.value.icons)

            viewModel.add("https://example.com/logos.json")
            viewModel.add("https://example.com/two.json")
            assertEquals(emptyMap(), viewModel.state.value.icons)
        }

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
            viewModel.add("https://example.com/two.json")

            viewModel.toggle(starter.id)

            assertEquals(Choice(listed(starter, "starter.apk"), ChoiceStatus.Installed), viewModel.choosing.choices.last())
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
            val first = CompletableDeferred<Unit>()
            val second = CompletableDeferred<Unit>()
            store.gates += listOf(first, second)

            viewModel.addTicked()

            assertEquals(Progress(done = 0, total = 2), viewModel.choosing.progress)
            viewModel.toggle(starter.id)
            assertEquals(listOf(true, true), viewModel.choosing.choices.map { it.ticked })
            first.complete(Unit)
            assertEquals(Progress(done = 1, total = 2), viewModel.choosing.progress)
            assertEquals(
                ChoiceStatus.Installed,
                viewModel.choosing.choices
                    .first()
                    .status,
            )
            second.complete(Unit)
            assertEquals(Adding.AddedAll(listOf(starter, demo)), viewModel.state.value.adding)
        }

    @Test
    fun dismissingWhileAddingLetsItFinish() =
        test {
            val store = FakeStore()
            val viewModel = viewModel(store)
            viewModel.add("https://example.com/two.json")
            viewModel.toggle(starter.id)
            viewModel.toggle(demo.id)
            val gate = CompletableDeferred<Unit>()
            store.gates += gate
            viewModel.addTicked()

            viewModel.dismiss()
            gate.complete(Unit)

            assertEquals(listOf(starter.id, demo.id), store.installs)
            assertEquals(Adding.AddedAll(listOf(starter, demo)), viewModel.state.value.adding)
        }

    @Test
    fun theSameVersionCountsAsInstalled() =
        test {
            val store = FakeStore().apply { installed.value = listOf(starter) }
            val viewModel = viewModel(store)

            viewModel.add("https://example.com/two.json")

            assertEquals(
                listOf(ChoiceStatus.New, ChoiceStatus.Installed),
                viewModel.choosing.choices.map { it.status },
            )
        }

    @Test
    fun aPreReleaseComesBeforeItsReleaseAndALeadingVIsIgnored() =
        test {
            val store = FakeStore().apply { installed.value = listOf(starter.copy(version = "1.3.0"), demo) }
            val viewModel = viewModel(store)

            viewModel.add("https://example.com/beta.json")

            // Demo 1.0.0 is installed and the list has v1.1.0. Starter 1.3.0 is installed and the list has 1.3.0-beta.1.
            assertEquals(
                listOf(ChoiceStatus.Update(from = "1.0.0"), ChoiceStatus.Installed),
                viewModel.choosing.choices.map { it.status },
            )
        }

    @Test
    fun aReleaseUpdatesItsPreRelease() =
        test {
            val store = FakeStore().apply { installed.value = listOf(starter.copy(version = "1.2.0-beta.1")) }
            val viewModel = viewModel(store)

            viewModel.add("https://example.com/mine.json")

            assertEquals(
                ChoiceStatus.Update(from = "1.2.0-beta.1"),
                viewModel.choosing.choices
                    .single()
                    .status,
            )
        }

    @Test
    fun aListWithEverythingInstalledSaysSo() =
        test {
            val store = FakeStore().apply { installed.value = listOf(starter) }
            val viewModel = viewModel(store)

            viewModel.add("https://example.com/mine.json")

            assertEquals(Adding.Failed(AddError.ALL_INSTALLED), viewModel.state.value.adding)
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
