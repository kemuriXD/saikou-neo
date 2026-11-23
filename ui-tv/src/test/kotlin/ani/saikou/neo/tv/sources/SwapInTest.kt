package ani.saikou.neo.tv.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class SwapInTest {
    @get:Rule val folder = TemporaryFolder()

    private val kept by lazy { File(folder.root, "com.example.alpha.apk").apply { writeText("OLD") } }

    @Test
    fun theNewFileReplacesTheOldOne() {
        val incoming = folder.newFile("incoming").apply { writeText("NEW") }

        swapIn(incoming, kept)

        assertEquals("NEW", kept.readText())
        assertFalse(kept.canWrite())
        assertEquals(listOf(kept.name, "incoming"), folder.root.list()?.sorted())
    }

    @Test
    fun aCopyThatFailsKeepsTheOldVersion() {
        val missing = File(folder.root, "gone")
        kept.readText()

        val failed =
            try {
                swapIn(missing, kept)
                false
            } catch (_: IOException) {
                true
            }

        assertTrue(failed)
        assertEquals("OLD", kept.readText())
    }
}
