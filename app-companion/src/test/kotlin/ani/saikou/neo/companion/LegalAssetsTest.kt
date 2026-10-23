package ani.saikou.neo.companion

import org.junit.Assert.assertFalse
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
    fun shipsTheDisclaimer() {
        assertTrue(asset("DISCLAIMER.md").contains("does not host"))
    }

    @Test
    fun shipsThePrivacyPolicy() {
        assertTrue(asset("PRIVACY.md").contains("# Privacy policy"))
    }

    @Test
    fun hasNoExtensionLinkingPermission() {
        assertFalse(
            RuntimeEnvironment
                .getApplication()
                .assets
                .list("legal")!!
                .contains("EXTENSION-LINKING-PERMISSION.md"),
        )
    }

    @Test
    fun shipsTheNoticesAndEveryLicenceTheyLinkTo() {
        val notices = asset("THIRD_PARTY_NOTICES.md")
        val linked = Regex("""\((licenses/[^)]+)\)""").findAll(notices).map { it.groupValues[1] }.toSet()
        assertTrue(linked.isNotEmpty())
        linked.forEach { assertTrue(it, asset(it).isNotBlank()) }
    }
}
