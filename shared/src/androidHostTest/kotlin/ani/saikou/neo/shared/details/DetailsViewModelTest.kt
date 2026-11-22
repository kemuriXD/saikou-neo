package ani.saikou.neo.shared.details

import ani.saikou.neo.shared.account.ListChange
import ani.saikou.neo.shared.account.ListEntry
import ani.saikou.neo.shared.account.ListStatus
import ani.saikou.neo.shared.account.ListedProgress
import ani.saikou.neo.shared.account.OnYourList
import ani.saikou.neo.shared.account.ShowEntry
import ani.saikou.neo.shared.account.WatchedEpisodes
import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AniZipClient
import ani.saikou.neo.shared.catalogue.AniZipEngine
import ani.saikou.neo.shared.catalogue.AnimeTitle
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
import ani.saikou.neo.shared.home.PickUp
import ani.saikou.neo.shared.home.PickUpSides
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.shared.playback.FoundServer
import ani.saikou.neo.shared.playback.LastServer
import ani.saikou.neo.shared.playback.NextPlay
import ani.saikou.neo.shared.playback.PlayRequest
import ani.saikou.neo.shared.playback.PlayServer
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.shared.sources.SourceProblem
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.time.Duration
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
        progress: Flow<WatchProgress?> = flowOf(null),
        listed: ListedProgress? = null,
        sides: PickUpSides? = null,
        marks: WatchedEpisodes? = null,
        animeId: Int = 195516,
        zoneOffsetSeconds: Int = 0,
        warmSources: () -> Unit = {},
        asksWhereToPickUp: Boolean = true,
        lastServer: LastServer? = null,
        installed: List<InstalledSource>? = null,
    ) = DetailsViewModel(
        clientFor(engine, main),
        library,
        episodes,
        Raters(mal, omdb),
        animeId = animeId,
        today = AirDate(2026, 10, 26),
        showUpcomingEpisodes = showUpcomingEpisodes,
        progress = progress,
        listed = { listed },
        sides = sides,
        marks = marks,
        zoneOffsetSeconds = zoneOffsetSeconds,
        warmSources = warmSources,
        asksWhereToPickUp = asksWhereToPickUp,
        lastServer = flowOf(lastServer),
        installed = flowOf(installed),
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
    fun opensWithTheSourcesSitesBeingGotReady() {
        // So a site's browser check runs while the episodes are browsed, not once one is played (#369).
        var warmed = 0

        detailsViewModel(warmSources = { warmed++ })

        assertEquals(1, warmed)
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
            val viewModel = detailsViewModel(progress = flowOf(progress))

            assertEquals(progress, assertIs<DetailsState.Loaded>(viewModel.settled()).progress)
        }

    @Test
    fun progressSavedWhileDetailsIsOpenShowsAtOnce() =
        runTest(main) {
            val saved = MutableStateFlow<WatchProgress?>(null)
            val viewModel = detailsViewModel(progress = saved)
            viewModel.settled()

            saved.value = WatchProgress(7, 14.minutes, 24.minutes, null)
            advanceUntilIdle()

            assertEquals(saved.value, assertIs<DetailsState.Loaded>(viewModel.state.value).progress)
        }

    // The show's fifth episode airs next, so four are out.
    @Test
    fun upNextIsTheEpisodeAfterOneWatchedToTheEnd() =
        runTest(main) {
            val viewModel = detailsViewModel(progress = flowOf(WatchProgress(3, 24.minutes, 24.minutes, null)))

            val loaded = assertIs<DetailsState.Loaded>(viewModel.settled())
            assertEquals(WatchProgress(4, Duration.ZERO, 24.minutes, null), loaded.upNext)
        }

    @Test
    fun nothingIsUpNextOnceEveryAiredEpisodeIsWatched() =
        runTest(main) {
            val viewModel = detailsViewModel(progress = flowOf(WatchProgress(4, 24.minutes, 24.minutes, null)))

            assertEquals(null, assertIs<DetailsState.Loaded>(viewModel.settled()).upNext)
        }

    private class RecordedSides : PickUpSides {
        val saved = mutableListOf<Pair<Int, WatchProgress>>()
        val moved = mutableListOf<Triple<Int, Int, Int>>()

        override fun saveHere(
            animeId: Int,
            place: WatchProgress,
        ) {
            saved += animeId to place
        }

        override suspend fun moveAniList(
            animeId: Int,
            watched: Int,
            listed: ListedProgress,
        ) {
            moved += Triple(animeId, watched, listed.progress)
        }
    }

    // A show only on AniList gets Resume too (ADR 0050).
    @Test
    fun aniListsPlaceIsUsedWithNoneHere() =
        runTest(main) {
            val viewModel = detailsViewModel(listed = ListedProgress(195516, 2, 500, 24))

            val loaded = assertIs<DetailsState.Loaded>(viewModel.settled())
            assertEquals(WatchProgress(2, 24.minutes, 24.minutes, null, 500), loaded.place)
            assertEquals(WatchProgress(3, Duration.ZERO, 24.minutes, null, 500), loaded.upNext)
        }

    @Test
    fun pickingAniListCarriesOnThereAndSavesItHere() =
        runTest(main) {
            val sides = RecordedSides()
            val here = WatchProgress(1, 12.minutes, 24.minutes, null, 300)
            val viewModel = detailsViewModel(progress = flowOf(here), listed = ListedProgress(195516, 2, 500, 24), sides = sides)
            val asked = assertIs<PickUp.Ask>(assertIs<DetailsState.Loaded>(viewModel.settled()).pickUp)

            viewModel.keepAniList()
            advanceUntilIdle()

            assertEquals(listOf(195516 to asked.aniList), sides.saved)
            assertEquals(PickUp.At(asked.aniList), assertIs<DetailsState.Loaded>(viewModel.state.value).pickUp)
        }

    // Private mode (ADR 0053): this TV's place wins, so nothing is asked and nothing goes to AniList.
    @Test
    fun inPrivateModeThisTvsPlaceWinsWithoutAsking() =
        runTest(main) {
            val here = WatchProgress(1, 12.minutes, 24.minutes, null, 300)
            val viewModel =
                detailsViewModel(progress = flowOf(here), listed = ListedProgress(195516, 2, 500, 24), asksWhereToPickUp = false)

            assertEquals(PickUp.At(here), assertIs<DetailsState.Loaded>(viewModel.settled()).pickUp)
        }

    @Test
    fun pickingThisTvMovesAniListAndStopsAsking() =
        runTest(main) {
            val sides = RecordedSides()
            val here = WatchProgress(3, 22.minutes, 24.minutes, null, 300)
            val viewModel = detailsViewModel(progress = flowOf(here), listed = ListedProgress(195516, 1, 500, 24), sides = sides)
            assertIs<PickUp.Ask>(assertIs<DetailsState.Loaded>(viewModel.settled()).pickUp)

            viewModel.keepThisTv()
            advanceUntilIdle()

            assertEquals(listOf(Triple(195516, 3, 1)), sides.moved)
            assertEquals(PickUp.At(here), assertIs<DetailsState.Loaded>(viewModel.state.value).pickUp)
        }

    private val demo = InstalledSource("com.example.demo", "Demo source", "1.0.0")
    private val lastDub = LastServer(demo.id, "Server 1", ServerEdition.DUB, audio = "English")

    /** The request Details' main button plays [episode] with on [lastDub], carrying on from [place] (#499). */
    private fun playing(
        episode: Int,
        place: WatchProgress?,
        loaded: DetailsState.Loaded,
    ) = NextPlay.Play(
        PlayRequest(
            loaded.details,
            episode,
            loaded.episodes
                ?.episodes
                ?.firstOrNull { it.number == episode }
                ?.title,
            listOf(PlayServer(demo, FoundServer("", "Server 1", ServerEdition.DUB, null), 1)),
            0,
            loaded.backdrop,
            loaded.episodes,
            place,
            audio = "English",
            findServer = true,
            resume = true,
        ),
    )

    @Test
    fun resumePlaysTheEpisodeOnTheShowsLastServer() =
        runTest(main) {
            val here = WatchProgress(1, 12.minutes, 24.minutes, null, 300)
            val viewModel = detailsViewModel(progress = flowOf(here), lastServer = lastDub, installed = listOf(demo))
            val loaded = assertIs<DetailsState.Loaded>(viewModel.settled())

            assertEquals(playing(1, here, loaded), viewModel.play())
        }

    @Test
    fun anEpisodeWatchedToTheEndPlaysTheNextOne() =
        runTest(main) {
            val here = WatchProgress(1, 23.minutes, 24.minutes, null, 300)
            val viewModel = detailsViewModel(progress = flowOf(here), lastServer = lastDub, installed = listOf(demo))
            val loaded = assertIs<DetailsState.Loaded>(viewModel.settled())

            assertEquals(playing(2, here, loaded), viewModel.play())
        }

    @Test
    fun withNoLastServerTheEpisodesServersOpen() =
        runTest(main) {
            val viewModel = detailsViewModel(progress = flowOf(WatchProgress(1, 12.minutes, 24.minutes, null)), installed = listOf(demo))
            viewModel.settled()

            assertEquals(NextPlay.Choose(1), viewModel.play())
        }

    @Test
    fun aLastServerWhoseSourceIsGoneOrBrokenOpensTheServers() =
        runTest(main) {
            val here = WatchProgress(1, 12.minutes, 24.minutes, null)
            val gone = detailsViewModel(progress = flowOf(here), lastServer = lastDub, installed = emptyList())
            val broken =
                detailsViewModel(
                    progress = flowOf(here),
                    lastServer = lastDub,
                    installed = listOf(demo.copy(problem = SourceProblem.entries.first())),
                )
            gone.settled()
            broken.settled()

            assertEquals(NextPlay.Choose(1), gone.play())
            assertEquals(NextPlay.Choose(1), broken.play())
        }

    // Nothing watched yet: Play starts the first episode, on the servers page as no server is known.
    @Test
    fun playWithNothingWatchedOpensTheFirstEpisodesServers() =
        runTest(main) {
            val viewModel = detailsViewModel(installed = listOf(demo))
            viewModel.settled()

            assertEquals(NextPlay.Choose(1), viewModel.play())
        }

    @Test
    fun afterPickingAniListItsPlacePlays() =
        runTest(main) {
            val here = WatchProgress(1, 12.minutes, 24.minutes, null, 300)
            val viewModel =
                detailsViewModel(
                    progress = flowOf(here),
                    listed = ListedProgress(195516, 2, 500, 24),
                    sides = RecordedSides(),
                    lastServer = lastDub,
                    installed = listOf(demo),
                )
            val asked = assertIs<PickUp.Ask>(assertIs<DetailsState.Loaded>(viewModel.settled()).pickUp)

            viewModel.keepAniList()

            val loaded = assertIs<DetailsState.Loaded>(viewModel.state.value)
            assertEquals(playing(3, asked.aniList, loaded), viewModel.play())
        }

    @Test
    fun afterPickingThisTvItsPlacePlays() =
        runTest(main) {
            val here = WatchProgress(3, 12.minutes, 24.minutes, null, 300)
            val viewModel =
                detailsViewModel(
                    progress = flowOf(here),
                    listed = ListedProgress(195516, 1, 500, 24),
                    sides = RecordedSides(),
                    lastServer = lastDub,
                    installed = listOf(demo),
                )
            assertIs<PickUp.Ask>(assertIs<DetailsState.Loaded>(viewModel.settled()).pickUp)

            viewModel.keepThisTv()

            val loaded = assertIs<DetailsState.Loaded>(viewModel.state.value)
            assertEquals(playing(3, here, loaded), viewModel.play())
        }

    // The signed-in user's entry for the show, or none signed out.
    private class FakeMarks(
        var progress: Int? = 3,
        val signedIn: Boolean = true,
        var works: Boolean = true,
    ) : WatchedEpisodes {
        val sent = MutableSharedFlow<ListChange>(extraBufferCapacity = 8)
        override val changes = sent
        val calls = mutableListOf<String>()
        val titles = mutableListOf<AnimeTitle?>()

        override suspend fun entry(animeId: Int) =
            if (signedIn) OnYourList(1, ShowEntry(12, progress?.let { ListEntry(ListStatus.WATCHING, it, true) })) else null

        override suspend fun changeStatus(
            animeId: Int,
            to: ListStatus,
            forUser: Int,
        ) = error("Not used here")

        override suspend fun remove(
            animeId: Int,
            forUser: Int,
        ) = error("Not used here")

        override suspend fun markWatched(
            animeId: Int,
            upTo: Int,
            forUser: Int,
            title: AnimeTitle?,
        ): Boolean {
            titles += title
            calls += "watched $upTo for $forUser"
            return works
        }

        override suspend fun markUnwatched(
            animeId: Int,
            episode: Int,
            forUser: Int,
            title: AnimeTitle?,
        ): Boolean {
            titles += title
            calls += "unwatched $episode for $forUser"
            return works
        }
    }

    private suspend fun TestScope.loaded(viewModel: DetailsViewModel): DetailsState.Loaded {
        viewModel.settled()
        advanceUntilIdle()
        return assertIs<DetailsState.Loaded>(viewModel.state.value)
    }

    // ADR 0052: signed in, AniList's progress marks the episodes. This TV adds only its part-watched episode past it.
    @Test
    fun signedInTheMarksComeFromAniList() =
        runTest(main) {
            val here = WatchProgress(6, 24.minutes, 24.minutes, null)
            val loaded = loaded(detailsViewModel(progress = flowOf(here), marks = FakeMarks()))

            assertEquals(AniListWatched(userId = 1, progress = 3), loaded.aniList)
            assertEquals(EpisodeMark.Watched, loaded.markFor(3))
            assertNull(loaded.markFor(4))
            assertNull(loaded.markFor(6))
        }

    @Test
    fun signedInThisTvsPartWatchedEpisodeStillShows() =
        runTest(main) {
            val here = WatchProgress(5, 12.minutes, 24.minutes, null)
            val loaded = loaded(detailsViewModel(progress = flowOf(here), marks = FakeMarks()))

            assertEquals(EpisodeMark.InProgress(here), loaded.markFor(5))
        }

    @Test
    fun aShowNotOnTheListHasNoneWatched() =
        runTest(main) {
            val here = WatchProgress(2, 24.minutes, 24.minutes, null)
            val loaded = loaded(detailsViewModel(progress = flowOf(here), marks = FakeMarks(progress = null)))

            assertNull(loaded.markFor(1))
        }

    @Test
    fun signedOutTheMarksComeFromThisTv() =
        runTest(main) {
            val here = WatchProgress(3, 24.minutes, 24.minutes, null)
            val loaded = loaded(detailsViewModel(progress = flowOf(here), marks = FakeMarks(signedIn = false)))

            assertNull(loaded.aniList)
            assertEquals(EpisodeMark.Watched, loaded.markFor(3))
        }

    @Test
    fun markingWatchedMovesAniListAndThisTv() =
        runTest(main) {
            val marks = FakeMarks()
            val sides = RecordedSides()
            val here = WatchProgress(2, 24.minutes, 24.minutes, null)
            val viewModel = detailsViewModel(progress = flowOf(here), sides = sides, marks = marks)
            loaded(viewModel)

            viewModel.markWatched(8)
            advanceUntilIdle()

            assertEquals(listOf("watched 8 for 1"), marks.calls)
            assertEquals(8, assertIs<DetailsState.Loaded>(viewModel.state.value).aniList?.progress)
            assertEquals(listOf(195516 to WatchProgress(8, 24.minutes, 24.minutes, null)), sides.saved)
        }

    // This TV further on keeps its place.
    @Test
    fun markingWatchedLeavesThisTvFurtherOn() =
        runTest(main) {
            val sides = RecordedSides()
            val here = WatchProgress(9, 12.minutes, 24.minutes, null)
            val viewModel = detailsViewModel(progress = flowOf(here), sides = sides, marks = FakeMarks())
            loaded(viewModel)

            viewModel.markWatched(8)
            advanceUntilIdle()

            assertEquals(emptyList(), sides.saved)
        }

    // #512: the show's title goes with each mark, so a notice can say which one wasn't saved.
    @Test
    fun aMarkAniListDoesntTakeGoesBack() =
        runTest(main) {
            val sides = RecordedSides()
            val marks = FakeMarks(works = false)
            val viewModel = detailsViewModel(sides = sides, marks = marks)
            val title = loaded(viewModel).details.card.title

            viewModel.markWatched(8)
            advanceUntilIdle()
            viewModel.markUnwatched(2)
            advanceUntilIdle()

            assertEquals(3, assertIs<DetailsState.Loaded>(viewModel.state.value).aniList?.progress)
            assertEquals(emptyList(), sides.saved)
            assertEquals(listOf<AnimeTitle?>(title, title), marks.titles)
        }

    @Test
    fun markingUnwatchedGoesBackToTheEpisodeBefore() =
        runTest(main) {
            val marks = FakeMarks()
            val sides = RecordedSides()
            val here = WatchProgress(5, 10.minutes, 24.minutes, null)
            val viewModel = detailsViewModel(progress = flowOf(here), sides = sides, marks = marks)
            loaded(viewModel)

            viewModel.markUnwatched(2)
            advanceUntilIdle()

            assertEquals(listOf("unwatched 2 for 1"), marks.calls)
            assertEquals(1, assertIs<DetailsState.Loaded>(viewModel.state.value).aniList?.progress)
            assertEquals(listOf(195516 to WatchProgress(2, Duration.ZERO, 24.minutes, null)), sides.saved)
        }

    // Marks that wouldn't change anything aren't sent.
    @Test
    fun onlyAnUnwatchedEpisodeCanBeMarkedWatchedAndTheOtherWayRound() =
        runTest(main) {
            val marks = FakeMarks()
            val viewModel = detailsViewModel(marks = marks)
            loaded(viewModel)

            viewModel.markWatched(3)
            viewModel.markUnwatched(4)
            advanceUntilIdle()

            assertEquals(emptyList(), marks.calls)
        }

    @Test
    fun aniListsPlaceMovesWithAMark() =
        runTest(main) {
            // Four episodes are out.
            val listed = ListedProgress(195516, 1, 500, 24, userId = 1)
            val viewModel = detailsViewModel(listed = listed, marks = FakeMarks(progress = 1))
            loaded(viewModel)

            viewModel.markWatched(3)
            advanceUntilIdle()

            val loaded = assertIs<DetailsState.Loaded>(viewModel.state.value)
            assertEquals(3, loaded.listed?.progress)
            assertIs<PickUp.At>(loaded.pickUp)
            assertEquals(4, loaded.upNext?.episode)
        }

    @Test
    fun aChangeMadeElsewhereOnTheTvMovesTheMarks() =
        runTest(main) {
            val marks = FakeMarks()
            val viewModel = detailsViewModel(marks = marks)
            loaded(viewModel)

            marks.sent.emit(ListChange(195516, ListStatus.WATCHING, userId = 1, progress = 7))
            marks.sent.emit(ListChange(195516, ListStatus.WATCHING, userId = 2, progress = 9))
            marks.sent.emit(ListChange(1, ListStatus.WATCHING, userId = 1, progress = 9))
            advanceUntilIdle()
            assertEquals(7, assertIs<DetailsState.Loaded>(viewModel.state.value).aniList?.progress)

            marks.sent.emit(ListChange(195516, null, userId = 1))
            advanceUntilIdle()
            assertEquals(0, assertIs<DetailsState.Loaded>(viewModel.state.value).aniList?.progress)
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
