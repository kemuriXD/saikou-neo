package ani.saikou.neo.shared.crash

import ani.saikou.neo.shared.log.LogLine
import ani.saikou.neo.shared.log.LogSink

/** Sends each log line to a [CrashReporter], scrubbed. The reporter adds its own timestamps. */
public class CrashLogSink(
    private val reporter: CrashReporter,
    private val scrubber: CrashLogScrubber,
) : LogSink {
    override fun write(line: LogLine) {
        try {
            reporter.log("${line.level.letter} ${line.tag}: ${scrubber.scrub(line.message)}")
        } catch (_: Throwable) {
            // A broken reporter must not stop the log.
        }
    }
}
