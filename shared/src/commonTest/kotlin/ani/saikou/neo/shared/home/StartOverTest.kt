package ani.saikou.neo.shared.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class StartOverTest {
    private val left = 1_000_000L

    private fun after(away: Duration) = startsOver(backgroundedAtMillis = left, nowMillis = left + away.inWholeMilliseconds)

    @Test
    fun aShortBreakKeepsThePlace() {
        assertFalse(after(5.minutes))
    }

    @Test
    fun thirtyMinutesAwayStillKeepsThePlace() {
        assertFalse(after(30.minutes))
    }

    @Test
    fun longerThanThirtyMinutesStartsOver() {
        assertTrue(after(30.minutes + 1.seconds))
    }

    // A clock that went backwards can't say how long it's been, so the app starts over.
    @Test
    fun aClockThatWentBackStartsOver() {
        assertTrue(after((-1).minutes))
    }
}
