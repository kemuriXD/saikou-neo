package ani.saikou.neo.shared.sources

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import ani.saikou.neo.extension.api.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Mints a Cloudflare clearance by loading the site in an offscreen WebView with
 * the pinned User-Agent and letting Cloudflare's own JavaScript run. When the
 * `cf_clearance` cookie appears it goes into the shared [cookieJar], so the next
 * request and the player send it. It gives up at [timeout] (a challenge needing
 * a person), returning null, and reports a page that won't load as
 * [SourceException.SourceDown]. The WebView has no bridge back into the app and
 * is destroyed after each call.
 */
internal class WebViewCloudflareClearer(
    private val context: Context,
    private val cookieJar: CookieJar,
    private val timeout: Duration = 20.seconds,
    private val clearanceLifetime: Duration = 30.minutes,
) : CloudflareClearer {
    private val lastByHost = mutableMapOf<String, Clearance>()

    override suspend fun clear(url: HttpUrl): Clearance? =
        withContext(Dispatchers.Main) {
            lastByHost[url.host]?.let { if (it.isFresh(System.currentTimeMillis())) return@withContext it }

            val webView = WebView(context.applicationContext)
            try {
                val failed = AtomicBoolean(false)
                prepare(webView, failed)
                webView.loadUrl(url.toString())

                val token =
                    withTimeoutOrNull(timeout) {
                        var found: String? = null
                        while (found == null) {
                            if (failed.get()) throw SourceException.SourceDown("the WebView could not load the page")
                            found = CookieManager.getInstance().getCookie(url.toString())?.let(::cfClearanceFrom)
                            if (found == null) delay(POLL_MILLIS)
                        }
                        found
                    } ?: return@withContext null

                cookieJar.saveFromResponse(
                    url,
                    listOf(
                        Cookie
                            .Builder()
                            .name("cf_clearance")
                            .value(token)
                            .domain(url.host)
                            .build(),
                    ),
                )
                Clearance(
                    cookie = "cf_clearance=$token",
                    userAgent = SOURCE_USER_AGENT,
                    expiresAtMillis = System.currentTimeMillis() + clearanceLifetime.inWholeMilliseconds,
                ).also { lastByHost[url.host] = it }
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
    ) {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = SOURCE_USER_AGENT
        // The challenge page has no business reaching the app's own files.
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.allowFileAccessFromFileURLs = false
        webView.settings.allowUniversalAccessFromFileURLs = false
        webView.webViewClient =
            object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) failed.set(true)
                }
            }
    }

    private companion object {
        const val POLL_MILLIS = 250L
    }
}

/** Pulls the `cf_clearance` value out of a `getCookie` string such as `a=b; cf_clearance=xyz; c=d`. */
private fun cfClearanceFrom(cookies: String): String? =
    cookies
        .split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("cf_clearance=") }
        ?.substringAfter("=")
        ?.takeIf { it.isNotEmpty() }
