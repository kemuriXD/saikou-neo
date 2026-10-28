package ani.saikou.neo.tv

import android.os.Bundle
import android.os.SystemClock
import ani.saikou.neo.shared.home.startsOver

private const val BACKGROUNDED_AT = "backgrounded-at"

/**
 * Times how long the app spends in the background, so that after a long absence it opens on Home again rather than
 * where it was left ([startsOver]). The time it left is saved with the app's state, so this holds even when Android
 * closed the app in the meantime. Measured since the TV started, so changing its clock doesn't count.
 */
internal class LongAbsence(
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    // Set while the app is in the background.
    private var backgroundedAt: Long? = null

    /** [saved] to restore the app as it was, or null to start over after a long absence. */
    fun restore(saved: Bundle?): Bundle? = saved?.takeUnless { startsOver(it.getLong(BACKGROUNDED_AT), now()) }

    fun stopped() {
        backgroundedAt = now()
    }

    /** Whether the app, back on screen, was away long enough to start over. */
    fun startedAgain(): Boolean {
        val left = backgroundedAt ?: return false
        backgroundedAt = null
        return startsOver(left, now())
    }

    /** Saves when the app left, or now if it's still on screen. */
    fun save(state: Bundle) = state.putLong(BACKGROUNDED_AT, backgroundedAt ?: now())
}
