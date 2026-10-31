package ani.saikou.neo.shared.playback

import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.settings.SubOrDub
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.shared.sources.SourceProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val example = InstalledSource("com.example.example", "Example source", "1.0.0")
private val demo = InstalledSource("com.example.demo", "Demo source", "1.0.0")
private val starter = InstalledSource("com.example.starter", "Starter source", "1.0.0")

private val show =
    AnimeDetails(
        card = AnimeCard(1535, AnimeTitle("Death Note", null, null), null, null, null, "TV", 37, null, null, null),
        description = null,
        status = null,
        episodeMinutes = 23,
        genres = emptyList(),
        studios = emptyList(),
        nextEpisode = null,
    )

private val sub1 = FoundServer("1", "Server 1", ServerEdition.SUB, 24)
private val hard = FoundServer("2", "Server 2", ServerEdition.HARD_SUB, null)
private val dub = FoundServer("3", "Server 3", ServerEdition.DUB, 23)

/** Answers each source once its gate opens, with its servers or by throwing. A source without a gate answers at once. */
private class FakeFinder(
    private val servers: Map<String, List<FoundServer>>,
    private val failing: Set<String> = emptySet(),
) : ServerFinder {
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
    val asked = mutableListOf<String>()

    fun gate(source: InstalledSource) = CompletableDeferred<Unit>().also { gates[source.id] = it }

    override suspend fun servers(
        source: InstalledSource,
        show: AnimeDetails,
        episode: Int,
    ): List<FoundServer> {
        asked += source.id
        gates[source.id]?.await()
        if (source.id in failing) error("Broken")
        return servers[source.id].orEmpty()
    }
}

