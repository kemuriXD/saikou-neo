package ani.saikou.neo.shared.log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.time.Instant

public enum class LogLevel(
    public val letter: Char,
) {
    Info('I'),
    Warn('W'),
    Error('E'),
}

/** One log entry. The message has already been redacted. */
public data class LogLine(
    val at: Instant,
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    /** For example `2026-11-24T10:15:30.123Z W Player: message`. */
    public fun format(): String = "$at ${level.letter} $tag: $message"
}

/** Gets every line as it is logged, for example to forward it somewhere else. */
public fun interface LogSink {
    public fun write(line: LogLine)
}

public enum class LogSession { Current, Previous }

/** Keeps the lines of the current and the previous run of the app. */
public interface LogStore {
    /** Must not block the caller on file IO. */
    public fun append(line: String)

    /** Blocking. Empty when there is none. */
    public fun read(session: LogSession): String

    /** Blocks until pending lines are on disk. */
    public fun flush()
}

/**
 * The app's own log. Lines are redacted before they reach the store or any sink, and logging is safe from any thread.
 */
@OptIn(ExperimentalAtomicApi::class)
public object AppLog {
    private const val RAW_LIMIT = 8_000
    private const val LINE_LIMIT = 1_000
    private const val CAUSE_LIMIT = 4
    private const val DROPPED = "[line dropped: could not redact]"
    private const val DEFAULT_TAG = "App"

    private val tagShape = Regex("[A-Za-z]{1,16}")

    private val store = AtomicReference<LogStore?>(null)
    private val clock = AtomicReference<Clock>(Clock.System)
    private val sinks = AtomicReference<List<LogSink>>(emptyList())

    internal var redact: (String) -> String = LogRedactor::redact

    public fun install(
        store: LogStore,
        clock: Clock = Clock.System,
    ) {
        this.clock.store(clock)
        this.store.store(store)
    }

    public fun addSink(sink: LogSink) {
        while (true) {
            val old = sinks.load()
            if (sinks.compareAndSet(old, old + sink)) return
        }
    }

    public fun removeSink(sink: LogSink) {
        while (true) {
            val old = sinks.load()
            if (sinks.compareAndSet(old, old - sink)) return
        }
    }

    public fun i(
        tag: String,
        message: String,
    ) {
        log(LogLevel.Info, tag, message, null)
    }

    public fun w(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) {
        log(LogLevel.Warn, tag, message, error)
    }

    public fun e(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) {
        log(LogLevel.Error, tag, message, error)
    }

    /** The lines of a run, read off the main thread. Empty before [install]. */
    public suspend fun read(session: LogSession): String {
        val store = store.load() ?: return ""
        return withContext(Dispatchers.IO) { store.read(session) }
    }

    /** Blocks until the lines logged so far are on disk. */
    public fun flush() {
        store.load()?.flush()
    }

    /** For tests: drops the store and sinks and goes back to the system clock. */
    internal fun reset() {
        store.store(null)
        clock.store(Clock.System)
        sinks.store(emptyList())
        redact = LogRedactor::redact
    }

    private fun log(
        level: LogLevel,
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        val safeTag = if (tagShape.matches(tag)) tag else DEFAULT_TAG
        val line = LogLine(clock.load().now(), level, safeTag, clean(message, error))
        store.load()?.let { runCatching { it.append(line.format()) } }
        for (sink in sinks.load()) {
            try {
                sink.write(line)
            } catch (_: Throwable) {
                // A broken sink must not stop the log.
            }
        }
    }

    private fun clean(
        message: String,
        error: Throwable?,
    ): String {
        val redacted =
            try {
                val raw = if (error == null) message.take(RAW_LIMIT) else "${message.take(RAW_LIMIT)}: ${describe(error)}"
                redact(raw.take(RAW_LIMIT))
            } catch (_: Throwable) {
                return DROPPED
            }
        return if (redacted.length > LINE_LIMIT) redacted.take(LINE_LIMIT) + "…" else redacted
    }

    /** The error's type and message, then its causes', as a log line shows them. Not yet redacted. */
    internal fun describe(error: Throwable): String {
        val text = StringBuilder(summary(error))
        var cause = error.cause
        var count = 0
        while (cause != null && cause !== error && count < CAUSE_LIMIT) {
            text.append(" <- caused by ").append(summary(cause))
            cause = cause.cause
            count++
        }
        return text.toString()
    }

    private fun summary(error: Throwable): String {
        val name = error::class.simpleName ?: "Throwable"
        // A message getter can throw or return something huge.
        val message = runCatching { error.message?.take(RAW_LIMIT) }.getOrNull()
        return message?.let { "$name: $it" } ?: name
    }
}
