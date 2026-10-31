package ani.saikou.neo.shared.playback

import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.settings.SubOrDub
import ani.saikou.neo.shared.sources.InstalledSource

/** Whether a server is subtitled or dubbed. */
public enum class ServerEdition {
    /** Original audio with separate subtitles. */
    SUB,

    /** Dubbed audio. */
    DUB,

    /** Original audio with subtitles burned into the video. */
    HARD_SUB,
}

/** One place a source can play the episode from. Only the source reads [id]. */
public data class FoundServer(
    val id: String,
    val name: String,
    val edition: ServerEdition,
    /** How long this server's video runs, if the source knows. */
    val runtimeMinutes: Int?,
)

/** Asks an installed source which servers have an episode. The TV app loads the source and calls it. */
public fun interface ServerFinder {
    /** The servers [source] has for [episode] of [show]. Throws when the source can't answer. */
    public suspend fun servers(
        source: InstalledSource,
        show: AnimeDetails,
        episode: Int,
    ): List<FoundServer>
}

/**
 * The server last picked for a show. Server ids change between episodes, so it's kept by name, with its edition since a
 * source can give its Sub and Dub servers the same name.
 */
public data class LastServer(
    val sourceId: String,
    val serverName: String,
    val edition: ServerEdition,
) {
    /** Whether [server] from [source] is this one. */
    public fun isServer(
        source: InstalledSource,
        server: FoundServer,
    ): Boolean = source.id == sourceId && server.name == serverName && server.edition == edition
}

/** The server last picked for each show. */
public interface LastServers {
    public suspend fun get(animeId: Int): LastServer?

    public suspend fun set(
        animeId: Int,
        server: LastServer,
    )
}

/** Whether [edition] is one [this] setting shows. HardSub counts as Sub. */
internal fun SubOrDub.shows(edition: ServerEdition): Boolean =
    when (this) {
        SubOrDub.BOTH -> true
        SubOrDub.SUB -> edition != ServerEdition.DUB
        SubOrDub.DUB -> edition == ServerEdition.DUB
    }

// Separate subtitles come before burned-in ones, and both before a dub.
private val editionOrder = listOf(ServerEdition.SUB, ServerEdition.HARD_SUB, ServerEdition.DUB)

/**
 * The server to suggest from [sources], listed in the order they answered: the [last] one used if it's there, or else
 * the first source with a server to show, its servers in [editionOrder]. Only one of several sources can answer first.
 */
internal fun bestPick(
    sources: List<SourceServers>,
    last: LastServer?,
): BestPick? {
    val found = sources.mapNotNull { entry -> (entry.answer as? SourceAnswer.Found)?.let { entry.source to it.servers } }
    if (last != null) {
        for ((source, servers) in found) {
            val server = servers.firstOrNull { last.isServer(source, it) } ?: continue
            return BestPick(source, server, lastUsed = true, answeredFirst = false)
        }
    }
    val (source, servers) = found.firstOrNull { it.second.isNotEmpty() } ?: return null
    val server = servers.minBy { editionOrder.indexOf(it.edition) }
    val first = sources.size > 1 && sources.first().source == source
    return BestPick(source, server, lastUsed = false, answeredFirst = first)
}
