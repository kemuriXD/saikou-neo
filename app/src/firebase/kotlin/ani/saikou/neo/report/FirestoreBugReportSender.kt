package ani.saikou.neo.report

import android.os.SystemClock
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

    override suspend fun send(report: BugReport): SendResult {
        // Only signing in is timed out. Once a transaction is under way it may still be stored after any timeout, so the
        // user isn't told it failed when it went through.
        val user = withTimeoutOrNull(SIGN_IN_TIMEOUT_MS) { signIn() } ?: return SendResult.Failed
        // A code already taken makes the rules refuse the write, so another is tried.
        repeat(TRIES) {
            val code = newReportCode()
            try {
                return if (store(user, code, report)) SendResult.Sent(code) else SendResult.Failed
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
                AppLog.w(TAG, "Report refused, trying another code")
            }
        }
        return SendResult.Failed
    }

    /**
     * Signs in anonymously and reads the server's time from a fresh ID token, as a TV's own clock is often wrong and the
     * rules check times against the server's.
     */
    private suspend fun signIn(): SignedIn? {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: auth.signInAnonymously().await().user ?: return null
        val issued = user.getIdToken(true).await().issuedAtTimestamp
        return SignedIn(user.uid, issued * 1000, SystemClock.elapsedRealtime())
    }

    private class SignedIn(
        val uid: String,
        val serverMillis: Long,
        val atElapsed: Long,
    ) {
        fun serverNow(): Long = serverMillis + (SystemClock.elapsedRealtime() - atElapsed)
    }

    /** False when the install has to wait before sending another. */
    private suspend fun store(
        user: SignedIn,
        code: String,
        report: BugReport,
    ): Boolean =
        db
            .runTransaction { tx ->
                val installRef = db.collection(INSTALLS).document(user.uid)
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
                val now = user.serverNow()
                val next = nextReportCount(last, now) ?: return@runTransaction false
                val since: Any = install.getTimestamp(SINCE)?.takeIf { next.count > 1 } ?: FieldValue.serverTimestamp()
                tx.set(
                    installRef,
                    mapOf(
                        COUNT to next.count,
                        SINCE to since,
                        LAST to FieldValue.serverTimestamp(),
                        // The rules only let a report through with its own move of the count.
                        "code" to code,
                        "expireAt" to Timestamp(Date(now + KEEP_COUNT_MS)),
                    ),
                )
                tx.set(db.collection(REPORTS).document(code), fields(user.uid, report, now))
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
        const val SIGN_IN_TIMEOUT_MS = 30_000L
        const val DAY_MS = 24L * 60 * 60 * 1000

        // 89 days, in the 88 to 90 the rules allow, which leaves room for the token's time being a little out.
        const val KEEP_MS = 89 * DAY_MS

        // The count goes two days after the last report.
        const val KEEP_COUNT_MS = 2 * DAY_MS
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
