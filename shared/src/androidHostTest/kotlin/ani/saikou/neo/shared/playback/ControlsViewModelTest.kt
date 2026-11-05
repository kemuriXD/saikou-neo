package ani.saikou.neo.shared.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val LENGTH = 1_420_000L

@OptIn(ExperimentalCoroutinesApi::class)
class ControlsViewModelTest {
    private val main = UnconfinedTestDispatcher()

    init {
        Dispatchers.setMain(main)
    }

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private val controls = ControlsViewModel(main.scheduler.timeSource)

    private fun test(body: suspend TestScope.() -> Unit) = runTest(main) { body() }

    private val state get() = controls.state.value

    @Test
    fun startsHidden() = assertEquals(ControlsState(), state)

    @Test
    fun hidesFiveSecondsAfterTheLastKeyWhilePlaying() =
        test {
            controls.show(playing = true)
            advanceTimeBy(4.seconds)
            controls.touched(playing = true)
            advanceTimeBy(4.seconds)
            assertTrue(state.shown)

            advanceTimeBy(1.1.seconds)
            assertFalse(state.shown)
        }

    @Test
    fun staysUpWhilePaused() =
        test {
            controls.show(playing = false)
            advanceTimeBy(60.seconds)
            assertTrue(state.shown)

            controls.touched(playing = true)
            advanceTimeBy(5.1.seconds)
            assertFalse(state.shown)
        }

    @Test
    fun backHides() {
        controls.show(playing = false)
        controls.hide()
        assertFalse(state.shown)
    }

    @Test
    fun seeksTenSecondsAndAddsUpQuickPresses() =
        test {
            assertEquals(-10_000, controls.nudge(forward = false))
            assertEquals(-10_000, controls.nudge(forward = false))
            assertEquals(-20_000L, state.nudgeMs)

            advanceTimeBy(1.6.seconds)
            assertNull(state.nudgeMs)

            assertEquals(10_000, controls.nudge(forward = true))
            assertEquals(10_000L, state.nudgeMs)
        }

    @Test
    fun aPressTheOtherWayStartsAgain() {
        controls.nudge(forward = true)
        controls.nudge(forward = true)
        controls.nudge(forward = false)
        assertEquals(-10_000L, state.nudgeMs)
    }

    @Test
    fun holdingStartsAScrubThatShowsTheControls() {
        controls.scrub(forward = false, held = true, positionMs = 724_000, durationMs = LENGTH)

        assertTrue(state.shown)
        assertEquals(Scrub(fromMs = 724_000, targetMs = 714_000), state.scrub)
        assertEquals(-10_000, state.scrub?.jumpMs)
    }

    @Test
    fun holdingLongerScrubsFaster() =
        test {
            controls.scrub(forward = true, held = true, positionMs = 0, durationMs = LENGTH)
            // 30 s a second at first, 60 from one second, then 120 from three.
            advanceTimeBy(500.milliseconds)
            controls.scrub(forward = true, held = true, positionMs = 0, durationMs = LENGTH)
            assertEquals(10_000 + 15_000L, state.scrub?.targetMs)

            advanceTimeBy(1.seconds)
            controls.scrub(forward = true, held = true, positionMs = 0, durationMs = LENGTH)
            assertEquals(25_000 + 60_000L, state.scrub?.targetMs)

            advanceTimeBy(2.seconds)
            controls.scrub(forward = true, held = true, positionMs = 0, durationMs = LENGTH)
            assertEquals(85_000 + 240_000L, state.scrub?.targetMs)
        }

    @Test
    fun aTapWhileScrubbingMovesTenSecondsAndEndsTheHold() =
        test {
            controls.scrub(forward = true, held = true, positionMs = 100_000, durationMs = LENGTH)
            advanceTimeBy(5.seconds)
            controls.scrub(forward = false, held = false, positionMs = 100_000, durationMs = LENGTH)
            assertEquals(100_000L, state.scrub?.targetMs)

            // A new hold starts slow again.
            controls.scrub(forward = true, held = true, positionMs = 100_000, durationMs = LENGTH)
            assertEquals(110_000L, state.scrub?.targetMs)
        }

    @Test
    fun lettingGoEndsTheHold() =
        test {
            controls.scrub(forward = true, held = true, positionMs = 100_000, durationMs = LENGTH)
            controls.released()
            advanceTimeBy(5.seconds)

            controls.scrub(forward = true, held = true, positionMs = 100_000, durationMs = LENGTH)
            assertEquals(120_000L, state.scrub?.targetMs)
        }

