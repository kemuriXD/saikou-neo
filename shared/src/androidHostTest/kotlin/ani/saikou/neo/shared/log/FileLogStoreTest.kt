package ani.saikou.neo.shared.log

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileLogStoreTest {
    private val dir = createTempDirectory("logs").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun startMovesCurrentToPrevious() {
        val a = FileLogStore(dir)
        a.append("one")
        a.flush()
        val b = FileLogStore(dir)
        assertEquals("one\n", b.read(LogSession.Previous))
        assertEquals("", b.read(LogSession.Current))
    }

    @Test
    fun onlyTwoSessionsKept() {
        for (text in listOf("one", "two")) {
            FileLogStore(dir).apply {
                append(text)
                flush()
            }
        }
        val third = FileLogStore(dir)
        third.append("three")
        third.flush()
        assertEquals("two\n", third.read(LogSession.Previous))
        assertEquals(setOf("current.log", "previous.log"), dir.list()!!.toSet())
    }

    @Test
    fun flushWritesPendingLines() {
        val store = FileLogStore(dir)
        repeat(100) { store.append("line $it") }
        store.flush()
        assertEquals(100, File(dir, "current.log").readLines().size)
    }

    @Test
    fun currentIsCapped() {
        val store = FileLogStore(dir, capBytes = 1024)
        repeat(200) { store.append("%03d".format(it) + "x".repeat(37)) }
        store.flush()
        val file = File(dir, "current.log")
        assertTrue(file.length() <= 1024, "${file.length()}")
        val lines = file.readLines()
        assertTrue(lines.first().length == 40, lines.first())
        assertEquals("199", lines.last().take(3))
    }

    @Test
    fun rotationRunsOffTheCallingThread() {
        File(dir, "current.log").writeText("one\n")
        val gate = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        executor.execute { gate.await() }
        val store = FileLogStore(dir, executor = executor)
        assertFalse(File(dir, "previous.log").exists())
        assertEquals("one\n", File(dir, "current.log").readText())
        gate.countDown()
        assertEquals("one\n", store.read(LogSession.Previous))
        assertEquals("", store.read(LogSession.Current))
        executor.shutdown()
    }

    @Test
    fun failedRenameKeepsOldLines() {
        File(dir, "current.log").writeText("one\n")
        File(dir, "previous.log/blocker").apply {
            parentFile!!.mkdirs()
            writeText("x")
        }
        val store = FileLogStore(dir)
        store.append("two")
        assertEquals("one\ntwo\n", store.read(LogSession.Current))
    }
}
