package ani.saikou.neo.crash

/**
 * What Crashlytics is given for an error the app recovered from: the cleaned message, and the app's frames of the
 * original error's stack so reports group by where it happened. Neither its message nor its causes come along.
 */
internal class RecoveredError(
    message: String,
    frames: Array<StackTraceElement>?,
) : Exception(message) {
    init {
        if (frames != null) stackTrace = frames
    }
}
