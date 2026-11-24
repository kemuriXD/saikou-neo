package ani.saikou.neo.shared.log

import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class AppLogTest {
    private class MemoryStore : LogStore {
        val lines = mutableListOf<String>()

        override fun append(line: String) {
            lines += line
        }

        override fun read(session: LogSession): String = if (session == LogSession.Current) lines.joinToString("") { it + "\n" } else ""

        override fun flush() = Unit
    }

    private val store = MemoryStore()
    private val sink = RecordingSink()

    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-11-24T10:15:30.123Z")
        }

    private fun install() {
        AppLog.install(store, clock)
        AppLog.addSink(sink)
    }

    @AfterTest
    fun cleanUp() = AppLog.reset()

    @Test
    fun lineIsFormatted() {
        install()
        AppLog.w("Player", "stopped")
        assertEquals(listOf("2026-11-24T10:15:30.123Z W Player: stopped"), store.lines)
    }

    @Test
    fun messageIsRedactedBeforeStoreAndSinks() {
        install()
        AppLog.i("Net", "GET https://x.test/a?token=abc")
        val stored = store.lines.single()
        val sunk = sink.lines.single().message
        for (text in listOf(stored, sunk)) {
            assertFalse("abc" in text, text)
            assertFalse("?" in text, text)
        }
    }

    @Test
    fun throwableTextIsRedacted() {
        install()
        AppLog.e("Net", "failed", IOException("https://x.test/p?sig=zz", SocketTimeoutException("10.0.0.5")))
        assertTrue(
            store.lines.single().endsWith("failed: IOException: https://x.test/… <- caused by SocketTimeoutException: [ip]"),
            store.lines.single(),
        )
    }

    @Test
    fun throwingSinkDoesNotBreakLogging() {
        AppLog.install(store, clock)
        AppLog.addSink { error("broken") }
        AppLog.addSink(sink)
        AppLog.i("App", "hello")
        assertEquals(1, store.lines.size)
        assertEquals(1, sink.lines.size)
    }

    @Test
    fun removedSinkGetsNothing() {
        install()
        AppLog.removeSink(sink)
        AppLog.i("App", "hello")
        assertTrue(sink.lines.isEmpty())
    }

    @Test
    fun longMessageIsCut() {
        install()
        AppLog.i("App", "a".repeat(5000))
        val message = sink.lines.single().message
        assertEquals(1001, message.length)
        assertTrue(message.endsWith("…"))
        assertTrue(store.lines.single().endsWith("a…"))
    }

    @Test
    fun redactionFailureDropsLine() {
        install()
        AppLog.redact = { throw StackOverflowError() }
        AppLog.i("App", "secret")
        assertEquals("[line dropped: could not redact]", sink.lines.single().message)
        assertFalse("secret" in store.lines.single())
    }

    @Test
    fun throwingMessageGetterDoesNotThrow() {
        install()
        val bad =
            object : RuntimeException() {
                override val message: String get() = throw IllegalStateException("boom")
            }
        AppLog.e("Net", "failed", IOException("outer", bad))
        val line = store.lines.single()
        assertTrue(line.contains("failed: IOException: outer <- caused by "), line)
    }

    @Test
    fun hugeErrorMessageIsCut() {
        install()
        AppLog.e("Net", "failed", IOException("a b ".repeat(5_000_000)))
        assertTrue(
            sink.lines
                .single()
                .message.length <= 1001,
        )
    }

    @Test
    fun oddTagIsReplaced() {
        install()
        AppLog.i("token=abc123", "hello")
        AppLog.i("Player", "hello")
        AppLog.i("", "hello")
        assertEquals(listOf("App", "Player", "App"), sink.lines.map { it.tag })
        assertFalse(store.lines.any { "abc123" in it })
    }

    @Test
    fun readBeforeInstallIsEmpty() {
        AppLog.reset()
        assertEquals("", runBlocking { AppLog.read(LogSession.Current) })
    }

    @Test
    fun linesBeforeInstallReachSinksOnly() {
        AppLog.addSink(sink)
        AppLog.i("App", "early")
        assertEquals(1, sink.lines.size)
    }
}
