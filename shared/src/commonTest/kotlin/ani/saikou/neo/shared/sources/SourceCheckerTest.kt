package ani.saikou.neo.shared.sources

import ani.saikou.neo.extension.api.Edition
import ani.saikou.neo.extension.api.Episode
import ani.saikou.neo.extension.api.ExtractionRequest
import ani.saikou.neo.extension.api.ExtractionResult
import ani.saikou.neo.extension.api.HostRequest
import ani.saikou.neo.extension.api.HostResponse
import ani.saikou.neo.extension.api.HostServices
import ani.saikou.neo.extension.api.Quality
import ani.saikou.neo.extension.api.Server
import ani.saikou.neo.extension.api.SourceException
import ani.saikou.neo.extension.api.SourceExtension
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.extension.api.StreamFormat
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.sources.CheckStep.SERVERS
import ani.saikou.neo.shared.sources.CheckStep.SITE
import ani.saikou.neo.shared.sources.CheckStep.START
import ani.saikou.neo.shared.sources.CheckStep.STREAM
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SourceCheckerTest {
    private val installed = InstalledSource(id = "fake", name = "Fake", version = "1.0.0")

    @Test
    fun aWorkingSourcePassesEveryStepInOrder() =
        runTest {
            val host = FakeHost()
            val reported = mutableListOf<StepResult>()

            val steps = checker(FakeSource(), host).check(installed) { reported += it }

            assertEquals(listOf(START, SITE, SERVERS, STREAM), steps.map { it.step })
            assertTrue(steps.all { it.outcome == StepOutcome.Passed })
            assertEquals(steps, reported)
            assertEquals(listOf("https://site.example/"), host.asked)
        }

    @Test
    fun aSourceThatWontLoadStopsAtStart() =
        runTest {
            assertEquals(listOf(START to failed(FailReason.WontStart)), SourceChecker({ null }, FakeHost()).check(installed).outcomes())
            val throwing = SourceChecker({ error("broken") }, FakeHost())
            assertEquals(listOf(START to failed(FailReason.WontStart)), throwing.check(installed).outcomes())
        }

    @Test
    fun aSourceWithNoSiteSkipsTheSiteStep() =
        runTest {
            val host = FakeHost()

            val steps = checker(FakeSource(site = null), host).check(installed)

            assertEquals(StepOutcome.Skipped, steps.single { it.step == SITE }.outcome)
            assertEquals(StepOutcome.Passed, steps.last().outcome)
            assertEquals(STREAM, steps.last().step)
            assertTrue(host.asked.isEmpty())
        }

    @Test
    fun aSiteThatRefusesOrIsDownStopsAtSite() =
        runTest {
            val cases =
                listOf(
                    FakeHost(status = 403) to FailReason.Blocked,
                    FakeHost(status = 429) to FailReason.RateLimited(null),
                    FakeHost(status = 503) to FailReason.SourceDown,
                    FakeHost(error = IllegalStateException("no route")) to FailReason.SourceDown,
                )
            for ((host, reason) in cases) {
                val steps = checker(FakeSource(), host).check(installed)
                assertEquals(listOf(START to StepOutcome.Passed, SITE to failed(reason)), steps.outcomes())
            }
        }

    @Test
    fun aSiteThatIsNotAWebAddressIsNeverRequested() =
        runTest {
            val host = FakeHost()

            val steps = checker(FakeSource(site = "file:///data/secret"), host).check(installed)

            assertEquals(SITE to failed(FailReason.SourceDown), steps.outcomes().last())
            assertTrue(host.asked.isEmpty())
        }

    @Test
    fun serversAreNotFoundOnlyWhenEveryShowComesBackEmpty() =
        runTest {
            val source = FakeSource(servers = { emptyList() })

            val steps = checker(source).check(installed)

            assertEquals(SERVERS to failed(FailReason.NotFound), steps.outcomes().last())
            assertTrue(source.asked.size in 2..3)
            assertEquals(
                source.asked.size,
                source.asked
                    .map { it.anime.ids.anilist }
                    .distinct()
                    .size,
            )
        }

    @Test
    fun aShowFoundOnALaterTryPasses() =
        runTest {
            var tries = 0
            val source =
                FakeSource(servers = {
                    tries++
                    if (tries == 1) throw SourceException.NotFound() else listOf(server)
                })

            val steps = checker(source).check(installed)

            assertTrue(steps.all { it.outcome == StepOutcome.Passed })
            assertEquals(2, tries)
        }

    @Test
    fun aServersErrorKeepsItsReasonWithoutTryingOtherShows() =
        runTest {
            val source = FakeSource(servers = { throw SourceException.Blocked() })

            val steps = checker(source).check(installed)

            assertEquals(SERVERS to failed(FailReason.Blocked), steps.outcomes().last())
            assertEquals(1, source.asked.size)
        }

    @Test
    fun aStreamThatFailsStopsAtStream() =
        runTest {
            val source = FakeSource(stream = { throw SourceException.RateLimited(30.seconds) })

            val steps = checker(source).check(installed)

            assertEquals(STREAM to failed(FailReason.RateLimited(30)), steps.outcomes().last())
        }

    @Test
    fun aStepThatNeverAnswersFailsWithNoAnswer() =
        runTest {
            val steps = checker(FakeSource(servers = { awaitCancellation() })).check(installed)

            assertEquals(SERVERS to failed(FailReason.NoAnswer), steps.outcomes().last())
            assertEquals(STEP_LIMIT, steps.last().took)
        }

    @Test
    fun aSiteBehindASlowBrowserCheckStillPasses() =
        runTest {
            // A Fire TV can take over a minute to pass a site's browser check (#356).
            val host =
                object : HostServices by FakeHost() {
                    override suspend fun http(request: HostRequest): HostResponse {
                        delay(70.seconds)
                        return HostResponse(200)
                    }
                }

            val steps = checker(FakeSource(), host).check(installed)

            assertEquals(SITE to StepOutcome.Passed, steps.outcomes()[1])
        }

    @Test
    fun eachStepRecordsHowLongItTook() =
        runTest {
            val source =
                FakeSource(servers = {
                    delay(2.seconds)
                    listOf(server)
                })

            val steps = checker(source).check(installed)

            assertEquals(2.seconds, steps.single { it.step == SERVERS }.took)
            assertEquals(0.seconds, steps.single { it.step == STREAM }.took)
        }

    @Test
    fun aSourceThatThrowsCancellationWhileNotCancelledFails() =
        runTest {
            val source = FakeSource(stream = { throw CancellationException("fake") })

            val steps = checker(source).check(installed)

            assertEquals(STREAM to failed(FailReason.SourceDown), steps.outcomes().last())
        }

    @Test
    fun cancellingACheckStopsItWithoutReportingAFailure() =
        runTest {
            val reported = mutableListOf<StepResult>()
            val job = launch { checker(FakeSource(servers = { awaitCancellation() })).check(installed) { reported += it } }
            runCurrent()

            job.cancel()
            runCurrent()

            assertTrue(job.isCancelled)
            assertEquals(listOf(START, SITE), reported.map { it.step })
        }

    private fun TestScope.checker(
        source: SourceExtension,
        host: HostServices = FakeHost(),
    ) = SourceChecker({ source }, host, timeSource = testScheduler.timeSource)

    private fun failed(reason: FailReason) = StepOutcome.Failed(reason)

    private fun List<StepResult>.outcomes() = map { it.step to it.outcome }
}

private val server = Server(id = "sub-1", name = "Main", edition = Edition.Sub, runtime = null)

/** A source that answers from memory, with its servers and stream swapped out per test. */
internal class FakeSource(
    private val site: String? = "https://site.example/",
    private val servers: suspend (Episode) -> List<Server> = { listOf(server) },
    private val stream: suspend (Server) -> Stream = { Stream(listOf(Quality(1080, "https://cdn.example/1080.m3u8", StreamFormat.Hls))) },
) : SourceExtension {
    val asked = mutableListOf<Episode>()

    override val siteUrl: String? get() = site

    override suspend fun servers(episode: Episode): List<Server> {
        asked += episode
        return servers.invoke(episode)
    }

    override suspend fun stream(server: Server): Stream = stream.invoke(server)
}

/** Answers every request with [status], or throws [error]. */
internal class FakeHost(
    private val status: Int = 200,
    private val error: Throwable? = null,
) : HostServices {
    val asked = mutableListOf<String>()

    override suspend fun http(request: HostRequest): HostResponse {
        asked += request.url
        error?.let { throw it }
        return HostResponse(status)
    }

    override suspend fun extract(request: ExtractionRequest): ExtractionResult = error("Not used")
}
