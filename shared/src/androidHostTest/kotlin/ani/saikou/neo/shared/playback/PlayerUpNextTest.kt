package ani.saikou.neo.shared.playback

import ani.saikou.neo.shared.account.WatchingUpdates
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.home.WatchHistory
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.shared.settings.StillWatching
import ani.saikou.neo.shared.sources.InstalledSource
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val demo = InstalledSource("com.example.demo", "Demo source", "1.0.0")
private val sub = FoundServer("e7-sub", "Server 1", ServerEdition.SUB, 24)
private val dub = FoundServer("e7-dub", "Server 1", ServerEdition.DUB, 24)
private val nextSub = FoundServer("e8-sub", "Server 1", ServerEdition.SUB, 24)
private val nextDub = FoundServer("e8-dub", "Server 1", ServerEdition.DUB, 24)
private val other = FoundServer("e8-other", "Server 2", ServerEdition.SUB, 24)

private val show =
    AnimeDetails(
        card = AnimeCard(21, AnimeTitle("A show", null, null), null, null, null, "TV", 12, null, null, null),
        description = null,
        status = null,
        episodeMinutes = 24,
        genres = emptyList(),
        studios = emptyList(),
        nextEpisode = null,
    )

/** Every stream has the [audio] tracks given. */
private class Streams(
    private val audio: List<String> = emptyList(),
) : StreamFinder<String> {
    override suspend fun stream(
        source: InstalledSource,
        server: FoundServer,
    ) = FoundStream("stream ${server.id}", 1080, audio = audio)

    override suspend fun refresh(
        source: InstalledSource,
        server: FoundServer,
        expired: String,
    ) = stream(source, server)
}

/** The next episode's servers, or a failure. */
private class Servers(
    private val servers: List<FoundServer> = listOf(other, nextSub, nextDub),
    private val fails: Boolean = false,
) : ServerFinder {
    val asked = mutableListOf<Pair<String, Int>>()

    override suspend fun servers(
        source: InstalledSource,
        show: AnimeDetails,
        episode: Int,
    ): List<FoundServer> {
        asked += source.id to episode
        if (fails) throw SourceFailure(FailReason.NotFound)
        return servers
    }
}

private class Updates(
    private val asks: Int?,
) : WatchingUpdates {
    val watched = mutableListOf<Int>()
    val answers = mutableListOf<Boolean>()

    override fun watching(
        animeId: Int,
        episode: Int,
        title: AnimeTitle?,
    ) {
        watched += episode
    }

    override suspend fun asksFirst(animeId: Int) = asks

    override fun answer(
        animeId: Int,
        forUser: Int,
        update: Boolean,
        episode: Int,
        title: AnimeTitle?,
    ) {
        answers += update
    }
}

private object NoHistory : WatchHistory {
    override val shows = kotlinx.coroutines.flow.flowOf(emptyMap<Int, WatchProgress>())

