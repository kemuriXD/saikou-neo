package ani.saikou.neo.shared.details

import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AniZipEngine
import ani.saikou.neo.shared.catalogue.ArtLibrary
import ani.saikou.neo.shared.catalogue.ArtSources
import ani.saikou.neo.shared.catalogue.EpisodeKind
import ani.saikou.neo.shared.catalogue.FanartTvClient
import ani.saikou.neo.shared.catalogue.FanartTvEngine
import ani.saikou.neo.shared.catalogue.MalClient
import ani.saikou.neo.shared.catalogue.OfflineEngine
import ani.saikou.neo.shared.catalogue.OmdbClient
import ani.saikou.neo.shared.catalogue.QueryEngine
import ani.saikou.neo.shared.catalogue.RecordedEngine
import ani.saikou.neo.shared.catalogue.SilentEngine
import ani.saikou.neo.shared.catalogue.SiteEngine
import ani.saikou.neo.shared.catalogue.TmdbClient
import ani.saikou.neo.shared.catalogue.card
import ani.saikou.neo.shared.catalogue.clientFor
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.shared.home.WatchProgress
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    // On the test's dispatcher, so no art is still on its way once a test ends.
    private val aniZipEngine = AniZipEngine("mappings")
    private val aniZip = AniZipClient(aniZipEngine, main)

    private fun detailsViewModel(
        engine: HttpEngine = RecordedEngine("details"),
        aniZip: AniZipClient = this.aniZip,
        fanartTv: FanartTvClient = FanartTvClient(null, OfflineEngine, main),
        tmdb: TmdbClient = TmdbClient(null, OfflineEngine, main),
        library: ArtLibrary = library(engine, aniZip, fanartTv, tmdb),
        episodes: AniZipClient = AniZipClient(AniZipEngine("episodes-new"), main),
        mal: MalClient = MalClient(OfflineEngine, main),
        omdb: OmdbClient = OmdbClient(null, OfflineEngine, main),
        showUpcomingEpisodes: Boolean = true,
        progress: WatchProgress? = null,
        animeId: Int = 195516,
        zoneOffsetSeconds: Int = 0,
    ) = DetailsViewModel(
        clientFor(engine, main),
        library,
        episodes,
        Raters(mal, omdb),
        animeId = animeId,
        today = AirDate(2026, 10, 26),
        showUpcomingEpisodes = showUpcomingEpisodes,
        progress = progress,
        zoneOffsetSeconds = zoneOffsetSeconds,
    )

    private fun library(
        engine: HttpEngine = RecordedEngine("details"),
        aniZip: AniZipClient = this.aniZip,
        fanartTv: FanartTvClient = FanartTvClient(null, OfflineEngine, main),
        tmdb: TmdbClient = TmdbClient(null, OfflineEngine, main),
    ) = ArtLibrary(ArtSources(clientFor(engine, main), aniZip, fanartTv, tmdb), CoroutineScope(main))

    @Test
    fun startsLoading() {
        val viewModel = detailsViewModel()

        assertEquals(DetailsState.Loading, viewModel.state.value)
    }

    @Test
    fun loadsTheAnimeItWasGiven() =
        runTest(main) {
            val engine = RecordedEngine("details")
            val viewModel = detailsViewModel(engine)

            val loaded = assertIs<DetailsState.Loaded>(viewModel.settled())
            assertEquals(195516, loaded.details.card.id)
            assertEquals(listOf("Drama", "Mystery"), loaded.details.genres)
            assertEquals("Crunchyroll", loaded.synopsis.source)
            assertTrue(engine.requestBodies.single().contains("\"id\":195516"))
        }

    @Test
    fun carriesTheProgressItWasGiven() =
        runTest(main) {
            val progress = WatchProgress(7, 14.minutes, 24.minutes, null)
            val viewModel = detailsViewModel(progress = progress)

            assertEquals(progress, assertIs<DetailsState.Loaded>(viewModel.settled()).progress)
        }

    @Test
    fun hasNoProgressWithoutAny() =
        runTest(main) {
            assertEquals(null, assertIs<DetailsState.Loaded>(detailsViewModel().settled()).progress)
        }

    @Test
    fun theEpisodesComeWithWhatAniZipKnows() =
        runTest(main) {
            val viewModel = detailsViewModel()

            val loaded = viewModel.state.first { (it as? DetailsState.Loaded)?.episodes != null } as DetailsState.Loaded
            val episodes = loaded.episodes!!
            assertEquals(loaded.details.card.episodes, episodes.count)
            assertEquals("Day of Atonement", episodes.episodes[3].title)
        }

    @Test
    fun upcomingEpisodesAreLeftOutWhenTheViewerChoseThat() =
        runTest(main) {
            val viewModel = detailsViewModel(showUpcomingEpisodes = false)

            val loaded = viewModel.state.first { (it as? DetailsState.Loaded)?.episodes != null } as DetailsState.Loaded
            val episodes = loaded.episodes!!
            assertTrue(episodes.aired < episodes.count)
            assertEquals(episodes.aired, episodes.episodes.size)
        }

    @Test
    fun withoutAniZipTheEpisodesAreJustNumbers() =
        runTest(main) {
            val viewModel = detailsViewModel(episodes = AniZipClient(OfflineEngine, main))

            val loaded = viewModel.state.first { (it as? DetailsState.Loaded)?.episodes != null } as DetailsState.Loaded
            assertTrue(loaded.episodes!!.episodes.all { it.title == null && it.airDate == null })
            assertEquals(loaded.details.card.episodes, loaded.episodes.episodes.size)
        }

    // AniZip's record for Steel Ball Run is empty, so AniList's airing schedule dates its episodes.
    @Test
    fun withoutAniZipTheEpisodesTakeAniListsDates() =
        runTest(main) {
            val viewModel = recordedEpisodes("details-steel-ball-run", 210482)

            val episodes = viewModel.loadedEpisodes()
            assertEquals(AirDate(2026, 9, 25), episodes.episodes[0].airDate)
            assertEquals(AirDate(2026, 12, 4), episodes.episodes[10].airDate)
            assertEquals(6, episodes.aired)
        }

    // TANK CHAIR has no episode count yet, so it lists the four out so far.
    @Test
    fun aShowStillAiringTakesAniListsDatesForTheEpisodesOutSoFar() =
        runTest(main) {
            val episodes = recordedEpisodes("details-tank-chair", 209499).loadedEpisodes()

            assertEquals(4, episodes.count)
            assertEquals(AirDate(2026, 10, 4), episodes.episodes[0].airDate)
            assertEquals(25, episodes.episodes[0].minutes)
        }

    // TANK CHAIR's first episode aired at 14:00 UTC on 4 Oct, which was already the 5th in Sydney.
    @Test
    fun aniListsDatesAreTheDayOnTheTVsClock() =
        runTest(main) {
            val sydney = 11 * 3600
            val viewModel =
                detailsViewModel(
                    RecordedEngine("details-tank-chair"),
                    episodes = AniZipClient(AniZipEngine(null), main),
                    animeId = 209499,
                    zoneOffsetSeconds = sydney,
                )

            assertEquals(AirDate(2026, 10, 5), viewModel.loadedEpisodes().episodes[0].airDate)
        }

    @Test
    fun aFilmAniZipDoesNotKnowTakesItsLengthAndReleaseFromAniList() =
        runTest(main) {
            val film = recordedEpisodes("details-hathaway-film", 113971).loadedEpisodes()

            assertTrue(film.film)
            assertEquals(AirDate(2026, 1, 30), film.episodes.single().airDate)
            assertEquals(108, film.episodes.single().minutes)
        }

    private fun recordedEpisodes(
        fixture: String,
        animeId: Int,
    ) = detailsViewModel(RecordedEngine(fixture), episodes = AniZipClient(AniZipEngine(null), main), animeId = animeId)

    private suspend fun DetailsViewModel.loadedEpisodes(): EpisodeList =
        (state.first { (it as? DetailsState.Loaded)?.episodes != null } as DetailsState.Loaded).episodes!!

    @Test
    fun aniListsScoreIsThereFromTheStart() =
        runTest(main) {
            val loaded = assertIs<DetailsState.Loaded>(detailsViewModel().settled())

            assertEquals(85, loaded.ratings.aniList)
        }

    @Test
    fun malAndImdbRatingsJoinAsTheyCome() =
        runTest(main) {
            val malEngine = SiteEngine("mal") { "anime" }
            val omdbEngine = SiteEngine("omdb") { "series" }
            val viewModel =
                detailsViewModel(mal = MalClient(malEngine, main), omdb = OmdbClient("key", omdbEngine, main))

            advanceUntilIdle()

            val loaded = assertIs<DetailsState.Loaded>(viewModel.state.value)
            assertEquals(Ratings(aniList = 85, mal = 87, imdb = 86), loaded.ratings)
            assertEquals("https://api.tenrai.org/v1/anime/61987", malEngine.urls.single { "/episodes" !in it })
            // The IMDb id is AniZip's, from the same answer as the episodes.
            assertTrue(omdbEngine.urls.single().startsWith("https://www.omdbapi.com/?i=tt"))
        }

    @Test
    fun fillerAndRecapsJoinTheEpisodesAsTheyCome() =
        runTest(main) {
            val malEngine =
                SiteEngine("mal") { url ->
                    if ("/episodes" in url) "naruto-episodes-${url.substringAfter("page=")}" else "anime"
                }
            val viewModel = detailsViewModel(mal = MalClient(malEngine, main))

            advanceUntilIdle()

            val loaded = assertIs<DetailsState.Loaded>(viewModel.state.value)
            assertEquals(EpisodeKind.Recap, loaded.episodes!!.kinds[26])
            assertEquals(EpisodeKind.Filler, loaded.episodes.kinds[97])
            assertEquals(5, loaded.episodes.kinds.size)
            assertTrue(malEngine.urls.contains("https://api.tenrai.org/v1/anime/61987/episodes?page=1"))
        }

    @Test
    fun withoutMalTheEpisodesHaveNoKinds() =
        runTest(main) {
            val viewModel = detailsViewModel()

            advanceUntilIdle()

            val loaded = assertIs<DetailsState.Loaded>(viewModel.state.value)
            assertTrue(loaded.episodes!!.kinds.isEmpty())
        }

    // AniZip sometimes gives a season or film the id of another entry, which OMDb then calls a film or a series.
    @Test
    fun anImdbEntryOfTheWrongKindIsLeftOut() =
        runTest(main) {
            val viewModel = detailsViewModel(omdb = OmdbClient("key", SiteEngine("omdb") { "movie" }, main))

            advanceUntilIdle()

            val loaded = assertIs<DetailsState.Loaded>(viewModel.state.value)
            assertEquals(Ratings(aniList = 85), loaded.ratings)
        }

    @Test
    fun anAnimeAniListDoesNotKnowFails() =
        runTest(main) {
            val viewModel = detailsViewModel(RecordedEngine("details-not-found"))

            assertEquals(DetailsState.Failed, viewModel.settled())
        }

    @Test
    fun noConnectionFails() =
        runTest(main) {
            val viewModel = detailsViewModel(OfflineEngine)

            assertEquals(DetailsState.Failed, viewModel.settled())
        }

    @Test
    fun theBackdropIsTheShowsFanArt() =
        runTest(main) {
            val viewModel = detailsViewModel()

            val loaded = viewModel.state.first { (it as? DetailsState.Loaded)?.backdrop != null } as DetailsState.Loaded
            assertEquals("https://artworks.thetvdb.com/banners/v4/series/423732/backgrounds/6654700131bab.jpg", loaded.backdrop)
            assertEquals("https://api.ani.zip/mappings?anilist_id=195516", aniZipEngine.urls.single())
        }

    // The Apothecary Diaries movie has no fan art, but the series it belongs to does.
    @Test
    fun withoutFanArtTheBackdropIsItsSeriesFanArt() =
        runTest(main) {
            val engine = QueryEngine("details", mapOf("AnimeRelations" to "relations"))
            val aniZip = AniZipClient(AniZipEngine("mappings-unknown", byId = mapOf(21175 to "mappings-second-season")), main)
            val viewModel = detailsViewModel(engine, aniZip)

            val loaded = viewModel.state.first { (it as? DetailsState.Loaded)?.backdrop != null } as DetailsState.Loaded
            assertEquals("https://artworks.thetvdb.com/banners/v4/series/377543/backgrounds/609cba7794bab.jpg", loaded.backdrop)
        }

    @Test
    fun withoutAniZipFanArtTheBackdropIsFanartTvs() =
        runTest(main) {
            val engine = QueryEngine("details", mapOf("AnimeRelations" to "relations"))
            val aniZip = AniZipClient(AniZipEngine("mappings-movie"), main)
            val viewModel = detailsViewModel(engine, aniZip, FanartTvClient("test-project-key", FanartTvEngine("movie"), main))

            val loaded = viewModel.state.first { (it as? DetailsState.Loaded)?.backdrop != null } as DetailsState.Loaded
            assertEquals("https://assets.fanart.tv/fanart/spirited-away-50f0c9c556cad.jpg", loaded.backdrop)
        }

    @Test
    fun withoutFanArtTheBackdropIsTheBanner() =
        runTest(main) {
            val viewModel = detailsViewModel(aniZip = AniZipClient(OfflineEngine, main))

            val loaded = viewModel.state.first { (it as? DetailsState.Loaded)?.backdrop != null } as DetailsState.Loaded
            assertEquals(loaded.details.card.bannerImageUrl, loaded.backdrop)
        }

    @Test
    fun theTitleIsTheShowsLogo() =
        runTest(main) {
            // Found while the viewer rested on its card.
            val library = library(fanartTv = FanartTvClient("test-project-key", FanartTvEngine("tv"), main))
            library.art(card(195516, "The Apothecary Diaries"))

            val loaded = assertIs<DetailsState.Loaded>(detailsViewModel(library = library).settled())
            assertEquals(Logo.Image("https://assets.fanart.tv/fanart/sakamoto-days-66866dc54d0e7.png"), loaded.logo)
            // The art the app already had, with no second request.
            assertEquals("https://api.ani.zip/mappings?anilist_id=195516", aniZipEngine.urls.single())
        }

    @Test
    fun withoutALogoFromTmdbOrFanartTvTheTitleStaysText() =
        runTest(main) {
            val viewModel = detailsViewModel()

            val loaded = viewModel.withLogo()
            assertEquals(Logo.None, loaded.logo)
        }

    @Test
    fun withoutAniZipTheTitleStaysText() =
        runTest(main) {
            val viewModel = detailsViewModel(aniZip = AniZipClient(OfflineEngine, main))

            val loaded = viewModel.withLogo()
            assertEquals(Logo.None, loaded.logo)
        }

    @Test
    fun withoutArtYetTheTitleIsInWords() =
        runTest(main) {
            val viewModel = detailsViewModel(aniZip = AniZipClient(SilentEngine, main))

            assertEquals(Logo.None, assertIs<DetailsState.Loaded>(viewModel.settled()).logo)
        }

    // On a first visit the art takes about twice as long as AniList's details. Details waits for it a little.
    @Test
    fun detailsWaitsAMomentForArtStillOnItsWay() =
        runTest(main) {
            val aniZip = GatedEngine(AniZipEngine("mappings"))
            val viewModel =
                detailsViewModel(
                    aniZip = AniZipClient(aniZip, main),
                    fanartTv = FanartTvClient("test-project-key", FanartTvEngine("tv"), main),
                )
            advanceTimeBy(ART_WAIT - 1.seconds)
            assertEquals(DetailsState.Loading, viewModel.state.value)

            aniZip.open.complete(Unit)

            val loaded = assertIs<DetailsState.Loaded>(viewModel.settled())
            assertEquals(Logo.Image("https://assets.fanart.tv/fanart/sakamoto-days-66866dc54d0e7.png"), loaded.logo)
        }

    // So the title never swaps from words to the logo in front of the viewer.
    @Test
    fun aLogoFoundOnceDetailsShowsIsLeftOut() =
        runTest(main) {
            val aniZip = GatedEngine(AniZipEngine("mappings"))
            val viewModel =
                detailsViewModel(
                    aniZip = AniZipClient(aniZip, main),
                    fanartTv = FanartTvClient("test-project-key", FanartTvEngine("tv"), main),
                )
            assertEquals(Logo.None, assertIs<DetailsState.Loaded>(viewModel.settled()).logo)

            aniZip.open.complete(Unit)

            val loaded = viewModel.state.first { (it as? DetailsState.Loaded)?.backdrop != null } as DetailsState.Loaded
            assertEquals("https://assets.fanart.tv/fanart/sakamoto-days-66866dfcc9fd5.jpg", loaded.backdrop)
            assertEquals(Logo.None, loaded.logo)
        }

    // So the art fades in once, rather than AniList's first and then the fan art.
    @Test
    fun theBackdropWaitsForAniZip() =
        runTest(main) {
            val viewModel = detailsViewModel(aniZip = AniZipClient(SilentEngine, main))

            assertNull(assertIs<DetailsState.Loaded>(viewModel.settled()).backdrop)
        }
}

/** Waits until loading ends. */
private suspend fun DetailsViewModel.settled() = state.first { it != DetailsState.Loading }

/** Waits until the art has been found. */
private suspend fun DetailsViewModel.withLogo() =
    state.first {
        it is DetailsState.Loaded && it.logo != Logo.Loading
    } as DetailsState.Loaded

/** Holds every request until [open] completes. */
private class GatedEngine(
    private val engine: HttpEngine,
) : HttpEngine {
    val open = CompletableDeferred<Unit>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        open.await()
        return engine.execute(request)
    }

    override fun close() {}
}
