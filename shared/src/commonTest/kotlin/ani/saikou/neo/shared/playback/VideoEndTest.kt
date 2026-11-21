package ani.saikou.neo.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val LENGTH = 752_000L

class VideoEndTest {
    /** Checks every half second from [fromMs] for [forMs], with the video at [positionMs]. How many said it ended. */
    private fun VideoEnd.sits(
        positionMs: Long,
        fromMs: Long,
        forMs: Long,
        playing: Boolean = true,
    ) = (fromMs..fromMs + forMs step 500).count { check(positionMs, LENGTH, playing, it) }

    // #474: after a seek near the end, the video stayed at 12:32 / 12:32 with nothing more to come.
    @Test
    fun aVideoStuckAtItsEndEndsOnce() {
        val end = VideoEnd()

        assertEquals(1, end.sits(LENGTH, fromMs = 0, forMs = 10_000))
    }

    @Test
    fun aMomentAtTheEndIsNotTheEndYet() {
        val end = VideoEnd()

        assertEquals(0, end.sits(LENGTH, fromMs = 0, forMs = 2_500))
    }

    @Test
    fun aVideoStillPlayingNearItsEndHasNotEnded() {
        val end = VideoEnd()

        val told = (0L..5_000L step 500).count { end.check(LENGTH - 2_000 + it / 2, LENGTH, playing = true, nowMs = it) }

        assertEquals(0, told)
    }

    @Test
    fun aVideoPausedAtItsEndHasNotEnded() {
        val end = VideoEnd()

        assertEquals(0, end.sits(LENGTH, fromMs = 0, forMs = 10_000, playing = false))
    }

    @Test
    fun aVideoStuckBeforeItsEndHasNotEnded() {
        val end = VideoEnd()

        assertEquals(0, end.sits(LENGTH / 2, fromMs = 0, forMs = 10_000))
    }

    @Test
    fun anEndThePlayerToldIsNotToldAgain() {
        val end = VideoEnd()

        assertTrue(end.ended())
        assertEquals(0, end.sits(LENGTH, fromMs = 0, forMs = 10_000))
        assertFalse(end.ended())
    }

    @Test
    fun goingBackFromTheEndLetsItEndAgain() {
        val end = VideoEnd()
        end.sits(LENGTH, fromMs = 0, forMs = 10_000)

        end.check(LENGTH - 60_000, LENGTH, playing = true, nowMs = 10_500)

        assertEquals(1, end.sits(LENGTH, fromMs = 70_000, forMs = 10_000))
    }

    @Test
    fun aLengthNotKnownYetIsNoEnd() {
        val end = VideoEnd()

        assertEquals(0, (0L..10_000L step 500).count { end.check(0, 0, playing = true, nowMs = it) })
    }
}
