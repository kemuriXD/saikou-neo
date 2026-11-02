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

    @Test
    fun formatsAJumpWithItsSign() {
        assertEquals("−5:00", formatJump(-300_000))
        assertEquals("+0:30", formatJump(30_000))
        assertEquals("+0:00", formatJump(0))
    }

    @Test
    fun endsWhatsLeftAfterNow() {
        assertEquals(1_000_000L + 696_000, endsAtMs(nowMs = 1_000_000, positionMs = 724_000, durationMs = 1_420_000))
        assertEquals(null, endsAtMs(nowMs = 1_000_000, positionMs = 0, durationMs = 0))
    }

    @Test
    fun endsSoonerAtAFasterSpeed() {
        assertEquals(1_000_000L + 348_000, endsAtMs(nowMs = 1_000_000, positionMs = 724_000, durationMs = 1_420_000, speed = 2f))
        assertEquals(1_000_000L + 1_392_000, endsAtMs(nowMs = 1_000_000, positionMs = 724_000, durationMs = 1_420_000, speed = 0.5f))
    }

    @Test
    fun speedsLeaveOffAWholeNumbersPoint() {
        assertEquals("1", formatSpeed(1f))
        assertEquals("2", formatSpeed(2f))
        assertEquals("0.75", formatSpeed(0.75f))
        assertEquals("1.5", formatSpeed(1.5f))
    }

    @Test
    fun subtitleTimingShowsItsSignInTenths() {
        assertEquals("0.0 s", formatSubtitleDelay(0))
        assertEquals("+0.5 s", formatSubtitleDelay(500))
        assertEquals("−1.2 s", formatSubtitleDelay(-1_200))
        assertEquals("+12.0 s", formatSubtitleDelay(12_000))
    }

    @Test
    fun subtitleTimingMovesInStepsUpToAMinute() {
        assertEquals(100, nextSubtitleDelay(0, later = true))
        assertEquals(-100, nextSubtitleDelay(0, later = false))
        assertEquals(60_000, nextSubtitleDelay(60_000, later = true))
        assertEquals(-60_000, nextSubtitleDelay(-60_000, later = false))
    }

    @Test
    fun normalSpeedIsOnTheList() = assertEquals(true, 1f in PLAYBACK_SPEEDS)
}
