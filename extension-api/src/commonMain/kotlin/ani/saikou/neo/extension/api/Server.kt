package ani.saikou.neo.extension.api

import kotlin.time.Duration

/** One place a source can play the episode from. */
public data class Server(
    /** Only the extension reads this. It can hold whatever it needs to find the stream. */
    val id: String,
    /** Shown in the server list. */
    val name: String,
    val edition: Edition,
    /** How long this server's video runs, if known. Servers can have different cuts. */
    val runtime: Duration?,
)

public enum class Edition {
    /** Original audio with separate subtitles. */
    Sub,

    /** Dubbed audio. */
    Dub,

    /** Original audio with subtitles burned into the video. */
    HardSub,
}
