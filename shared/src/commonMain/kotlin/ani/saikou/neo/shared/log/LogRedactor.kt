package ani.saikou.neo.shared.log

/**
 * Strips secrets and personal details from a log line before it is saved.
 *
 * URLs keep only their scheme and host. Auth headers, tokens, secret pairs and
 * IP addresses are replaced with placeholders. Running it twice changes nothing.
 */
public object LogRedactor {
    private const val REDACTED = "[redacted]"
    private const val TOKEN = "[token]"
    private const val IP = "[ip]"

    // Scheme length is capped so long runs of scheme characters stay linear.
    private val url =
        Regex(
            """\b([a-z][a-z0-9+.\-]{0,30})(://|:\\/\\/)([^\s"'<>@?#]*@)?([^\s/\\?#"'<>]*)([^\s"'<>]*)""",
            RegexOption.IGNORE_CASE,
        )
    private val relativeUrl =
        Regex(
            """(?<![^\s"'(=,\[])//([A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)+|\[[^\]\s]*\])((?::[0-9]{1,5})?)(/[^\s"'<>]*)""",
        )
    private val query = Regex("""\?[\w%.\-]\S*""")
    private val header =
        Regex(
            """\b(Proxy-Authorization|Authorization|Set-Cookie|Cookie|X-Api-Key|X-Auth-Token)(\\?"?[ \t]*[:=][ \t]*)[^\r\n]*""",
            RegexOption.IGNORE_CASE,
        )
    private val scheme = Regex("""\b(Bearer|Basic)([ \t]+)[^\s"',;]+""", RegexOption.IGNORE_CASE)
    private val pair =
        Regex(
            "(?<![A-Za-z0-9])" +
                "(access_token|refresh_token|id_token|client_secret|api_key|apikey|password|signature|" +
                "session|secret|token|code|auth|key|sig)" +
                """(\\?"?[ \t]*[:=][ \t]*)""" +
                """(\\"(?:(?!\\")[^\r\n])*(?:\\")?|"(?:[^"\\\r\n]|\\.)*"?|'[^'\r\n]*'?|[^\s"'&,;}\\]+)""",
            RegexOption.IGNORE_CASE,
        )
    private val jwt = Regex("""\beyJ[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]*""")
    private val opaque = Regex("""[A-Za-z0-9+/_=\-]{32,}""")
    private val letter = Regex("[A-Za-z]")
    private val digit = Regex("[0-9]")
    private val ipv6 =
        Regex(
            """(?<![0-9A-Za-z:])(?:[0-9A-Fa-f]{0,4}:){2,8}[0-9A-Fa-f.]*(?:%[0-9A-Za-z_.\-]+)?(?![0-9A-Za-z:])""",
        )
    private const val OCTET = "(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])"
    private val ipv4 = Regex("""(?<![0-9.])(?:$OCTET\.){3}$OCTET(?![0-9]|\.[0-9])""")
    private val ipv4Exact = Regex("""(?:$OCTET\.){3}$OCTET""")
    private val hexGroup = Regex("[0-9A-Fa-f]{1,4}")
    private val quotes = listOf("\\\"", "\"", "'")

    /** Returns [text] with secrets, URL paths and IP addresses replaced. */
    public fun redact(text: String): String {
        var out =
            url.replace(text) { m ->
                val escaped = m.groupValues[2] != "://"
                m.groupValues[1] + m.groupValues[2] + redactHost(m.groupValues[4], m.groupValues[5], escaped)
            }
        out = relativeUrl.replace(out) { m -> "//" + redactHost(m.groupValues[1] + m.groupValues[2], m.groupValues[3], false) }
        out = query.replace(out, "")
        out = header.replace(out) { m -> m.groupValues[1] + m.groupValues[2] + REDACTED }
        out = scheme.replace(out) { m -> m.groupValues[1] + m.groupValues[2] + REDACTED }
        out = pair.replace(out) { m -> m.groupValues[1] + m.groupValues[2] + quoted(m.groupValues[3]) }
        out = jwt.replace(out, TOKEN)
        out =
            opaque.replace(out) { m ->
                if (letter.containsMatchIn(m.value) && digit.containsMatchIn(m.value)) TOKEN else m.value
            }
        out =
            ipv6.replace(out) { m ->
                val address = m.value.trimEnd('.')
                if (isIpv6(address)) IP + m.value.substring(address.length) else m.value
            }
        out = ipv4.replace(out, IP)
        return out
    }

    /** Replaces [value] with the redacted marker, keeping its quotes whether or not they close. */
    private fun quoted(value: String): String {
        val open = quotes.firstOrNull { value.startsWith(it) } ?: return REDACTED
        val closed = value.length >= open.length * 2 && value.endsWith(open)
        return open + REDACTED + if (closed) open else ""
    }

    /** Returns [authority]'s host and port with IPs hidden, plus a marker when [rest] is not empty. */
    private fun redactHost(
        authority: String,
        rest: String,
        escaped: Boolean,
    ): String {
        val hostPort = authority.substringAfterLast('@')
        val host: String
        val port: String
        if (hostPort.startsWith('[') && ']' in hostPort) {
            host = IP
            port = hostPort.substringAfter(']')
        } else {
            val colon = hostPort.lastIndexOf(':')
            val maybePort = if (colon >= 0) hostPort.substring(colon + 1) else ""
            if (colon >= 0 && maybePort.all { it.isDigit() }) {
                host = hostPort.substring(0, colon)
                port = ":$maybePort"
            } else {
                host = hostPort
                port = ""
            }
        }
        val safeHost = if (ipv4Exact.matches(host)) IP else host
        val tail =
            when {
                rest.isEmpty() -> ""
                escaped -> "\\/…"
                else -> "/…"
            }
        return "$safeHost$port$tail"
    }

    private fun isIpv6(candidate: String): Boolean {
        var address = candidate.substringBefore('%')
        val last = address.substringAfterLast(':')
        if ('.' in last) {
            if (!ipv4Exact.matches(last)) return false
            address = address.dropLast(last.length) + "0:0"
        }
        val double = address.indexOf("::")
        if (double < 0) {
            val groups = address.split(':')
            return groups.size == 8 && groups.all { hexGroup.matches(it) }
        }
        if (address.indexOf("::", double + 1) >= 0) return false
        val groups =
            listOf(address.substring(0, double), address.substring(double + 2))
                .filter { it.isNotEmpty() }
                .flatMap { it.split(':') }
        return groups.size in 1..7 && groups.all { hexGroup.matches(it) }
    }
}
