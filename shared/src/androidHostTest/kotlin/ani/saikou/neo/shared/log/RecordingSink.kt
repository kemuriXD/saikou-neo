package ani.saikou.neo.shared.log

/** A [LogSink] that keeps every line it gets, for tests. */
class RecordingSink : LogSink {
    val lines = mutableListOf<LogLine>()

    @Synchronized
    override fun write(line: LogLine) {
        lines += line
    }
}
