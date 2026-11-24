package ani.saikou.neo.shared.report

import kotlin.random.Random

/** The AniList user a report came from. */
public data class ReportAccount(
    val id: String,
    val name: String,
)

/**
 * A bug report ready to send, already cleaned and cut to [BugReportLimits]. [log] is the gzipped log and [screenshot]
 * an image of the app, if there's one under the cap.
 */
public class BugReport(
    public val category: String,
    public val description: String,
    public val device: Map<String, String>,
    public val context: Map<String, String>,
    public val account: ReportAccount?,
    public val log: ByteArray,
    public val screenshot: ByteArray?,
) {
    /** Roughly what the report takes up stored, counted the way Firestore counts a document, plus some room. */
    public fun sizeBytes(): Int {
        fun text(value: String) = value.encodeToByteArray().size + 1

        fun map(values: Map<String, String>) = values.entries.sumOf { text(it.key) + text(it.value) }
        return OVERHEAD + text(category) + text(description) + map(device) + map(context) +
            (account?.let { text(it.id) + text(it.name) } ?: 0) + log.size + (screenshot?.size ?: 0)
    }

    private companion object {
        // The document's name, field names, timestamps and the user ID, with plenty to spare.
        const val OVERHEAD = 1_024
    }
}

/** What a report may hold. The Firestore rules check the same limits. */
public object BugReportLimits {
    /** Firestore allows 1 MiB a document. */
    public const val REPORT_BYTES: Int = 1_000_000
    public const val LOG_BYTES: Int = 256 * 1024
    public const val SCREENSHOT_BYTES: Int = 600 * 1024
    public const val DESCRIPTION_CHARS: Int = 2_000
    public const val VALUE_CHARS: Int = 100

    public val DEVICE_KEYS: Set<String> =
        setOf(
            "version",
            "channel",
            "ui",
            "model",
            "manufacturer",
            "android",
            "network",
            "memory_total_mb",
            "memory_low",
            "storage_free_mb",
        )

    /** The keys [ani.saikou.neo.shared.crash.CrashContext] sets. */
    public val CONTEXT_KEYS: Set<String> = setOf("screen", "anime_id", "episode", "extension", "extension_version")

    public val CATEGORY: Regex = Regex("[a-z_]{1,32}")
}

/** What sending a report came to. */
public sealed interface SendResult {
    /** Stored. [code] is what the user can quote, such as `SN-4K7Q`. */
    public data class Sent(
        val code: String,
    ) : SendResult

    /** This build has nowhere to send reports. */
    public data object Unavailable : SendResult

    /** Not sent, as Firebase turned it down or couldn't be reached. Trying later may work. */
    public data object Failed : SendResult
}

/** Where bug reports go. The app has nowhere to send them unless a build adds a sender. */
public interface BugReportSender {
    /** False when this build can't send reports at all. */
    public val canSend: Boolean

    public suspend fun send(report: BugReport): SendResult
}

/** A build without Firebase: reports can't be sent. */
public object NoBugReportSender : BugReportSender {
    override val canSend: Boolean = false

    override suspend fun send(report: BugReport): SendResult = SendResult.Unavailable
}

// No 0, 1, I, L, O or U, which are easy to misread or mistype.
private const val CODE_LETTERS = "23456789ABCDEFGHJKMNPQRSTVWXYZ"

/** A new report code, such as `SN-4K7Q`. It also names the stored report. */
public fun newReportCode(random: Random = Random.Default): String =
    "SN-" + (1..4).map { CODE_LETTERS[random.nextInt(CODE_LETTERS.length)] }.joinToString("")
