package ani.saikou.neo.shared.report

import ani.saikou.neo.shared.crash.CrashContext
import ani.saikou.neo.shared.crash.CrashLogScrubber
import ani.saikou.neo.shared.crash.RecordingReporter
import kotlinx.coroutines.test.runTest
import java.util.zip.GZIPInputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BugReportsTest {
    private class Recording(
        override val canSend: Boolean = true,
    ) : BugReportSender {
        var sent: BugReport? = null

        override suspend fun send(report: BugReport): SendResult {
            sent = report
            return SendResult.Sent("SN-2345")
        }
    }

    private fun reports(sender: BugReportSender) =
        BugReports(
            sender = sender,
            scrubber = CrashLogScrubber(),
            device = { mapOf("model" to "AFTMM") },
            account = { ReportAccount("7", "kemuri") },
            log = { "2026-11-24T10:00:00Z I App: Started" },
        )

    @AfterTest
    fun reset() = CrashContext.reset()

    @Test
    fun aBuildWithoutFirebaseSaysReportsCantBeSent() =
        runTest {
            val reports = reports(NoBugReportSender)

            assertFalse(reports.canSend)
            assertEquals(SendResult.Unavailable, reports.send("playback", "", null))
        }

    @Test
    fun sendsWhatTheAppKnows() =
        runTest {
            CrashContext.install(RecordingReporter(), CrashLogScrubber())
            CrashContext.screen("details")
            val sender = Recording()

            val result = reports(sender).send("sync", "Lost my progress", byteArrayOf(9))

            assertEquals(SendResult.Sent("SN-2345"), result)
            val report = assertIs<BugReport>(sender.sent)
            assertEquals("sync", report.category)
            assertEquals("Lost my progress", report.description)
            assertEquals(mapOf("model" to "AFTMM"), report.device)
            assertEquals(mapOf("screen" to "details"), report.context)
            assertEquals(ReportAccount("7", "kemuri"), report.account)
            assertTrue(byteArrayOf(9).contentEquals(report.screenshot))
            val log = GZIPInputStream(report.log.inputStream()).bufferedReader().readText()
            assertEquals("2026-11-24T10:00:00Z I App: Started\n", log)
        }

    @Test
    fun signedOutReportsHaveNoAccount() =
        runTest {
            val sender = Recording()
            BugReports(sender, CrashLogScrubber(), { emptyMap() }, { null }, { "" }).send("other", "", null)

            assertNull(sender.sent?.account)
        }
}
