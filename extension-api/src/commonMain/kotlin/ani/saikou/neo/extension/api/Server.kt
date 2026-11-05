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
    /**
     * The video heights this server offers, such as 1080 and 720, shown next to its name. Only fill this in when
     * the site lists them with its servers. Leave it empty rather than asking the site for more just to find them.
     */
    val qualities: List<Int>,
) {
    /** A server with no qualities listed. Sources built for API version 2 call this, so it has to stay. */
    public constructor(id: String, name: String, edition: Edition, runtime: Duration?) :
        this(id, name, edition, runtime, emptyList())

    /** The `copy` sources built for API version 2 call. Hidden, so new code gets the one with [qualities]. */
    @Deprecated("Kept for sources built for API version 2", level = DeprecationLevel.HIDDEN)
    public fun copy(
        id: String = this.id,
        name: String = this.name,
        edition: Edition = this.edition,
        runtime: Duration? = this.runtime,
    ): Server = Server(id, name, edition, runtime, qualities)
}

public enum class Edition {
    /** Original audio with separate subtitles. */
    Sub,

    /** Dubbed audio. */
    Dub,

    /** Original audio with subtitles burned into the video. */
    HardSub,
}
