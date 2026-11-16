package ani.saikou.neo.shared.schedule

import ani.saikou.neo.shared.account.AccountStore
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.account.AniListToken
import ani.saikou.neo.shared.account.SignInAttempt
import ani.saikou.neo.shared.account.SignedInAccount
import ani.saikou.neo.shared.account.WatchListShow
import ani.saikou.neo.shared.account.WatchListStatus
import ani.saikou.neo.shared.catalogue.AirType
import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeScheduleError
import ani.saikou.neo.shared.catalogue.AnimeScheduleResult
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.catalogue.LoadProblem
import ani.saikou.neo.shared.catalogue.ShowLookup
import ani.saikou.neo.shared.settings.ScheduleSettings
import ani.saikou.neo.shared.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {
    private val main = StandardTestDispatcher()

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    // Wednesday 18 November 2026.
    private val now = Instant.parse("2026-11-18T12:00:00Z")

    private class FakeSource : ScheduleSource {
        var week: AnimeScheduleResult =
            AnimeScheduleResult.Success(
                listOf(
                    episode("frieren", 5, AirType.SUB, "2026-11-16T15:00:00Z"),
                    episode("psyren", 2, AirType.RAW, "2026-11-18T10:00:00Z"),
                    episode("one-piece", 1100, AirType.SUB, "2026-11-18T01:00:00Z"),
                ),
            )
        var list: AniListResult<List<WatchListShow>> =
            AniListResult.Success(listOf(show(154587, airing = true), show(21, airing = true), show(1, airing = false)))
        val routes = mutableMapOf<Int, ShowLookup<String>>(154587 to ShowLookup.Found("frieren"), 21 to ShowLookup.Found("one-piece"))
        val ids = mutableMapOf<String, ShowLookup<Int>>("psyren" to ShowLookup.Found(9))
        val routesAsked = mutableListOf<Int>()
        val idsAsked = mutableListOf<String>()
        var weeksAsked = 0

        override suspend fun week(zone: ScheduleZone): AnimeScheduleResult {
            weeksAsked++
            return week
        }

        override suspend fun watchList(account: SignedInAccount) = list

        override suspend fun routeFor(aniListId: Int): ShowLookup<String> {
            routesAsked += aniListId
            return routes[aniListId] ?: ShowLookup.NoMatch
        }

        override suspend fun aniListIdFor(route: String): ShowLookup<Int> {
            idsAsked += route
            return ids[route] ?: ShowLookup.NoMatch
        }
    }

    private class Accounts(
        signedIn: Boolean,
    ) : AccountStore {
        private val account = SignedInAccount(AniListProfile(7, "Hikari", null), AniListToken("t", 9_000)).takeIf { signedIn }

        override fun readAccount() = account

        override fun saveAccount(account: SignedInAccount) = Unit

        override fun clearAccount() = Unit

        override fun readAttempt(): SignInAttempt? = null

        override fun saveAttempt(attempt: SignInAttempt) = Unit

        override fun clearAttempt() = Unit
    }

    private class Filters : ScheduleFilterStore {
        var saved: ScheduleFilter? = null

        override fun readFilter() = saved

        override fun saveFilter(filter: ScheduleFilter) {
            saved = filter
        }
    }

    private val source = FakeSource()
    private val filters = Filters()
    private val settings = MutableStateFlow(Settings())

    private fun model(signedIn: Boolean) =
        ScheduleViewModel(source, Accounts(signedIn), filters, settings, TestZone(0), now = { now }, io = main)

    private fun ScheduleViewModel.ready() = assertIs<ScheduleState.Ready>(state.value)

    private fun ScheduleState.Ready.routes() = days.flatMap { day -> day.rows.map { it.route } }

    @Test
    fun signedOutShowsAllShowsWithoutAToggle() =
        runTest(main) {
            val model = model(signedIn = false)
            assertEquals(ScheduleState.Loading, model.state.value)
            advanceUntilIdle()

            val ready = model.ready()
            assertNull(ready.filter)
            assertEquals(listOf("frieren", "one-piece", "psyren"), ready.routes())
            assertEquals(emptyList(), source.routesAsked)
        }

    @Test
    fun signedInStartsOnYourListAndOnlyLooksUpAiringShows() =
        runTest(main) {
            val model = model(signedIn = true)
            advanceUntilIdle()

            val ready = model.ready()
            assertEquals(ScheduleFilter.YOUR_LIST, ready.filter)
            assertEquals(listOf("frieren", "one-piece"), ready.routes())
            assertEquals(listOf(154587, 21), source.routesAsked)
        }

    @Test
    fun theToggleSwitchesTheRowsAndCountsAndIsRemembered() =
        runTest(main) {
            val model = model(signedIn = true)
            advanceUntilIdle()
            assertEquals(
                1,
                model
                    .ready()
                    .days[2]
                    .rows.size,
            )

            model.showFilter(ScheduleFilter.ALL_SHOWS)
            advanceUntilIdle()
            assertEquals(ScheduleFilter.ALL_SHOWS, model.ready().filter)
            assertEquals(
                2,
                model
                    .ready()
                    .days[2]
                    .rows.size,
            )
            assertEquals(ScheduleFilter.ALL_SHOWS, filters.saved)

            val again = model(signedIn = true)
            advanceUntilIdle()
            assertEquals(ScheduleFilter.ALL_SHOWS, again.ready().filter)
        }

    @Test
    fun filteredShowsAreLeftOutFromTheStart() =
        runTest(main) {
            settings.value = Settings(schedule = ScheduleSettings(rawEpisodes = false))
            val model = model(signedIn = false)
            advanceUntilIdle()

            assertEquals(listOf("frieren", "one-piece"), model.ready().routes())
        }

    @Test
    fun aChangedSettingRefiltersTheWeekWithoutAskingAgain() =
        runTest(main) {
            val model = model(signedIn = false)
            advanceUntilIdle()
            model.selectDay(4)
            assertEquals(
                2,
                model
                    .ready()
                    .days[2]
                    .rows.size,
            )

            settings.value = Settings(schedule = ScheduleSettings(rawEpisodes = false))
            advanceUntilIdle()

            assertEquals(
                1,
                model
                    .ready()
                    .days[2]
                    .rows.size,
            )
            assertEquals(listOf("frieren", "one-piece"), model.ready().routes())
            assertEquals(4, model.ready().selectedDay)
            assertEquals(1, source.weeksAsked)
        }

    @Test
    fun theWeekStartsOnToday() =
        runTest(main) {
            val model = model(signedIn = false)
            advanceUntilIdle()
            assertEquals(2, model.ready().today)
            assertEquals(2, model.ready().selectedDay)

            model.selectDay(0)
            assertEquals(0, model.ready().selectedDay)
        }

    @Test
    fun aShowThatCantBeMatchedIsLeftOffYourList() =
        runTest(main) {
            source.routes[21] = ShowLookup.Failure(AnimeScheduleError.Network(null))
            val model = model(signedIn = true)
            advanceUntilIdle()

            assertEquals(listOf("frieren"), model.ready().routes())
        }

    @Test
    fun withoutATokenScheduleIsNotAvailable() =
        runTest(main) {
            source.week = AnimeScheduleResult.Failure(AnimeScheduleError.NoToken)
            val model = model(signedIn = true)
            advanceUntilIdle()

            assertEquals(ScheduleState.NoToken, model.state.value)
        }

    @Test
    fun aFailedWeekCanBeTriedAgain() =
        runTest(main) {
            source.week = AnimeScheduleResult.Failure(AnimeScheduleError.Network(null))
            val model = model(signedIn = false)
            advanceUntilIdle()
            assertEquals(ScheduleState.Failed(LoadProblem.NO_NETWORK), model.state.value)

            source.week = AnimeScheduleResult.Success(emptyList())
            model.retry()
            assertEquals(ScheduleState.Loading, model.state.value)
            advanceUntilIdle()
            assertEquals(7, model.ready().days.size)
        }

    @Test
    fun aFailedListFailsTheScreen() =
        runTest(main) {
            source.list = AniListResult.Failure(AniListError.Network(null))
            val model = model(signedIn = true)
            advanceUntilIdle()

            assertEquals(ScheduleState.Failed(LoadProblem.NO_NETWORK), model.state.value)
        }

    @Test
    fun aRefusedTokenShowsAllShowsAsWhenSignedOut() =
        runTest(main) {
            source.list = AniListResult.Failure(AniListError.SignedOut)
            val model = model(signedIn = true)
            advanceUntilIdle()

            assertNull(model.ready().filter)
            assertEquals(3, model.ready().routes().size)
        }

    @Test
    fun aRowOnYourListOpensWithoutAskingAgain() =
        runTest(main) {
            val model = model(signedIn = true)
            advanceUntilIdle()

            model.open(
                model
                    .ready()
                    .days[0]
                    .rows
                    .single(),
            )
            advanceUntilIdle()

            assertEquals(ScheduleEvent.OpenDetails(154587), model.events.first())
            assertEquals(emptyList(), source.idsAsked)
        }

    @Test
    fun anyOtherRowLooksUpItsAniListId() =
        runTest(main) {
            val model = model(signedIn = false)
            advanceUntilIdle()

            model.open(
                model
                    .ready()
                    .days[2]
                    .rows
                    .first { it.route == "psyren" },
            )
            advanceUntilIdle()

            assertEquals(ScheduleEvent.OpenDetails(9), model.events.first())
        }

    @Test
    fun aRowWithoutAMatchCantBeOpened() =
        runTest(main) {
            source.ids["psyren"] = ShowLookup.NoMatch
            val model = model(signedIn = false)
            advanceUntilIdle()
            val psyren =
                model
                    .ready()
                    .days[2]
                    .rows
                    .first { it.route == "psyren" }

            model.open(psyren)
            advanceUntilIdle()
            assertEquals(ScheduleEvent.CantOpen(tryAgain = false), model.events.first())

            source.ids["psyren"] = ShowLookup.Failure(AnimeScheduleError.Network(null))
            model.open(psyren)
            advanceUntilIdle()
            assertEquals(ScheduleEvent.CantOpen(tryAgain = true), model.events.first())
        }
}

private fun show(
    id: Int,
    airing: Boolean,
) = WatchListShow(id, AnimeTitle(romaji = "Show $id", english = null, native = null), null, WatchListStatus.WATCHING, airing)
