package ani.saikou.neo.shared.schedule

import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AirType
import ani.saikou.neo.shared.catalogue.AiringStatus
import ani.saikou.neo.shared.catalogue.TimetableEpisode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal fun episode(
    route: String,
    number: Int?,
    type: AirType,
    at: String,
    status: AiringStatus = AiringStatus.UNAIRED,
): TimetableEpisode =
    TimetableEpisode(
        route = route,
        title = route.replaceFirstChar { it.uppercase() },
        romaji = null,
        english = null,
        episodeNumber = number,
        airType = type,
        episodeDate = Instant.parse(at),
        delayedFrom = null,
        delayedUntil = null,
        airingStatus = status,
        donghua = false,
        imageUrl = null,
    )

/** A zone [offsetHours] ahead of UTC, or [laterOffsetHours] from [changesAt] on, like a clock change. */
internal class TestZone(
    private val offsetHours: Int,
    private val laterOffsetHours: Int = offsetHours,
    private val changesAt: Instant = Instant.DISTANT_FUTURE,
) : ScheduleZone {
    override val id: String = "Test/Zone"

    override fun offsetSecondsAt(instant: Instant): Int = (if (instant < changesAt) offsetHours else laterOffsetHours) * 3_600
}

class ScheduleWeekTest {
    @Test
    fun aRowUsesTheSubTimeAndShowsTheSameEpisodesDub() {
        val rows =
            scheduleRows(
                listOf(
                    episode("frieren", 5, AirType.RAW, "2026-11-18T14:00:00Z"),
                    episode("frieren", 5, AirType.SUB, "2026-11-18T15:30:00Z"),
                    episode("frieren", 5, AirType.DUB, "2026-11-21T18:00:00Z"),
                ),
            )

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(AirType.SUB, row.release)
        assertEquals(Instant.parse("2026-11-18T15:30:00Z"), row.airsAt)
        assertEquals(Instant.parse("2026-11-21T18:00:00Z"), row.dubAt)
        assertEquals(5, row.episodeNumber)
    }

    @Test
    fun aRowWithoutASubUsesTheRawTime() {
        val row = scheduleRows(listOf(episode("psyren", 2, AirType.RAW, "2026-11-17T10:00:00Z"))).single()

        assertEquals(AirType.RAW, row.release)
        assertEquals(Instant.parse("2026-11-17T10:00:00Z"), row.airsAt)
        assertEquals(null, row.dubAt)
    }

    @Test
    fun aDubOfAnotherEpisodeGetsItsOwnRow() {
        val rows =
            scheduleRows(
                listOf(
                    episode("frieren", 5, AirType.SUB, "2026-11-18T15:30:00Z"),
                    episode("frieren", 2, AirType.DUB, "2026-11-19T18:00:00Z"),
                ),
            )

        assertEquals(listOf(AirType.SUB to 5, AirType.DUB to 2), rows.map { it.release to it.episodeNumber })
        assertEquals(null, rows[0].dubAt)
    }

    @Test
    fun rowsAreInAirTimeOrder() {
        val rows =
            scheduleRows(
                listOf(
                    episode("late", 1, AirType.SUB, "2026-11-18T20:00:00Z"),
                    episode("early", 1, AirType.SUB, "2026-11-18T08:00:00Z"),
                    episode("noon", 1, AirType.RAW, "2026-11-18T12:00:00Z"),
                ),
            )

        assertEquals(listOf("early", "noon", "late"), rows.map { it.route })
    }

    @Test
    fun aDelayedEpisodeSaysSo() {
        val row = scheduleRows(listOf(episode("x", 1, AirType.SUB, "2026-11-18T08:00:00Z", AiringStatus.DELAYED))).single()

        assertEquals(true, row.delayed)
    }

    @Test
    fun theWeekRunsMondayToSundayInTheZoneAndStartsOnToday() {
        // Wednesday 18 November, 15:30 UTC is already Thursday the 19th in UTC+9.
        val now = Instant.parse("2026-11-18T15:30:00Z")
        val zone = TestZone(9)
        val rows =
            scheduleRows(
                listOf(
                    episode("monday", 1, AirType.SUB, "2026-11-16T01:00:00Z"),
                    // Sunday 15th 20:00 UTC is Monday 16th 05:00 in UTC+9.
                    episode("sunday-utc", 1, AirType.SUB, "2026-11-15T20:00:00Z"),
                    episode("sunday", 1, AirType.SUB, "2026-11-22T12:00:00Z"),
                ),
            )

        val week = scheduleWeek(rows, zone, now)

        assertEquals((16..22).map { AirDate(2026, 11, it) }, week.days.map { it.date })
        assertEquals(3, week.today)
        assertEquals(listOf("sunday-utc", "monday"), week.days[0].rows.map { it.route })
        assertEquals(listOf("sunday"), week.days[6].rows.map { it.route })
    }

    @Test
    fun eachTimeUsesTheZonesOffsetAtThatTime() {
        // The clock goes back an hour on Wednesday, so a 23:30 show on Thursday is still Thursday.
        val zone = TestZone(1, laterOffsetHours = 0, changesAt = Instant.parse("2026-11-18T00:00:00Z"))
        val rows = scheduleRows(listOf(episode("late", 1, AirType.SUB, "2026-11-19T23:30:00Z")))

        val week = scheduleWeek(rows, zone, Instant.parse("2026-11-17T12:00:00Z"))

        assertEquals(listOf("late"), week.days[3].rows.map { it.route })
    }

    @Test
    fun aRowIsOutOnceItsTimeHasPassed() {
        val at = Instant.parse("2026-11-18T15:30:00Z")

        assertEquals(Countdown.In(2.hours + 30.minutes), countdown(at, now = Instant.parse("2026-11-18T13:00:00Z")))
        assertEquals(Countdown.Out, countdown(at, now = at))
        assertEquals(Countdown.Out, countdown(at, now = at + 1.hours))
    }
}
