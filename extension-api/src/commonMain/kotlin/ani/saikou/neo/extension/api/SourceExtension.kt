package ani.saikou.neo.extension.api

/**
 * What a source implements. The app calls these off the main thread. They can
 * take a while, so they should check for cancellation rather than time out early.
 */
public interface SourceExtension {
    /** The servers that have [episode]. An empty list means the source doesn't have it. */
    public suspend fun servers(episode: Episode): List<Server>

    /** The stream for one of the servers from [servers]. */
    public suspend fun stream(server: Server): Stream
}
