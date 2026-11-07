package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AniZipEngine
import ani.saikou.neo.shared.catalogue.ArtLibrary
import ani.saikou.neo.shared.catalogue.ArtSources
import ani.saikou.neo.shared.catalogue.FanartTvClient
import ani.saikou.neo.shared.catalogue.LoadProblem
import ani.saikou.neo.shared.catalogue.OfflineEngine
import ani.saikou.neo.shared.catalogue.RecordedEngine
import ani.saikou.neo.shared.catalogue.TmdbClient
import ani.saikou.neo.shared.catalogue.clientFor
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class ContinueWatchingViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun continueWatching(
        history: WatchHistory,
        engine: HttpEngine = RecordedEngine("cards"),
    ): ContinueWatchingViewModel {
        val client = clientFor(engine, main)
        val sources =
            ArtSources(
                client,
                AniZipClient(AniZipEngine("mappings"), main),
                FanartTvClient(null, OfflineEngine, main),
                TmdbClient(null, OfflineEngine, main),
            )
        return ContinueWatchingViewModel(client, ArtLibrary(sources, CoroutineScope(main)), history)
    }

    private fun progress(
        episode: Int,
        savedAt: Long,
        watched: Duration = 12.minutes,
    ) = WatchProgress(episode, watched, 24.minutes, null, savedAt)

    private suspend fun ContinueWatchingViewModel.settled() = state.first { it.more != More.Loading }

    // Show 1001 has 10 episodes, so its last one watched to the end is finished.
    @Test
    fun listsEveryUnfinishedShowMostRecentFirst() =
        runTest(main) {
            val history =
                FakeHistory(
                    mapOf(
                        1000 to progress(3, savedAt = 100),
                        1001 to progress(10, savedAt = 300, watched = 24.minutes),
                        1002 to progress(5, savedAt = 200),
                    ),
                )

            val state = continueWatching(history).settled()

            assertEquals(listOf(1002, 1000), state.items.map { it.id })
            assertEquals(mapOf(1002 to progress(5, savedAt = 200), 1000 to progress(3, savedAt = 100)), state.progress)
            assertEquals(More.Done, state.more)
        }

    // AniList sends 50 cards at most, so 51 shows take two requests.
    @Test
    fun asksForMoreThanFiftyShowsInTurn() =
        runTest(main) {
            val engine = RecordedEngine("cards")
            val history = FakeHistory((1..51).associate { 2000 + it to progress(1, savedAt = it.toLong()) } + (1000 to progress(3, 500)))

            val state = continueWatching(history, engine).settled()

            assertEquals(2, engine.requestBodies.size)
            assertEquals(1000, state.items.first().id)
        }

    @Test
    fun watchingAgainMovesAShowToTheFront() =
        runTest(main) {
            val history = FakeHistory(mapOf(1000 to progress(3, savedAt = 100), 1002 to progress(2, savedAt = 200)))
            val viewModel = continueWatching(history)
            viewModel.settled()

            history.save(1000, progress(4, savedAt = 300))

            val state = viewModel.state.first { it.items.firstOrNull()?.id == 1000 }
            assertEquals(listOf(1000, 1002), state.items.map { it.id })
            assertEquals(4, state.progress.getValue(1000).episode)
        }

    @Test
    fun loadsWhenTriedAgainOnceOnline() =
        runTest(main) {
            val engine = OfflineUntil()
            val viewModel = continueWatching(FakeHistory(mapOf(1000 to progress(3, savedAt = 100))), engine)
            val failed = viewModel.settled()
            assertEquals(More.Failed, failed.more)
            assertEquals(LoadProblem.NO_NETWORK, failed.problem)

            engine.online = true
            viewModel.retry()
            advanceUntilIdle()

            assertEquals(
                listOf(1000),
                viewModel.state.value.items
                    .map { it.id },
            )
            assertEquals(More.Done, viewModel.state.value.more)
        }
}

/** Fails every request as if offline, until [online], then answers with the cards. */
private class OfflineUntil : HttpEngine {
    private val cards = RecordedEngine("cards")
    var online = false

    override suspend fun execute(request: HttpRequest): HttpResponse =
        if (online) cards.execute(request) else OfflineEngine.execute(request)

    override fun close() {}
}
