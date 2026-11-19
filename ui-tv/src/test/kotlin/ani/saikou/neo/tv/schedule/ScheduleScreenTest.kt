package ani.saikou.neo.tv.schedule

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AirType
import ani.saikou.neo.shared.catalogue.LoadProblem
import ani.saikou.neo.shared.schedule.ScheduleDay
import ani.saikou.neo.shared.schedule.ScheduleEvent
import ani.saikou.neo.shared.schedule.ScheduleFilter
import ani.saikou.neo.shared.schedule.ScheduleRow
import ani.saikou.neo.shared.schedule.ScheduleState
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class ScheduleScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val savedZone = TimeZone.getDefault()
    private val savedLocale = Locale.getDefault()

    @Before
    fun utc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.UK)
    }

    @After
    fun restore() {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }

    // Monday 26 October 2026, 00:00 UTC. "Now" is Wednesday 28 October at 15:16.
    private val monday = Instant.fromEpochSeconds(1_792_972_800)
    private val now = monday + 2.days + 15.hours + 16.minutes

    private fun row(
        route: String,
        title: String,
        at: Instant,
        episode: Int? = 1,
        release: AirType = AirType.SUB,
        dubAt: Instant? = null,
        delayed: Boolean = false,
    ) = ScheduleRow(route, title, imageUrl = null, episodeNumber = episode, release, at, dubAt, delayed)

    private val kaiju = row("kaiju", "Kaiju No. 8", monday + 2.days + 12.hours, episode = 10, dubAt = monday + 4.days + 18.hours)
    private val dandadan = row("dandadan", "Dandadan", monday + 2.days + 17.hours + 30.minutes, episode = 4)
    private val solo = row("solo", "Solo Leveling", monday + 2.days + 23.hours, episode = 8, release = AirType.DUB)
    private val blueLock = row("blue-lock", "Blue Lock", monday + 2.days + 23.hours + 30.minutes, episode = 3, delayed = true)

    private fun week(
        today: List<ScheduleRow> = listOf(kaiju, dandadan, solo, blueLock),
        filter: ScheduleFilter? = ScheduleFilter.YOUR_LIST,
        selected: Int = 2,
    ) = ScheduleState.Ready(
        days =
            List(7) { day ->
                // 26 October to 1 November.
                val date = if (day < 6) AirDate(2026, 10, 26 + day) else AirDate(2026, 11, 1)
                ScheduleDay(date, if (day == 2) today else List(day) { row("r$day-$it", "Show $it", monday + day.days + it.hours) })
            },
        today = 2,
        selectedDay = selected,
        filter = filter,
    )

    private val days = mutableListOf<Int>()
    private val filters = mutableListOf<ScheduleFilter>()
    private val opened = mutableListOf<ScheduleRow>()
    private var retried = 0
    private var wentHome = 0

    private fun show(
        state: ScheduleState = week(),
        cantOpen: ScheduleEvent.CantOpen? = null,
    ) {
        Settings.System.putString(compose.activity.contentResolver, Settings.System.TIME_12_24, "24")
        compose.setContent {
            NeoTheme {
                ScheduleScreen(
                    state,
                    actions =
                        ScheduleActions(
                            onSelectDay = { days += it },
                            onFilter = { filters += it },
                            onOpen = { opened += it },
                            onRetry = { retried++ },
                            onHome = { wentHome++ },
                        ),
                    cantOpen = cantOpen,
                    clock = { now },
                )
            }
        }
    }

    @Test
    fun theWeekShowsEachDayWithItsEpisodeCountAndTodayPicked() {
        show()

        compose.onNodeWithText("Airing this week").assertExists()
        compose.onNodeWithText("Mon").assertExists()
        compose.onNodeWithTag(dayTag(2)).assertTextEquals("Today", "28", "4 eps")
        compose.onNodeWithTag(dayTag(1)).assertTextEquals("Tue", "27", "1 ep")
    }

    // It's 15:16, so today opens on Dandadan at 17:30, just after Kaiju No. 8 at 12:00.
    @Test
    fun todayOpensOnTheNextEpisodeToAir() {
        show()

        compose.onNodeWithTag(rowTag(dandadan)).assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag(rowTag(kaiju)).assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag(dayTag(2)).assertIsFocused()
    }

    @Test
    fun downFromNowGoesToLaterEpisodes() {
        show()

        press(Key.DirectionDown)

        compose.onNodeWithTag(rowTag(solo)).assertIsFocused()
    }

    @Test
    fun onceTodayHasAiredItOpensOnTheLastEpisode() {
        show(week(today = listOf(kaiju)))

        compose.onNodeWithTag(rowTag(kaiju)).assertIsFocused()
    }

    // Another day opens on its tab, and its list starts at its first episode.
    @Test
    fun anotherDayStartsAtTheTop() {
        show(week(selected = 4))

        compose.onNodeWithTag(dayTag(4)).assertIsFocused()
        press(Key.DirectionDown)

        compose.onNodeWithTag(rowTag(row("r4-0", "Show 0", monday + 4.days))).assertIsFocused()
    }

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun aDayListsItsEpisodesByAirTimeInTheTvsZone() {
        show()

        compose.onNodeWithText("12:00").assertExists()
        compose.onNodeWithText("17:30").assertExists()
        compose.onNodeWithText("Kaiju No. 8").assertExists()
    }

    @Test
    fun anEpisodeThatsOutSaysSoAndOneStillToComeCountsDown() {
        show()

        compose.onNodeWithText("Out now").assertExists()
        compose.onNodeWithText("Episode 10 · Sub out now, dub Friday").assertExists()
        compose.onNodeWithText("Episode 4 · Sub · in 2h 14m").assertExists()
        compose.onNodeWithText("Wed 28 Oct · 17:30").assertExists()
        compose.onNodeWithText("Episode 8 · Dub · in 7h 44m").assertExists()
        compose.onNodeWithText("Delayed").assertExists()
    }

    @Test
    fun movingAlongTheDaysPicksEach() {
        show()

        press(Key.DirectionUp, times = 2)
        press(Key.DirectionRight)

        compose.runOnIdle { assertEquals(3, days.last()) }
    }

    @Test
    fun selectingARowOpensIt() {
        show()

        press(Key.DirectionCenter)

        compose.runOnIdle { assertEquals(listOf(dandadan), opened) }
    }

    @Test
    fun signedInTheToggleSwitchesToAllShows() {
        show()

        compose.onNodeWithTag(filterTag(ScheduleFilter.ALL_SHOWS)).assertExists()
        press(Key.DirectionUp, times = 2)
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionCenter)
        }

        compose.runOnIdle { assertEquals(ScheduleFilter.ALL_SHOWS, filters.last()) }
    }

    @Test
    fun signedOutThereIsNoToggle() {
        show(week(filter = null))

        compose.onNodeWithTag(filterTag(ScheduleFilter.ALL_SHOWS)).assertDoesNotExist()
        compose.onNodeWithText("Times in your time zone · Sign in from your phone to see just your list").assertExists()
    }

    @Test
    fun aDayWithNothingFromYourListOffersAllShows() {
        show(week(today = emptyList()))

        compose.onNodeWithText("Nothing from your list today").assertExists()
        compose.onNodeWithText("See all shows").assertExists()
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionCenter)
        }

        compose.runOnIdle { assertEquals(ScheduleFilter.ALL_SHOWS, filters.last()) }
    }

    @Test
    fun aDayWithNothingAtAllHasNoButton() {
        show(week(today = emptyList(), filter = ScheduleFilter.ALL_SHOWS))

        compose.onNodeWithText("Nothing airing today").assertExists()
        compose.onNodeWithText("See all shows").assertDoesNotExist()
    }

    @Test
    fun whenItFailsTryAgainLoadsItAgain() {
        show(ScheduleState.Failed(LoadProblem.NO_NETWORK))

        compose.onNodeWithText("Schedule couldn't load").assertExists()
        compose.onNodeWithText("Check the TV's connection, then try again.").assertExists()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, retried) }

        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionCenter)
        }
        compose.runOnIdle { assertEquals(1, wentHome) }
    }

    @Test
    fun withoutATokenScheduleSaysItIsntAvailable() {
        show(ScheduleState.NoToken)

        compose.onNodeWithText("Schedule isn't available").assertExists()
    }

    @Test
    fun whileLoadingRowsPulseInPlace() {
        show(ScheduleState.Loading)

        compose.onNodeWithTag(SCHEDULE_LOADING_TAG).assertExists()
    }

    @Test
    fun aShowWithNoAniListMatchSaysItCantBeOpened() {
        show(cantOpen = ScheduleEvent.CantOpen(tryAgain = false))

        compose.onNodeWithText("This show isn't on AniList yet, so it can't be opened.").assertExists()
    }
}
