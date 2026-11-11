package ani.saikou.neo.shared.sources

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import ani.saikou.neo.extension.api.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Mints a Cloudflare clearance by loading the site in an offscreen WebView with
 * the pinned User-Agent and letting Cloudflare's own JavaScript run. Once the page
 * has left the challenge, its `cf_clearance` cookie goes into the shared
 * [cookieJar], so the next request and the player send it. It gives up at
 * [timeout] (a challenge needing a person), returning null, and reports a page
 * that won't load as [SourceException.SourceDown]. The WebView has no bridge back
 * into the app and is destroyed after each call. Each clearance is kept in
 * [store], and those still usable go back into [cookieJar] when this starts, so a
 * restart doesn't mean a new check.
 */
internal class WebViewCloudflareClearer(
    private val context: Context,
    private val cookieJar: CookieJar,
    private val userAgent: String = SOURCE_USER_AGENT,
    private val timeout: Duration = SITE_CHECK_LIMIT,
    private val clearanceLifetime: Duration = 30.minutes,
    private val store: ClearanceStore? = null,
) : CloudflareClearer {
    private val lastByHost: MutableMap<String, Clearance> =
        store?.let { seedClearances(it, cookieJar, userAgent, System.currentTimeMillis()).toMutableMap() } ?: mutableMapOf()
    private val checks = SiteChecks()

    /** Whether a check is running now, for those waiting on a source to allow for it. */
    val checking: StateFlow<Boolean> = checks.checking

    // One WebView at a time: asking again while a check runs waits for it and takes its clearance, or its failure.
    override suspend fun clear(
        url: HttpUrl,
        sentAtMillis: Long,
    ): Clearance? =
        withContext(Dispatchers.Main) {
            checks.run(url.host) { clearNow(url, sentAtMillis) }
        }

    private suspend fun clearNow(
        url: HttpUrl,
        sentAtMillis: Long,
    ): Clearance? =
        withContext(Dispatchers.Main) {
            lastByHost[url.host]?.let { if (it.canRetry(sentAtMillis, System.currentTimeMillis())) return@withContext it }

            val webView = WebView(context.applicationContext)
            try {
                val failed = AtomicBoolean(false)
                val loaded = AtomicBoolean(false)
                prepare(webView, failed, loaded)
                webView.loadUrl(url.toString())

                val token =
                    withTimeoutOrNull(timeout) {
                        var found: String? = null
                        while (found == null) {
                            if (failed.get()) throw SourceException.SourceDown("the WebView could not load the page")
                            // Before the first load the page is blank, and a cookie from an earlier run can't be judged yet.
                            if (loaded.get()) {
                                found =
                                    clearedToken(
                                        cookies = CookieManager.getInstance().getCookie(url.toString()),
                                        // A page moving on can drop the answer, so an answer that never comes means ask again.
                                        onChallengePage =
                                            withTimeoutOrNull(EVALUATE_LIMIT_MS) { webView.evaluate(ON_CHALLENGE_PAGE) } != "false",
                                    )
                            }
                            if (found == null) delay(POLL_MILLIS)
                        }
                        found
                    } ?: return@withContext null

                cookieJar.saveFromResponse(url, listOf(clearanceCookie(url.host, token)))
                val now = System.currentTimeMillis()
                Clearance(
                    cookie = "cf_clearance=$token",
                    userAgent = userAgent,
                    mintedAtMillis = now,
                    expiresAtMillis = now + clearanceLifetime.inWholeMilliseconds,
                ).also {
                    lastByHost[url.host] = it
                    store?.save(lastByHost.filterValues { kept -> kept.isFresh(now) })
                }
            } finally {
                webView.stopLoading()
                webView.destroy()
            }
        }

    // JavaScript is what clears the challenge; there is no bridge from the page back into the app.
    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun prepare(
        webView: WebView,
        failed: AtomicBoolean,
        loaded: AtomicBoolean,
    ) {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = userAgent
        // The challenge page has no business reaching the app's own files.
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.allowFileAccessFromFileURLs = false
        webView.settings.allowUniversalAccessFromFileURLs = false
        webView.webViewClient = ClearerWebClient(failed, loaded)
    }

    private companion object {
        const val POLL_MILLIS = 250L

        // Set by the challenge page in every language, and gone once Cloudflare sends the browser on to the real page.
        const val ON_CHALLENGE_PAGE = "typeof window._cf_chl_opt !== 'undefined'"
    }
}

/** Tells the waiting clearer that the page has loaded, or that it never will. */
internal class ClearerWebClient(
    private val failed: AtomicBoolean,
    private val loaded: AtomicBoolean,
) : WebViewClient() {
    override fun onPageFinished(
        view: WebView,
        url: String,
    ) {
        loaded.set(true)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) failed.set(true)
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        // The renderer died, often a hostile page running itself out of memory. Fail this
        // check rather than hang, and return true so the framework doesn't kill the app.
        failed.set(true)
        return true
    }
}

/**
 * The clearance in [cookies], once it can be trusted. Cloudflare sets a `cf_clearance` while its check is still
 * running, so the cookie alone proves nothing: the check is over only when the browser has left the challenge page.
 */
internal fun clearedToken(
    cookies: String?,
    onChallengePage: Boolean,
): String? = if (onChallengePage) null else cookies?.let(::cfClearanceFrom)

/** Pulls the `cf_clearance` value out of a `getCookie` string such as `a=b; cf_clearance=xyz; c=d`. */
private fun cfClearanceFrom(cookies: String): String? =
    cookies
        .split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("cf_clearance=") }
        ?.substringAfter("=")
        ?.takeIf { it.isNotEmpty() }
