package ani.saikou.neo.shared.sources

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClearanceStoreTest {
    private val jar = InMemoryCookieJar()

    private fun clearance(
        token: String = "abc",
        userAgent: String = "this-webview",
        expiresAtMillis: Long = 2_000,
    ) = Clearance("cf_clearance=$token", userAgent, mintedAtMillis = 500, expiresAtMillis = expiresAtMillis)

    private class FakeStore(
        var saved: Map<String, Clearance> = emptyMap(),
    ) : ClearanceStore {
        override fun load(): Map<String, Clearance> = saved

        override fun save(clearances: Map<String, Clearance>) {
            saved = clearances
        }
    }

    @Test
    fun aSavedClearanceIsSentAfterARestart() {
        val store = FakeStore(mapOf("a.example" to clearance()))

        val seeded = seedClearances(store, jar, userAgent = "this-webview", nowMillis = 1_000)

        assertEquals(setOf("a.example"), seeded.keys)
        assertEquals(listOf("cf_clearance=abc"), jar.loadForRequest("https://a.example/".toHttpUrl()).map { "${it.name}=${it.value}" })
    }

    @Test
    fun aClearanceMintedForAnotherUserAgentIsDropped() {
        // Cloudflare binds a clearance to the browser it was minted for, and a WebView update changes that (ADR 0030).
        val store = FakeStore(mapOf("a.example" to clearance(userAgent = "older-webview")))

        val seeded = seedClearances(store, jar, userAgent = "this-webview", nowMillis = 1_000)

        assertTrue(seeded.isEmpty())
        assertTrue(jar.loadForRequest("https://a.example/".toHttpUrl()).isEmpty())
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun anExpiredClearanceIsDropped() {
        val store = FakeStore(mapOf("a.example" to clearance(expiresAtMillis = 1_000)))

        assertTrue(seedClearances(store, jar, userAgent = "this-webview", nowMillis = 1_000).isEmpty())
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun aClearanceReadsBackAsSaved() {
        val saved = clearance()

        assertEquals(saved, decodeClearance(encodeClearance(saved)))
    }

    @Test
    fun aDamagedEntryReadsAsNone() {
        assertNull(decodeClearance("not a clearance"))
        assertNull(decodeClearance("cf_clearance=abc\nua\nsoon\n2000"))
    }
}
