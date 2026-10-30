package ani.saikou.neo.shared.catalogue

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KitsuClientTest {
    @Test
    fun aTypoFindsTheAniListIdOfTheShowItMeant() =
        runTest {
            val engine = SiteEngine("kitsu") { "sakamota-dyas" }

            assertEquals(177709, KitsuClient(engine).aniListId("sakamota dyas"))
            val url = engine.urls.single()
            assertTrue(url.startsWith("https://kitsu.app/api/edge/anime?"))
            assertTrue("filter%5Btext%5D=sakamota%20dyas" in url)
            assertTrue("page%5Blimit%5D=1" in url)
        }

    @Test
    fun theTopMatchIsTheOneSuggested() =
        runTest {
            assertEquals(1735, KitsuClient(SiteEngine("kitsu") { "naurto" }).aniListId("naurto"))
        }

    @Test
    fun nothingFoundSuggestsNothing() =
        runTest {
            assertNull(KitsuClient(SiteEngine("kitsu") { "none" }).aniListId("zzzzqqq"))
        }

    @Test
    fun aMatchAniListDoesNotKnowSuggestsNothing() =
        runTest {
            assertNull(KitsuClient(SiteEngine("kitsu") { "no-anilist" }).aniListId("naurto"))
        }

    @Test
    fun whenKitsuFailsThereIsNoSuggestion() =
        runTest {
            assertNull(KitsuClient(SiteEngine("kitsu", status = 500) { "none" }).aniListId("naurto"))
            assertNull(KitsuClient(OfflineEngine).aniListId("naurto"))
        }
}
