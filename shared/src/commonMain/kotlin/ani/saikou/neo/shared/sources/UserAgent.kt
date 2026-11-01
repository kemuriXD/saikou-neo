package ani.saikou.neo.shared.sources

/**
 * The one User-Agent every source request, Cloudflare clearance and the player
 * send. Cloudflare ties a clearance to the exact User-Agent that minted it, so
 * this stays the same across all of them and changes only on purpose.
 */
internal const val SOURCE_USER_AGENT: String =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
