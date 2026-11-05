package ani.saikou.neo.shared.playback

import ani.saikou.neo.extension.api.SourceException
import kotlin.time.Duration.Companion.days

// The longest wait a rate limit can ask for. An endless one counts as not saying.
private val LONGEST_WAIT = 1.days

/**
 * The reason to show for what a source threw. Its typed errors keep their reason, any other exception means the site
 * is down, and an error such as a linkage error means the source doesn't fit the app, so it won't start.
 */
public fun Throwable.failReason(): FailReason =
    when (this) {
        is SourceException.NotFound -> {
            FailReason.NotFound
        }

        is SourceException.Blocked -> {
            FailReason.Blocked
        }

        is SourceException.RateLimited -> {
            FailReason.RateLimited(
                retryAfter?.takeIf { it.isFinite() }?.let { (it.coerceAtMost(LONGEST_WAIT).inWholeMilliseconds + 999) / 1000 },
            )
        }

        is SourceException.LinkExpired -> {
            FailReason.LinkExpired
        }

        is Exception -> {
            FailReason.SourceDown
        }

        else -> {
            FailReason.WontStart
        }
    }
