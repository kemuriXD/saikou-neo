package ani.saikou.neo.shared.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportCountTest {
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    @Test
    fun theFirstReportStartsTheCount() {
        assertEquals(ReportCount(1, since = 5, last = 5), nextReportCount(null, now = 5))
    }

    @Test
    fun laterReportsAddOne() {
        val count = ReportCount(3, since = 0, last = 0)

        assertEquals(ReportCount(4, since = 0, last = 2 * minute), nextReportCount(count, now = 2 * minute))
    }

    @Test
    fun aReportWithinAMinuteOfTheLastWaits() {
        assertNull(nextReportCount(ReportCount(1, since = 0, last = 0), now = minute))
    }

    @Test
    fun tenADayIsTheMost() {
        assertNull(nextReportCount(ReportCount(10, since = 0, last = 0), now = day))
    }

    @Test
    fun aDayAfterTheCountStartedItStartsAgain() {
        val now = day + 1

        assertEquals(ReportCount(1, since = now, last = now), nextReportCount(ReportCount(10, since = 0, last = 0), now))
    }
}
