package ani.saikou.neo.extension.api

import kotlin.time.Duration.Companion.minutes

/** A source that answers from memory, the way a real one would from its site. */
class FakeExtension : SourceExtension {
    val asked = mutableListOf<Episode>()

    override suspend fun servers(episode: Episode): List<Server> {
        asked += episode
        return listOf(
            Server(id = "sub-1", name = "Main", edition = Edition.Sub, runtime = 24.minutes),
            Server(id = "dub-1", name = "Main", edition = Edition.Dub, runtime = 23.minutes),
            Server(id = "hard-1", name = "Backup", edition = Edition.HardSub, runtime = null),
        )
    }
}
