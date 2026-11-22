package ani.saikou.neo.shared.playback

import kotlinx.coroutines.flow.Flow

/**
 * The server a show last played on: its source, by id, the server's name and edition, and the separate audio track
 * picked by name, if any. Details' main button plays on it (#499), the way Up next does (ADR 0054).
 */
public data class LastServer(
    val sourceId: String,
    val name: String,
    val edition: ServerEdition,
    val audio: String?,
)

/** The server each show last played on, keyed by anime id. */
public interface LastServers {
    public val lastServers: Flow<Map<Int, LastServer>>

    /** Keeps [server] for [animeId], in place of the one before. It's saved even once the caller has gone. */
    public fun saveServer(
        animeId: Int,
        server: LastServer,
    )
}
