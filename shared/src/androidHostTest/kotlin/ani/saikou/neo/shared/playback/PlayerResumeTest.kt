package ani.saikou.neo.shared.playback

import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.shared.log.AppLog
import ani.saikou.neo.shared.log.LogLevel
import ani.saikou.neo.shared.log.RecordingSink
import ani.saikou.neo.shared.sources.InstalledSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val resumeSource = InstalledSource("com.example.resumeSource", "Demo source", "1.0.0")
private val dub = FoundServer("e5-dub", "Server 1", ServerEdition.DUB, 24)
private val sub = FoundServer("e5-sub", "Server 1", ServerEdition.SUB, 24)
private val other = FoundServer("e5-other", "Server 2", ServerEdition.DUB, 24)

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

private class ResumeStreams(
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

/** The episode's servers, a failure, or no answer until [answer] completes. */
private class ResumeServers(
    private val servers: List<FoundServer> = listOf(other, sub, dub),
    private val fails: Boolean = false,
    private val answer: CompletableDeferred<Unit>? = null,
) : ServerFinder {
    val asked = mutableListOf<Pair<String, Int>>()

    override suspend fun servers(
        source: InstalledSource,
        show: AnimeDetails,
        episode: Int,
    ): List<FoundServer> {
        asked += source.id to episode
        answer?.await()
        if (fails) throw SourceFailure(FailReason.NotFound)
        return servers
    }
}

private class SavedServers : LastServers {
    override val lastServers = MutableStateFlow(emptyMap<Int, LastServer>())

    override fun saveServer(
        animeId: Int,
        server: LastServer,
    ) {
        lastServers.value += animeId to server
    }
}

// Details' main button plays the episode it names on the server the show last played on (#499).
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerResumeTest {
    private val main = UnconfinedTestDispatcher()

    init {
        Dispatchers.setMain(main)
    }

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(main) { body() }

    private val fiveAt12 = WatchProgress(5, 12.minutes, 24.minutes, null)

    // What Details hands the player: the saved server, standing in until the source names it for this episode.
    private fun resuming(
        servers: ServerFinder,
        saved: SavedServers = SavedServers(),
        audio: List<String> = emptyList(),
        wantsAudio: String? = null,
    ) = PlayerViewModel(
        ResumeStreams(audio),
        PlayRequest(
            show,
            5,
            null,
            listOf(PlayServer(resumeSource, FoundServer("", "Server 1", ServerEdition.DUB, null), 1)),
            0,
            progress = fiveAt12,
            audio = wantsAudio,
            findServer = true,
            resume = true,
        ),
        answerWithin = 10.seconds,
        servers = servers,
        lastServers = saved,
    )

    @Test
    fun theSameServerPlaysFromTheSavedSpotWithoutAsking() =
        test {
            val servers = ResumeServers()
            val player = resuming(servers)

            assertEquals(listOf(resumeSource.id to 5), servers.asked)
            assertEquals(PlayServer(resumeSource, dub, 3), player.state.value.server)
            assertEquals(PlayerPhase.Starting(FoundStream("stream e5-dub", 1080)), player.state.value.phase)
            assertEquals(12.minutes.inWholeMilliseconds, player.state.value.startAtMs)
            assertNull(player.state.value.going)
        }

    @Test
    fun withoutTheServerTheEpisodesServersOpen() =
        test {
            val player = resuming(ResumeServers(servers = listOf(other, sub)))

            assertEquals(NextPlay.Choose(5), player.state.value.going)
        }

    @Test
    fun aFailedSourceOpensTheEpisodesServers() =
        test {
            val player = resuming(ResumeServers(fails = true))

            assertEquals(NextPlay.Choose(5), player.state.value.going)
        }

    @Test
    fun aFailedSourceIsLogged() =
        test {
            val sink = RecordingSink()
            AppLog.addSink(sink)
            try {
                resuming(ResumeServers(fails = true))

                assertTrue(sink.lines.any { it.level == LogLevel.Warn && "SourceFailure" in it.message }, sink.lines.toString())
            } finally {
                AppLog.removeSink(sink)
            }
        }

    @Test
    fun aSourceThatDoesNotAnswerOpensTheEpisodesServers() =
        test {
            val player = resuming(ResumeServers(answer = CompletableDeferred()))
            assertIs<PlayerPhase.Starting<*>>(player.state.value.phase)
            assertNull(player.state.value.going)

            advanceTimeBy(11.seconds)

            assertEquals(NextPlay.Choose(5), player.state.value.going)
        }

    @Test
    fun theSavedAudioMustBeThereToo() =
        test {
            val player = resuming(ResumeServers(), audio = listOf("Japanese"), wantsAudio = "English")

            assertEquals(NextPlay.Choose(5), player.state.value.going)
        }

    @Test
    fun startingToPlaySavesTheServerAndAudio() =
        test {
            val saved = SavedServers()
            val player = resuming(ResumeServers(), saved, audio = listOf("Japanese", "English"), wantsAudio = "English")

            player.started()

            assertEquals(mapOf(21 to LastServer(resumeSource.id, "Server 1", ServerEdition.DUB, "English")), saved.lastServers.value)
        }

    @Test
    fun aServerPickedOnTheServersPageIsSavedToo() =
        test {
            val saved = SavedServers()
            val player =
                PlayerViewModel(
                    ResumeStreams(),
                    PlayRequest(show, 5, null, listOf(PlayServer(resumeSource, sub, 2), PlayServer(resumeSource, dub, 2)), 0),
                    lastServers = saved,
                )

            player.started()

            assertEquals(mapOf(21 to LastServer(resumeSource.id, "Server 1", ServerEdition.SUB, null)), saved.lastServers.value)
        }
}
