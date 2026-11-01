package ani.saikou.neo.extension.api

/**
 * What the app gives a source so it can reach networking the source can't hold
 * itself: one HTTP client with a shared cookie jar and pinned User-Agent, shared
 * Cloudflare clearance, and a WebView that resolves a stream a site only exposes
 * after its own player runs. The app sets it through [SourceExtension.attach]
 * before it asks the source for anything. The calls run off the main thread and
 * may block.
 */
public interface HostServices {
    /**
     * Makes [request] over the app's shared HTTP client. The client carries the
     * shared cookie jar and pinned User-Agent and clears Cloudflare on its own, so
     * a source's requests clear and stay cleared without asking.
     */
    public suspend fun http(request: HostRequest): HostResponse

    /**
     * Resolves [request]'s embed by running the site's own player in an offscreen
     * WebView and reading back what the recipe asks for. Throws
     * [SourceException.SourceDown] when the embed won't load or the wait runs out,
     * and [SourceException.Blocked] when a challenge needs a person.
     */
    public suspend fun extract(request: ExtractionRequest): ExtractionResult
}
