package ani.saikou.neo.tv.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class EpisodeDayTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")

    // 30 October 2026, 23:00 in Tokyo.
    private val now = ZonedDateTime.of(2026, 10, 30, 23, 0, 0, 0, tokyo)

    private fun at(
        day: Int,
        hour: Int,
    ) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, tokyo).toEpochSecond()

    @Test
    fun countsCalendarDaysInTheTvsZoneNotHours() {
        assertEquals(0L, daysUntil(at(30, 23) + 30 * 60, now))
        // Two hours away, but after midnight.
        assertEquals(1L, daysUntil(at(31, 1), now))
        assertEquals(3L, daysUntil(ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, tokyo).toEpochSecond(), now))
    }
}
