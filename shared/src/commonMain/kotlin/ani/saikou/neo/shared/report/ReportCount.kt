package ani.saikou.neo.shared.report

private const val MINUTE_MS = 60_000L
private const val DAY_MS = 24 * 60 * MINUTE_MS

/** How many reports this install has sent since [since], the last at [last]. Times are epoch milliseconds. */
public data class ReportCount(
    val count: Int,
    val since: Long,
    val last: Long,
)

/**
 * The count after one more report at [now], or null when the install must wait: a minute after its last report, and
 * at most [MAX_REPORTS_A_DAY] reports in a day from the first. The Firestore rules check the same.
 */
public fun nextReportCount(
    last: ReportCount?,
    now: Long,
): ReportCount? =
    when {
        last == null -> ReportCount(1, since = now, last = now)
        now <= last.last + MINUTE_MS -> null
        now > last.since + DAY_MS -> ReportCount(1, since = now, last = now)
        last.count >= MAX_REPORTS_A_DAY -> null
        else -> ReportCount(last.count + 1, since = last.since, last = now)
    }

/** The most reports an install can send in a day. */
public const val MAX_REPORTS_A_DAY: Int = 10
