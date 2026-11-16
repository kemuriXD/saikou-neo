package ani.saikou.neo.shared.schedule

import ani.saikou.neo.shared.catalogue.AnimeScheduleError
import ani.saikou.neo.shared.catalogue.AnimeScheduleResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class WeekCacheTest {
    private var now = Instant.parse("2026-11-18T12:00:00Z")
    private var answer: AnimeScheduleResult = AnimeScheduleResult.Success(emptyList())
    private val asked = mutableListOf<String>()
    private val cache =
        WeekCache(fetch = { zone ->
            asked += zone
            answer
        }, now = { now })

    @Test
    fun theWeekIsKeptForFifteenMinutes() =
        runTest {
            cache.week(TestZone(0))
            now += 14.minutes
            cache.week(TestZone(0))
            assertEquals(1, asked.size)

            now += 2.minutes
            cache.week(TestZone(0))
            assertEquals(2, asked.size)
        }

    @Test
    fun aNewWeekIsFetchedEvenWithinFifteenMinutes() =
        runTest {
            now = Instant.parse("2026-11-22T23:55:00Z")
            cache.week(TestZone(0))
            now = Instant.parse("2026-11-23T00:05:00Z")
            cache.week(TestZone(0))

            assertEquals(2, asked.size)
        }

    @Test
    fun aFailureIsNotKept() =
        runTest {
            answer = AnimeScheduleResult.Failure(AnimeScheduleError.Network(null))
            cache.week(TestZone(0))
            answer = AnimeScheduleResult.Success(emptyList())

            assertEquals(AnimeScheduleResult.Success(emptyList()), cache.week(TestZone(0)))
            assertEquals(2, asked.size)
        }

    @Test
    fun theWeekIsAskedForInTheDevicesZone() =
        runTest {
            cache.week(TestZone(9))

            assertEquals(listOf("Test/Zone"), asked)
        }
}
