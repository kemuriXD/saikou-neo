package ani.saikou.neo.shared.sources

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import ani.saikou.neo.extension.api.ExtractionRequest
import ani.saikou.neo.extension.api.ExtractionResult
import ani.saikou.neo.extension.api.SourceException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** How often the recipe's script is re-read while the player settles. */
private const val POLL_INTERVAL_MS: Long = 250

/**
 * Resolves a stream a site only exposes after its own player runs. It loads the
 * embed in an offscreen [WebView] with the pinned [userAgent], lets the site's
 * JavaScript run, and reads back what the recipe asks for: the first request
 * whose URL matches the glob (with the headers it carried) and/or a value polled
 * out of the page. It gives up at the request's timeout as [SourceException.SourceDown].
 *
 * Standalone the way [SourceHttpClient] is; the full `HostServices` is assembled
 * elsewhere. Backs `HostServices.extract`.
 *
 * The WebView is bridge-free (no `addJavascriptInterface`), reads no local files,
 * keeps its top frame on the embed's host so the recipe's script only ever runs
 * on that origin, and is stopped and destroyed after each call. If the untrusted
 * page kills its own renderer the host app survives and the call fails cleanly.
 * It shares the process-wide [CookieManager] with the Cloudflare clearer so a
 * source keeps the clearance the clearer earned; it is host-scoped the same way
 * and never clears that store.
 */
internal class WebViewStreamExtractor(
    context: Context,
    private val userAgent: String = SOURCE_USER_AGENT,
) {
    // The application context, so the WebView never holds an Activity.
    private val appContext = context.applicationContext

    // ADR 0029 runs one offscreen extraction WebView at a time, so a burst of embeds can't multiply the memory.
    private val oneAtATime = Mutex()

    suspend fun extract(request: ExtractionRequest): ExtractionResult =
        oneAtATime.withLock {
            // The WebView must be built and driven on the main thread.
            val result =
                withContext(Dispatchers.Main.immediate) {
                    val session = ExtractionSession(request.urlPattern, request.embedUrl)
                    var webView: WebView? = null
                    try {
                        webView = buildWebView(session)
                        withTimeoutOrNull(request.timeout) {
                            loadEmbed(webView, request)
                            awaitResult(webView, request, session)
                        }
                    } finally {
                        webView?.stopLoading()
                        webView?.destroy()
                    }
                }
            result ?: throw SourceException.SourceDown("Extraction timed out after ${request.timeout}")
        }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun buildWebView(session: ExtractionSession): WebView =
        WebView(appContext).apply {
            // Share the clearer's cookies for this host, but never wipe the process store.
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.apply {
                javaScriptEnabled = true
                userAgentString = userAgent
                // Players keep their state in DOM storage; this is not a bridge back to the app.
                domStorageEnabled = true
                // The player has to fetch the stream on its own for us to capture it.
                mediaPlaybackRequiresUserGesture = false
                // No local files or app content: the embed sees only its own origin over the network.
                allowFileAccess = false
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                // Defence in depth: the site can't open popups, extra windows, or ask for location.
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                setGeolocationEnabled(false)
            }
            // Deliberately no addJavascriptInterface: the site's JavaScript has no way back into the app.
            webViewClient =
                object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        req: WebResourceRequest,
                    ): WebResourceResponse? {
                        // Only read the request; returning null lets it load as usual.
                        session.captured.offer(req.url.toString(), req.requestHeaders)
                        return null
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        req: WebResourceRequest,
                    ): Boolean {
                        // Keep the top frame on the embed's host so a hostile redirect can't move the
                        // whole page to another origin and have the recipe's script run there. Subframes
                        // and subresources (the stream's own CDN included) are free to load.
                        if (!req.isForMainFrame) return false
                        return !session.sameHostAsEmbed(req.url)
                    }

                    override fun onRenderProcessGone(
                        view: WebView,
                        detail: RenderProcessGoneDetail,
                    ): Boolean {
                        // The renderer died, often a hostile page running itself out of memory. Fail this
                        // call rather than hang, and return true so the framework doesn't kill the app.
                        session.fatal.complete(SourceException.SourceDown("The extraction WebView's renderer stopped"))
                        return true
                    }
                }
        }

    private fun loadEmbed(
        webView: WebView,
        request: ExtractionRequest,
    ) {
        val headers =
            buildMap {
                putAll(request.headers)
                request.referer?.let { put("Referer", it) }
            }
        webView.loadUrl(request.embedUrl, headers)
    }

    private suspend fun awaitResult(
        webView: WebView,
        request: ExtractionRequest,
        session: ExtractionSession,
    ): ExtractionResult =
        coroutineScope {
            val poll = async { pollForResult(webView, request, session) }
            try {
                select {
                    // A dead renderer fails the whole call; it won't ever finish the poll.
                    session.fatal.onAwait { throw it }
                    poll.onAwait { it }
                }
            } finally {
                poll.cancel()
            }
        }

    private suspend fun pollForResult(
        webView: WebView,
        request: ExtractionRequest,
        session: ExtractionSession,
    ): ExtractionResult {
        val scriptWanted = request.script != null
        while (true) {
            val scriptValue = if (scriptWanted) decodeJsEvalResult(webView.evaluate(request.script!!)) else null
            val match = session.captured.match()
            if (extractionDone(request.urlPattern != null, scriptWanted, match?.url, scriptValue)) {
                return ExtractionResult(
                    url = match?.url,
                    requestHeaders = match?.headers ?: emptyMap(),
                    value = scriptValue,
                )
            }
            delay(POLL_INTERVAL_MS)
        }
    }
}

/** Reads one `evaluateJavascript` call back as a suspending call. */
private suspend fun WebView.evaluate(script: String): String? =
    suspendCancellableCoroutine { cont ->
        evaluateJavascript(script) { value -> cont.resume(value) }
    }

/** Per-call state the WebView's client and the poll loop share. */
private class ExtractionSession(
    pattern: String?,
    embedUrl: String,
) {
    private val embedHost = Uri.parse(embedUrl).host

    val captured = CapturedRequest(pattern)

    /** Completed when the call can't go on, so the awaiting coroutine fails instead of waiting out the timeout. */
    val fatal = CompletableDeferred<SourceException>()

    /** Whether [url] is on the embed's host. True when the embed's host is unknown, so we don't over-block. */
    fun sameHostAsEmbed(url: Uri): Boolean {
        val host = embedHost ?: return true
        return url.host?.equals(host, ignoreCase = true) == true
    }
}

/** The first request whose URL matched, captured from the WebView's own thread. */
private class CapturedRequest(
    pattern: String?,
) {
    data class Match(
        val url: String,
        val headers: Map<String, String>,
    )

    // Compiled once; the pattern never changes for the life of a call.
    private val regex = pattern?.let { globToRegex(it) }
    private var match: Match? = null

    @Synchronized
    fun offer(
        url: String,
        headers: Map<String, String>,
    ) {
        if (regex == null || match != null) return
        if (regex.matches(url)) match = Match(url, headers.toMap())
    }

    @Synchronized
    fun match(): Match? = match
}
