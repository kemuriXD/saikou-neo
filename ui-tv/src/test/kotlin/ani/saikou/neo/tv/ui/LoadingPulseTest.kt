package ani.saikou.neo.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LoadingPulseTest {
    @Test
    fun aCycleTakesOnePointTwoSeconds() {
        assertEquals(1200, PULSE_PERIOD_MILLIS)
    }

    @Test
    fun thePulseRisesToFullHalfwayAndFallsBack() {
        assertEquals(0f, pulseAt(0), 0.001f)
        assertEquals(0.5f, pulseAt(300), 0.001f)
        assertEquals(1f, pulseAt(600), 0.001f)
        assertEquals(0.5f, pulseAt(900), 0.001f)
        assertEquals(0f, pulseAt(1200), 0.001f)
    }

    // Every placeholder works it out from the same frame time, so they all show the same value together.
    @Test
    fun thePulseRepeatsEveryCycle() {
        listOf(0L, 137L, 450L, 1099L).forEach { t ->
            assertEquals(pulseAt(t), pulseAt(t + 1200 * 7), 0.001f)
        }
    }
}
