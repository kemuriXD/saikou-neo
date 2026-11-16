package ani.saikou.neo.shared.catalogue

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ShowMatcherTest {
    private var now = Instant.parse("2026-11-16T12:00:00Z")
    private val store = FakeShowMatchStore()

    private fun matcher(engine: LookupEngine) = ShowMatcher(AnimeScheduleClient("test-token", engine, now = { now }), store, now = { now })

    @Test
    fun aFoundRouteIsOnlyAskedForOnce() =
        runTest {
            val engine = LookupEngine(body = byAniListId(154587, "sousou-no-frieren"))

            assertEquals(ShowLookup.Found("sousou-no-frieren"), matcher(engine).routeFor(154587))
            now += 400.days
            assertEquals(ShowLookup.Found("sousou-no-frieren"), matcher(engine).routeFor(154587))

            assertEquals(1, engine.requests.size)
        }

    @Test
    fun aFoundAniListIdIsOnlyAskedForOnce() =
        runTest {
            val engine = LookupEngine(body = byRoute("anilist.co/anime/154587/Sousou-no-Frieren/"))

            assertEquals(ShowLookup.Found(154587), matcher(engine).aniListIdFor("sousou-no-frieren"))
            assertEquals(ShowLookup.Found(154587), matcher(engine).aniListIdFor("sousou-no-frieren"))

            assertEquals(1, engine.requests.size)
        }

    // A show found one way is known the other way too.
    @Test
    fun aMatchIsSavedBothWays() =
        runTest {
            val engine = LookupEngine(body = byAniListId(154587, "sousou-no-frieren"))
            matcher(engine).routeFor(154587)

            assertEquals(ShowLookup.Found(154587), matcher(engine).aniListIdFor("sousou-no-frieren"))
            assertEquals(1, engine.requests.size)

            val other = LookupEngine(body = byRoute("anilist.co/anime/21/"))
            matcher(other).aniListIdFor("one-piece")
            assertEquals(ShowLookup.Found("one-piece"), matcher(other).routeFor(21))
            assertEquals(1, other.requests.size)
        }

    @Test
    fun noMatchIsAskedAgainAfterAWeek() =
        runTest {
            val engine = LookupEngine(body = NOT_FOUND)

            assertEquals(ShowLookup.NoMatch, matcher(engine).routeFor(1))
            now += 7.days - 1.hours
            assertEquals(ShowLookup.NoMatch, matcher(engine).routeFor(1))
            assertEquals(1, engine.requests.size)

            now += 1.hours
            assertEquals(ShowLookup.NoMatch, matcher(engine).routeFor(1))
            assertEquals(2, engine.requests.size)
        }

    @Test
    fun aMalformedLinkIsRememberedAsNoMatch() =
        runTest {
            val engine = LookupEngine(body = byRoute("anilist.co/anime/oops/"))

            assertEquals(ShowLookup.NoMatch, matcher(engine).aniListIdFor("a"))
            now += 6.days
            assertEquals(ShowLookup.NoMatch, matcher(engine).aniListIdFor("a"))
            assertEquals(1, engine.requests.size)

            now += 1.days
            matcher(engine).aniListIdFor("a")
            assertEquals(2, engine.requests.size)
        }

    // Only AnimeSchedule's answers are saved, so a failed call is tried again next time.
    @Test
    fun aFailureIsNotSaved() =
        runTest {
            val engine = LookupEngine(status = 503, body = "")

            assertEquals(ShowLookup.Failure(AnimeScheduleError.Http(503)), matcher(engine).routeFor(1))
            matcher(engine).routeFor(1)

            assertEquals(2, engine.requests.size)
        }

    private companion object {
        const val NOT_FOUND = "Couldn't find anime with these parameters."

        fun byAniListId(
            id: Int,
            route: String,
        ) = """{"page":1,"totalAmount":1,"anime":[{"route":"$route","websites":{"aniList":"anilist.co/anime/$id/"}}]}"""

        fun byRoute(aniListLink: String) = """{"route":"a","websites":{"aniList":"$aniListLink"}}"""
    }
}

internal class FakeShowMatchStore : ShowMatchStore {
    private val routes = mutableMapOf<Int, SavedMatch<String>>()
    private val aniListIds = mutableMapOf<String, SavedMatch<Int>>()

    override fun route(aniListId: Int): SavedMatch<String>? = routes[aniListId]

    override fun aniListId(route: String): SavedMatch<Int>? = aniListIds[route]

    override fun saveRoute(
        aniListId: Int,
        match: SavedMatch<String>,
    ) {
        routes[aniListId] = match
    }

    override fun saveAniListId(
        route: String,
        match: SavedMatch<Int>,
    ) {
        aniListIds[route] = match
    }
}