private class FakeLastServers(
    val saved: MutableMap<Int, LastServer> = mutableMapOf(),
) : LastServers {
    override suspend fun get(animeId: Int) = saved[animeId]

    override suspend fun set(
        animeId: Int,
        server: LastServer,
    ) {
        saved[animeId] = server
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ServersViewModelTest {
    private val main = UnconfinedTestDispatcher()

    init {
        Dispatchers.setMain(main)
    }

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(main) { body() }

    private fun viewModel(
        finder: ServerFinder,
        sources: List<InstalledSource> = listOf(example),
        subOrDub: SubOrDub = SubOrDub.BOTH,
        last: LastServers = FakeLastServers(),
    ) = ServersViewModel(finder, MutableStateFlow(sources), show, 8, subOrDub, last)

    @Test
    fun aSourceThatTimesOutOnItsOwnHasFailed() =
        test {
            val servers = viewModel({ _, _, _ -> throw CancellationException("The source's own timeout") })

            assertEquals(
                SourceAnswer.Failed,
                servers.state.value.sources
                    .single()
                    .answer,
            )
        }

    @Test
    fun aLastServerIsKnownByItsEditionAsWellAsItsName() =
        test {
            val demoSub = FoundServer("s", "Demo", ServerEdition.SUB, 12)
            val demoDub = FoundServer("d", "Demo", ServerEdition.DUB, 10)
            val last = FakeLastServers(mutableMapOf(1535 to LastServer(example.id, "Demo", ServerEdition.DUB)))
            val servers = viewModel(FakeFinder(mapOf(example.id to listOf(demoSub, demoDub))), last = last)

            assertEquals(
                demoDub,
                servers.state.value.best
                    ?.server,
            )
            assertEquals(
                false,
                servers.state.value.lastUsed
                    ?.isServer(example, demoSub),
            )
            assertEquals(
                true,
                servers.state.value.lastUsed
                    ?.isServer(example, demoDub),
            )
        }

    @Test
    fun waitsForTheInstalledSourcesToBeRead() =
        test {
            val installed = MutableStateFlow<List<InstalledSource>?>(null)
            val finder = FakeFinder(mapOf(example.id to listOf(sub1)))
            val servers = ServersViewModel(finder, installed, show, 8, SubOrDub.BOTH, FakeLastServers())

            assertEquals(emptyList(), servers.state.value.sources)

            installed.value = listOf(example)

            assertEquals(listOf(sub1), servers.servers(example))
        }

    private fun ServersViewModel.servers(source: InstalledSource) =
        (
            state.value.sources
                .single { it.source == source }
                .answer as SourceAnswer.Found
        ).servers

    @Test
    fun bothShowsEveryServer() =
        test {
            val servers = viewModel(FakeFinder(mapOf(example.id to listOf(sub1, hard, dub))))

            assertEquals(listOf(sub1, hard, dub), servers.servers(example))
        }

    @Test
    fun subShowsSubAndHardSubOnly() =
        test {
            val servers = viewModel(FakeFinder(mapOf(example.id to listOf(sub1, hard, dub))), subOrDub = SubOrDub.SUB)

            assertEquals(listOf(sub1, hard), servers.servers(example))
        }

    @Test
    fun dubShowsDubOnly() =
        test {
            val servers = viewModel(FakeFinder(mapOf(example.id to listOf(sub1, hard, dub))), subOrDub = SubOrDub.DUB)

            assertEquals(listOf(dub), servers.servers(example))
        }

    @Test
    fun asksForTheEpisodeOfTheShow() =
        test {
            var asked: Pair<Int, Int>? = null
            viewModel({ _, show, episode ->
                asked = show.card.id to episode
                emptyList()
            })

            assertEquals(1535 to 8, asked)
        }

    @Test
    fun skipsSourcesThatCantBeUsed() =
        test {
            val broken = starter.copy(problem = SourceProblem.BROKEN)
            val finder = FakeFinder(mapOf(example.id to listOf(sub1)))
            val servers = viewModel(finder, listOf(example, broken))

            assertEquals(listOf(example.id), finder.asked)
            assertEquals(
                listOf(example),
                servers.state.value.sources
                    .map { it.source },
            )
        }

    @Test
    fun aSourceStillLookingSaysSoWhileOthersCanBePicked() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1), demo.id to listOf(dub)))
            val gate = finder.gate(example)
            val servers = viewModel(finder, listOf(example, demo))

            assertEquals(
                listOf(demo, example),
                servers.state.value.sources
                    .map { it.source },
            )
            assertEquals(
                SourceAnswer.Looking,
                servers.state.value.sources
                    .last()
                    .answer,
            )
            assertEquals(listOf(dub), servers.servers(demo))
            assertEquals(1, servers.state.value.answered)

            gate.complete(Unit)

            assertEquals(listOf(sub1), servers.servers(example))
            assertEquals(2, servers.state.value.answered)
        }

    @Test
    fun sourcesAreListedInTheOrderTheyAnswered() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1), demo.id to listOf(dub), starter.id to listOf(hard)))
            val gates = listOf(example, demo, starter).associateWith { finder.gate(it) }
            val servers = viewModel(finder, listOf(example, demo, starter))

            gates.getValue(starter).complete(Unit)
            gates.getValue(example).complete(Unit)

            assertEquals(
                listOf(starter, example, demo),
                servers.state.value.sources
                    .map { it.source },
            )
        }

    @Test
    fun aSourceThatThrowsHasFailed() =
        test {
            val servers = viewModel(FakeFinder(mapOf(demo.id to listOf(dub)), failing = setOf(example.id)), listOf(example, demo))

            assertEquals(
                SourceAnswer.Failed,
                servers.state.value.sources
                    .single { it.source == example }
                    .answer,
            )
            assertEquals(listOf(dub), servers.servers(demo))
        }

    @Test
    fun theBestPickIsTheLastUsedServer() =
        test {
            val last = FakeLastServers(mutableMapOf(1535 to LastServer(demo.id, "Server 3", ServerEdition.DUB)))
            val servers =
                viewModel(FakeFinder(mapOf(example.id to listOf(sub1), demo.id to listOf(dub))), listOf(example, demo), last = last)

            assertEquals(BestPick(demo, dub, lastUsed = true, answeredFirst = false), servers.state.value.best)
            assertEquals(LastServer(demo.id, "Server 3", ServerEdition.DUB), servers.state.value.lastUsed)
        }

    @Test
    fun aLastUsedServerFilteredOutIsntTheBestPick() =
        test {
            val last = FakeLastServers(mutableMapOf(1535 to LastServer(example.id, "Server 3", ServerEdition.DUB)))
            val servers = viewModel(FakeFinder(mapOf(example.id to listOf(dub, sub1))), subOrDub = SubOrDub.SUB, last = last)

            assertEquals(BestPick(example, sub1, lastUsed = false, answeredFirst = false), servers.state.value.best)
        }

    @Test
    fun withoutALastUsedServerTheFirstSourceToAnswerGivesTheBestPick() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1), demo.id to listOf(hard)))
            val gate = finder.gate(example)
            val servers = viewModel(finder, listOf(example, demo))

            assertEquals(BestPick(demo, hard, lastUsed = false, answeredFirst = true), servers.state.value.best)

            gate.complete(Unit)

            assertEquals(BestPick(demo, hard, lastUsed = false, answeredFirst = true), servers.state.value.best)
        }

    @Test
    fun bothPrefersSubThenHardSubThenDub() =
        test {
            assertEquals(
                sub1,
                viewModel(FakeFinder(mapOf(example.id to listOf(dub, hard, sub1))))
                    .state.value.best
                    ?.server,
            )
            assertEquals(
                hard,
                viewModel(FakeFinder(mapOf(example.id to listOf(dub, hard))))
                    .state.value.best
                    ?.server,
            )
            assertEquals(
                dub,
                viewModel(FakeFinder(mapOf(example.id to listOf(dub))))
                    .state.value.best
                    ?.server,
            )
        }

    @Test
    fun aSourceWithNothingMatchingIsPassedOverForTheBestPick() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1), demo.id to listOf(dub)))
            val gate = finder.gate(example)
            val servers = viewModel(finder, listOf(example, demo), subOrDub = SubOrDub.SUB)

            assertNull(servers.state.value.best)

            gate.complete(Unit)

            assertEquals(BestPick(example, sub1, lastUsed = false, answeredFirst = false), servers.state.value.best)
        }

    @Test
    fun pickingAServerMakesItTheLastUsedForThisShow() =
        test {
            val last = FakeLastServers()
            val servers = viewModel(FakeFinder(mapOf(example.id to listOf(sub1, dub))), last = last)

            servers.pick(example, dub)

            assertEquals(mapOf(1535 to LastServer(example.id, "Server 3", ServerEdition.DUB)), last.saved)
            assertEquals(LastServer(example.id, "Server 3", ServerEdition.DUB), servers.state.value.lastUsed)
        }

    @Test
    fun theFilterShowsOneSource() =
        test {
            val servers = viewModel(FakeFinder(mapOf(example.id to listOf(sub1), demo.id to listOf(dub))), listOf(example, demo))

            servers.filter(demo.id)

            assertEquals(demo.id, servers.state.value.filter)
            assertEquals(
                listOf(demo),
                servers.state.value.shown
                    .map { it.source },
            )

            servers.filter(null)

            assertEquals(2, servers.state.value.shown.size)
        }
}
