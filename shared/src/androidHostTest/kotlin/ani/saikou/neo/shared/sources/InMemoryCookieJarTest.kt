package ani.saikou.neo.shared.sources

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryCookieJarTest {
    private val jar = InMemoryCookieJar()

    private fun cookie(
        name: String,
        value: String,
        host: String = "a.example",
        expiresAt: Long? = null,
    ): Cookie {
        val builder =
            Cookie
                .Builder()
                .name(name)
                .value(value)
                .domain(host)
        return (expiresAt?.let { builder.expiresAt(it) } ?: builder).build()
    }

    @Test
    fun aCookieComesBackForTheSameHost() {
        val url = "https://a.example/".toHttpUrl()
        jar.saveFromResponse(url, listOf(cookie("sid", "abc")))

        assertEquals(listOf("sid"), jar.loadForRequest(url).map { it.name })
    }

    @Test
    fun anotherHostSeesNothing() {
        jar.saveFromResponse("https://a.example/".toHttpUrl(), listOf(cookie("sid", "abc")))

        assertTrue(jar.loadForRequest("https://b.example/".toHttpUrl()).isEmpty())
    }

    @Test
    fun anExpiredCookieIsDropped() {
        val url = "https://a.example/".toHttpUrl()
        jar.saveFromResponse(url, listOf(cookie("old", "x", expiresAt = 1)))

        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun aNewValueReplacesTheOld() {
        val url = "https://a.example/".toHttpUrl()
        jar.saveFromResponse(url, listOf(cookie("sid", "old")))
        jar.saveFromResponse(url, listOf(cookie("sid", "new")))

        assertEquals(listOf("new"), jar.loadForRequest(url).map { it.value })
    }
}
