package ani.saikou.neo.report

import android.content.Context
import ani.saikou.neo.shared.report.BugReportSender
import ani.saikou.neo.shared.report.NoBugReportSender
import com.google.firebase.FirebaseApp

/** Firestore once Firebase has started, which it does before the app is created. Tests never start it. */
fun newBugReportSender(context: Context): BugReportSender =
    if (FirebaseApp.getApps(context).isEmpty()) NoBugReportSender else FirestoreBugReportSender()
