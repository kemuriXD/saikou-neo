package ani.saikou.neo.report

import ani.saikou.neo.shared.log.AppLog
import ani.saikou.neo.shared.report.BugReport
import ani.saikou.neo.shared.report.BugReportSender
import ani.saikou.neo.shared.report.ReportCount
import ani.saikou.neo.shared.report.SendResult
import ani.saikou.neo.shared.report.newReportCode
import ani.saikou.neo.shared.report.nextReportCount
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Stores reports in Firestore as `reports/<code>`, signed in anonymously. The install's count in `installs/<uid>` moves
 * on in the same transaction, which needs the server, so a report is never queued to go later. `firebase/firestore.rules`
 * checks both.
 */
internal class FirestoreBugReportSender : BugReportSender {
    override val canSend: Boolean = true

    // Kept in memory only, so a report's log and screenshot never land in Firestore's disk cache.
    private val db by lazy {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = firestoreSettings { setLocalCacheSettings(memoryCacheSettings {}) }
        }
    }

    override suspend fun send(report: BugReport): SendResult =
        withTimeoutOrNull(TIMEOUT_MS) {
            val auth = FirebaseAuth.getInstance()
            val uid = (auth.currentUser ?: auth.signInAnonymously().await().user)?.uid ?: return@withTimeoutOrNull SendResult.Failed
            // A code already taken makes the rules refuse the write, so another is tried.
            repeat(TRIES) {
                val code = newReportCode()
                try {
                    return@withTimeoutOrNull if (store(uid, code, report)) SendResult.Sent(code) else SendResult.Failed
                } catch (e: FirebaseFirestoreException) {
                    if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
                    AppLog.w(TAG, "Report refused, trying another code")
                }
            }
            SendResult.Failed
        } ?: SendResult.Failed

    /** False when the install has to wait before sending another. */
    private suspend fun store(
        uid: String,
        code: String,
        report: BugReport,
    ): Boolean =
        db
            .runTransaction { tx ->
                val installRef = db.collection(INSTALLS).document(uid)
                val install = tx.get(installRef)
                val last =
                    if (install.exists()) {
                        ReportCount(
                            count = install.getLong(COUNT)?.toInt() ?: 0,
                            since = install.getTimestamp(SINCE)?.toDate()?.time ?: 0,
                            last = install.getTimestamp(LAST)?.toDate()?.time ?: 0,
                        )
                    } else {
                        null
                    }
                val now = System.currentTimeMillis()
                val next = nextReportCount(last, now) ?: return@runTransaction false
                val since: Any = install.getTimestamp(SINCE)?.takeIf { next.count > 1 } ?: FieldValue.serverTimestamp()
                tx.set(installRef, mapOf(COUNT to next.count, SINCE to since, LAST to FieldValue.serverTimestamp()))
                tx.set(db.collection(REPORTS).document(code), fields(uid, report, now))
                true
            }.await()

    private fun fields(
        uid: String,
        report: BugReport,
        now: Long,
    ): Map<String, Any> =
        buildMap {
            put("uid", uid)
            put("category", report.category)
            put("description", report.description)
            put("device", report.device)
            put("context", report.context)
            report.account?.let { put("account", mapOf("id" to it.id, "name" to it.name)) }
            put("log", Blob.fromBytes(report.log))
            report.screenshot?.let { put("screenshot", Blob.fromBytes(it)) }
            put("createdAt", FieldValue.serverTimestamp())
            // Firestore's TTL policy deletes the report after this time.
            put("expireAt", Timestamp(Date(now + KEEP_MS)))
        }

    private companion object {
        const val TAG = "Report"
        const val REPORTS = "reports"
        const val INSTALLS = "installs"
        const val COUNT = "count"
        const val SINCE = "since"
        const val LAST = "last"
        const val TRIES = 3
        const val TIMEOUT_MS = 30_000L

        // 89 days, so a clock a day out still falls in the 88 to 90 the rules allow.
        const val KEEP_MS = 89L * 24 * 60 * 60 * 1000
    }
}

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            val error = task.exception
            when {
                error != null -> continuation.resumeWithException(error)
                task.isCanceled -> continuation.cancel()
                else -> continuation.resume(task.result)
            }
        }
    }
