package ani.saikou.neo.extension.api

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What [HostServices.extract] loads and what to read back from it. The host opens
 * [embedUrl] in an offscreen WebView with [referer] and [headers], lets the site's
 * own JavaScript run, and reads back one or both of [urlPattern] and [script]. A
 * recipe needs at least one of them.
 */
public data class ExtractionRequest(
    val embedUrl: String,
    /** Sent as the Referer, when the embed needs one. */
    val referer: String? = null,
    /** Sent with the first load. */
    val headers: Map<String, String> = emptyMap(),
    /** A glob such as `*.m3u8` for the first request to read back. */
    val urlPattern: String? = null,
    /** A JavaScript expression read back once the player has settled. */
    val script: String? = null,
    /** How long to wait before giving up. */
    val timeout: Duration = 20.seconds,
) {
    init {
        require(urlPattern != null || script != null) { "A recipe needs a urlPattern or a script" }
    }

    /** Leaves out header values, which often hold cookies or tokens. */
    override fun toString(): String =
        "ExtractionRequest(embedUrl=$embedUrl, referer=$referer, headers=${headers.keys}, " +
            "urlPattern=$urlPattern, script=$script, timeout=$timeout)"
}

/** What [HostServices.extract] read back. */
public data class ExtractionResult(
    /** The URL of the first request that matched the recipe's pattern, if any. */
    val url: String? = null,
    /** The headers that request carried, so the player can repeat it. */
    val requestHeaders: Map<String, String> = emptyMap(),
    /** The value the recipe's script read back, if any. */
    val value: String? = null,
) {
    /** Leaves out header values and the read-back value, which often hold cookies or tokens. */
    override fun toString(): String =
        "ExtractionResult(url=$url, requestHeaders=${requestHeaders.keys}, " +
            "value=${if (value == null) "null" else "<hidden>"})"
}
