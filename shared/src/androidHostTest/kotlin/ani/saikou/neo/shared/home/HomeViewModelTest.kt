package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AniZipEngine
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.ArtLibrary
import ani.saikou.neo.shared.catalogue.ArtSources
import ani.saikou.neo.shared.catalogue.FanartTvClient
import ani.saikou.neo.shared.catalogue.FanartTvEngine
import ani.saikou.neo.shared.catalogue.OfflineEngine
import ani.saikou.neo.shared.catalogue.RecordedEngine
import ani.saikou.neo.shared.catalogue.SilentEngine
import ani.saikou.neo.shared.catalogue.TmdbClient
import ani.saikou.neo.shared.catalogue.card
import ani.saikou.neo.shared.catalogue.clientFor
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Buffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    // On the test's dispatcher, so no logo is still on its way once a test ends.
    private val aniZipEngine = AniZipEngine("mappings")
    private val aniZip = AniZipClient(aniZipEngine, main)

    private fun homeViewModel(
        engine: HttpEngine = HomeEngine(),
        aniZip: AniZipClient = this.aniZip,
        fanartTv: FanartTvClient = FanartTvClient(null, OfflineEngine, main),
        tmdb: TmdbClient = TmdbClient(null, OfflineEngine, main),
        sampleProgress: Boolean = false,
        now: Long = 1_793_300_000,
    ): HomeViewModel {
        val client = clientFor(engine, main)
        val library = ArtLibrary(ArtSources(client, aniZip, fanartTv, tmdb), CoroutineScope(main))
        return HomeViewModel(client, library, AnimeSeason.FALL, 2026, sampleProgress) { now }
    }

    private val aniListRows = listOf(HomeRow.TRENDING, HomeRow.THIS_SEASON, HomeRow.POPULAR)

    @Test
    fun startsWithEveryAniListRowLoading() {
        val viewModel = homeViewModel()

        assertEquals(aniListRows.map { RowState.Loading }, aniListRows.map { viewModel.state.value.rows[it] })
    }

    @Test
    fun continueWatchingIsEmptyWithoutSamples() =
        runTest(main) {
            val viewModel = homeViewModel()

            assertEquals(RowState.Loaded(emptyList()), viewModel.state.value.rows[HomeRow.CONTINUE_WATCHING])
            viewModel.settledRows()
            assertEquals(RowState.Loaded(emptyList()), viewModel.state.value.rows[HomeRow.CONTINUE_WATCHING])
            assertEquals(emptyMap(), viewModel.state.value.progress)
        }

    @Test
    fun continueWatchingLoadsWithTheSpotlightWithSamples() =
        runTest(main) {
            val viewModel = homeViewModel(RecordedEngine("browse-eight"), sampleProgress = true)

            assertEquals(RowState.Loading, viewModel.state.value.rows[HomeRow.CONTINUE_WATCHING])
            val spotlight = viewModel.settledSpotlight()
            val state = viewModel.state.value
            val shows = assertIs<RowState.Loaded>(state.rows[HomeRow.CONTINUE_WATCHING]).items
            assertEquals(spotlight.shows.take(5), shows)
            assertEquals(shows.map { it.id }.toSet(), state.progress.keys)
            assertEquals(7, state.progress.getValue(shows.first().id).episode)
        }

    @Test
    fun continueWatchingIsEmptyWhenEveryRowFailsWithSamples() =
        runTest(main) {
            val viewModel = homeViewModel(OfflineEngine, sampleProgress = true)

            assertEquals(RowState.Loaded(emptyList()), viewModel.settledRows()[HomeRow.CONTINUE_WATCHING])
            assertEquals(emptyMap(), viewModel.state.value.progress)
        }

    @Test
    fun loadsTheRowsInOrder() =
        runTest(main) {
            val viewModel = homeViewModel()

            val rows = viewModel.settledRows()
            assertEquals(HomeRow.entries, rows.keys.toList())
            aniListRows.map(rows::getValue).forEach { assertEquals(195516, assertIs<RowState.Loaded>(it).items.first().id) }
        }

    @Test
    fun thisSeasonAsksForTheGivenSeason() =
        runTest(main) {
            val engine = RecordedEngine("browse-trending")
            val client = clientFor(engine, main)
            HomeViewModel(
                client,
                ArtLibrary(
                    ArtSources(client, aniZip, FanartTvClient(null, OfflineEngine, main), TmdbClient(null, OfflineEngine, main)),
                    CoroutineScope(main),
                ),
                AnimeSeason.SPRING,
                2027,
            ).settledRows()

            assertTrue(engine.requestBodies.any { "\"season\":\"SPRING\"" in it && "\"seasonYear\":2027" in it })
        }

    @Test
    fun aFailedRowDoesNotStopTheOthers() =
        runTest(main) {
            val viewModel = homeViewModel(SeasonOfflineEngine())

            val rows = viewModel.settledRows()
            assertIs<RowState.Loaded>(rows[HomeRow.TRENDING])
            assertEquals(RowState.Failed, rows[HomeRow.THIS_SEASON])
            assertIs<RowState.Loaded>(rows[HomeRow.POPULAR])
        }

    @Test
    fun theSpotlightFeaturesTheFirstSixTrendingShows() =
        runTest(main) {
            val viewModel = homeViewModel(RecordedEngine("browse-eight"))

            val spotlight = viewModel.settledSpotlight()
            val trending = assertIs<RowState.Loaded>(viewModel.state.value.rows[HomeRow.TRENDING]).items
            assertEquals(8, trending.size)
            assertEquals(trending.take(6), spotlight.shows)
            assertEquals(0, spotlight.index)
        }

    @Test
    fun theSpotlightUsesTheNextRowWhenTrendingFails() =
        runTest(main) {
            val viewModel = homeViewModel(TrendingOfflineEngine())

            val spotlight = viewModel.settledSpotlight()
            assertEquals(RowState.Failed, viewModel.state.value.rows[HomeRow.TRENDING])
            assertEquals(assertIs<RowState.Loaded>(viewModel.state.value.rows[HomeRow.THIS_SEASON]).items.take(6), spotlight.shows)
        }

    @Test
    fun noRowsMeansNoSpotlight() =
        runTest(main) {
            val viewModel = homeViewModel(OfflineEngine)

            viewModel.settledRows()
            assertNull(viewModel.state.value.spotlight)
        }

    @Test
    fun eachFeaturedShowGetsItsLogo() =
        runTest(main) {
            val viewModel = homeViewModel(fanartTv = FanartTvClient("test-project-key", FanartTvEngine("tv"), main))

            val spotlight = viewModel.state.first { it.spotlight?.logos?.size == 2 }.spotlight!!
            val logo = Logo.Image("https://assets.fanart.tv/fanart/sakamoto-days-66866dc54d0e7.png")
            spotlight.shows.forEach { assertEquals(logo, spotlight.logo(it.id)) }
        }

    @Test
    fun aShowWithoutALogoSaysSo() =
        runTest(main) {
            val viewModel = homeViewModel(aniZip = AniZipClient(OfflineEngine, main))

            val spotlight = viewModel.state.first { it.spotlight?.logos?.size == 2 }.spotlight!!
            assertEquals(Logo.None, spotlight.logo(spotlight.shows.first().id))
        }

    @Test
    fun eachFeaturedShowIsShownOverItsFanArt() =
        runTest(main) {
            val viewModel = homeViewModel()

            val spotlight = viewModel.state.first { it.spotlight?.logos?.size == 2 }.spotlight!!
            val fanArt = "https://artworks.thetvdb.com/banners/v4/series/423732/backgrounds/6654700131bab.jpg"
            spotlight.shows.forEach { assertEquals(fanArt, spotlight.backdrop(it)) }
        }

    // So the art fades in once, rather than AniList's first and then the fan art.
    @Test
    fun theBackdropWaitsForAniZip() =
        runTest(main) {
            val viewModel = homeViewModel(aniZip = AniZipClient(SilentEngine, main))

            val spotlight = viewModel.settledSpotlight()
            assertNull(spotlight.backdrop(spotlight.show))
        }

    @Test
    fun aShowWithoutFanArtIsShownOverItsSeriesFanArt() =
        runTest(main) {
            val aniZip = AniZipClient(AniZipEngine("mappings-unknown", byId = mapOf(21175 to "mappings-second-season")), main)
            val viewModel = homeViewModel(aniZip = aniZip)

            val spotlight = viewModel.state.first { it.spotlight?.logos?.size == 2 }.spotlight!!
            assertEquals(
                "https://artworks.thetvdb.com/banners/v4/series/377543/backgrounds/609cba7794bab.jpg",
                spotlight.backdrop(spotlight.show),
            )
        }

    @Test
    fun aShowWithoutAniZipFanArtIsShownOverFanartTvs() =
        runTest(main) {
            val aniZip = AniZipClient(AniZipEngine("mappings-movie"), main)
            val fanartTv = FanartTvClient("test-project-key", FanartTvEngine("movie"), main)
            val viewModel = homeViewModel(aniZip = aniZip, fanartTv = fanartTv)

            val spotlight = viewModel.state.first { it.spotlight?.logos?.size == 2 }.spotlight!!
            assertEquals("https://assets.fanart.tv/fanart/spirited-away-50f0c9c556cad.jpg", spotlight.backdrop(spotlight.show))
        }

    @Test
    fun withoutFanArtTheBackdropIsTheBanner() =
        runTest(main) {
            val viewModel = homeViewModel(aniZip = AniZipClient(OfflineEngine, main))

            val spotlight = viewModel.state.first { it.spotlight?.logos?.size == 2 }.spotlight!!
            assertEquals(spotlight.show.bannerImageUrl, spotlight.backdrop(spotlight.show))
            assertTrue(spotlight.show.bannerImageUrl!!.startsWith("https://s4.anilist.co/"))
        }

    @Test
    fun restingOnACardFindsItsArt() =
        runTest(main) {
            val viewModel = homeViewModel(fanartTv = FanartTvClient("test-project-key", FanartTvEngine("tv"), main))
            viewModel.settledRows()

            viewModel.rest(card(424242, "Rested on"))
            advanceTimeBy(REST - 1.milliseconds)
            assertTrue(aniZipEngine.urls.none { "=424242" in it })

            assertEquals(
                Logo.Image("https://assets.fanart.tv/fanart/sakamoto-days-66866dc54d0e7.png"),
                viewModel.state
                    .first {
                        it.restingLogo !=
                            null
                    }.restingLogo,
            )
            assertEquals(1, aniZipEngine.urls.count { "=424242" in it })
        }

    @Test
    fun cardsFocusPassesOverAreNotAskedAbout() =
        runTest(main) {
            val viewModel = homeViewModel()
            viewModel.settledRows()

            viewModel.rest(card(424242, "Passed over"))
            advanceTimeBy(REST / 2)
            viewModel.rest(card(434343, "Rested on"))
            advanceUntilIdle()

            assertTrue(aniZipEngine.urls.none { "=424242" in it })
            assertEquals(1, aniZipEngine.urls.count { "=434343" in it })
        }

    @Test
    fun leavingTheRowsForgetsTheRestedLogo() =
        runTest(main) {
            val viewModel = homeViewModel(fanartTv = FanartTvClient("test-project-key", FanartTvEngine("tv"), main))
            viewModel.rest(card(424242, "Rested on"))
            viewModel.state.first { it.restingLogo != null }

            viewModel.rest(null)

            assertNull(viewModel.state.value.restingLogo)
        }

    @Test
    fun eachFeaturedShowGetsItsStory() =
        runTest(main) {
            val viewModel = homeViewModel()

            val spotlight = viewModel.state.first { it.spotlight?.synopses?.isNotEmpty() == true }.spotlight!!
            val story = spotlight.synopses.getValue(195516)
            assertTrue(story.startsWith("In the aftermath of the Shi Clan"))
            // Only the story: the source credit is for the Details screen.
            assertTrue("Crunchyroll" !in story)
            // A preview of a few lines, so its paragraphs run on rather than leave half-empty lines.
            assertTrue('\n' !in story)
            assertTrue("investigating its true nature. Meanwhile, Jinshi" in story)
        }

    // The first show's next episode is out within a day. The second has none and isn't in the top 10 rated, so it's
    // featured for its place in Trending.
    @Test
    fun eachFeaturedShowSaysWhyItsFeatured() =
        runTest(main) {
            val viewModel = homeViewModel()

            val spotlight = viewModel.state.first { it.spotlight?.reasons?.isNotEmpty() == true }.spotlight!!
            assertEquals(FeaturedReason.NewEpisode(1_793_368_800), spotlight.reasons[195516])
            assertEquals(FeaturedReason.Trending(2), spotlight.reasons[195539])
        }

    @Test
    fun anEpisodeMoreThanAWeekAwayIsntAReason() =
        runTest(main) {
            val viewModel = homeViewModel(now = 1_792_000_000)

            val spotlight = viewModel.state.first { it.spotlight?.reasons?.isNotEmpty() == true }.spotlight!!
            assertEquals(FeaturedReason.Trending(1), spotlight.reasons[195516])
        }

    // The screen waits for these before showing the first show, whether they came or not.
    @Test
    fun theSpotlightSaysOnceItsDetailsHaveAnswered() =
        runTest(main) {
            val viewModel = homeViewModel()

            assertFalse(viewModel.settledSpotlight().detailsLoaded)
            assertTrue(
                viewModel.state
                    .first { it.spotlight?.detailsLoaded == true }
                    .spotlight!!
                    .synopses
                    .isNotEmpty(),
            )
        }

    @Test
    fun theSpotlightsDetailsCountAsAnsweredWhenTheyFail() =
        runTest(main) {
            val viewModel = homeViewModel(HomeEngine(synopsesOffline = true))

            viewModel.state.first { it.spotlight?.detailsLoaded == true }
        }

    @Test
    fun noStoriesWhenTheyFailToLoad() =
        runTest(main) {
            val viewModel = homeViewModel(HomeEngine(synopsesOffline = true))

            viewModel.state.first { it.spotlight?.logos?.size == 2 }
            assertEquals(
                emptyMap(),
                viewModel.state.value.spotlight!!
                    .synopses,
            )
        }

    @Test
    fun theSpotlightMovesOnEveryTenSecondsAndWrapsAround() =
        runTest(main) {
            val viewModel = homeViewModel()
            viewModel.settledSpotlight()

            viewModel.whileWatching {
                advanceTimeBy(9_999)
                assertEquals(
                    0,
                    viewModel.state.value.spotlight!!
                        .index,
                )
                advanceTimeBy(2)
                assertEquals(
                    1,
                    viewModel.state.value.spotlight!!
                        .index,
                )
                advanceTimeBy(10_000)
                assertEquals(
                    0,
                    viewModel.state.value.spotlight!!
                        .index,
                )
            }
        }

    @Test
    fun theSpotlightStaysPutWhileOutOfView() =
        runTest(main) {
            val viewModel = homeViewModel()
            viewModel.settledSpotlight()
            viewModel.whileWatching { advanceTimeBy(5_000) }

            advanceTimeBy(60_000)
            assertEquals(
                0,
                viewModel.state.value.spotlight!!
                    .index,
            )
            // Back in view, it waits a full 10 seconds again.
            viewModel.whileWatching { advanceTimeBy(9_999) }
            assertEquals(
                0,
                viewModel.state.value.spotlight!!
                    .index,
            )
        }

    @Test
    fun pickingAShowStopsTheSpotlightMoving() =
        runTest(main) {
            val viewModel = homeViewModel()
            viewModel.settledSpotlight()

            viewModel.whileWatching {
                viewModel.pick(1)
                advanceTimeBy(60_000)
            }

            assertEquals(
                1,
                viewModel.state.value.spotlight!!
                    .index,
            )
        }
}

