package ani.saikou.neo

import ani.saikou.neo.crash.newCrashReporter
import ani.saikou.neo.shared.crash.CrashReporter
import ani.saikou.neo.tv.NeoApp

/** The app's Application. It adds the crash reporter, which only builds with google-services.json have. */
class SaikouNeoApp : NeoApp() {
    override fun crashReporter(): CrashReporter = newCrashReporter(this)
}
