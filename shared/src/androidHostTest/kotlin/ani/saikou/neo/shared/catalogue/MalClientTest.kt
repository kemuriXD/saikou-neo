package ani.saikou.neo.shared.catalogue

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MalClientTest {
    @Test
    fun theScoreIsOutOfAHundred() =
        runTest {
            val engine = SiteEngine("mal") { "anime" }

            // 8.65 rounds up.
            assertEquals(87, MalClient(engine).score(61987))
            assertEquals("https://api.tenrai.org/v1/anime/61987", engine.urls.single())
        }

    @Test
    fun aShowNobodyHasScoredHasNoScore() =
        runTest {
            assertNull(MalClient(SiteEngine("mal") { "unscored" }).score(61987))
        }

    @Test
    fun aShowMalDoesNotKnowHasNoScore() =
        runTest {
            assertNull(MalClient(SiteEngine("mal", status = 404) { "not-found" }).score(99999999))
        }

    @Test
    fun whileRateLimitedThereIsNoScore() =
        runTest {
            assertNull(MalClient(SiteEngine("mal", status = 429) { "rate-limited" }).score(61987))
        }

    @Test
    fun withoutAConnectionThereIsNoScore() =
        runTest {
            assertNull(MalClient(OfflineEngine).score(61987))
        }
}
