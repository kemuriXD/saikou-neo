package ani.saikou.neo.tv

import android.os.Bundle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
class LongAbsenceTest {
    private var now = 10_000_000L
    private val absence = LongAbsence { now }

    private fun wait(time: Duration) {
        now += time.inWholeMilliseconds
    }

    /** Leaves the app, and saves its state as Android does when it goes to the background. */
    private fun leave(): Bundle {
        absence.stopped()
        return Bundle().also(absence::save)
    }

    @Test
    fun theAppKeepsItsPlaceWhenItWasClosedDuringAShortBreak() {
        val saved = leave()
        wait(5.minutes)

        assertSame(saved, LongAbsence { now }.restore(saved))
    }

    @Test
    fun theAppStartsOverWhenItWasClosedDuringALongAbsence() {
        val saved = leave()
        wait(31.minutes)

        assertNull(LongAbsence { now }.restore(saved))
    }

    @Test
    fun theAppKeepsItsPlaceWhenItComesBackFromAShortBreak() {
        leave()
        wait(5.minutes)

        assertFalse(absence.startedAgain())
    }

    @Test
    fun theAppStartsOverWhenItComesBackFromALongAbsence() {
        leave()
        wait(31.minutes)

        assertTrue(absence.startedAgain())
    }

    @Test
    fun openingTheAppForTheFirstTimeDoesNotStartOver() {
        assertFalse(absence.startedAgain())
    }

    // Time on screen after a short break doesn't count as time away.
    @Test
    fun stateSavedWhileOnScreenKeepsItsPlace() {
        leave()
        wait(5.minutes)
        absence.startedAgain()
        wait(40.minutes)
        val saved = Bundle().also(absence::save)

        assertSame(saved, LongAbsence { now }.restore(saved))
    }
}
