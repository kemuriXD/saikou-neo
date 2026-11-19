package ani.saikou.neo.shared.account

import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.catalogue.OfflineEngine
import ani.saikou.neo.shared.catalogue.RecordedEngine
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.http.HttpHeader
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AniListAccountClientTest {
    private val token = AniListToken("the-token", 2_000_000_000)

    private fun clientFor(
        engine: HttpEngine,
        clock: CoroutineContext = Dispatchers.Default,
    ) = AniListAccountClient(
        ApolloClient
            .Builder()
            .serverUrl("https://anilist.test/graphql")
            .httpEngine(engine)
            .httpExposeErrorBody(true)
            .build(),
        clock,
    )

    @Test
    fun viewerReadsTheSignedInUser() =
        runTest {
            val result = clientFor(RecordedEngine("viewer")).viewer(token)

            assertEquals(
                AniListResult.Success(
                    AniListProfile(
                        5412345,
                        "Kemuri",
                        "https://anilist.co/user/5412345",
                        "https://s4.anilist.co/file/anilistcdn/user/avatar/large/b5412345.png",
                    ),
                ),
                result,
            )
        }

    @Test
    fun viewerSendsTheTokenAsABearerHeader() =
        runTest {
            val engine = HeaderEngine(RecordedEngine("viewer"))

            clientFor(engine).viewer(token)

            assertEquals(listOf("Bearer the-token"), engine.authorizations)
        }

    @Test
    fun aTokenAniListRefusesIsSignedOut() =
        runTest {
            for (status in listOf(400, 401, 403)) {
                val result = clientFor(RecordedEngine("viewer-invalid-token", status = status)).viewer(token)

                assertEquals(AniListResult.Failure(AniListError.SignedOut), result, "status $status")
            }
        }

    @Test
    fun aViewerQueryAniListRejectsIsNotSignedOut() =
        runTest {
            val result = clientFor(RecordedEngine("watch-list-bad-query", status = 400)).viewer(token)

            assertEquals(AniListResult.Failure(AniListError.Http(400)), result)
        }

    @Test
    fun noConnectionIsANetworkError() =
        runTest {
            val result = clientFor(OfflineEngine).viewer(token)

            assertIs<AniListError.Network>(assertIs<AniListResult.Failure>(result).error)
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun aCheckWithNoAnswerGivesUpAfterTenSeconds() =
        runTest {
            val result = clientFor(SilentEngine, StandardTestDispatcher(testScheduler)).viewer(token)

            assertIs<AniListError.Network>(assertIs<AniListResult.Failure>(result).error)
            assertEquals(10_000, currentTime)
        }

    @Test
    fun watchListReadsWatchingAndPlanningShows() =
        runTest {
            val result = clientFor(RecordedEngine("watch-list")).watchList(token, userId = 5412345)

            assertEquals(
                AniListResult.Success(
                    listOf(
                        WatchListShow(
                            id = 101,
                            title = AnimeTitle(romaji = "Hoshi no Kaze", english = "Star Wind", native = "星の風"),
                            coverImageUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101.jpg",
                            status = WatchListStatus.WATCHING,
                            airing = true,
                        ),
                        WatchListShow(
                            id = 102,
                            title = AnimeTitle(romaji = "Aoi Mori", english = null, native = "青い森"),
                            coverImageUrl = null,
                            status = WatchListStatus.WATCHING,
                            airing = false,
                        ),
                        WatchListShow(
                            id = 103,
                            title = AnimeTitle(romaji = "Tsuki no Michi", english = "Moon Road", native = "月の道"),
                            coverImageUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx103.jpg",
                            status = WatchListStatus.PLANNING,
                            airing = true,
                        ),
                        WatchListShow(
                            id = 104,
                            title = AnimeTitle(romaji = "Kumo no Ue", english = null, native = null),
                            coverImageUrl = null,
                            status = WatchListStatus.PLANNING,
                            airing = false,
                        ),
                    ),
                ),
                result,
            )
        }

    @Test
    fun watchListAsksForTheUsersWatchingAndPlanningAnime() =
        runTest {
            val engine = RecordedEngine("watch-list")

            clientFor(engine).watchList(token, userId = 5412345)

            val body = engine.requestBodies.single()
            assertTrue("\"userId\":5412345" in body, body)
            assertTrue("MediaListCollection" in body && "ANIME" in body, body)
            assertTrue("CURRENT" in body && "PLANNING" in body, body)
        }

    @Test
    fun watchListSendsTheTokenAsABearerHeader() =
        runTest {
            val engine = HeaderEngine(RecordedEngine("watch-list"))

            clientFor(engine).watchList(token, userId = 5412345)

            assertEquals(listOf("Bearer the-token"), engine.authorizations)
        }

    @Test
    fun anEmptyWatchListIsNoShows() =
        runTest {
            val result = clientFor(RecordedEngine("watch-list-empty")).watchList(token, userId = 5412345)

            assertEquals(AniListResult.Success(emptyList()), result)
        }

    @Test
    fun aTokenAniListRefusesForTheWatchListIsSignedOut() =
        runTest {
            for (status in listOf(400, 401, 403)) {
                val result = clientFor(RecordedEngine("viewer-invalid-token", status = status)).watchList(token, userId = 5412345)

                assertEquals(AniListResult.Failure(AniListError.SignedOut), result, "status $status")
            }
        }

    @Test
    fun aQueryAniListRejectsIsNotSignedOut() =
        runTest {
            val result = clientFor(RecordedEngine("watch-list-bad-query", status = 400)).watchList(token, userId = 5412345)

            assertEquals(AniListResult.Failure(AniListError.Http(400)), result)
        }

    @Test
    fun aRateLimitedWatchListSaysHowLongToWait() =
        runTest {
            val engine = RecordedEngine("rate-limited", status = 429, headers = listOf(HttpHeader("Retry-After", "30")))

            val result = clientFor(engine).watchList(token, userId = 5412345)

            assertEquals(AniListResult.Failure(AniListError.RateLimited(30.seconds)), result)
        }
}

/** A network that drops everything: the request is never answered. */
private object SilentEngine : HttpEngine {
    override suspend fun execute(request: HttpRequest): HttpResponse = awaitCancellation()

    override fun close() {}
}

/** Notes each request's Authorization header, then answers it with [engine]. */
private class HeaderEngine(
    private val engine: HttpEngine,
) : HttpEngine {
    val authorizations = mutableListOf<String>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        request.headers.filter { it.name.equals("Authorization", ignoreCase = true) }.forEach { authorizations += it.value }
        return engine.execute(request)
    }

    override fun close() {}
}
