package ani.saikou.neo.report

import android.content.Context
import ani.saikou.neo.shared.report.BugReportSender
import ani.saikou.neo.shared.report.NoBugReportSender

/** Built without google-services.json, so reports can't be sent. */
fun newBugReportSender(context: Context): BugReportSender = NoBugReportSender
