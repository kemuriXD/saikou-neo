package ani.saikou.neo.crash

/**
 * What Crashlytics is given for an error the app recovered from: the cleaned message, and the original error's stack
 * frames so reports group by where it happened. Neither the original's message nor its causes come along.
 */
internal class RecoveredError(
    message: String,
    frames: Array<StackTraceElement>?,
) : Exception(message) {
    init {
        if (frames != null) stackTrace = frames
    }
}
