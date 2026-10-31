package ani.saikou.neo.shared.sources

/** What was typed in Add a source: a link to the source's APK, or a v.gd short code for one. */
public sealed interface SourceAddress {
    public data class Link(
        val url: String,
    ) : SourceAddress

    public data class Code(
        val code: String,
    ) : SourceAddress

    public companion object {
        private val code = Regex("[A-Za-z0-9_-]{1,40}")

        /** [text] as a link when it starts with http:// or https://, else as a code, or null when it's neither. */
        public fun parse(text: String): SourceAddress? {
            val trimmed = text.trim()
            return when {
                scheme(trimmed) != null -> Link(trimmed)
                code.matches(trimmed) -> Code(trimmed)
                else -> null
            }
        }
    }
}

/**
 * Whether the app may download a source from [url]. A source is code the app runs, so it must come over https. Plain
 * http is allowed only for addresses at home, such as a PC on the same network, where a source is tested.
 */
public fun isAllowedSourceLink(url: String): Boolean {
    val scheme = scheme(url) ?: return false
    val host = host(url.substring(scheme.length + "://".length)) ?: return false
    return scheme == "https" || isHome(host)
}

private fun scheme(url: String): String? = listOf("https", "http").firstOrNull { url.startsWith("$it://", ignoreCase = true) }

/**
 * The host in what follows `scheme://`, or null when there's none, the link has spaces or a bad port, or it names a
 * user, which could hide the real host.
 */
private fun host(rest: String): String? {
    if (rest.any { it.isWhitespace() }) return null
    val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    if ('@' in authority) return null
    if (':' in authority) {
        val port = authority.substringAfter(':').toIntOrNull()
        if (port == null || port !in 1..65535) return null
    }
    return authority.substringBefore(':').lowercase().takeIf { it.isNotEmpty() }
}

/**
 * localhost, or an IPv4 address on a private network: 127/8, 10/8, 172.16/12 or 192.168/16. A part with a leading zero
 * is refused, since some resolvers read it as octal.
 */
private fun isHome(host: String): Boolean {
    if (host == "localhost") return true
    val parts =
        host.split('.').map { part ->
            if (part.length > 1 && part.startsWith('0')) return false
            part.toIntOrNull()?.takeIf { it in 0..255 && part.length <= 3 } ?: return false
        }
    if (parts.size != 4) return false
    val (a, b) = parts
    return a == 127 || a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
}
