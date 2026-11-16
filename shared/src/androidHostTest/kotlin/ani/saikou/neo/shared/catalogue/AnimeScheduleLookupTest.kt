package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class AnimeScheduleLookupTest {
    private fun client(
        engine: HttpEngine,
        token: String? = "test-token",
    ) = AnimeScheduleClient(token, engine, now = { Instant.parse("2026-11-16T12:00:00Z") })

    @Test
    fun aRouteIsAskedForOneAniListShow() =
        runTest {
            val engine = LookupEngine(body = BY_ANILIST_ID)

            assertEquals(ShowLookup.Found("sousou-no-frieren"), client(engine).routeFor(154587))

            val request = engine.requests.single()
            assertEquals("https://animeschedule.net/api/v3/anime?anilist-ids=154587", request.url)
            assertEquals("Bearer test-token", request.headers.single { it.name == "Authorization" }.value)
        }

    // AnimeSchedule answers 200 with plain text when it has nothing.
    @Test
    fun anAniListShowAnimeScheduleDoesNotHaveIsNoMatch() =
        runTest {
            assertEquals(ShowLookup.NoMatch, client(LookupEngine(body = NOT_FOUND)).routeFor(1))
            assertEquals(ShowLookup.NoMatch, client(LookupEngine(body = """{"page":1,"totalAmount":0,"anime":[]}""")).routeFor(1))
        }

    @Test
    fun anAniListIdIsReadFromTheShowsAniListLink() =
        runTest {
            val engine = LookupEngine(body = byRoute("anilist.co/anime/154587/Sousou-no-Frieren/"))

            assertEquals(ShowLookup.Found(154587), client(engine).aniListIdFor("sousou-no-frieren"))
            assertEquals("https://animeschedule.net/api/v3/anime/sousou-no-frieren", engine.requests.single().url)
        }

    @Test
    fun aLinkWithASchemeOrWithoutATrailingSlashIsRead() =
        runTest {
            assertEquals(ShowLookup.Found(21), client(LookupEngine(body = byRoute("https://anilist.co/anime/21"))).aniListIdFor("a"))
            assertEquals(ShowLookup.Found(21), client(LookupEngine(body = byRoute("anilist.co/anime/21/One-Piece"))).aniListIdFor("a"))
        }

    @Test
    fun aMalformedOrMissingLinkIsNoMatch() =
        runTest {
            for (link in listOf("anilist.co/manga/21/", "anilist.co/anime/abc/", "", "myanimelist.net/anime/21/")) {
                assertEquals(ShowLookup.NoMatch, client(LookupEngine(body = byRoute(link))).aniListIdFor("a"), link)
            }
            assertEquals(ShowLookup.NoMatch, client(LookupEngine(body = """{"route":"a","websites":{}}""")).aniListIdFor("a"))
            assertEquals(ShowLookup.NoMatch, client(LookupEngine(body = """{"route":"a"}""")).aniListIdFor("a"))
        }

    @Test
    fun anUnknownRouteIsNoMatch() =
        runTest {
            assertEquals(ShowLookup.NoMatch, client(LookupEngine(body = NOT_FOUND)).aniListIdFor("nope"))
            assertEquals(ShowLookup.NoMatch, client(LookupEngine(status = 404, body = "")).aniListIdFor("nope"))
        }

    @Test
    fun lookupsFailLikeTheTimetable() =
        runTest {
            val engine = LookupEngine(body = BY_ANILIST_ID)
            assertEquals(ShowLookup.Failure(AnimeScheduleError.NoToken), client(engine, null).routeFor(1))
            assertEquals(ShowLookup.Failure(AnimeScheduleError.NoToken), client(engine, null).aniListIdFor("a"))
            assertTrue(engine.requests.isEmpty())

            assertEquals(ShowLookup.Failure(AnimeScheduleError.BadToken), client(LookupEngine(status = 401, body = "")).routeFor(1))
            assertEquals(ShowLookup.Failure(AnimeScheduleError.Http(503)), client(LookupEngine(status = 503, body = "")).aniListIdFor("a"))
            assertIs<AnimeScheduleError.Unexpected>(
                assertIs<ShowLookup.Failure>(client(LookupEngine(body = """{"anime":[""")).routeFor(1)).error,
            )
        }

    private companion object {
        const val NOT_FOUND = "Couldn't find anime with these parameters."

        // Trimmed from a live answer. Streams are left in to show they're skipped.
        const val BY_ANILIST_ID =
            """{"page":1,"totalAmount":1,"anime":[{"title":"Sousou no Frieren","route":"sousou-no-frieren",""" +
                """"streams":{"x":"y"},"websites":{"aniList":"anilist.co/anime/154587/Sousou-no-Frieren/",""" +
                """"mal":"myanimelist.net/anime/52991/Sousou_no_Frieren"}}]}"""

        fun byRoute(aniListLink: String) =
            """{"title":"Show","route":"a","streams":{"x":"y"},"websites":{"mal":"myanimelist.net/anime/1/","aniList":"$aniListLink"}}"""
    }
}

/** Answers every request with [status] and [body], and records what was asked. */
internal class LookupEngine(
    private val status: Int = 200,
    private val body: String,
) : HttpEngine {
    val requests = mutableListOf<HttpRequest>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return HttpResponse
            .Builder(status)
            .body(Buffer().writeUtf8(body))
            .build()
    }

    override fun close() {}
}
