package ani.saikou.neo.shared.report

import ani.saikou.neo.shared.crash.CrashContext
import ani.saikou.neo.shared.crash.CrashLogScrubber
import ani.saikou.neo.shared.crash.withoutSourceIds
import ani.saikou.neo.shared.log.AppLog
import ani.saikou.neo.shared.log.LogSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Sends bug reports the user asked to send. A report takes the device's details, the signed-in AniList user, what
 * [CrashContext] knows and both runs' logs, cleaned the way crash reports are, as well as what the user picked.
 */
public class BugReports(
    private val sender: BugReportSender,
    private val scrubber: CrashLogScrubber,
    private val device: suspend () -> Map<String, String>,
    private val account: suspend () -> ReportAccount?,
    private val log: suspend () -> String = { AppLog.read(LogSession.Previous) + "\n" + AppLog.read(LogSession.Current) },
) {
    /** False in builds without Firebase, which can't send reports. */
    public val canSend: Boolean get() = sender.canSend

    /** [category] is a lower-case word such as `playback`. [screenshot] is an image of the app, if there is one. */
    public suspend fun send(
        category: String,
        description: String,
        screenshot: ByteArray?,
    ): SendResult {
        if (!sender.canSend) return SendResult.Unavailable
        val report =
            withContext(Dispatchers.IO) {
                buildBugReport(category, description, device(), CrashContext.snapshot(), account(), log(), screenshot, scrubber)
            }
        return try {
            sender.send(report)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "Bug report not sent", e)
            SendResult.Failed
        }
    }

    private companion object {
        const val TAG = "Report"
    }
}

internal const val EARLIER_LINES_DROPPED = "[earlier lines dropped]"

private const val SOURCES = "Sources"

// A stored log line: time, level, tag and message.
private val lineShape = Regex("""(\S+ [IWE] )([A-Za-z]+): (.*)""")

/**
 * Builds a report within [BugReportLimits]. Log lines are cleaned by [scrubber], as for crash reports, and gzipped.
 * When the log is too big its oldest lines go. A screenshot over the cap is left out, and other text is cut short.
 */
public fun buildBugReport(
    category: String,
    description: String,
    device: Map<String, String>,
    context: Map<String, String>,
    account: ReportAccount?,
    log: String,
    screenshot: ByteArray?,
    scrubber: CrashLogScrubber,
): BugReport {
    require(BugReportLimits.CATEGORY.matches(category)) { "Not a report category: $category" }
    val lines = log.lineSequence().filter { it.isNotBlank() }.map { clean(it, scrubber) }.toList()
    return BugReport(
        category = category,
        description = description.take(BugReportLimits.DESCRIPTION_CHARS),
        device = device.only(BugReportLimits.DEVICE_KEYS),
        context = context.only(BugReportLimits.CONTEXT_KEYS),
        account = account?.let { ReportAccount(it.id.take(BugReportLimits.VALUE_CHARS), it.name.take(BugReportLimits.VALUE_CHARS)) },
        log = compress(lines),
        screenshot = screenshot?.takeIf { it.size <= BugReportLimits.SCREENSHOT_BYTES },
    )
}

private fun Map<String, String>.only(keys: Set<String>): Map<String, String> =
    filterKeys { it in keys }.mapValues { it.value.take(BugReportLimits.VALUE_CHARS) }

// Only the message is cleaned, so the time stays readable. The source store's lines lose source IDs too.
private fun clean(
    line: String,
    scrubber: CrashLogScrubber,
): String {
    val match = lineShape.matchEntire(line) ?: return scrubber.scrub(line)
    val (head, tag, message) = match.destructured
    val text = if (tag == SOURCES) withoutSourceIds(message) else message
    return "$head$tag: ${scrubber.scrub(text)}"
}

/** Gzips the newest lines that fit in [BugReportLimits.LOG_BYTES]. A line is at most a few KB, so one always fits. */
private fun compress(lines: List<String>): ByteArray {
    var start = 0
    while (true) {
        val kept = lines.subList(start, lines.size)
        val text =
            buildString {
                if (start > 0) append(EARLIER_LINES_DROPPED).append('\n')
                kept.forEach { append(it).append('\n') }
            }
        val bytes = gzip(text)
        if (bytes.size <= BugReportLimits.LOG_BYTES || kept.size <= 1) return bytes
        // Keep about as many lines as should fit, with some room, and always drop at least one.
        val fits = (kept.size.toLong() * BugReportLimits.LOG_BYTES * 9 / 10 / bytes.size).toInt()
        start += (kept.size - fits).coerceAtLeast(1)
    }
}

private fun gzip(text: String): ByteArray {
    val out = ByteArrayOutputStream()
    GZIPOutputStream(out).use { it.write(text.toByteArray()) }
    return out.toByteArray()
}
