package ani.saikou.neo.shared.sources

import ani.saikou.neo.shared.catalogue.OfflineEngine
import ani.saikou.neo.shared.catalogue.SiteEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShortCodesTest {
    @Test
    fun aCodeBecomesTheLinkVgdHasForIt() =
        runTest {
            val engine = SiteEngine("vgd") { "found" }

            assertEquals("http://10.0.2.2:8000/example-source.apk", ShortCodes(engine).link("Example_1"))
            assertEquals("https://v.gd/forward.php?format=json&shorturl=Example_1", engine.urls.single())
        }

    @Test
    fun aCodeVgdDoesNotKnowHasNoLink() =
        runTest {
            assertNull(ShortCodes(SiteEngine("vgd") { "missing" }).link("nothing"))
            assertNull(ShortCodes(SiteEngine("vgd") { "disabled" }).link("gone"))
        }

    @Test
    fun whenVgdFailsThereIsNoLink() =
        runTest {
            assertNull(ShortCodes(SiteEngine("vgd", status = 502) { "found" }).link("Example_1"))
            assertNull(ShortCodes(OfflineEngine).link("Example_1"))
        }
}
