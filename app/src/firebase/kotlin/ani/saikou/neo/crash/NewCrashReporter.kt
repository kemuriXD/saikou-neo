package ani.saikou.neo.crash

import android.content.Context
import ani.saikou.neo.shared.crash.CrashReporter
import ani.saikou.neo.shared.crash.NoCrashReporter
import com.google.firebase.FirebaseApp

/** Crashlytics once Firebase has started, which it does before the app is created. Tests never start it. */
fun newCrashReporter(context: Context): CrashReporter =
    if (FirebaseApp.getApps(context).isEmpty()) NoCrashReporter else FirebaseCrashReporter()
