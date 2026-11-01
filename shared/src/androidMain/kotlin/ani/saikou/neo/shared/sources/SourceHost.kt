package ani.saikou.neo.shared.sources

import android.content.Context
import ani.saikou.neo.extension.api.ExtractionRequest
import ani.saikou.neo.extension.api.ExtractionResult
import ani.saikou.neo.extension.api.HostRequest
import ani.saikou.neo.extension.api.HostResponse
import ani.saikou.neo.extension.api.HostServices
import okhttp3.OkHttpClient

/**
 * Assembles the one set of host services the app hands to every source, and the
 * single OkHttp [client] the player streams through. One cookie jar, one pinned
 * User-Agent and one Cloudflare clearance are shared across a source's own HTTP,
 * the extraction WebView and playback, so clearance earned anywhere is kept
 * everywhere (ADR 0027, 0029). Hold one per app.
 */
public class SourceHost(
    context: Context,
) {
    private val cookieJar = InMemoryCookieJar()
    private val clearer = WebViewCloudflareClearer(context, cookieJar)

    /** The shared client: the cookie jar, the pinned User-Agent and the Cloudflare retry. Media3 streams through it. */
    public val client: OkHttpClient = sharedHttpClient(cookieJar, clearer)

    private val http = SourceHttpClient(client)
    private val extractor = WebViewStreamExtractor(context)

    /** Handed to each source through `SourceExtension.attach` before the app asks it for anything. */
    public val services: HostServices =
        object : HostServices {
            override suspend fun http(request: HostRequest): HostResponse = http.http(request)

            override suspend fun extract(request: ExtractionRequest): ExtractionResult = extractor.extract(request)
        }
}
