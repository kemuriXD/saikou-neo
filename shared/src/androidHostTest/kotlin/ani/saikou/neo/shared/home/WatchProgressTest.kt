package ani.saikou.neo.shared.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WatchProgressTest {
    private fun progress(watched: kotlin.time.Duration) = WatchProgress(episode = 7, watched = watched, length = 24.minutes, still = null)

    @Test
    fun minutesLeftRoundsUp() {
        assertEquals(12, progress(12.minutes).minutesLeft)
        assertEquals(12, progress(12.minutes + 1.seconds).minutesLeft)
    }

    @Test
    fun aFewSecondsLeftIsStillAMinute() = assertEquals(1, progress(24.minutes - 20.seconds).minutesLeft)

    @Test
    fun fractionIsHowMuchWasWatched() {
        assertEquals(0.5f, progress(12.minutes).fraction)
        assertEquals(1f, progress(30.minutes).fraction)
    }
}
