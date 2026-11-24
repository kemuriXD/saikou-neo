package ani.saikou.neo.crash

import android.content.Context
import ani.saikou.neo.shared.crash.CrashReporter
import ani.saikou.neo.shared.crash.NoCrashReporter

/** Built without google-services.json, so there's nowhere to send reports. */
fun newCrashReporter(context: Context): CrashReporter = NoCrashReporter
