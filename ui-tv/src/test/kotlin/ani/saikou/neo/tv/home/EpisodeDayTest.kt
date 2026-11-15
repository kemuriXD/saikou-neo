package ani.saikou.neo.tv.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class EpisodeDayTest {
    private val tokyo = TimeZone.getTimeZone("Asia/Tokyo")

    private fun at(
        month: Int,
        day: Int,
        hour: Int,
    ): Calendar = GregorianCalendar(tokyo).apply { set(2026, month, day, hour, 0, 0) }

    // 30 October 2026, 23:00 in Tokyo.
    private val now = at(Calendar.OCTOBER, 30, 23)

    private fun Calendar.seconds() = timeInMillis / 1000

    @Test
    fun countsCalendarDaysInTheTvsZoneNotHours() {
        assertEquals(0, daysUntil(now.seconds() + 30 * 60, now))
        // Two hours away, but after midnight.
        assertEquals(1, daysUntil(at(Calendar.OCTOBER, 31, 1).seconds(), now))
        // Across the end of the month.
        assertEquals(3, daysUntil(at(Calendar.NOVEMBER, 2, 9).seconds(), now))
    }
}
