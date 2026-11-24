package ani.saikou.neo.crash

import ani.saikou.neo.shared.crash.CrashReporter
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Sends reports to Crashlytics. */
internal class FirebaseCrashReporter : CrashReporter {
    private val crashlytics get() = FirebaseCrashlytics.getInstance()

    override fun setEnabled(enabled: Boolean) {
        // Crashlytics applies the new value from the next run and keeps reports on the device while it's off, so
        // turning it off also deletes those. Called at every start, it clears a run that crashed while off.
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        if (!enabled) crashlytics.deleteUnsentReports()
    }

    override fun setUser(
        id: String?,
        name: String?,
    ) {
        // Crashlytics has no way to remove a user, so signing out blanks both.
        crashlytics.setUserId(id.orEmpty())
        crashlytics.setCustomKey(USER_NAME, name.orEmpty())
    }

    override fun setKey(
        key: String,
        value: String,
    ) {
        crashlytics.setCustomKey(key, value)
    }

    override fun log(line: String) {
        crashlytics.log(line)
    }

    override fun recordNonFatal(
        message: String,
        error: Throwable?,
    ) {
        crashlytics.recordException(RecoveredError(message, error?.stackTrace))
    }

    private companion object {
        const val USER_NAME = "user_name"
    }
}
