package ani.saikou.neo.crash

import ani.saikou.neo.shared.crash.CrashReporter
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Sends reports to Crashlytics. */
internal class FirebaseCrashReporter : CrashReporter {
    private val crashlytics get() = FirebaseCrashlytics.getInstance()

    override fun setEnabled(enabled: Boolean) {
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
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

    private companion object {
        const val USER_NAME = "user_name"
    }
}