/** Waits until no row is loading. */
private suspend fun HomeViewModel.settledRows() = state.first { state -> RowState.Loading !in state.rows.values }.rows

/** Runs [block] with the spotlight in view, then out of view, as the test's clock runs until no timer is left. */
private suspend fun HomeViewModel.whileWatching(block: suspend () -> Unit) {
    watchSpotlight(true)
    try {
        block()
    } finally {
        watchSpotlight(false)
    }
}

/**
 * Answers the spotlight's synopses request with its recording, or fails it, a show's series with Dragon Ball Super, and
 * every row with the trending page.
 */
private class HomeEngine(
    private val synopsesOffline: Boolean = false,
) : HttpEngine {
    private val rows = RecordedEngine("browse-trending")
    private val synopses = RecordedEngine("synopses")
    private val parents = RecordedEngine("relations")

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        if ("AnimeRelations" in body) return parents.execute(request)
        if ("AnimeSynopses" !in body) return rows.execute(request)
        if (synopsesOffline) throw ApolloNetworkException("Unable to resolve host")
        return synopses.execute(request)
    }

    override fun close() {}
}

/** Waits for the rows to load and the spotlight to be chosen from them. */
private suspend fun HomeViewModel.settledSpotlight() = state.first { it.spotlight != null }.spotlight!!

/** Fails the trending request as if the connection dropped, and answers the rest. */
private class TrendingOfflineEngine : HttpEngine {
    private val recorded = RecordedEngine("browse-trending")

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        if ("TRENDING_DESC" in body) throw ApolloNetworkException("Unable to resolve host")
        return recorded.execute(request)
    }

    override fun close() {}
}

/** Fails the season request as if the connection dropped, and answers the rest. */
private class SeasonOfflineEngine : HttpEngine {
    private val recorded = RecordedEngine("browse-trending")

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        if ("\"season\"" in body) throw ApolloNetworkException("Unable to resolve host")
        return recorded.execute(request)
    }

    override fun close() {}
}
