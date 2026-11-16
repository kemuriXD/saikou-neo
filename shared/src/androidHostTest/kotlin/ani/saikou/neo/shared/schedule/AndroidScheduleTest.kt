package ani.saikou.neo.shared.schedule

import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class AndroidScheduleTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun theToggleIsRememberedAfterARestart() {
        assertNull(PrefsScheduleFilterStore(context).readFilter())

        PrefsScheduleFilterStore(context).saveFilter(ScheduleFilter.ALL_SHOWS)

        assertEquals(ScheduleFilter.ALL_SHOWS, PrefsScheduleFilterStore(context).readFilter())
    }

    @Test
    fun theDeviceZoneFollowsItsClockChanges() {
        val zone = DeviceScheduleZone(TimeZone.getTimeZone("Europe/London"))

        assertEquals("Europe/London", zone.id)
        assertEquals(3_600, zone.offsetSecondsAt(Instant.parse("2026-07-01T12:00:00Z")))
        assertEquals(0, zone.offsetSecondsAt(Instant.parse("2026-11-18T12:00:00Z")))
    }
}
