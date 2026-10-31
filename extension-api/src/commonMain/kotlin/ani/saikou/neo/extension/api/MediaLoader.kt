package ani.saikou.neo.extension.api

/**
 * Fetches media for the player when plain HTTP with headers isn't enough. The
 * player calls it on its own loading thread, so it may block.
 */
public fun interface MediaLoader {
    public fun open(request: MediaRequest): MediaResponse
}

public data class MediaRequest(
    val url: String,
    /** The stream's headers, plus any the player adds, such as Range. */
    val headers: Map<String, String>,
    /** The byte to start from. */
    val position: Long,
    /** How many bytes the player wants, or null for the rest. */
    val length: Long?,
)

public interface MediaResponse {
    /** Bytes left from the requested position, if known. */
    public val length: Long?

    /** Reads up to [length] bytes into [buffer] at [offset]. Returns how many, or [END]. */
    public fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int

    public fun close()

    public companion object {
        /** Returned by [read] when there's nothing left. */
        public const val END: Int = -1
    }
}
