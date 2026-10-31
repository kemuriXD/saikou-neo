package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MalClientTest {
    @Test
    fun theScoreIsOutOfAHundred() =
        runTest {
            val engine = SiteEngine("mal") { "anime" }

            // 8.65 rounds up.
            assertEquals(87, MalClient(engine).score(61987))
            assertEquals("https://api.tenrai.org/v1/anime/61987", engine.urls.single())
        }

    @Test
    fun aShowNobodyHasScoredHasNoScore() =
        runTest {
            assertNull(MalClient(SiteEngine("mal") { "unscored" }).score(61987))
        }

    @Test
    fun aShowMalDoesNotKnowHasNoScore() =
        runTest {
            assertNull(MalClient(SiteEngine("mal", status = 404) { "not-found" }).score(99999999))
        }

    @Test
    fun whileRateLimitedThereIsNoScore() =
        runTest {
            assertNull(MalClient(SiteEngine("mal", status = 429) { "rate-limited" }).score(61987))
        }

    @Test
    fun withoutAConnectionThereIsNoScore() =
        runTest {
            assertNull(MalClient(OfflineEngine).score(61987))
        }

    @Test
    fun fillerAndRecapsComeFromEveryPage() =
        runTest {
            val engine = SiteEngine("mal") { "naruto-episodes-${it.substringAfter("page=")}" }

            val kinds = MalClient(engine).episodeKinds(20)

            val expected =
                mapOf(
                    26 to EpisodeKind.Recap,
                    97 to EpisodeKind.Filler,
                    101 to EpisodeKind.Filler,
                    106 to EpisodeKind.Filler,
                    219 to EpisodeKind.Filler,
                )
            assertEquals(expected, kinds)
            assertEquals((1..3).map { "https://api.tenrai.org/v1/anime/20/episodes?page=$it" }, engine.urls)
        }

    @Test
    fun anEpisodeMarkedBothIsARecap() =
        runTest {
            // Naruto's episode 26 with its filler flag set as well, since no recorded episode has both.
            assertEquals(mapOf(26 to EpisodeKind.Recap), MalClient(SiteEngine("mal") { "marked-both" }).episodeKinds(20))
        }

    @Test
    fun aShowMalDoesNotKnowHasNoKinds() =
        runTest {
            assertNull(MalClient(SiteEngine("mal", status = 404) { "episodes-not-found" }).episodeKinds(99999999))
        }

    @Test
    fun aPageThatFailsKeepsThePagesBefore() =
        runTest {
            val engine = FailingAfter(SiteEngine("mal") { "naruto-episodes-${it.substringAfter("page=")}" }, pages = 1)
            val client = MalClient(engine)

            assertEquals(mapOf(26 to EpisodeKind.Recap, 97 to EpisodeKind.Filler), client.episodeKinds(20))
            // What it got isn't kept, so the next visit asks again.
            client.episodeKinds(20)
            assertEquals(3, engine.asked)
        }

    @Test
    fun aShowIsAskedForOnce() =
        runTest {
            val engine = SiteEngine("mal") { "naruto-episodes-${it.substringAfter("page=")}" }
            val client = MalClient(engine)

            client.episodeKinds(20)
            val again = client.episodeKinds(20)

            assertEquals(5, again?.size)
            assertEquals(3, engine.urls.size)
        }

    @Test
    fun onlyTheLastTwentyShowsAreKept() =
        runTest {
            val engine = SiteEngine("mal") { "naruto-episodes-3" }
            val client = MalClient(engine)

            (1..21).forEach { client.episodeKinds(it) }
            client.episodeKinds(21)
            assertEquals(21, engine.urls.size)
            client.episodeKinds(1)
            assertEquals(22, engine.urls.size)
        }

    @Test
    fun withoutAConnectionThereAreNoKinds() =
        runTest {
            assertNull(MalClient(OfflineEngine).episodeKinds(20))
        }
}

/** Passes the first [pages] requests to [engine], then fails the rest as if the connection dropped. */
private class FailingAfter(
    private val engine: HttpEngine,
    private val pages: Int,
) : HttpEngine {
    var asked = 0

    override suspend fun execute(request: HttpRequest): HttpResponse {
        asked++
        return if (asked > pages) OfflineEngine.execute(request) else engine.execute(request)
    }

    override fun close() {}
}
