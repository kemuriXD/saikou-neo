package ani.saikou.neo.example

import ani.saikou.neo.extension.api.Episode
import ani.saikou.neo.extension.api.Server
import ani.saikou.neo.extension.api.SourceException
import ani.saikou.neo.extension.api.SourceExtension
import ani.saikou.neo.extension.api.Stream

/** Has no servers for any episode. */
class ExampleSource : SourceExtension {
    override suspend fun servers(episode: Episode): List<Server> = emptyList()

    override suspend fun stream(server: Server): Stream = throw SourceException.NotFound()
}
