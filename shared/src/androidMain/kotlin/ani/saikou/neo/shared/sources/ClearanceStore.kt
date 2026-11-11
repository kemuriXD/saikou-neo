package ani.saikou.neo.shared.sources

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Keeps Cloudflare clearances, by host, across app restarts. */
internal interface ClearanceStore {
    fun load(): Map<String, Clearance>

    /** Replaces everything kept with [clearances]. */
    fun save(clearances: Map<String, Clearance>)
}

/** Keeps clearances in the app's own preferences, which no other app can read. */
internal class PrefsClearanceStore(
    context: Context,
) : ClearanceStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): Map<String, Clearance> =
        prefs.all.mapNotNull { (host, value) -> (value as? String)?.let(::decodeClearance)?.let { host to it } }.toMap()

    override fun save(clearances: Map<String, Clearance>) {
        prefs
            .edit()
            .clear()
            .apply { clearances.forEach { (host, clearance) -> putString(host, encodeClearance(clearance)) } }
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "cloudflare_clearances"
    }
}

/**
 * Puts the clearances in [store] that still work into [cookieJar], so a restart doesn't need a new browser check, and
 * returns them. A clearance only works for the User-Agent it was minted with (ADR 0030), so one minted for another
 * is dropped, as is one that has expired. The store is left holding only those returned.
 */
internal fun seedClearances(
    store: ClearanceStore,
    cookieJar: CookieJar,
    userAgent: String,
    nowMillis: Long,
): Map<String, Clearance> {
    val saved = store.load()
    val usable = saved.filterValues { it.userAgent == userAgent && it.isFresh(nowMillis) }
    if (usable != saved) store.save(usable)
    usable.forEach { (host, clearance) ->
        val url =
            HttpUrl
                .Builder()
                .scheme("https")
                .host(host)
                .build()
        cookieJar.saveFromResponse(url, listOf(clearanceCookie(host, clearance.cookie.substringAfter("="))))
    }
    return usable
}

/** The `cf_clearance` cookie for [host]. It is sent until the site turns it down, as the WebView would. */
internal fun clearanceCookie(
    host: String,
    token: String,
): Cookie =
    Cookie
        .Builder()
        .name("cf_clearance")
        .value(token)
        .domain(host)
        .build()

internal fun encodeClearance(clearance: Clearance): String =
    listOf(clearance.cookie, clearance.userAgent, clearance.mintedAtMillis, clearance.expiresAtMillis).joinToString("\n")

/** The clearance [encoded] holds, or null if it isn't one. */
internal fun decodeClearance(encoded: String): Clearance? {
    val parts = encoded.split("\n")
    if (parts.size != 4) return null
    val minted = parts[2].toLongOrNull() ?: return null
    val expires = parts[3].toLongOrNull() ?: return null
    return Clearance(parts[0], parts[1], minted, expires)
}
