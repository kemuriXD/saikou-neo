package ani.saikou.neo.shared.sources

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Keeps cookies in memory, by host, and hands them back to later requests to the
 * same host. One jar is shared across every source and the player, so a
 * `cf_clearance` or session cookie one request earns is sent by the next. A new
 * value for a name replaces the old one, and expired cookies are dropped.
 */
internal class InMemoryCookieJar : CookieJar {
    private val byHost = mutableMapOf<String, MutableList<Cookie>>()

    @Synchronized
    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        val kept = byHost.getOrPut(url.host) { mutableListOf() }
        for (cookie in cookies) {
            kept.removeAll { it.name == cookie.name }
            kept.add(cookie)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val kept = byHost[url.host] ?: return emptyList()
        kept.removeAll { it.expiresAt < now }
        return kept.filter { it.matches(url) }
    }
}
