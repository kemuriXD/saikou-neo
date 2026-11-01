package ani.saikou.neo.extension.api

/**
 * What a source implements. The app calls these off the main thread. They can
 * take a while, so they should check for cancellation rather than time out early.
 */
public interface SourceExtension {
    /**
     * Hands the source the app's [HostServices]. The app calls it once, after it
     * builds the source and before [servers] or [stream]. A source that doesn't
     * need host services can ignore it.
     */
    public fun attach(host: HostServices) {}

    /** The servers that have [episode]. An empty list means the source doesn't have it. */
    public suspend fun servers(episode: Episode): List<Server>

    /** The stream for one of the servers from [servers]. */
    public suspend fun stream(server: Server): Stream

    /**
     * A fresh stream for [server] after [expired] stopped working. The app keeps
     * the playback position and quality, so this only needs new links. By
     * default it fetches the stream again.
     */
    public suspend fun refresh(
        server: Server,
        expired: Stream,
    ): Stream = stream(server)
}
