package ani.saikou.neo.shared.playback

import ani.saikou.neo.shared.settings.StillWatching
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchingRunTest {
    @Test
    fun asksOnceTheRunReachesTheSetting() =
        runTest {
            val run = WatchingRun { StillWatching.AFTER_2 }
            assertNull(run.asks())

            run.playedOn()

            assertEquals(2, run.asks())
        }

    @Test
    fun threeEpisodesTakeTwoPlayedOn() =
        runTest {
            val run = WatchingRun { StillWatching.AFTER_3 }
            run.playedOn()
            assertNull(run.asks())

            run.playedOn()

            assertEquals(3, run.asks())
        }

    @Test
    fun aPressStartsTheRunAgain() =
        runTest {
            val run = WatchingRun { StillWatching.AFTER_2 }
            run.playedOn()

            run.pressed()

            assertNull(run.asks())
        }

    @Test
    fun neverNeverAsks() =
        runTest {
            val run = WatchingRun { StillWatching.NEVER }
            repeat(10) { run.playedOn() }

            assertNull(run.asks())
        }
}
