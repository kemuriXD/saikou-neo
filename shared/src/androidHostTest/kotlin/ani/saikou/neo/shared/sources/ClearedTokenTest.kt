package ani.saikou.neo.shared.sources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClearedTokenTest {
    @Test
    fun aCookieSetWhileTheChallengeIsRunningDoesNotCount() {
        // Cloudflare sets cf_clearance about a second in, long before its check is over.
        assertNull(clearedToken("cf_clearance=early", onChallengePage = true))
    }

    @Test
    fun theCookieCountsOnceThePageHasLeftTheChallenge() {
        assertEquals("real", clearedToken("a=b; cf_clearance=real; c=d", onChallengePage = false))
    }

    @Test
    fun aPageWithoutTheCookieHasNoClearance() {
        assertNull(clearedToken("a=b", onChallengePage = false))
        assertNull(clearedToken(null, onChallengePage = false))
        assertNull(clearedToken("cf_clearance=", onChallengePage = false))
    }
}
