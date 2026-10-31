package ani.saikou.neo.extension.api

import kotlin.time.Duration

/**
 * Thrown by a source when it can't do what was asked. The app shows a plain
 * reason for each type. Anything else a source throws counts as [SourceDown].
 */
public sealed class SourceException(
    message: String?,
    cause: Throwable?,
) : Exception(message, cause) {
    /** The site refused the request, for example with a captcha or a region block. */
    public class Blocked(
        message: String? = null,
        cause: Throwable? = null,
    ) : SourceException(message, cause)

    /** Too many requests. Try again after [retryAfter], if the site said. */
    public class RateLimited(
        public val retryAfter: Duration? = null,
        message: String? = null,
        cause: Throwable? = null,
    ) : SourceException(message, cause) {
        init {
            require(retryAfter == null || !retryAfter.isNegative()) { "retryAfter can't be negative: $retryAfter" }
        }
    }

    /** The site isn't working right now. */
    public class SourceDown(
        message: String? = null,
        cause: Throwable? = null,
    ) : SourceException(message, cause)

    /** A link stopped working. The app calls [SourceExtension.refresh] and carries on from the same spot. */
    public class LinkExpired(
        message: String? = null,
        cause: Throwable? = null,
    ) : SourceException(message, cause)

    /** The site doesn't have this anime or episode. */
    public class NotFound(
        message: String? = null,
        cause: Throwable? = null,
    ) : SourceException(message, cause)
}
