package ani.saikou.neo.shared.sources

import okhttp3.HttpUrl

/**
 * Mints a Cloudflare clearance for [url] by running the site in an offscreen
 * WebView and reading its `cf_clearance` cookie into the shared cookie jar.
 * Returns null when it can't clear on its own, which on a TV means a person is
 * needed. Throws `SourceException.SourceDown` when the page itself won't load.
 */
internal fun interface CloudflareClearer {
    suspend fun clear(url: HttpUrl): Clearance?
}
