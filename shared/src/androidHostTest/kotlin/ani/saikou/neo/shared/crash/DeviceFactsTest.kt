package ani.saikou.neo.shared.crash

import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DeviceFactsTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun everyFactIsThere() {
        val facts = deviceFacts(context, channel = "beta", tv = true)

        assertEquals(
            setOf("channel", "ui", "network", "memory_total_mb", "memory_low", "storage_free_mb"),
            facts.keys,
        )
        assertEquals("beta", facts["channel"])
        assertTrue(facts.getValue("memory_low") in setOf("true", "false"))
        facts.getValue("memory_total_mb").toLong()
        facts.getValue("storage_free_mb").toLong()
    }

    @Test
    fun theUiComesFromTheCaller() {
        assertEquals("tv", deviceFacts(context, channel = "alpha", tv = true)["ui"])
        assertEquals("phone", deviceFacts(context, channel = "alpha", tv = false)["ui"])
    }

    @Test
    fun theNetworkIsAKnownKind() {
        val network = deviceFacts(context, channel = "alpha", tv = true).getValue("network")

        assertTrue(network in setOf("wifi", "ethernet", "cellular", "none", "other"), network)
    }
}
