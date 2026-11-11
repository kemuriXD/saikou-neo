package ani.saikou.neo.shared.sources

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudflareChallengeTest {
    @Test
    fun aManagedChallengeHeaderIsAChallenge() {
        assertTrue(cloudflareChallenge(status = 403, headers = mapOf("cf-mitigated" to "challenge"), body = ""))
    }

    @Test
    fun theChallengeHeaderIsReadWhateverItsCase() {
        assertTrue(cloudflareChallenge(status = 403, headers = mapOf("CF-Mitigated" to "challenge"), body = ""))
    }

    @Test
    fun aCloudflareChallengePageIsAChallenge() {
        assertTrue(
            cloudflareChallenge(
                status = 503,
                headers = mapOf("Server" to "cloudflare"),
                body = "<html><head><title>Just a moment...</title></head></html>",
            ),
        )
    }

    @Test
    fun aPlainPageFromCloudflareIsNotAChallenge() {
        assertFalse(cloudflareChallenge(status = 200, headers = mapOf("Server" to "cloudflare"), body = "<html>the page</html>"))
    }

    @Test
    fun aForbiddenPageFromAnotherServerIsNotAChallenge() {
        assertFalse(cloudflareChallenge(status = 403, headers = mapOf("Server" to "nginx"), body = "Just a moment"))
    }

    @Test
    fun aPlainCloudflareBlockWithoutChallengeMarkersIsNotAChallenge() {
        assertFalse(
            cloudflareChallenge(status = 403, headers = mapOf("Server" to "cloudflare"), body = "error 1006: your IP is banned"),
        )
    }
}

class ClearanceTest {
    private val clearance =
        Clearance(cookie = "cf_clearance=abc", userAgent = "a-user-agent", mintedAtMillis = 100, expiresAtMillis = 1_000)

    @Test
    fun aClearanceIsFreshBeforeItExpires() {
        assertTrue(clearance.isFresh(nowMillis = 999))
    }

    @Test
    fun aClearanceIsStaleFromItsExpiryOnward() {
        assertFalse(clearance.isFresh(nowMillis = 1_000))
        assertFalse(clearance.isFresh(nowMillis = 1_001))
    }

    @Test
    fun aClearanceMintedAfterARequestWasSentCanServeItsRetry() {
        assertTrue(clearance.canRetry(sentAtMillis = 100, nowMillis = 500))
    }

    @Test
    fun aClearanceMintedBeforeARequestWasSentIsTheOneItWasRefusedWith() {
        assertFalse(clearance.canRetry(sentAtMillis = 101, nowMillis = 500))
    }

    @Test
    fun aStaleClearanceCantServeARetry() {
        assertFalse(clearance.canRetry(sentAtMillis = 0, nowMillis = 1_000))
    }
}
