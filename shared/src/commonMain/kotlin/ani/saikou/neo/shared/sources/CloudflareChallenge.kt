package ani.saikou.neo.shared.sources

/**
 * Whether a response is a Cloudflare challenge the app should try to clear, as
 * opposed to a plain block or a normal page. A managed challenge sets
 * `cf-mitigated: challenge`; an interstitial comes back as a 403 or 503 from
 * Cloudflare with a challenge marker in its body. A plain Cloudflare block, such
 * as a banned IP, is not a challenge and is left to the caller.
 */
internal fun cloudflareChallenge(
    status: Int,
    headers: Map<String, String>,
    body: String,
): Boolean {
    if (headers.valueOf("cf-mitigated").equals("challenge", ignoreCase = true)) return true
    if (status != 403 && status != 503) return false
    if (headers.valueOf("server")?.contains("cloudflare", ignoreCase = true) != true) return false
    return CHALLENGE_MARKERS.any { body.contains(it, ignoreCase = true) }
}

private val CHALLENGE_MARKERS =
    listOf("just a moment", "challenge-platform", "cf_chl", "__cf_chl", "cf-browser-verification")

private fun Map<String, String>.valueOf(name: String): String? = entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

/**
 * A Cloudflare clearance: the `cf_clearance` cookie, the User-Agent it was minted
 * with, and when it stops being usable. It is shared across sources and the
 * player, so all of them clear at once and keep playing until it expires.
 */
internal data class Clearance(
    val cookie: String,
    val userAgent: String,
    val expiresAtMillis: Long,
) {
    fun isFresh(nowMillis: Long): Boolean = nowMillis < expiresAtMillis
}
