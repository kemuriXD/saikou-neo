package ani.saikou.neo.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackTimeTest {
    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("0:00", formatPlaybackTime(0))
        assertEquals("0:09", formatPlaybackTime(9_999))
        assertEquals("23:40", formatPlaybackTime(1_420_000))
    }

    @Test
    fun addsHoursFromOneHour() {
        assertEquals("59:59", formatPlaybackTime(3_599_000))
        assertEquals("1:00:00", formatPlaybackTime(3_600_000))
        assertEquals("1:02:03", formatPlaybackTime(3_723_000))
    }

    @Test
    fun clampsNegativePositionsToZero() {
        assertEquals("0:00", formatPlaybackTime(-5_000))
    }
}
