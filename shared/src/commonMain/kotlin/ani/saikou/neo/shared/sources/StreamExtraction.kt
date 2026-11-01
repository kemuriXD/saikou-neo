package ani.saikou.neo.shared.sources

/*
 * The plain logic behind the WebView stream extractor: matching a request URL
 * against a recipe's glob, deciding when a recipe has everything it asked for,
 * and reading a value back out of `evaluateJavascript`. It holds no Android types
 * so it can be tested on the host.
 */

/** Regex characters that stand for themselves in a glob and so must be escaped. */
private const val REGEX_SPECIAL = ".\\+()[]{}^$|"

/**
 * Matches [url] against a glob [pattern] such as `*.m3u8`. `*` stands for any run
 * of characters (including none) and `?` for a single one; everything else is
 * literal. The whole URL must match, so a source that wants to allow a trailing
 * query writes its own `*`, as in `*.m3u8*`.
 */
internal fun urlMatches(
    pattern: String,
    url: String,
): Boolean = globToRegex(pattern).matches(url)

/**
 * Compiles a glob [pattern] into an anchored [Regex]. The caller compiles it once
 * and reuses it, so a flood of intercepted requests doesn't recompile per request.
 * See [urlMatches] for the glob rules.
 */
internal fun globToRegex(pattern: String): Regex {
    val source =
        buildString {
            append('^')
            for (c in pattern) {
                when (c) {
                    '*' -> {
                        append(".*")
                    }

                    '?' -> {
                        append('.')
                    }

                    in REGEX_SPECIAL -> {
                        append('\\')
                        append(c)
                    }

                    else -> {
                        append(c)
                    }
                }
            }
            append('$')
        }
    return Regex(source)
}

/**
 * Whether an extraction has everything its recipe asked for. A recipe that set a
 * url pattern ([urlWanted]) is done once a request matched it ([matchedUrl]); one
 * that set a script ([scriptWanted]) is done once the script read back a non-blank
 * [scriptValue]; one that set both needs both.
 */
internal fun extractionDone(
    urlWanted: Boolean,
    scriptWanted: Boolean,
    matchedUrl: String?,
    scriptValue: String?,
): Boolean =
    (!urlWanted || matchedUrl != null) &&
        (!scriptWanted || !scriptValue.isNullOrBlank())

/**
 * Turns one `evaluateJavascript` result into a plain string. The WebView hands
 * back a JSON literal: `null` (or a Kotlin null) when the value isn't there yet, a
 * quoted and escaped string for a string result, or a bare token for a number or
 * boolean. A quoted string is unquoted and unescaped; anything else is returned as
 * it came.
 */
internal fun decodeJsEvalResult(raw: String?): String? {
    if (raw == null || raw == "null") return null
    if (raw.length >= 2 && raw.first() == '"' && raw.last() == '"') {
        return unescapeJsonString(raw.substring(1, raw.length - 1))
    }
    return raw
}

private fun unescapeJsonString(body: String): String =
    buildString {
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c != '\\' || i == body.length - 1) {
                append(c)
                i++
                continue
            }
            when (val next = body[i + 1]) {
                '"' -> {
                    append('"')
                }

                '\\' -> {
                    append('\\')
                }

                '/' -> {
                    append('/')
                }

                'n' -> {
                    append('\n')
                }

                't' -> {
                    append('\t')
                }

                'r' -> {
                    append('\r')
                }

                'b' -> {
                    append('\b')
                }

                'f' -> {
                    append('\u000C')
                }

                'u' -> {
                    val hex = body.substring(i + 2, minOf(i + 6, body.length))
                    val code = hex.toIntOrNull(16)
                    if (hex.length == 4 && code != null) {
                        append(code.toChar())
                        i += 6
                        continue
                    }
                    append(c)
                    append(next)
                }

                else -> {
                    append(c)
                    append(next)
                }
            }
            i += 2
        }
    }
