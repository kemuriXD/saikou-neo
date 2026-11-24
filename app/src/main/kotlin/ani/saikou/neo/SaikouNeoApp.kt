package ani.saikou.neo

import ani.saikou.neo.crash.newCrashReporter
import ani.saikou.neo.shared.crash.CrashReporter
import ani.saikou.neo.tv.NeoApp

/**
 * The app's Application. It adds the crash reporter, which only builds with google-services.json have, and tells crash
 * reports the channel and whether the TV or phone screens open.
 */
class SaikouNeoApp : NeoApp() {
    override fun crashReporter(): CrashReporter = newCrashReporter(this)

    override val channel: String = BuildConfig.FLAVOR

    override fun opensTvScreens(): Boolean = deviceUi() == Ui.Tv
}