    override fun save(
        animeId: Int,
        progress: WatchProgress,
    ) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerUpNextTest {
    private val main = UnconfinedTestDispatcher()

    init {
        Dispatchers.setMain(main)
    }

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(main) { body() }

    private fun player(
        episode: Int = 7,
        server: FoundServer = sub,
        servers: ServerFinder = Servers(),
        updates: Updates = Updates(asks = null),
        streams: Streams = Streams(),
        audio: String? = null,
        run: WatchingRun? = null,
    ) = PlayerViewModel(
        streams,
        PlayRequest(show, episode, null, listOf(PlayServer(demo, server, 2)), 0, audio = audio),
        history = NoHistory,
        updates = updates,
        servers = servers,
        run = run,
    ).also { it.started() }

    private val PlayerViewModel<String>.going get() = state.value.going

    @Test
    fun theNextCardPlaysTheSameServerFromTheSameSource() =
        test {
            val servers = Servers()
            val player = player(servers = servers)

            assertEquals(
                8,
                player.state.value.next
                    ?.number,
            )
            player.playNext()

            assertEquals(listOf(demo.id to 8), servers.asked)
            val play = player.going as NextPlay.Play
            assertEquals(8, play.request.episode)
            assertEquals(listOf(PlayServer(demo, nextSub, 1)), play.request.servers)
            assertEquals(0, play.request.picked)
        }

    @Test
    fun aDubKeepsTheDub() =
        test {
            val play = player(server = dub).also { it.playNext() }.going as NextPlay.Play

            assertEquals(listOf(PlayServer(demo, nextDub, 1)), play.request.servers)
        }

    @Test
    fun withoutTheSameServerTheServersPageOpensForTheNextEpisode() =
        test {
            assertEquals(NextPlay.Choose(8), player(servers = Servers(listOf(other, nextDub))).also { it.playNext() }.going)
            assertEquals(NextPlay.Choose(8), player(servers = Servers(fails = true)).also { it.playNext() }.going)
        }

    @Test
    fun movingOnCountsTheEpisode() =
        test {
            val updates = Updates(asks = null)
            player(updates = updates).playNext()

            assertEquals(listOf(7), updates.watched)
        }

    @Test
    fun theSeparateAudioPlayingGoesWithIt() =
        test {
            val player = player(streams = Streams(listOf("Japanese", "English")))
            player.changeAudio(1, positionMs = 1000)
            player.started()
            player.playNext()

            assertEquals("English", (player.going as NextPlay.Play).request.audio)
        }

    @Test
    fun theNextEpisodePicksThatAudioOrOpensTheServersPage() =
        test {
            val found = player(episode = 8, streams = Streams(listOf("Japanese", "English")), audio = "English")
            assertEquals(1, found.state.value.pickedAudio)
            assertNull(found.going)

            val missing = player(episode = 8, streams = Streams(listOf("Japanese")), audio = "English")
            assertEquals(NextPlay.Choose(8), missing.going)
        }

    @Test
    fun upNextCountsDownTenSecondsThenPlaysOn() =
        test {
            val player = player()
            player.reachedUpNext()
            assertEquals(UpNext(secondsLeft = 10), player.state.value.upNext)

            advanceTimeBy(9.5.seconds)
            assertEquals(
                1,
                player.state.value.upNext
                    ?.secondsLeft,
            )
            assertNull(player.going)

            advanceTimeBy(1.seconds)
            assertTrue(player.going is NextPlay.Play)
        }

    @Test
    fun watchCreditsWaitsForTheEndThenAsksAgain() =
        test {
            val player = player()
            player.reachedUpNext()
            player.watchCredits()
            assertNull(player.state.value.upNext)

            player.reachedUpNext()
            advanceTimeBy(20.seconds)
            assertNull(player.state.value.upNext)
            assertNull(player.going)

            assertFalse(player.ended())
            assertEquals(UpNext(secondsLeft = 10), player.state.value.upNext)
        }

    @Test
    fun theLastEpisodeHasNoUpNext() =
        test {
            val player = player(episode = 12)
            assertNull(player.state.value.next)

            player.reachedUpNext()
            player.playNext()
            assertNull(player.state.value.upNext)
            assertNull(player.going)
            assertFalse(player.ended())
        }

    @Test
    fun askingShowsOverUpNextAndTheCountdownWaitsForTheAnswer() =
        test {
            val updates = Updates(asks = 1)
            val player = player(updates = updates)
            player.played(positionMs = 1_400_000, durationMs = 1_440_000)
            player.reachedUpNext()

            assertEquals(7, player.state.value.askAniList)
            assertEquals(UpNext(secondsLeft = null), player.state.value.upNext)
            advanceTimeBy(20.seconds)
            assertNull(player.going)

            player.answer(true)
            assertEquals(listOf(true), updates.answers)
            assertEquals(UpNext(secondsLeft = 10), player.state.value.upNext)
        }

    @Test
    fun theNextCardAsksFirstThenPlaysOn() =
        test {
            val updates = Updates(asks = 1)
            val player = player(updates = updates)
            player.playNext()

            assertEquals(7, player.state.value.askAniList)
            assertNull(player.going)

            player.noAnswer()
            assertTrue(player.going is NextPlay.Play)
            assertEquals(emptyList(), updates.watched)
        }

    @Test
    fun watchCreditsWhileTheNextServerIsFoundStaysOnThisEpisode() =
        test {
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val servers = GatedServers(gate)
            val player = player(servers = servers)
            player.playNext()
            player.playNext()
            player.watchCredits()
            gate.complete(Unit)

            assertEquals(1, servers.asked)
            assertNull(player.going)
            assertNull(player.state.value.upNext)
        }

    // After 2 with one episode already played on: this one's Up next asks.
    private fun runAtTheSetting() = WatchingRun { StillWatching.AFTER_2 }.also { it.playedOn() }

    @Test
    fun afterARunUpNextAsksInsteadOfCountingDown() =
        test {
            val player = player(run = runAtTheSetting())
            player.reachedUpNext()

            assertEquals(UpNext(secondsLeft = null, stillWatching = 2), player.state.value.upNext)
            advanceTimeBy(60.seconds)
            assertNull(player.going)

            player.playNext()
            assertTrue(player.going is NextPlay.Play)
        }

    @Test
    fun theCountdownPlayingOnAddsToTheRun() =
        test {
            val run = WatchingRun { StillWatching.AFTER_2 }
            val player = player(run = run)
            player.reachedUpNext()
            advanceTimeBy(10.5.seconds)

            assertTrue(player.going is NextPlay.Play)
            assertEquals(2, run.asks())
        }

    @Test
    fun playingOnFromPlayDoesntAddToTheRun() =
        test {
            val run = WatchingRun { StillWatching.AFTER_2 }
            val player = player(run = run)
            player.reachedUpNext()
            player.playNext()

            assertTrue(player.going is NextPlay.Play)
            assertNull(run.asks())
        }

    @Test
    fun afterWatchCreditsTheEndAsksAgainThoughBackWasAPress() =
        test {
            val run = runAtTheSetting()
            val player = player(run = run)
            player.reachedUpNext()
            player.watchCredits()
            run.pressed()

            assertFalse(player.ended())
            assertEquals(UpNext(secondsLeft = null, stillWatching = 2), player.state.value.upNext)
            advanceTimeBy(60.seconds)
            assertNull(player.going)
        }

    @Test
    fun answeringAniListDoesntStartACountdownWhileItAsks() =
        test {
            val player = player(updates = Updates(asks = 1), run = runAtTheSetting())
            player.played(positionMs = 1_400_000, durationMs = 1_440_000)
            player.reachedUpNext()
            assertEquals(7, player.state.value.askAniList)

            player.answer(true)

            assertEquals(UpNext(secondsLeft = null, stillWatching = 2), player.state.value.upNext)
            advanceTimeBy(60.seconds)
            assertNull(player.going)
        }

    @Test
    fun whereUpNextShowsTheEpisodeIsDoneSoStoppingLeavesTheNextOneWaiting() =
        test {
            val saved = mutableListOf<WatchProgress>()
            val history =
                object : WatchHistory {
                    override val shows = kotlinx.coroutines.flow.flowOf(emptyMap<Int, WatchProgress>())

                    override fun save(
                        animeId: Int,
                        progress: WatchProgress,
                    ) {
                        saved += progress
                    }
                }
            val updates = Updates(asks = null)
            val player =
                PlayerViewModel(
                    Streams(),
                    PlayRequest(show, 7, null, listOf(PlayServer(demo, sub, 2)), 0),
                    history = history,
                    updates = updates,
                    run = runAtTheSetting(),
                ).also { it.started() }
            val length = 1_440_000L
            player.played(positionMs = length - UP_NEXT_BEFORE_END.inWholeMilliseconds, durationMs = length)
            player.reachedUpNext()

            assertTrue(saved.last().watchedToEnd)
            assertEquals(listOf(7), updates.watched)
            assertTrue(player.leave().not())
        }
}

/** Answers once [gate] opens. */
private class GatedServers(
    private val gate: kotlinx.coroutines.CompletableDeferred<Unit>,
) : ServerFinder {
    var asked = 0

    override suspend fun servers(
        source: InstalledSource,
        show: AnimeDetails,
        episode: Int,
    ): List<FoundServer> {
        asked++
        gate.await()
        return listOf(nextSub)
    }
}
