package ani.saikou.neo.shared.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NsdCallbacksTest {
    @Test
    fun callbacksRunWhileTheLookupIsOn() {
        val callbacks = NsdCallbacks()
        var ran = false
        callbacks.execute { ran = true }
        assertTrue(ran)
    }

    @Test
    fun aCallbackAfterTheLookupEndsIsDroppedWithoutCrashing() {
        // NSD still sends "unregistered" after the lookup stops. Rejecting it crashed the app on Android's own thread.
        val callbacks = NsdCallbacks()
        callbacks.stop()
        var runs = 0
        callbacks.execute { runs++ }
        assertEquals(0, runs)
    }
}
