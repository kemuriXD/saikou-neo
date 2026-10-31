package ani.saikou.neo.shared.playback

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.sources.InstalledSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val demo = InstalledSource("com.example.demo", "Demo source", "1.0.0")
private val example = InstalledSource("com.example.example", "Example source", "1.0.0")

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

private val one = FoundServer("1", "Server 1", ServerEdition.SUB, 24)
private val two = FoundServer("2", "Server 2", ServerEdition.SUB, 24)
private val three = FoundServer("3", "Server 3", ServerEdition.DUB, 24)

private val servers = listOf(PlayServer(demo, one, 2), PlayServer(demo, two, 2), PlayServer(example, three, 1))

/** Answers with "stream N" for server N, or by throwing what [failing] holds for it. A gated server waits for its gate. */
private class FakeStreams(
    private val failing: MutableMap<String, MutableList<Exception>> = mutableMapOf(),
) : StreamFinder<String> {
    val asked = mutableListOf<String>()
    val refreshed = mutableListOf<String>()
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
    var cancelled = false

    fun fail(
        server: FoundServer,
        vararg errors: Exception,
    ) {
        failing[server.id] = errors.toMutableList()
    }

    fun gate(server: FoundServer) = CompletableDeferred<Unit>().also { gates[server.id] = it }

    override suspend fun stream(
        source: InstalledSource,
        server: FoundServer,
    ): FoundStream<String> {
        asked += server.id
        try {
            gates[server.id]?.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelled = true
            throw e
        }
        failing[server.id]?.removeFirstOrNull()?.let { throw it }
        return FoundStream("stream ${server.id}", 1080)
    }

    override suspend fun refresh(
        source: InstalledSource,
        server: FoundServer,
        expired: String,
    ): FoundStream<String> {
        refreshed += expired
        failing[server.id]?.removeFirstOrNull()?.let { throw it }
        return FoundStream("fresh ${server.id}", 1080)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val main = UnconfinedTestDispatcher()

    init {
        Dispatchers.setMain(main)
    }

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(main) { body() }

    private fun request(picked: Int = 0) = PlayRequest(show, 7, "The Hitman Is Back", servers, picked)

    private fun player(
        finder: StreamFinder<String>,
        picked: Int = 0,
    ) = PlayerViewModel(finder, request(picked))

    private val PlayerViewModel<String>.phase get() = state.value.phase

    @Test
    fun startsOnThePickedServer() =
        test {
            val finder = FakeStreams()
            val gate = finder.gate(two)
            val player = player(finder, picked = 1)

            assertEquals(PlayServer(demo, two, 2), player.state.value.server)
            assertEquals(PlayerPhase.Starting(null), player.phase)
            assertEquals(0, player.state.value.load)

            gate.complete(Unit)

            assertEquals(PlayerPhase.Starting(FoundStream("stream 2", 1080)), player.phase)
            assertEquals(1, player.state.value.load)
        }

    @Test
    fun startedPlays() =
        test {
            val player = player(FakeStreams())

            player.started()

            assertEquals(PlayerPhase.Playing(FoundStream("stream 1", 1080)), player.phase)
        }

    @Test
    fun aStreamCallOver25SecondsTriesTheNextServerOnce() =
        test {
            val finder = FakeStreams()
            finder.gate(one)
            finder.gate(two)
            val player = player(finder)

            advanceTimeBy(24.9.seconds)
            assertEquals(PlayServer(demo, one, 2), player.state.value.server)

            advanceTimeBy(0.2.seconds)
            assertEquals(PlayServer(demo, two, 2), player.state.value.server)
            assertEquals(PlayerPhase.Starting(null), player.phase)
        }

    @Test
    fun aSecondFailureBeforeStartingStops() =
        test {
            val finder = FakeStreams()
            finder.fail(one, SourceFailure(FailReason.SourceDown))
            finder.fail(two, SourceFailure(FailReason.Blocked))
            val player = player(finder)

            assertEquals(listOf("1", "2"), finder.asked)
            assertEquals(PlayServer(demo, two, 2), player.state.value.server)
            assertEquals(PlayerPhase.Stopped(FailReason.Blocked, null, null, PlayServer(example, three, 1)), player.phase)
        }

    @Test
    fun theLastServerStopsWithoutANext() =
        test {
            val finder = FakeStreams()
            finder.fail(three, SourceFailure(FailReason.NotFound))
            val player = player(finder, picked = 2)

            assertEquals(listOf("3"), finder.asked)
            assertEquals(PlayerPhase.Stopped(FailReason.NotFound, null, null, null), player.phase)
        }

    @Test
    fun anythingElseAStreamCallThrowsMeansTheSiteIsDown() =
        test {
            val finder = FakeStreams()
            finder.fail(three, IllegalStateException("Broken"))
            val player = player(finder, picked = 2)

            assertEquals(PlayerPhase.Stopped(FailReason.SourceDown, null, null, null), player.phase)
        }

    @Test
    fun aPlayerFailureBeforeStartingTriesTheNextServer() =
        test {
            val finder = FakeStreams()
            val player = player(finder)

            player.failed(PlayerError.Failed(FailReason.SourceDown, 404), positionMs = 0)

            assertEquals(PlayServer(demo, two, 2), player.state.value.server)
            assertEquals(PlayerPhase.Starting(FoundStream("stream 2", 1080)), player.phase)
            assertEquals(2, player.state.value.load)
        }

    @Test
    fun aFailureAfterStartingStopsWithThePosition() =
        test {
            val finder = FakeStreams()
            val player = player(finder)
            player.started()

            player.failed(PlayerError.Failed(FailReason.SourceDown, 503), positionMs = 724_000)

            assertEquals(PlayServer(demo, one, 2), player.state.value.server)
            assertEquals(PlayerPhase.Stopped(FailReason.SourceDown, 503, 724_000, PlayServer(demo, two, 2)), player.phase)
        }

    @Test
    fun anExpiredLinkRefreshesOnceAndKeepsThePosition() =
        test {
            val finder = FakeStreams()
            val player = player(finder)
            player.started()

            player.failed(PlayerError.Expired, positionMs = 60_000)

            assertEquals(listOf("stream 1"), finder.refreshed)
            assertEquals(PlayerPhase.Starting(FoundStream("fresh 1", 1080), quiet = true), player.phase)
            assertEquals(60_000, player.state.value.startAtMs)
            assertEquals(2, player.state.value.load)
        }

    @Test
    fun aSecondExpiryStops() =
        test {
            val player = player(FakeStreams())
            player.started()
            player.failed(PlayerError.Expired, positionMs = 60_000)
            player.started()

            player.failed(PlayerError.Expired, positionMs = 90_000)

            assertEquals(PlayerPhase.Stopped(FailReason.LinkExpired, null, 90_000, PlayServer(demo, two, 2)), player.phase)
        }

    @Test
    fun aRefreshThatFailsStops() =
        test {
            val finder = FakeStreams()
            val player = player(finder)
            player.started()
            finder.fail(one, SourceFailure(FailReason.RateLimited(30)))

            player.failed(PlayerError.Expired, positionMs = 60_000)

            assertEquals(PlayerPhase.Stopped(FailReason.RateLimited(30), null, 60_000, PlayServer(demo, two, 2)), player.phase)
        }

    @Test
    fun tryNextKeepsThePosition() =
        test {
            val player = player(FakeStreams())
            player.started()
            player.failed(PlayerError.Failed(FailReason.SourceDown), positionMs = 724_000)

            player.tryNext()

            assertEquals(PlayServer(demo, two, 2), player.state.value.server)
            assertEquals(PlayerPhase.Starting(FoundStream("stream 2", 1080)), player.phase)
            assertEquals(724_000, player.state.value.startAtMs)
        }

    @Test
    fun tryAgainKeepsThePositionAndCanRefreshAgain() =
        test {
            val finder = FakeStreams()
            val player = player(finder)
            player.started()
            player.failed(PlayerError.Expired, positionMs = 10_000)
            player.started()
            player.failed(PlayerError.Expired, positionMs = 20_000)

            player.tryAgain()
            player.started()
            player.failed(PlayerError.Expired, positionMs = 30_000)

            assertEquals(listOf("1", "1"), finder.asked)
            assertEquals(2, finder.refreshed.size)
            assertEquals(30_000, player.state.value.startAtMs)
            assertEquals(PlayerPhase.Starting(FoundStream("fresh 1", 1080), quiet = true), player.phase)
        }

    @Test
    fun tryNextMovesOnFromTheServerThatStopped() =
        test {
            val finder = FakeStreams()
            finder.fail(one, SourceFailure(FailReason.SourceDown))
            finder.fail(two, SourceFailure(FailReason.SourceDown))
            val player = player(finder)

            player.tryNext()

            assertEquals(listOf("1", "2", "3"), finder.asked)
            assertEquals(PlayServer(example, three, 1), player.state.value.server)
            assertEquals(PlayerPhase.Starting(FoundStream("stream 3", 1080)), player.phase)
        }

    @Test
    fun aStreamCallThatWontStopStillTimesOut() =
        test {
            val finder =
                object : StreamFinder<String> {
                    override suspend fun stream(
                        source: InstalledSource,
                        server: FoundServer,
                    ): FoundStream<String> {
                        withContext(NonCancellable) { delay(60.seconds) }
                        return FoundStream("late", 1080)
                    }

                    override suspend fun refresh(
                        source: InstalledSource,
                        server: FoundServer,
                        expired: String,
                    ) = awaitCancellation()
                }
            val player = player(finder, picked = 2)

            advanceTimeBy(25.1.seconds)

            assertEquals(PlayerPhase.Stopped(FailReason.NoAnswer, null, null, null), player.phase)
        }

    @Test
    fun leavingCancelsTheStreamCall() =
        test {
            val finder = FakeStreams()
            finder.gate(one)
            val store = ViewModelStore()
            ViewModelProvider.create(store, viewModelFactory { initializer { player(finder) } })[PlayerViewModel::class]

            store.clear()

            assertTrue(finder.cancelled)
        }

    @Test
    fun playRequestListsTheShownServersInPageOrder() {
        val state =
            ServersState(
                sources =
                    listOf(
                        SourceServers(demo, SourceAnswer.Found(listOf(one, two))),
                        SourceServers(
                            InstalledSource("com.example.starter", "Starter source", "1.0.0"),
                            SourceAnswer.Failed(FailReason.Blocked),
                        ),
                        SourceServers(example, SourceAnswer.Found(listOf(three))),
                    ),
                ready = true,
            )

        val request = state.playRequest(example, three, show, 7, "The Hitman Is Back")

        assertEquals(PlayRequest(show, 7, "The Hitman Is Back", servers, picked = 2), request)
    }

    @Test
    fun playRequestKeepsToTheFilteredSource() {
        val state =
            ServersState(
                sources =
                    listOf(
                        SourceServers(demo, SourceAnswer.Found(listOf(one, two))),
                        SourceServers(example, SourceAnswer.Found(listOf(three))),
                    ),
                filter = demo.id,
                ready = true,
            )

        val request = state.playRequest(demo, two, show, 7, null)

        assertEquals(listOf(PlayServer(demo, one, 2), PlayServer(demo, two, 2)), request.servers)
        assertEquals(1, request.picked)
    }
}
