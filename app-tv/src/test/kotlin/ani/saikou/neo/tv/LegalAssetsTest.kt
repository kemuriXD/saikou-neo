package ani.saikou.neo.tv

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LegalAssetsTest {
    private fun asset(path: String): String =
        RuntimeEnvironment
            .getApplication()
            .assets
            .open("legal/$path")
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun shipsTheGplLicence() {
        assertTrue(asset("LICENSE").contains("GNU GENERAL PUBLIC LICENSE"))
        assertTrue(asset("LICENSE").contains("Version 3, 29 June 2007"))
    }

    @Test
    fun shipsTheExtensionLinkingPermission() {
        assertTrue(asset("EXTENSION-LINKING-PERMISSION.md").contains("section 7"))
    }

    @Test
    fun shipsTheNoticesAndEveryLicenceTheyLinkTo() {
        val notices = asset("THIRD_PARTY_NOTICES.md")
        val linked = Regex("""\((licenses/[^)]+)\)""").findAll(notices).map { it.groupValues[1] }.toSet()
        assertTrue(linked.isNotEmpty())
        linked.forEach { assertTrue(it, asset(it).isNotBlank()) }
    }
}
