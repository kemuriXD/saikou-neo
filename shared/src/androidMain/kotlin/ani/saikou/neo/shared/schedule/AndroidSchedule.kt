package ani.saikou.neo.shared.schedule

import android.content.Context
import java.util.TimeZone
import kotlin.time.Instant

/** Keeps the Schedule toggle in the app's private storage. */
public class PrefsScheduleFilterStore(
    context: Context,
) : ScheduleFilterStore {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun readFilter(): ScheduleFilter? = ScheduleFilter.entries.firstOrNull { it.name == preferences.getString(FILTER, null) }

    override fun saveFilter(filter: ScheduleFilter) {
        preferences.edit().putString(FILTER, filter.name).apply()
    }

    private companion object {
        const val FILE = "schedule"
        const val FILTER = "filter"
    }
}

/** The device's time zone. java.util.TimeZone rather than java.time, which minSdk 24 doesn't have. */
public class DeviceScheduleZone(
    private val zone: TimeZone = TimeZone.getDefault(),
) : ScheduleZone {
    override val id: String = zone.id

    override fun offsetSecondsAt(instant: Instant): Int = zone.getOffset(instant.toEpochMilliseconds()) / 1_000
}
