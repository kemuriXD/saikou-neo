package ani.saikou.neo.tv.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val ID = "com.example.alpha"
private const val LINK = "https://example.com/list.json"
private const val NEWER = "https://example.com/newer.json"

class SourceLinksTest {
    @get:Rule val folder = TemporaryFolder()

    private val links by lazy { SourceLinks(folder.root) }

    // Kept next to the source's APK, as <id>.list.
    private val file by lazy { File(folder.root, "$ID.list") }

    @Test
    fun aListInstallSavesTheLink() {
        assertEquals(LINK, links.keep(ID, LINK))

        assertEquals(LINK, file.readText())
        assertEquals(LINK, links.read(ID))
    }

    @Test
    fun anInstallFromAnotherListReplacesTheLink() {
        links.keep(ID, LINK)

        assertEquals(NEWER, links.keep(ID, NEWER))

        assertEquals(NEWER, links.read(ID))
    }

    @Test
    fun aSingleApkReinstallClearsTheLink() {
        links.keep(ID, LINK)

        assertNull(links.keep(ID, null))

        assertFalse(file.exists())
        assertNull(links.read(ID))
    }

    @Test
    fun forgettingASourceDeletesItsListFile() {
        links.keep(ID, LINK)

        links.forget(ID)

        assertFalse(file.exists())
    }

    @Test
    fun aSourceWithNoFileHasNoLink() {
        assertNull(links.read(ID))
    }

    @Test
    fun aBlankFileReadsAsNoLink() {
        file.writeText("  \n")

        assertNull(links.read(ID))
    }

    @Test
    fun anUnreadableFileReadsAsNoLink() {
        // A folder where the file should be can't be read as text.
        assertTrue(file.mkdir())

        assertNull(links.read(ID))
    }

    @Test
    fun aLinkThatCannotBeWrittenKeepsTheOldOne() {
        links.keep(ID, LINK)
        assertTrue(file.setReadOnly())

        assertEquals(LINK, links.keep(ID, NEWER))

        assertEquals(LINK, links.read(ID))
        // So the folder can be cleaned up.
        file.setWritable(true)
    }
}
