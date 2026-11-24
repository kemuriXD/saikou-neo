package ani.saikou.neo.shared.crash

import ani.saikou.neo.shared.log.LogLevel
import ani.saikou.neo.shared.log.LogLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class CrashLogSinkTest {
    private class Recording(
        private val fail: Boolean = false,
    ) : CrashReporter {
        val lines = mutableListOf<String>()

        override fun setEnabled(enabled: Boolean) = Unit

        override fun setUser(
            id: String?,
            name: String?,
        ) = Unit

        override fun setKey(
            key: String,
            value: String,
        ) = Unit

        override fun log(line: String) {
            if (fail) error("broken")
            lines += line
        }
    }

    private fun line(
        level: LogLevel,
        message: String,
    ) = LogLine(Instant.fromEpochSeconds(0), level, "Player", message)

    @Test
    fun aLineReachesTheReporterScrubbed() {
        val reporter = Recording()

        CrashLogSink(reporter, CrashLogScrubber()).write(line(LogLevel.Info, "Playing show 21 episode 3"))

        assertEquals(listOf("I Player: Playing show [id] episode [n]"), reporter.lines)
    }

    @Test
    fun aReporterThatThrowsDoesNotBreakTheCaller() {
        CrashLogSink(Recording(fail = true), CrashLogScrubber()).write(line(LogLevel.Error, "boom"))
    }

    @Test
    fun noCrashReporterDoesNothing() {
        NoCrashReporter.setEnabled(true)
        NoCrashReporter.setUser("1", "a")
        NoCrashReporter.setKey("k", "v")
        NoCrashReporter.log("x")
    }
}
