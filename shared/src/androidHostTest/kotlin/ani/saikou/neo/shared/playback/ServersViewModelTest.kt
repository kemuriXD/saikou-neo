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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

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
    private val failing: Map<String, Exception> = emptyMap(),
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
        failing[source.id]?.let { throw it }
        return servers[source.id].orEmpty()
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
    ) = ServersViewModel(finder, MutableStateFlow(sources), show, 8, subOrDub)

    private fun ServersViewModel.answer(source: InstalledSource = example) =
        state.value.sources
            .single { it.source == source }
            .answer

    @Test
    fun aSourceThatTimesOutOnItsOwnIsDown() =
        test {
            val servers = viewModel({ _, _, _ -> throw CancellationException("The source's own timeout") })

            assertEquals(SourceAnswer.Failed(FailReason.SourceDown), servers.answer())
        }

    @Test
    fun aSourceThatGivesAReasonFailsWithIt() =
        test {
            val failure = SourceFailure(FailReason.RateLimited(retryAfterSeconds = 30))
            val servers = viewModel(FakeFinder(emptyMap(), failing = mapOf(example.id to failure)))

            assertEquals(SourceAnswer.Failed(FailReason.RateLimited(30), waitSeconds = 30), servers.answer())
        }

    @Test
    fun anythingElseASourceThrowsMeansItsDown() =
        test {
            val servers = viewModel(FakeFinder(emptyMap(), failing = mapOf(example.id to IllegalStateException("Broken"))))

            assertEquals(SourceAnswer.Failed(FailReason.SourceDown), servers.answer())
        }

    @Test
    fun aSourceSilentFor25SecondsHasNoAnswer() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1)))
            finder.gate(example)
            val servers = viewModel(finder)

            advanceTimeBy(24.9.seconds)
            assertEquals(SourceAnswer.Looking, servers.answer())

            advanceTimeBy(0.2.seconds)
            assertEquals(SourceAnswer.Failed(FailReason.NoAnswer), servers.answer())
        }

    @Test
    fun aSourceThatIgnoresBeingStoppedStillHasNoAnswerAfter25Seconds() =
        test {
            val servers =
                viewModel({ _, _, _ ->
                    withContext(NonCancellable) { delay(60.seconds) }
                    listOf(sub1)
                })

            advanceTimeBy(25.1.seconds)

            assertEquals(SourceAnswer.Failed(FailReason.NoAnswer), servers.answer())
        }

    @Test
    fun aSourceGetsPastItsSiteBrowserCheckOnASlowTv() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1)))
            val gate = finder.gate(example)
            val checking = MutableStateFlow(true)
            val servers = ServersViewModel(finder, MutableStateFlow(listOf(example)), show, 8, SubOrDub.BOTH, siteChecking = checking)

            // A Fire TV takes over a minute to pass the check, then the source asks the site for its servers (#356).
            advanceTimeBy(68.seconds)
            assertEquals(SourceAnswer.Looking, servers.answer())
            checking.value = false
            advanceTimeBy(3.seconds)
            gate.complete(Unit)
            advanceTimeBy(1.seconds)

            assertEquals(listOf(sub1), servers.servers(example))
        }

    @Test
    fun aSourceAnsweringJustInTimeIsShown() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1)))
            val gate = finder.gate(example)
            val servers = viewModel(finder)

            advanceTimeBy(24.9.seconds)
            gate.complete(Unit)
            advanceTimeBy(1.seconds)

            assertEquals(listOf(sub1), servers.servers(example))
        }

    @Test
    fun waitsForTheInstalledSourcesToBeRead() =
        test {
            val installed = MutableStateFlow<List<InstalledSource>?>(null)
            val finder = FakeFinder(mapOf(example.id to listOf(sub1)))
            val servers = ServersViewModel(finder, installed, show, 8, SubOrDub.BOTH)

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

    private fun ServersViewModel.groups(source: InstalledSource) =
        state.value.groups(
            state.value.sources
                .single { it.source == source },
        )

    @Test
    fun allGivesASubGroupWithHardSubLastAndADubGroup() =
        test {
            val sub2 = FoundServer("4", "Server 4", ServerEdition.SUB, 24)
            val servers = viewModel(FakeFinder(mapOf(example.id to listOf(hard, dub, sub1, sub2))))

            assertEquals(ServerGroups(sub = listOf(sub1, sub2, hard), dub = listOf(dub)), servers.groups(example))
        }

    @Test
    fun subShowsSubThenHardSubOnly() =
        test {
            val servers = viewModel(FakeFinder(mapOf(example.id to listOf(hard, dub, sub1))), subOrDub = SubOrDub.SUB)

            assertEquals(ServerGroups(sub = listOf(sub1, hard), dub = emptyList()), servers.groups(example))
        }

    @Test
    fun dubShowsDubOnly() =
        test {
            val servers = viewModel(FakeFinder(mapOf(example.id to listOf(sub1, hard, dub))), subOrDub = SubOrDub.DUB)

            assertEquals(ServerGroups(sub = emptyList(), dub = listOf(dub)), servers.groups(example))
        }

    @Test
    fun theEditionCanBeChangedForThePageAlone() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1, dub)))
            val servers = viewModel(finder, subOrDub = SubOrDub.SUB)
            finder.asked.clear()

            servers.showEdition(SubOrDub.DUB)

            assertEquals(SubOrDub.DUB, servers.state.value.subOrDub)
            assertEquals(ServerGroups(sub = emptyList(), dub = listOf(dub)), servers.groups(example))
            assertEquals(emptyList(), finder.asked)

            servers.showEdition(SubOrDub.BOTH)

            assertEquals(ServerGroups(sub = listOf(sub1), dub = listOf(dub)), servers.groups(example))
        }

    @Test
    fun aSourceStillLookingSaysSoWhileOthersCanBePicked() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1), demo.id to listOf(dub)))
            val gate = finder.gate(example)
            val servers = viewModel(finder, listOf(example, demo))

            assertEquals(SourceAnswer.Looking, servers.answer(example))
            assertEquals(listOf(dub), servers.servers(demo))
            assertEquals(1, servers.state.value.answered)

            gate.complete(Unit)

            assertEquals(listOf(sub1), servers.servers(example))
            assertEquals(2, servers.state.value.answered)
        }

    @Test
    fun sourcesStayInTheOrderTheyreInstalled() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1), demo.id to listOf(dub), starter.id to listOf(hard)))
            val gates = listOf(example, demo, starter).associateWith { finder.gate(it) }
            val servers = viewModel(finder, listOf(example, demo, starter))

            gates.getValue(starter).complete(Unit)
            gates.getValue(demo).complete(Unit)

            assertEquals(
                listOf(example, demo, starter),
                servers.state.value.sources
                    .map { it.source },
            )
        }

    @Test
    fun aSourceAskedAgainKeepsItsPlace() =
        test {
            val finder = FakeFinder(mapOf(demo.id to listOf(dub)), failing = mapOf(example.id to SourceFailure(FailReason.Blocked)))
            val servers = viewModel(finder, listOf(example, demo))
            finder.gate(example)

            servers.retry(example)

            assertEquals(
                listOf(example, demo),
                servers.state.value.sources
                    .map { it.source },
            )
            assertEquals(SourceAnswer.Looking, servers.answer(example))
        }

    @Test
    fun retryingOneSourceAsksOnlyIt() =
        test {
            val finder =
                FakeFinder(
                    mapOf(demo.id to emptyList()),
                    failing = mapOf(example.id to SourceFailure(FailReason.Blocked), starter.id to SourceFailure(FailReason.NotFound)),
                )
            val servers = viewModel(finder, listOf(example, demo, starter))
            finder.asked.clear()

            servers.retry(starter)

            assertEquals(listOf(starter.id), finder.asked)
        }

    @Test
    fun retryingOneSourceThatHasServersOrIsLookingDoesNothing() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1), demo.id to listOf(dub)))
            finder.gate(demo)
            val servers = viewModel(finder, listOf(example, demo))
            finder.asked.clear()

            servers.retry(example)
            servers.retry(demo)

            assertEquals(emptyList(), finder.asked)
        }

    @Test
    fun aRateLimitedSourceCountsDownTheWait() =
        test {
            val failing = mapOf(example.id to SourceFailure(FailReason.RateLimited(retryAfterSeconds = 3)))
            val servers = viewModel(FakeFinder(emptyMap(), failing = failing))

            assertEquals(SourceAnswer.Failed(FailReason.RateLimited(3), waitSeconds = 3), servers.answer())

            advanceTimeBy(1.1.seconds)
            assertEquals(SourceAnswer.Failed(FailReason.RateLimited(3), waitSeconds = 2), servers.answer())

            advanceTimeBy(2.seconds)
            assertEquals(SourceAnswer.Failed(FailReason.RateLimited(3), waitSeconds = null), servers.answer())
        }

    @Test
    fun aRateLimitedSourceIsntAskedAgainUntilTheWaitIsOver() =
        test {
            val failing = mapOf(example.id to SourceFailure(FailReason.RateLimited(retryAfterSeconds = 3)))
            val finder = FakeFinder(emptyMap(), failing = failing)
            val servers = viewModel(finder)
            finder.asked.clear()

            servers.retry(example)
            assertEquals(emptyList(), finder.asked)

            advanceTimeBy(3.1.seconds)
            servers.retry(example)
            assertEquals(listOf(example.id), finder.asked)
        }

    @Test
    fun aRateLimitWithoutAWaitCanBeRetriedAtOnce() =
        test {
            val failing = mapOf(example.id to SourceFailure(FailReason.RateLimited(retryAfterSeconds = null)))
            val finder = FakeFinder(emptyMap(), failing = failing)
            val servers = viewModel(finder)
            finder.asked.clear()

            servers.retry(example)

            assertEquals(listOf(example.id), finder.asked)
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
    fun aSourceThatThrowsHasFailed() =
        test {
            val failing = mapOf(example.id to SourceFailure(FailReason.NotFound))
            val servers = viewModel(FakeFinder(mapOf(demo.id to listOf(dub)), failing = failing), listOf(example, demo))

            assertEquals(SourceAnswer.Failed(FailReason.NotFound), servers.answer(example))
            assertEquals(listOf(dub), servers.servers(demo))
        }

    @Test
    fun noUsableSourceMeansNoSources() =
        test {
            val broken = starter.copy(problem = SourceProblem.BROKEN)

            assertEquals(StreamsOutcome.NoSources, viewModel(FakeFinder(emptyMap()), emptyList()).state.value.outcome)
            assertEquals(StreamsOutcome.NoSources, viewModel(FakeFinder(emptyMap()), listOf(broken)).state.value.outcome)
        }

    @Test
    fun beforeTheSourcesAreReadThereIsNoOutcome() =
        test {
            val servers = ServersViewModel(FakeFinder(emptyMap()), MutableStateFlow(null), show, 8, SubOrDub.BOTH)

            assertNull(servers.state.value.outcome)
        }

    @Test
    fun aSourceAddedAfterThereWereNoneIsAsked() =
        test {
            val installed = MutableStateFlow<List<InstalledSource>?>(emptyList())
            val servers =
                ServersViewModel(FakeFinder(mapOf(example.id to listOf(sub1))), installed, show, 8, SubOrDub.BOTH)

            installed.value = listOf(example)

            assertEquals(listOf(sub1), servers.servers(example))
            assertNull(servers.state.value.outcome)
        }

    @Test
    fun onlySubWithDubChosenIsNoDub() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1), demo.id to emptyList()))
            val servers = viewModel(finder, listOf(example, demo), subOrDub = SubOrDub.DUB)

            assertEquals(StreamsOutcome.MissingEdition(missing = ServerEdition.DUB, have = ServerEdition.SUB), servers.state.value.outcome)
        }

    @Test
    fun onlyDubWithSubChosenIsNoSub() =
        test {
            val servers = viewModel(FakeFinder(mapOf(example.id to listOf(dub))), subOrDub = SubOrDub.SUB)

            assertEquals(StreamsOutcome.MissingEdition(missing = ServerEdition.SUB, have = ServerEdition.DUB), servers.state.value.outcome)
        }

    @Test
    fun changingTheEditionOnThePageDoesntMakeItAnError() =
        test {
            val servers = viewModel(FakeFinder(mapOf(example.id to listOf(sub1))), subOrDub = SubOrDub.SUB)

            servers.showEdition(SubOrDub.DUB)

            assertNull(servers.state.value.outcome)
        }

    @Test
    fun changingTheEditionOnThePageCanShowWhatTheSettingHid() =
        test {
            val servers = viewModel(FakeFinder(mapOf(example.id to listOf(dub))), subOrDub = SubOrDub.SUB)

            servers.showEdition(SubOrDub.BOTH)

            assertNull(servers.state.value.outcome)
        }

    @Test
    fun aFailureAmongTheWrongEditionMeansNothingPlays() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1)), failing = mapOf(demo.id to SourceFailure(FailReason.Blocked)))
            val servers = viewModel(finder, listOf(example, demo), subOrDub = SubOrDub.DUB)

            assertEquals(StreamsOutcome.NothingPlays, servers.state.value.outcome)
        }

    @Test
    fun everySourceFailingOrEmptyMeansNothingPlays() =
        test {
            val finder = FakeFinder(mapOf(demo.id to emptyList()), failing = mapOf(example.id to SourceFailure(FailReason.NotFound)))
            val servers = viewModel(finder, listOf(example, demo))

            assertEquals(StreamsOutcome.NothingPlays, servers.state.value.outcome)
        }

    @Test
    fun whileASourceIsStillLookingThereIsNoOutcome() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1)), failing = mapOf(demo.id to SourceFailure(FailReason.Blocked)))
            finder.gate(example)
            val servers = viewModel(finder, listOf(example, demo))

            assertNull(servers.state.value.outcome)
        }

    @Test
    fun aServerToPickMeansNoOutcome() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1)), failing = mapOf(demo.id to SourceFailure(FailReason.Blocked)))

            assertNull(viewModel(finder, listOf(example, demo)).state.value.outcome)
        }

    @Test
    fun tryingAgainAsksOnlySourcesWithNothingToPick() =
        test {
            val finder =
                FakeFinder(
                    mapOf(example.id to listOf(sub1), demo.id to emptyList()),
                    failing = mapOf(starter.id to SourceFailure(FailReason.Blocked)),
                )
            val servers = viewModel(finder, listOf(example, demo, starter))
            finder.asked.clear()
            finder.gate(starter)

            servers.retry()

            assertEquals(setOf(demo.id, starter.id), finder.asked.toSet())
            assertEquals(SourceAnswer.Looking, servers.answer(starter))
            assertEquals(listOf(sub1), servers.servers(example))
        }

    @Test
    fun tryingAgainDoesntAskASourceStillLooking() =
        test {
            val finder = FakeFinder(mapOf(example.id to listOf(sub1)))
            finder.gate(example)
            val servers = viewModel(finder)
            finder.asked.clear()

            servers.retry()

            assertEquals(emptyList(), finder.asked)
        }
}