    @Test
    fun scrubbingStopsAtTheStartAndTheEnd() {
        controls.scrub(forward = false, held = false, positionMs = 4_000, durationMs = LENGTH)
        assertEquals(0L, state.scrub?.targetMs)

        controls.stay()
        controls.scrub(forward = true, held = false, positionMs = LENGTH - 3_000, durationMs = LENGTH)
        assertEquals(LENGTH, state.scrub?.targetMs)
    }

    @Test
    fun jumpGivesTheTargetAndEndsTheScrub() {
        controls.scrub(forward = false, held = false, positionMs = 724_000, durationMs = LENGTH)

        assertEquals(714_000L, controls.jump())
        assertNull(state.scrub)
        assertTrue(state.shown)
        assertNull(controls.jump())
    }

    @Test
    fun stayDropsTheScrub() {
        controls.scrub(forward = false, held = false, positionMs = 724_000, durationMs = LENGTH)
        controls.stay()
        assertNull(state.scrub)
        assertTrue(state.shown)
    }

    @Test
    fun controlsDontHideMidScrub() =
        test {
            controls.show(playing = true)
            controls.scrub(forward = true, held = false, positionMs = 0, durationMs = LENGTH)
            advanceTimeBy(30.seconds)
            assertTrue(state.shown)
        }

    @Test
    fun hidingDropsTheScrub() {
        controls.scrub(forward = true, held = false, positionMs = 0, durationMs = LENGTH)
        controls.hide()
        assertEquals(ControlsState(), state)
    }

    @Test
    fun thePanelOpensOnTheTabAsked() {
        controls.openPanel(PanelTab.Speed)
        assertEquals(PanelTab.Speed, state.panel)
    }

    @Test
    fun thePanelStaysOpenPastTheHideTime() =
        test {
            controls.show(playing = true)
            controls.openPanel(PanelTab.Tracks)
            controls.touched(playing = true)
            advanceTimeBy(30.seconds)
            assertEquals(PanelTab.Tracks, state.panel)
            assertTrue(state.shown)
        }

    @Test
    fun closingThePanelGoesBackToTheControlsThenHidesThem() =
        test {
            controls.show(playing = true)
            controls.openPanel(PanelTab.Tracks)
            controls.closePanel(playing = true)
            assertNull(state.panel)
            assertTrue(state.shown)
            advanceTimeBy(6.seconds)
            assertFalse(state.shown)
        }

    @Test
    fun closingAPanelOpenedOverTheVideoLeavesTheControlsHidden() {
        controls.openPanel(PanelTab.Tracks)
        controls.closePanel(playing = true)
        assertEquals(ControlsState(), state)
    }

    @Test
    fun openingThePanelDropsTheScrub() {
        controls.scrub(forward = true, held = false, positionMs = 0, durationMs = LENGTH)
        controls.openPanel(PanelTab.Tracks)
        assertNull(state.scrub)
    }

    @Test
    fun theEpisodesDrawerStaysOpenPastTheHideTime() =
        test {
            controls.show(playing = true)
            controls.openEpisodes()
            controls.touched(playing = true)
            advanceTimeBy(30.seconds)
            assertTrue(state.episodes)
            assertTrue(state.shown)
        }

    @Test
    fun closingTheDrawerGoesBackToTheControlsThenHidesThem() =
        test {
            controls.show(playing = true)
            controls.openEpisodes()
            controls.closeEpisodes(playing = true)
            assertFalse(state.episodes)
            assertTrue(state.shown)
            advanceTimeBy(6.seconds)
            assertFalse(state.shown)
        }

    @Test
    fun closingADrawerOpenedOverTheVideoLeavesTheControlsHidden() {
        controls.openEpisodes()
        controls.closeEpisodes(playing = true)
        assertEquals(ControlsState(), state)
    }

    @Test
    fun openingTheDrawerDropsTheScrub() {
        controls.scrub(forward = true, held = false, positionMs = 0, durationMs = LENGTH)
        controls.openEpisodes()
        assertNull(state.scrub)
    }

    @Test
    fun thePanelAndTheDrawerTakeEachOthersPlace() {
        controls.openEpisodes()
        controls.openPanel(PanelTab.Tracks)
        assertFalse(state.episodes)
        controls.openEpisodes()
        assertNull(state.panel)
        assertTrue(state.episodes)
    }
}
